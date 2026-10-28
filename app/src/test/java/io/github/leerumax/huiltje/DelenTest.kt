package io.github.leerumax.huiltje

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** Twee "telefoons" (twee databases) die over een echte socket op localhost met elkaar bijwerken. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DelenTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var a: Db
    private lateinit var b: Db
    private val sleutel = Code.sleutel("ABCD-EFGH-JKMN")

    @Before
    fun zetOp() {
        Db.naWijziging = null
        a = Db(ctx, "a-${System.nanoTime()}.db").apply { set("naam", "Ouder A") }
        b = Db(ctx, "b-${System.nanoTime()}.db").apply { set("naam", "Ouder B") }
    }

    private fun sync(): Pair<String, String> {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { srv ->
            var bZag = ""
            val t = thread {
                srv.accept().use { s -> bZag = Protocol.alsGever(b, sleutel, DataInputStream(s.getInputStream().buffered()), DataOutputStream(s.getOutputStream().buffered())) }
            }
            val aZag = Socket(InetAddress.getLoopbackAddress(), srv.localPort).use { s ->
                Protocol.alsVrager(a, sleutel, DataInputStream(s.getInputStream().buffered()), DataOutputStream(s.getOutputStream().buffered()), null)
            }
            t.join(10_000)
            return aZag to bZag
        }
    }

    @Test
    fun beideKantenKrijgenAllesEnWieWintKlopt() {
        a.put(Ev("x", T.LUIER, 1000, kant = "nat"))
        val emb = FloatArray(1024) { it / 1024f }
        b.put(Ev("huil-1", T.HUIL, 2000, label = 2, probs = floatArrayOf(.1f, .1f, .6f, .1f, .1f), emb = emb))
        val (aZag, bZag) = sync()
        assertEquals(b.dev, aZag)
        assertEquals(a.dev, bZag)
        assertEquals("nat", b.een("x")!!.kant)
        assertEquals("Ouder A", b.een("x")!!.wie)
        assertTrue(a.een("huil-1")!!.emb!!.contentEquals(emb))
        assertEquals(1, a.eigenVoorbeelden().samples.size) // bevestiging van B leert ook de telefoon van A

        // Allebei dezelfde regel gewijzigd: de laatste wijziging wint, op beide telefoons.
        a.put(a.een("x")!!.copy(kant = "poep"))
        Thread.sleep(5)
        b.put(b.een("x")!!.copy(kant = "nat", ml = 1))
        sync()
        assertEquals(1, a.een("x")!!.ml)
        assertEquals(1, b.een("x")!!.ml)

        // Weghalen gaat ook mee.
        a.put(a.een("x")!!.copy(weg = true))
        sync()
        assertTrue(b.alle().none { it.id == "x" })

        // Daarna niets meer te doen: geen eindeloos heen en weer.
        sync()
        val voor = a.sinds(0).second to b.sinds(0).second
        sync()
        assertEquals(voor, a.sinds(0).second to b.sinds(0).second)
    }

    @Test
    fun verkeerdeCodeKrijgtNiets() {
        a.put(Ev("geheim", T.LUIER, 1000, kant = "nat"))
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { srv ->
            val t = thread { srv.accept().use { s -> runCatching { Protocol.alsGever(a, sleutel, DataInputStream(s.getInputStream()), DataOutputStream(s.getOutputStream())) } } }
            val r = runCatching {
                Socket(InetAddress.getLoopbackAddress(), srv.localPort).use { s ->
                    Protocol.alsVrager(b, Code.sleutel("ZZZZ-ZZZZ-ZZZZ"), DataInputStream(s.getInputStream()), DataOutputStream(s.getOutputStream()), null)
                }
            }
            t.join(10_000)
            assertTrue(r.isFailure)
        }
        assertTrue(b.alle().none { it.id == "geheim" })
    }

    @Test
    fun backupHeenEnTerug() {
        a.put(Ev("f", T.FLES, 1000, ml = 120, kant = "moedermelk"), Ev("g", T.GROEI, 2000, gram = 4210, cm = 54.5f))
        val zip = ByteArrayOutputStream().also { Backup.maak(ctx, a, it) }.toByteArray()
        assertEquals(2, Backup.terug(ctx, b, ByteArrayInputStream(zip)))
        assertEquals(120, b.een("f")!!.ml)
        assertEquals(54.5f, b.een("g")!!.cm!!, 1e-4f)
        assertEquals(0, Backup.terug(ctx, b, ByteArrayInputStream(zip))) // nog een keer: niets dubbel
    }

    /** Zoals op de telefoon: echte haak van HuiltjeApp actief, oude csv aanwezig, eerste Db.get bij het openen. */
    @Test(timeout = 30_000)
    fun opstartenMetOudeCsvLooptNietRond() {
        Db.vergeet()
        ApplicationProvider.getApplicationContext<HuiltjeApp>().onCreate()
        java.io.File(ctx.filesDir, "geschiedenis.csv").writeText("1700000002000;0.1,0.2,0.3,0.2,0.2;2\n")
        val db = Db.get(ctx)
        assertEquals(1, db.alle().count { it.id == "huil-1700000002000" })
        assertTrue(java.io.File(ctx.filesDir, "geschiedenis.csv.oud").exists())
        Db.naWijziging = null
    }

    @Test
    fun oudeCsvWordtOvergezet() {
        val dir = ctx.filesDir
        java.io.File(dir, "geschiedenis.csv").writeText("1700000000000;0.1,0.2,0.3,0.2,0.2;2\n1700000001000;0.5,0.1,0.1,0.2,0.1;\n")
        val opn = opnamesMap(ctx)
        java.io.File(opn, "1700000000000.emb").writeText((0 until 1024).joinToString(",") { "0.5" })
        val c = Db(ctx, "m-${System.nanoTime()}.db")
        c.migreerCsv(ctx)
        assertEquals(2, c.alle().count { it.type == T.HUIL })
        assertNotNull(c.een("huil-1700000000000")!!.emb)
        assertEquals(1, c.eigenVoorbeelden().samples.size)
        assertTrue(java.io.File(dir, "geschiedenis.csv.oud").exists())
    }
}

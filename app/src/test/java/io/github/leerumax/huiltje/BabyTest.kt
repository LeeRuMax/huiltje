package io.github.leerumax.huiltje

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class BabyTest {
    private val t0 = 1_800_000_000_000L

    /** Past tik() toe zoals de app: oude versie van een regel vervangen door de nieuwe. */
    private fun MutableList<Ev>.tikOp(soort: String, kant: String, t: Long) {
        tik(this, soort, kant, t, "Ouder A").forEach { n -> removeAll { it.id == n.id }; add(n) }
    }

    @Test
    fun borstEenKantTegelijkEnBegonEindigde() {
        val evs = mutableListOf<Ev>()
        evs.tikOp(T.BORST, "L", t0)
        evs.tikOp(T.BORST, "R", t0 + 10 * MIN) // wissel: L stopt
        val s = sessies(evs, T.BORST).single()
        assertEquals(1, s.lopend.size)
        assertEquals("R", s.loopt!!.kant)
        assertEquals(10 * MIN, s.ms("L", t0 + 15 * MIN))
        assertEquals(5 * MIN, s.ms("R", t0 + 15 * MIN))
        evs.tikOp(T.BORST, "R", t0 + 18 * MIN) // pauze
        val s2 = sessies(evs, T.BORST).single()
        assertNull(s2.loopt)
        assertEquals("L", s2.begon)
        assertEquals("R", s2.eindigde)
        assertEquals("R", volgendeKant(evs)) // beide kanten: begin waar je eindigde
    }

    @Test
    fun kolvenBeideTegelijkElkeKantLos() {
        val evs = mutableListOf<Ev>()
        evs.tikOp(T.KOLF, "L", t0)
        evs.tikOp(T.KOLF, "R", t0 + 1 * MIN) // L blijft lopen
        assertEquals(2, sessies(evs, T.KOLF).single().lopend.size)
        evs.tikOp(T.KOLF, "L", t0 + 12 * MIN) // L stopt eerst
        evs.tikOp(T.KOLF, "R", t0 + 14 * MIN) // R stopt als laatste
        val s = sessies(evs, T.KOLF).single()
        assertEquals("L", s.begon)
        assertEquals("R", s.eindigde)
        assertEquals(12 * MIN, s.ms("L", t0 + 20 * MIN))
        assertEquals(13 * MIN, s.ms("R", t0 + 20 * MIN))
        val dicht = klaar(s, t0 + 20 * MIN).single { it.id == s.ev.id }
        assertEquals(t0 + 14 * MIN, dicht.eind) // niets liep meer: einde = laatste stop
    }

    @Test
    fun vergetenKlaarWordtNieuweSessie() {
        val evs = mutableListOf<Ev>()
        evs.tikOp(T.BORST, "L", t0)
        evs.tikOp(T.BORST, "L", t0 + 15 * MIN) // pauze, daarna vergeten op Klaar te drukken
        evs.tikOp(T.BORST, "R", t0 + 3 * UUR)
        val ss = sessies(evs, T.BORST)
        assertEquals(2, ss.size)
        assertEquals(t0 + 15 * MIN, ss[0].ev.eind)
        assertEquals("L", volgendeKant(evs)) // alleen rechts gegeven: volgende keer links
    }

    @Test
    fun achterafInvullenEnAanpassen() {
        val evs = sessieUitInvoer(T.BORST, null, t0, 12, 8, "R", 0, 0).toMutableList()
        var s = sessies(evs, T.BORST).single()
        assertEquals("R", s.begon)
        assertEquals("L", s.eindigde)
        assertEquals(t0 + 20 * MIN, s.ev.eind)
        assertEquals(12 * MIN, s.ms("L", t0))
        // Aanpassen: alleen links, 15 min. Zelfde sessie, oude kanten weg.
        sessieUitInvoer(T.BORST, s, t0, 15, 0, "R", 0, 0).forEach { n -> evs.removeAll { it.id == n.id }; evs += n }
        s = sessies(evs, T.BORST).single()
        assertEquals(1, s.delen.size)
        assertEquals("L", s.begon)
        assertEquals("R", volgendeKant(evs))
        val k = sessieUitInvoer(T.KOLF, null, t0, 10, 10, "L", 60, 50).first()
        assertEquals(60, k.ml)
        assertEquals(50, k.ml2)
    }

    @Test
    fun klokMaaktHongerWaarschijnlijkerNaLangeTijd() {
        val p = floatArrayOf(0.3f, 0.3f, 0.2f, 0.1f, 0.1f)
        val kort = metKlok(p, klokFactor(10, 10))
        val lang = metKlok(p, klokFactor(200, 10))
        assertTrue(lang[0] > p[0] && kort[0] < p[0])
        assertTrue(kort[4] > p[4]) // vlak na de voeding: boertje
        assertEquals(1f, lang.sum(), 1e-5f)
        assertTrue(metKlok(p, klokFactor(60, 150))[1] > p[1]) // lang wakker: moe
    }

    @Test
    fun bewaartermijnen() {
        val v = Ev("v", T.VOORRAAD, t0, kant = "koelkast", sinds = t0)
        assertEquals(t0 + 3 * DAG, opTot(v))
        assertEquals(t0 + 5 * UUR + DAG, opTot(v.copy(kant = "ontdooid", sinds = t0 + 5 * UUR)))
        assertEquals(t0 + 14 * DAG, opTot(v.copy(kant = "vriesvak")))
    }

    @Test
    fun whoMediaanBijGeboorte() {
        assertEquals(3.3464f, lmsLijn(WFA_JONGEN, 0f)[0], 1e-3f)
        assertEquals(49.1477f, lmsLijn(LFA_MEISJE, 0f)[0], 1e-3f)
        val p97 = lmsLijn(WFA_MEISJE, 1.881f)
        assertTrue(p97[52] > lmsLijn(WFA_MEISJE, 0f)[52])
        assertEquals(53, p97.size)
    }

    @Test
    fun codeEnVersleuteling() {
        val c = Code.nieuw()
        assertNotNull(Code.schoon(c))
        assertEquals(Code.schoon(c), Code.schoon(c.lowercase().replace("-", " ")))
        assertNull(Code.schoon("ABC"))
        val k = Code.sleutel(c)
        val buf = ByteArrayOutputStream()
        Kanaal.stuur(DataOutputStream(buf), k, org.json.JSONObject().put("hoi", "daar"))
        assertEquals("daar", Kanaal.lees(DataInputStream(ByteArrayInputStream(buf.toByteArray())), k).getString("hoi"))
        val fout = runCatching { Kanaal.lees(DataInputStream(ByteArrayInputStream(buf.toByteArray())), Code.sleutel(Code.nieuw())) }
        assertTrue("verkeerde code mag niets lezen", fout.isFailure)
    }

    @Test
    fun inzichtNaVijfKeer() {
        val evs = (0 until 6).map { i -> Ev("h$i", T.HUIL, t0 - i * DAG, label = 3) } // elke dag zelfde uur
        assertTrue(inzichten(evs, t0 + MIN).any { it.contains("Krampjes") })
    }
}

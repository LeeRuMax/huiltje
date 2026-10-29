package io.github.leerumax.huiltje

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

const val TCP_POORT = 47320
const val UDP_POORT = 47321

/**
 * De partnercode is het gedeelde geheim: 12 tekens (60 bits) → sleutel via PBKDF2. Alles tussen de telefoons is
 * AES-GCM met die sleutel, dus wie de code niet heeft kan niets lezen of erbij schrijven.
 */
object Code {
    private const val TEKENS = "0123456789ABCDEFGHJKMNPQRSTVWXYZ" // Crockford: geen I, L, O, U

    fun nieuw(): String {
        val r = SecureRandom()
        return (1..12).map { TEKENS[r.nextInt(32)] }.joinToString("").chunked(4).joinToString("-")
    }

    /** Hoofdletters, streepjes en spaties weg, verwarrende tekens gelijkgetrokken. Null als het geen geldige code is. */
    fun schoon(invoer: String): String? {
        val s = invoer.uppercase().filter { it.isLetterOrDigit() }.map { c ->
            when (c) { 'O' -> '0'; 'I', 'L' -> '1'; else -> c }
        }.joinToString("")
        return s.takeIf { it.length == 12 && it.all { c -> c in TEKENS } }
    }

    fun sleutel(code: String): ByteArray = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(PBEKeySpec(schoon(code)!!.toCharArray(), "huiltje-partner-v1".toByteArray(), 150_000, 256)).encoded

    /** Herkenning in het zoekbericht op de wifi, zonder de sleutel prijs te geven. */
    fun paarId(sleutel: ByteArray): String = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(sleutel, "HmacSHA256"))
        doFinal("zoek".toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    }
}

/** Eén bericht = lengte + nonce + AES-GCM(gzip(json)). */
object Kanaal {
    private val rnd = SecureRandom()
    private val AAD = "huiltje1".toByteArray()

    /** nonce + AES-GCM(gzip(json)). Ook gebruikt voor de babyfoon-berichten via ntfy. */
    fun dicht(sleutel: ByteArray, o: JSONObject): ByteArray {
        val bytes = ByteArrayOutputStream().also { b -> GZIPOutputStream(b).use { it.write(o.toString().toByteArray()) } }.toByteArray()
        val nonce = ByteArray(12).also(rnd::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sleutel, "AES"), GCMParameterSpec(128, nonce))
        c.updateAAD(AAD)
        return nonce + c.doFinal(bytes)
    }

    /** Gooit bij een verkeerde sleutel of geknoei. */
    fun open(sleutel: ByteArray, buf: ByteArray): JSONObject {
        require(buf.size >= 29) { "te kort" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(sleutel, "AES"), GCMParameterSpec(128, buf, 0, 12))
        c.updateAAD(AAD)
        val plain = c.doFinal(buf, 12, buf.size - 12)
        return JSONObject(GZIPInputStream(ByteArrayInputStream(plain)).readBytes().decodeToString())
    }

    fun stuur(out: DataOutputStream, sleutel: ByteArray, o: JSONObject) {
        val b = dicht(sleutel, o)
        out.writeInt(b.size)
        out.write(b)
        out.flush()
    }

    fun lees(inp: DataInputStream, sleutel: ByteArray): JSONObject {
        val n = inp.readInt()
        require(n in 29..64_000_000) { "vreemde lengte" }
        return open(sleutel, ByteArray(n).also(inp::readFully))
    }
}

/**
 * Twee rondes: A vraagt "alles van jou na N", B stuurt dat en vraagt hetzelfde terug. Elke kant onthoudt per partner
 * tot welk volgnummer hij alles heeft (wm.<dev>). Werkt voor meer dan twee toestellen met dezelfde code.
 */
object Protocol {
    fun alsVrager(db: Db, sleutel: ByteArray, inp: DataInputStream, out: DataOutputStream, hun: String?): String {
        Kanaal.stuur(out, sleutel, JSONObject().put("dev", db.dev).put("naam", db.mijnNaam ?: "").put("na", wm(db, hun)))
        val a = Kanaal.lees(inp, sleutel)
        val dev = a.getString("dev")
        require(dev != db.dev) { "ik praat met mezelf" }
        db.merge(rijenVan(a.getJSONArray("rijen")))
        db.set("wm.$dev", a.getLong("max").toString())
        db.set("peer.$dev.naam", a.optString("naam"))
        val (rijen, max) = db.sinds(a.getLong("na"))
        Kanaal.stuur(out, sleutel, JSONObject().put("rijen", rijenJson(rijen)).put("max", max))
        return dev
    }

    fun alsGever(db: Db, sleutel: ByteArray, inp: DataInputStream, out: DataOutputStream): String {
        val h = Kanaal.lees(inp, sleutel)
        val dev = h.getString("dev")
        require(dev != db.dev) { "ik praat met mezelf" }
        val (rijen, max) = db.sinds(h.getLong("na"))
        Kanaal.stuur(out, sleutel, JSONObject().put("dev", db.dev).put("naam", db.mijnNaam ?: "").put("rijen", rijenJson(rijen)).put("max", max).put("na", wm(db, dev)))
        val b = Kanaal.lees(inp, sleutel)
        db.merge(rijenVan(b.getJSONArray("rijen")))
        db.set("wm.$dev", b.getLong("max").toString())
        db.set("peer.$dev.naam", h.optString("naam"))
        return dev
    }

    private fun wm(db: Db, dev: String?) = dev?.let { db.get("wm.$it")?.toLong() } ?: 0L
}

/** Delen: alleen het eigen netwerk thuis, nooit iets naar of van een internetadres. (De babyfoon is apart: Babyfoon.kt.) */
fun privéAdres(a: InetAddress) = a.isSiteLocalAddress || a.isLinkLocalAddress || a.isLoopbackAddress

object Delen {
    private val werk = Executors.newSingleThreadExecutor()
    @Volatile private var laatstGezocht = 0L
    private val gepland = java.util.concurrent.atomic.AtomicBoolean(false)

    fun sleutel(db: Db): ByteArray? = db.get("partner.sleutel")?.let { java.util.Base64.getDecoder().decode(it) }

    fun koppel(ctx: Context, code: String) {
        val db = Db.get(ctx)
        db.set("partner.sleutel", java.util.Base64.getEncoder().encodeToString(Code.sleutel(code)))
        db.set("partner.code", Code.schoon(code)!!.chunked(4).joinToString("-"))
        DelenService.start(ctx)
        nu(ctx)
    }

    fun ontkoppel(ctx: Context) {
        val db = Db.get(ctx)
        (db.keys("partner.") + db.keys("peer.") + db.keys("wm.")).forEach { db.set(it, null) }
        ctx.stopService(Intent(ctx, DelenService::class.java))
    }

    /** Probeer alle bekende partners en roep op de wifi. Op de achtergrond, nooit dubbel tegelijk. */
    fun nu(ctx: Context) {
        val app = ctx.applicationContext
        if (!gepland.compareAndSet(false, true)) return
        werk.execute {
            gepland.set(false)
            val db = Db.get(app)
            val sleutel = sleutel(db) ?: return@execute
            val wake = (app.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "huiltje:delen").apply { acquire(60_000) }
            try {
                db.keys("peer.").filter { it.endsWith(".ip") }.forEach { k ->
                    val dev = k.removePrefix("peer.").removeSuffix(".ip")
                    runCatching { verbind(app, sleutel, InetAddress.getByName(db.get(k)), TCP_POORT, dev) }
                }
                roep(sleutel, db.dev)
            } finally {
                wake.release()
            }
        }
    }

    fun naRoep(ctx: Context, van: InetAddress, dev: String) {
        val app = ctx.applicationContext
        werk.execute { sleutel(Db.get(app))?.let { runCatching { verbind(app, it, van, TCP_POORT, dev) } } }
    }

    private fun verbind(ctx: Context, sleutel: ByteArray, adres: InetAddress, poort: Int, dev: String?) {
        require(privéAdres(adres))
        Socket().use { s ->
            s.connect(InetSocketAddress(adres, poort), 3000)
            s.soTimeout = 15_000
            val hun = Protocol.alsVrager(Db.get(ctx), sleutel, DataInputStream(s.getInputStream().buffered()), DataOutputStream(s.getOutputStream().buffered()), dev)
            gelukt(ctx, hun, adres)
        }
    }

    fun gelukt(ctx: Context, dev: String, adres: InetAddress) {
        val db = Db.get(ctx)
        db.set("peer.$dev.ip", adres.hostAddress)
        db.set("peer.$dev.laatst", System.currentTimeMillis().toString())
        DelenService.melding(ctx)
    }

    /** "HUILTJE1 <paarId> <dev>" als broadcast op elk eigen IPv4-netwerk. */
    private fun roep(sleutel: ByteArray, dev: String) {
        val nu = System.currentTimeMillis()
        if (nu - laatstGezocht < 5_000) return
        laatstGezocht = nu
        val bericht = "HUILTJE1 ${Code.paarId(sleutel)} $dev".toByteArray()
        DatagramSocket().use { s ->
            s.broadcast = true
            val doelen = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }.filter { it.address is Inet4Address }.mapNotNull { it.broadcast }
            (doelen + InetAddress.getByName("255.255.255.255")).distinct().forEach { b ->
                runCatching { s.send(DatagramPacket(bericht, bericht.size, b, UDP_POORT)) }
            }
        }
    }

    fun peers(db: Db): List<Triple<String, String, Long?>> = db.keys("peer.").filter { it.endsWith(".naam") }.map { k ->
        val dev = k.removePrefix("peer.").removeSuffix(".naam")
        Triple(dev, db.get(k).orEmpty().ifBlank { "partner" }, db.get("peer.$dev.laatst")?.toLong())
    }
}

/** Luistert op de wifi zolang er een partner gekoppeld is; staat als stille melding in de balk (verplicht voor Android). */
class DelenService : Service() {
    companion object {
        private const val KANAAL = "delen"

        fun start(ctx: Context) {
            if (Delen.sleutel(Db.get(ctx)) == null) return
            runCatching { ctx.startForegroundService(Intent(ctx, DelenService::class.java)) }
        }

        @Volatile var draait = false

        fun melding(ctx: Context) {
            if (draait) ctx.getSystemService(NotificationManager::class.java).notify(1, bericht(ctx))
        }

        private fun bericht(ctx: Context): Notification {
            ctx.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(KANAAL, "Delen met partner", NotificationManager.IMPORTANCE_MIN))
            val db = Db.get(ctx)
            val laatst = Delen.peers(db).maxByOrNull { it.third ?: 0 }
            val tekst = laatst?.third?.let { "Bijgewerkt met ${laatst.second}, ${duur(System.currentTimeMillis() - it)} geleden" }
                ?: "Wacht op de telefoon van je partner (zelfde wifi)"
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            return Notification.Builder(ctx, KANAAL).setSmallIcon(R.drawable.ic_voor).setContentTitle("Huiltje deelt")
                .setContentText(tekst).setContentIntent(open).setOngoing(true).build()
        }
    }

    private var server: ServerSocket? = null
    private var udp: DatagramSocket? = null
    private var netCb: ConnectivityManager.NetworkCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("WakelockTimeout")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = bericht(this)
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(1, n)
        draait = true
        if (server == null) runCatching { luister() }
        Delen.nu(this)
        return START_STICKY
    }

    private fun luister() {
        val app = applicationContext
        val db = Db.get(app)
        val srv = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(TCP_POORT)) }
        server = srv
        Thread({
            while (true) {
                val s = runCatching { srv.accept() }.getOrNull() ?: break
                Thread {
                    s.use {
                        if (!privéAdres(s.inetAddress)) return@use
                        val sleutel = Delen.sleutel(db) ?: return@use
                        s.soTimeout = 15_000
                        runCatching {
                            val dev = Protocol.alsGever(db, sleutel, DataInputStream(s.getInputStream().buffered()), DataOutputStream(s.getOutputStream().buffered()))
                            Delen.gelukt(app, dev, s.inetAddress)
                        }
                    }
                }.start()
            }
        }, "huiltje-tcp").start()

        val sock = DatagramSocket(null).apply { reuseAddress = true; broadcast = true; bind(InetSocketAddress(UDP_POORT)) }
        udp = sock
        Thread({
            val buf = ByteArray(256)
            while (!sock.isClosed) {
                val p = DatagramPacket(buf, buf.size)
                if (runCatching { sock.receive(p) }.isFailure) continue
                val delen = String(p.data, 0, p.length).split(' ')
                val sleutel = Delen.sleutel(db) ?: continue
                if (delen.size == 3 && delen[0] == "HUILTJE1" && delen[1] == Code.paarId(sleutel) && delen[2] != db.dev && privéAdres(p.address)) {
                    Delen.naRoep(app, p.address, delen[2])
                }
            }
        }, "huiltje-udp").start()

        // Terug op de wifi thuis: meteen bijwerken.
        netCb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = Delen.nu(app)
        }
        getSystemService(ConnectivityManager::class.java).registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), netCb!!,
        )
    }

    override fun onDestroy() {
        draait = false
        runCatching { server?.close() }
        runCatching { udp?.close() }
        netCb?.let { runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } }
        server = null
        udp = null
        super.onDestroy()
    }
}

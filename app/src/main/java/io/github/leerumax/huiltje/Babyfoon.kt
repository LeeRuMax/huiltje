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
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.AudioTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import kotlin.math.sqrt

/*
 * Babyfoon: één gekoppelde telefoon is camera, de andere(n) kijken en kunnen terugpraten.
 * - Signalering via ntfy (topic afgeleid van de partnersleutel); elk bericht is AES-GCM met de partnersleutel, dus de
 *   ntfy-server ziet alleen ruis en kan niets vervalsen.
 * - Beeld en geluid via WebRTC (DTLS-SRTP). De DTLS-vingerafdrukken zitten in de versleutelde SDP, dus een server
 *   of relay ertussen kan niet meekijken of zich als de andere telefoon voordoen.
 * - Geen trickle-ICE: eerst alle kandidaten verzamelen, dan één offer/answer (twee berichten per verbinding).
 */

/** Babyfoon-servers, als logregel "server" gedeeld met de partners (niet in de backup). */
data class Server(
    val ntfy: String = "https://ntfy.sh", val ntfyToken: String = "",
    val turn: String = "", val turnUser: String = "", val turnPass: String = "",
) {
    fun json(): String = JSONObject().put("ntfy", ntfy).put("ntfyToken", ntfyToken).put("turn", turn)
        .put("turnUser", turnUser).put("turnPass", turnPass).toString()

    /** TURN als relay én als STUN (eigen publiek adres), zodat ICE eerst rechtstreeks probeert en de relay alleen invalt. */
    fun iceServers(): List<PeerConnection.IceServer> = turn.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.flatMap { u ->
        listOf(
            PeerConnection.IceServer.builder(u).setUsername(turnUser).setPassword(turnPass).createIceServer(),
            PeerConnection.IceServer.builder("stun:" + u.substringAfter(':').substringBefore('?')).createIceServer(),
        )
    }

    companion object {
        fun van(evs: List<Ev>): Server = evs.find { it.type == T.SERVER && !it.weg }?.tekst
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.let { Server(it.optString("ntfy", "https://ntfy.sh").ifBlank { "https://ntfy.sh" }, it.optString("ntfyToken"), it.optString("turn"), it.optString("turnUser"), it.optString("turnPass")) }
            ?: Server()
    }
}

/** Versleutelde signaalberichten. Puur, dus testbaar op de JVM. */
object Bericht {
    const val MAX_LEEFTIJD = 60_000L

    fun topic(sleutel: ByteArray): String = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(sleutel, "HmacSHA256"))
        "huiltje-" + doFinal("babyfoon".toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    }

    fun maak(sleutel: ByteArray, o: JSONObject, nu: Long = System.currentTimeMillis()): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(Kanaal.dicht(sleutel, o.put("ts", nu)))

    /** ntfy maakt van berichten > 4096 bytes een bijlage; een offer met alle codecs is groter → in stukken sturen. */
    fun knip(tekst: String, max: Int = 3500): List<String> {
        if (tekst.length <= max) return listOf(tekst)
        val id = java.util.UUID.randomUUID().toString().take(8)
        val stukken = tekst.chunked(max)
        return stukken.mapIndexed { i, d -> "~$id~$i~${stukken.size}~$d" }
    }

    /** Stuk binnen: geeft het hele bericht terug zodra alle stukken er zijn (anders null). */
    fun plak(deel: String, buf: MutableMap<String, Array<String?>>): String? {
        if (!deel.startsWith("~")) return deel
        val p = deel.split('~', limit = 5)
        if (p.size != 5) return null
        val n = p[3].toIntOrNull()?.takeIf { it in 1..20 } ?: return null
        val i = p[2].toIntOrNull()?.takeIf { it in 0 until n } ?: return null
        if (buf.size > 20) buf.clear() // ponytail: simpele grens tegen half aangekomen rommel
        val a = buf.getOrPut(p[1]) { arrayOfNulls(n) }
        if (a.size != n) return null
        a[i] = p[4]
        if (a.any { it == null }) return null
        buf.remove(p[1])
        return a.joinToString("")
    }

    /** Null als het niet van een partner is, te oud, van mezelf, of voor een andere telefoon. */
    fun lees(sleutel: ByteArray, tekst: String, ik: String, nu: Long = System.currentTimeMillis()): JSONObject? {
        val o = runCatching { Kanaal.open(sleutel, Base64.getUrlDecoder().decode(tekst.trim())) }.getOrNull() ?: return null
        if (abs(nu - o.optLong("ts")) > MAX_LEEFTIJD) return null
        if (o.optString("van") == ik) return null
        if (o.has("naar") && o.optString("naar") != ik) return null
        return o
    }
}

/** Mono PCM16 naar 16 kHz floats (lineair), voor YAMNet. */
fun naar16k(pcm: ShortArray, rate: Int): FloatArray {
    if (rate == SAMPLE_RATE) return FloatArray(pcm.size) { pcm[it] / 32768f }
    val n = (pcm.size.toLong() * SAMPLE_RATE / rate).toInt()
    val stap = rate.toDouble() / SAMPLE_RATE
    return FloatArray(n) { i ->
        val x = i * stap
        val a = x.toInt().coerceAtMost(pcm.size - 1)
        val b = (a + 1).coerceAtMost(pcm.size - 1)
        val f = (x - a).toFloat()
        (pcm[a] * (1 - f) + pcm[b] * f) / 32768f
    }
}

/** ntfy als brievenbus: publiceren en een doorlopende JSON-stroom lezen. */
class Ntfy(private val s: Server, private val topic: String) {
    private val basis get() = s.ntfy.trimEnd('/') + "/" + topic

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        if (s.ntfyToken.isNotBlank()) setRequestProperty("Authorization", "Bearer " + s.ntfyToken)
        connectTimeout = 10_000
    }

    fun stuur(tekst: String) {
        val c = open(basis)
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("X-Cache", "no") // niet bewaren: alleen wie nu luistert krijgt het
        c.setRequestProperty("X-Firebase", "no")
        c.outputStream.use { it.write(tekst.toByteArray()) }
        val code = c.responseCode
        c.disconnect()
        require(code in 200..299) { "ntfy $code" }
    }

    /** Blijft lezen tot [stop]; bij een fout na 3 s opnieuw. */
    fun luister(stop: () -> Boolean, bij: (String) -> Unit) {
        while (!stop()) {
            runCatching {
                val c = open("$basis/json")
                c.readTimeout = 90_000
                c.inputStream.bufferedReader().useLines { regels ->
                    for (r in regels) {
                        if (stop()) break
                        val o = runCatching { JSONObject(r) }.getOrNull() ?: continue
                        if (o.optString("event") == "message") bij(o.optString("message"))
                    }
                }
            }
            if (!stop()) Thread.sleep(3_000)
        }
    }
}

/** Gedeelde WebRTC-fabriek; [opname] krijgt de microfoon-PCM (alleen als er geluid verstuurd wordt). */
object Rtc {
    lateinit var egl: EglBase
    lateinit var fabriek: PeerConnectionFactory
    @Volatile var opname: ((ShortArray, Int) -> Unit)? = null
    private var klaar = false

    @Synchronized
    fun init(ctx: Context) {
        if (klaar) return
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(ctx.applicationContext).createInitializationOptions())
        egl = EglBase.create()
        val adm = JavaAudioDeviceModule.builder(ctx.applicationContext)
            .setUseHardwareNoiseSuppressor(false) // zachte babygeluidjes niet wegfilteren
            .setSamplesReadyCallback { s -> opname?.invoke(mono(s.data, s.channelCount), s.sampleRate) }
            .createAudioDeviceModule()
        fabriek = PeerConnectionFactory.builder().setAudioDeviceModule(adm)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
        klaar = true
    }

    private fun mono(data: ByteArray, kanalen: Int): ShortArray {
        val b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val k = kanalen.coerceAtLeast(1)
        return ShortArray(b.remaining() / k) { b.get(it * k) }
    }

    /** Geluid over de luidspreker (camera: terugpraten horen; kijker: de baby horen). */
    fun luidspreker(ctx: Context, aan: Boolean) {
        val am = ctx.getSystemService(AudioManager::class.java)
        am.mode = if (aan) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
        if (Build.VERSION.SDK_INT >= 31) {
            if (aan) am.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.let { am.setCommunicationDevice(it) }
            else am.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = aan
        }
    }

    fun audioSpoor(id: String): AudioTrack = fabriek.createAudioTrack(id, fabriek.createAudioSource(MediaConstraints()))
}

/** Eén WebRTC-verbinding met een andere telefoon. */
class Verbinding(server: Server, val met: String, private val bij: Bij) : PeerConnection.Observer {
    interface Bij {
        fun spoor(v: Verbinding, t: MediaStreamTrack) {}
        fun kanaal(v: Verbinding, dc: DataChannel) {}
        fun toestand(v: Verbinding, s: PeerConnection.PeerConnectionState) {}
    }

    private val verzameld = CountDownLatch(1)
    val pc: PeerConnection = Rtc.fabriek.createPeerConnection(
        PeerConnection.RTCConfiguration(server.iceServers()).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN },
        this,
    )!!
    var dc: DataChannel? = null

    /** Offer of answer maken, lokaal zetten en wachten tot alle ICE-kandidaten erin staan. */
    fun lokaal(offer: Boolean): String {
        var sdp: SessionDescription? = null
        val l = CountDownLatch(1)
        val obs = object : Sdp() { override fun onCreateSuccess(d: SessionDescription) { sdp = d; l.countDown() } override fun onCreateFailure(e: String?) { l.countDown() } }
        if (offer) pc.createOffer(obs, MediaConstraints()) else pc.createAnswer(obs, MediaConstraints())
        l.await(10, TimeUnit.SECONDS)
        zet(true, sdp ?: error("geen sdp"))
        verzameld.await(8, TimeUnit.SECONDS)
        return pc.localDescription.description
    }

    fun remote(offer: Boolean, sdp: String) = zet(false, SessionDescription(if (offer) SessionDescription.Type.OFFER else SessionDescription.Type.ANSWER, sdp))

    private fun zet(lokaal: Boolean, d: SessionDescription) {
        val l = CountDownLatch(1)
        var fout: String? = null
        val obs = object : Sdp() { override fun onSetSuccess() { l.countDown() } override fun onSetFailure(e: String?) { fout = e; l.countDown() } }
        if (lokaal) pc.setLocalDescription(obs, d) else pc.setRemoteDescription(obs, d)
        l.await(10, TimeUnit.SECONDS)
        fout?.let { error("sdp: $it") }
    }

    fun stuur(o: JSONObject) {
        val d = dc ?: return
        if (d.state() == DataChannel.State.OPEN) d.send(DataChannel.Buffer(ByteBuffer.wrap(o.toString().toByteArray()), false))
    }

    fun sluit() = runCatching { dc?.close(); pc.close() }

    override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) { if (s == PeerConnection.IceGatheringState.COMPLETE) verzameld.countDown() }
    override fun onTrack(t: RtpTransceiver) { t.receiver.track()?.let { bij.spoor(this, it) } }
    override fun onDataChannel(d: DataChannel) { dc = d; bij.kanaal(this, d) }
    override fun onConnectionChange(s: PeerConnection.PeerConnectionState) = bij.toestand(this, s)
    override fun onSignalingChange(s: PeerConnection.SignalingState) {}
    override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) {}
    override fun onIceConnectionReceivingChange(b: Boolean) {}
    override fun onIceCandidate(c: IceCandidate) {}
    override fun onIceCandidatesRemoved(c: Array<out IceCandidate>) {}
    override fun onAddStream(s: MediaStream) {}
    override fun onRemoveStream(s: MediaStream) {}
    override fun onRenegotiationNeeded() {}
    override fun onAddTrack(r: RtpReceiver, s: Array<out MediaStream>) {}
}

abstract class Sdp : SdpObserver {
    override fun onCreateSuccess(d: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(e: String?) {}
    override fun onSetFailure(e: String?) {}
}

fun DataChannel.luister(bij: (JSONObject) -> Unit) = registerObserver(object : DataChannel.Observer {
    override fun onBufferedAmountChange(p: Long) {}
    override fun onStateChange() {}
    override fun onMessage(b: DataChannel.Buffer) {
        val bytes = ByteArray(b.data.remaining()).also { b.data.get(it) }
        runCatching { bij(JSONObject(String(bytes))) }
    }
})

/** Gemeenschappelijk: sleutel, signaal-lus en werkdraad. */
abstract class Rol {
    protected val werk = Executors.newSingleThreadExecutor()
    @Volatile protected var actief = false
    protected lateinit var app: Context
    protected lateinit var db: Db
    protected lateinit var server: Server
    protected lateinit var sleutel: ByteArray
    private lateinit var ntfy: Ntfy
    private val stukken = mutableMapOf<String, Array<String?>>()
    val status = MutableStateFlow("")

    /** Null = gestart, anders de reden waarom niet. */
    protected fun basis(ctx: Context): String? {
        app = ctx.applicationContext
        db = Db.get(app)
        sleutel = Delen.sleutel(db) ?: return "Koppel eerst een partner (Meer → Delen met partner)."
        server = Server.van(db.alle())
        ntfy = Ntfy(server, Bericht.topic(sleutel))
        Rtc.init(app)
        actief = true
        Thread({
            ntfy.luister({ !actief }) { t ->
                val heel = synchronized(stukken) { Bericht.plak(t, stukken) } ?: return@luister
                Bericht.lees(sleutel, heel, db.dev)?.let { o -> werk.execute { runCatching { ontvang(o) }.onFailure { e -> status.value = "Fout: ${e.message}" } } }
            }
        }, "babyfoon-ntfy").start()
        return null
    }

    protected fun zend(o: JSONObject) = werk.execute {
        runCatching { Bericht.knip(Bericht.maak(sleutel, o.put("van", db.dev).put("naam", db.mijnNaam ?: ""))).forEach { ntfy.stuur(it) } }
            .onFailure { status.value = "Server niet bereikbaar (${server.ntfy}): ${it.message}" }
    }

    protected abstract fun ontvang(o: JSONObject)
}

/** Deze telefoon is de camera. */
object Camera : Rol(), Verbinding.Bij {
    val kijkers = MutableStateFlow<List<String>>(emptyList())
    val beeld = MutableStateFlow<VideoTrack?>(null)
    private val verbindingen = mutableMapOf<String, Verbinding>()
    private val namen = mutableMapOf<String, String>()
    private var capturer: CameraVideoCapturer? = null
    private var helper: SurfaceTextureHelper? = null
    private var bron: VideoSource? = null
    private var video: VideoTrack? = null
    private var geluid: AudioTrack? = null
    private var detector: Detector? = null

    fun start(ctx: Context): String? {
        if (actief) return null
        basis(ctx)?.let { return it }
        val en = Camera2Enumerator(app)
        val naam = en.deviceNames.firstOrNull { en.isBackFacing(it) } ?: en.deviceNames.firstOrNull() ?: return "Geen camera gevonden."
        capturer = en.createCapturer(naam, null)
        helper = SurfaceTextureHelper.create("camera", Rtc.egl.eglBaseContext)
        bron = Rtc.fabriek.createVideoSource(false).also { capturer!!.initialize(helper, app, it.capturerObserver) }
        capturer!!.startCapture(1280, 720, 15)
        video = Rtc.fabriek.createVideoTrack("beeld", bron).also { beeld.value = it }
        geluid = Rtc.audioSpoor("geluid")
        Rtc.luidspreker(app, true)
        detector = Detector(app, db, { v -> verbindingen.values.forEach { it.stuur(JSONObject().put("t", "niveau").put("v", v.toDouble())) } }) { label, p ->
            verbindingen.values.forEach { it.stuur(JSONObject().put("t", "huil").put("label", label).put("p", p.toDouble())) }
        }
        Rtc.opname = { pcm, rate -> detector?.voeg(pcm, rate) }
        BabyfoonService.start(app, camera = true)
        status.value = "Camera staat aan. Wacht op een kijker…"
        zend(JSONObject().put("t", "camera-aan"))
        return null
    }

    fun wissel() = capturer?.switchCamera(null)

    fun stop() {
        if (!actief) return
        actief = false
        werk.execute {
            verbindingen.values.forEach { it.sluit() }
            verbindingen.clear()
            kijkers.value = emptyList()
            Rtc.opname = null
            runCatching { capturer?.stopCapture() }
            capturer?.dispose(); helper?.dispose(); video?.dispose(); bron?.dispose(); geluid?.dispose()
            capturer = null; helper = null; video = null; bron = null; geluid = null
            beeld.value = null
            Rtc.luidspreker(app, false)
            status.value = ""
        }
        BabyfoonService.stop(app)
    }

    override fun ontvang(o: JSONObject) {
        if (!actief) return
        val van = o.getString("van")
        when (o.optString("t")) {
            "wil-kijken" -> {
                verbindingen.remove(van)?.sluit()
                namen[van] = o.optString("naam").ifBlank { "partner" }
                val v = Verbinding(server, van, this)
                v.pc.addTrack(video, listOf("baby"))
                v.pc.addTrack(geluid, listOf("baby"))
                v.dc = v.pc.createDataChannel("info", DataChannel.Init())
                v.dc!!.luister { m -> werk.execute { opdracht(v, m) } }
                verbindingen[van] = v
                val sdp = v.lokaal(offer = true)
                zend(JSONObject().put("t", "offer").put("naar", van).put("sdp", sdp))
                status.value = "${namen[van]} maakt verbinding…"
            }
            "answer" -> verbindingen[van]?.remote(offer = false, o.getString("sdp"))
            "ophangen" -> { verbindingen.remove(van)?.sluit(); bijwerken() }
        }
    }

    /** Opdrachten van een kijker: video aan/uit (alleen geluid). */
    private fun opdracht(v: Verbinding, m: JSONObject) {
        if (m.optString("t") == "video") {
            val aan = m.optBoolean("aan", true)
            v.pc.transceivers.firstOrNull { it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO }?.sender?.setTrack(if (aan) video else null, false)
        }
    }

    override fun toestand(v: Verbinding, s: PeerConnection.PeerConnectionState) {
        werk.execute {
            if (s == PeerConnection.PeerConnectionState.FAILED || s == PeerConnection.PeerConnectionState.CLOSED) {
                if (verbindingen[v.met] === v) verbindingen.remove(v.met)
                v.sluit()
            }
            bijwerken()
        }
    }

    private fun bijwerken() {
        val wie = verbindingen.filterValues { it.pc.connectionState() == PeerConnection.PeerConnectionState.CONNECTED }.keys.map { namen[it] ?: "partner" }
        kijkers.value = wie
        if (actief) status.value = if (wie.isEmpty()) "Camera staat aan. Wacht op een kijker…" else "👀 ${wie.joinToString(", ")} kijkt mee"
    }
}

/** Deze telefoon kijkt naar de camera. */
object Kijker : Rol(), Verbinding.Bij {
    val beeld = MutableStateFlow<VideoTrack?>(null)
    val niveau = MutableStateFlow(0f)
    val laatsteHuil = MutableStateFlow<String?>(null)
    val verbonden = MutableStateFlow(false)
    val geluidAan = MutableStateFlow(true)
    val alleenGeluid = MutableStateFlow(false)
    private var v: Verbinding? = null
    private var mic: AudioTrack? = null
    private var babyGeluid: AudioTrack? = null
    private var camera = ""
    @Volatile private var laatsteOffer = 0L
    private var luidSinds = 0L
    private var laatsteMelding = 0L

    fun start(ctx: Context): String? {
        if (actief) return null
        basis(ctx)?.let { return it }
        mic = Rtc.audioSpoor("terugpraten").apply { setEnabled(false) }
        Rtc.luidspreker(app, true)
        BabyfoonService.start(app, camera = false)
        status.value = "Zoekt de camera…"
        zoek()
        return null
    }

    private fun zoek() {
        if (System.currentTimeMillis() - laatsteOffer > 30_000) zend(JSONObject().put("t", "wil-kijken"))
        // Geen antwoord of verbinding weggevallen: over 20 s opnieuw (niet terwijl een verbinding nog wordt opgebouwd).
        Thread {
            Thread.sleep(20_000)
            if (actief && !verbonden.value && System.currentTimeMillis() - laatsteOffer > 30_000) {
                status.value = "Geen camera gevonden. Staat er een telefoon op 📷 Camera?"
                zoek()
            } else if (actief && !verbonden.value) zoek()
        }.start()
    }

    fun praat(aan: Boolean) { mic?.setEnabled(aan) }

    fun geluid(aan: Boolean) { geluidAan.value = aan; babyGeluid?.setEnabled(aan) }

    fun alleenGeluid(aan: Boolean) {
        alleenGeluid.value = aan
        werk.execute { v?.stuur(JSONObject().put("t", "video").put("aan", !aan)) }
    }

    fun stop() {
        if (!actief) return
        actief = false
        werk.execute {
            v?.let { zendDirect(JSONObject().put("t", "ophangen").put("naar", it.met)); it.sluit() }
            v = null
            beeld.value = null
            verbonden.value = false
            mic?.dispose(); mic = null
            Rtc.luidspreker(app, false)
            status.value = ""
        }
        BabyfoonService.stop(app)
    }

    private fun zendDirect(o: JSONObject) = runCatching { Bericht.knip(Bericht.maak(sleutel, o.put("van", db.dev))).forEach { Ntfy(server, Bericht.topic(sleutel)).stuur(it) } }

    override fun ontvang(o: JSONObject) {
        if (!actief || o.optString("t") != "offer") {
            if (actief && o.optString("t") == "camera-aan" && !verbonden.value) zoek()
            return
        }
        if (verbonden.value) return // al met een camera verbonden
        laatsteOffer = System.currentTimeMillis()
        v?.sluit()
        camera = o.optString("naam").ifBlank { "partner" }
        val nieuw = Verbinding(server, o.getString("van"), this)
        nieuw.pc.addTrack(mic, listOf("ouder"))
        nieuw.remote(offer = true, o.getString("sdp"))
        v = nieuw
        val sdp = nieuw.lokaal(offer = false)
        zend(JSONObject().put("t", "answer").put("naar", nieuw.met).put("sdp", sdp))
        status.value = "Verbinden met de camera van $camera…"
    }

    override fun spoor(v: Verbinding, t: MediaStreamTrack) {
        when (t) {
            is VideoTrack -> beeld.value = t
            is AudioTrack -> { babyGeluid = t; t.setEnabled(geluidAan.value) }
        }
    }

    override fun kanaal(v: Verbinding, dc: DataChannel) {
        dc.luister { m ->
            when (m.optString("t")) {
                "niveau" -> niveau(m.optDouble("v").toFloat())
                "huil" -> {
                    val s = SOUNDS.getOrNull(m.optInt("label")) ?: return@luister
                    val tekst = "${s.emoji} ${s.name}: ${s.meaning.lowercase()}?"
                    laatsteHuil.value = "$tekst (${klok(System.currentTimeMillis())})"
                    BabyfoonService.melding(app, "Huiltje gehoord", tekst)
                }
            }
        }
        if (alleenGeluid.value) alleenGeluid(true)
    }

    /** Geluid boven de drempel, 2 s lang: melding (hooguit één per minuut). */
    private fun niveau(x: Float) {
        niveau.value = x
        val nu = System.currentTimeMillis()
        val drempel = (db.get("babyfoon.drempel")?.toFloatOrNull() ?: 0.15f)
        if (x < drempel) { luidSinds = 0; return }
        if (luidSinds == 0L) luidSinds = nu
        if (nu - luidSinds > 2_000 && nu - laatsteMelding > 60_000) {
            laatsteMelding = nu
            BabyfoonService.melding(app, "Geluid bij de baby", "Tik om te kijken")
        }
    }

    override fun toestand(v: Verbinding, s: PeerConnection.PeerConnectionState) {
        when (s) {
            PeerConnection.PeerConnectionState.CONNECTED -> { verbonden.value = true; status.value = "📷 Camera van $camera" }
            PeerConnection.PeerConnectionState.CONNECTING -> status.value = "Verbinden met de camera van $camera (beeld en geluid)…"
            PeerConnection.PeerConnectionState.FAILED, PeerConnection.PeerConnectionState.DISCONNECTED, PeerConnection.PeerConnectionState.CLOSED -> {
                if (this.v === v && !verbonden.value && s == PeerConnection.PeerConnectionState.FAILED) {
                    status.value = if (server.turn.isBlank()) "Geen rechtstreekse verbinding en geen relay ingesteld (Meer → 📡 Babyfoon-server)."
                    else "Verbinding mislukt, ook via de relay. Opnieuw proberen…"
                    laatsteOffer = 0
                }
                if (this.v === v && verbonden.value) {
                    verbonden.value = false
                    beeld.value = null
                    status.value = "Verbinding weg, opnieuw zoeken…"
                    if (actief) werk.execute { v.sluit(); this.v = null; zoek() }
                }
            }
            else -> {}
        }
    }
}

/**
 * Huiltje op de camera: geluidsniveau elke halve seconde naar de kijkers, en bij geluid boven de drempel na 3 s de
 * laatste 4 s door het model. Hooguit één analyse per 15 s. Een herkend huiltje komt ook in het logboek.
 */
class Detector(private val ctx: Context, private val db: Db, private val opNiveau: (Float) -> Unit, private val opHuil: (Int, Float) -> Unit) {
    private val ring = FloatArray(SAMPLE_RATE * 4)
    private var pos = 0
    private var piek = 0f
    private var piekSinds = 0L
    private var trigger = 0L
    private var laatste = 0L
    private val werk = Executors.newSingleThreadExecutor()
    private var analyzer: Analyzer? = null

    fun voeg(pcm: ShortArray, rate: Int) {
        if (pcm.isEmpty()) return
        var s = 0.0
        for (x in pcm) s += x * x.toDouble()
        val lvl = (sqrt(s / pcm.size) / 8000).toFloat().coerceIn(0f, 1f)
        val nu = System.currentTimeMillis()
        piek = maxOf(piek, lvl)
        if (nu - piekSinds >= 500) { opNiveau(piek); piek = 0f; piekSinds = nu }
        for (f in naar16k(pcm, rate)) { ring[pos] = f; pos = (pos + 1) % ring.size }
        if (lvl > 0.08f && trigger == 0L && nu - laatste > 15_000) trigger = nu
        if (trigger != 0L && nu - trigger >= 3_000) {
            trigger = 0L
            laatste = nu
            val stuk = FloatArray(ring.size) { ring[(pos + it) % ring.size] }
            werk.execute { analyseer(stuk) }
        }
    }

    private fun analyseer(pcm: FloatArray) {
        val a = analyzer ?: Analyzer(ctx).also { analyzer = it }
        val res = runCatching { a.analyze(pcm, db.eigenVoorbeelden()) }.getOrNull() ?: return
        val label = res.probs.indices.maxBy { res.probs[it] }
        opHuil(label, res.probs[label])
        val nu = System.currentTimeMillis()
        db.put(Ev("huil-$nu", T.HUIL, nu, probs = res.probs, emb = res.embedding, tekst = "babyfoon"))
    }
}

/** Houdt de babyfoon draaiend met scherm uit; verplichte melding in de balk. */
class BabyfoonService : Service() {
    companion object {
        private const val KANAAL = "babyfoon"
        private const val KANAAL_ALARM = "babyfoon-alarm"

        fun start(ctx: Context, camera: Boolean) =
            ctx.startForegroundService(Intent(ctx, BabyfoonService::class.java).putExtra("camera", camera))

        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, BabyfoonService::class.java))

        fun melding(ctx: Context, titel: String, tekst: String) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(KANAAL_ALARM, "Babyfoon: geluid en huiltjes", NotificationManager.IMPORTANCE_HIGH))
            val open = PendingIntent.getActivity(ctx, 3, Intent(ctx, MainActivity::class.java).putExtra("open", "babyfoon"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            runCatching {
                nm.notify(4, Notification.Builder(ctx, KANAAL_ALARM).setSmallIcon(R.drawable.ic_voor).setContentTitle(titel)
                    .setContentText(tekst).setContentIntent(open).setAutoCancel(true).build())
            }
        }
    }

    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("WakelockTimeout")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val camera = intent?.getBooleanExtra("camera", false) ?: false
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(KANAAL, "Babyfoon actief", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java).putExtra("open", "babyfoon"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(this, KANAAL).setSmallIcon(R.drawable.ic_voor).setOngoing(true).setContentIntent(open)
            .setContentTitle(if (camera) "📷 Babyfoon-camera staat aan" else "📺 Babyfoon luistert mee")
            .setContentText("Tik om te openen of te stoppen").build()
        if (Build.VERSION.SDK_INT >= 30) {
            val type = if (camera) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            else ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            startForeground(5, n, type)
        } else startForeground(5, n)
        if (wake == null) wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "huiltje:babyfoon").apply { acquire() }
        if (wifi == null) wifi = getSystemService(WifiManager::class.java).createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "huiltje:babyfoon").apply { acquire() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { wake?.release() }
        runCatching { wifi?.release() }
        super.onDestroy()
    }
}

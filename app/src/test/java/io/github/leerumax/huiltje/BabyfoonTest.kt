package io.github.leerumax.huiltje

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BabyfoonTest {
    private val k = Code.sleutel("ABCD-EFGH-JKMN")
    private val t0 = 1_800_000_000_000L

    @Test
    fun topicIsGeheimEnVast() {
        assertEquals(Bericht.topic(k), Bericht.topic(k))
        assertNotEquals(Bericht.topic(k), Bericht.topic(Code.sleutel("ZZZZ-ZZZZ-ZZZZ")))
        assertTrue(Bericht.topic(k).matches(Regex("huiltje-[0-9a-f]{24}")))
    }

    @Test
    fun alleenPartnerKanLezenEnAlleenVers() {
        val b = Bericht.maak(k, JSONObject().put("t", "offer").put("van", "camera").put("naar", "kijker").put("sdp", "v=0"), t0)
        assertTrue("past in een ntfy-bericht", b.length < 4096)
        assertEquals("v=0", Bericht.lees(k, b, "kijker", t0 + 1_000)!!.getString("sdp"))
        assertNull("andere code", Bericht.lees(Code.sleutel("ZZZZ-ZZZZ-ZZZZ"), b, "kijker", t0))
        assertNull("voor een ander toestel", Bericht.lees(k, b, "iemand-anders", t0))
        assertNull("van mezelf", Bericht.lees(k, b, "camera", t0))
        assertNull("te oud (afgespeeld)", Bericht.lees(k, b, "kijker", t0 + Bericht.MAX_LEEFTIJD + 1))
        val geknoeid = b.substring(0, 20) + (if (b[20] == 'A') 'B' else 'A') + b.substring(21)
        assertNull("geknoeid", Bericht.lees(k, geknoeid, "kijker", t0))
        assertNull("rommel", Bericht.lees(k, "geen-base64!", "kijker", t0))
    }

    @Test
    fun echteSdpPastInEenBericht() {
        // Een SDP met video + audio + datachannel en kandidaten is ±4-6 kB; gzip + AES moet onder 4096 tekens blijven.
        val sdp = buildString { repeat(120) { append("a=candidate:$it 1 udp 2122260223 10.0.0.$it 4${it}0 typ host generation 0 network-id 1\r\n") } }
        val b = Bericht.maak(k, JSONObject().put("t", "offer").put("van", "a").put("sdp", sdp), t0)
        assertTrue("lengte ${b.length}", b.length < 4096)
    }

    @Test
    fun naar16kHz() {
        val pcm = ShortArray(48_000) { (it % 100).toShort() }
        assertEquals(16_000, naar16k(pcm, 48_000).size)
        assertEquals(16_000, naar16k(ShortArray(44_100), 44_100).size)
        assertEquals(1600, naar16k(ShortArray(1600), 16_000).size)
    }

    @Test
    fun serverUitLogregel() {
        val s = Server("https://push.voorbeeld.nl", "tk", "turn:turn.voorbeeld.nl:3478", "u", "p")
        val terug = Server.van(listOf(Ev(T.SERVER, T.SERVER, 0, tekst = s.json())))
        assertEquals(s, terug)
        assertEquals("https://ntfy.sh", Server.van(emptyList()).ntfy)
        assertNotNull(Server.van(listOf(Ev(T.SERVER, T.SERVER, 0, tekst = "kapot"))))
    }
}

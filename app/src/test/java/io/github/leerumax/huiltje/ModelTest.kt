package io.github.leerumax.huiltje

import org.json.JSONArray
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ModelTest {
    private val head = Head(File("src/main/assets/dunstan_head.json").readText())

    @Test
    fun headGeeftDezelfdeKansenAlsDeTraining() {
        val golden = JSONArray(javaClass.getResource("/golden.json")!!.readText())
        for (i in 0 until golden.length()) {
            val g = golden.getJSONObject(i)
            val emb = g.getJSONArray("emb").let { a -> FloatArray(a.length()) { a.getDouble(it).toFloat() } }
            val want = g.getJSONArray("probs").let { a -> FloatArray(a.length()) { a.getDouble(it).toFloat() } }
            assertArrayEquals(want, head.probs(emb), 1e-3f)
        }
    }

    @Test
    fun vensterknipGelijkAanPython() {
        assertEquals(1, windows(FloatArray(100), 15600, 7680).size)
        assertEquals(1, windows(FloatArray(15600), 15600, 7680).size)
        assertEquals(2, windows(FloatArray(15601), 15600, 7680).size)
        assertEquals(3, windows(FloatArray(30000), 15600, 7680).size)
        val w = windows(FloatArray(20000) { 1f }, 15600, 7680)
        assertEquals(1f, w[1][20000 - 7680 - 1])
        assertEquals(0f, w[1][20000 - 7680])
    }

    @Test
    fun eigenVoorbeeldenTrekkenDeUitkomst() {
        val p = Personal(mutableListOf(2 to floatArrayOf(1f, 0f), 2 to floatArrayOf(0.9f, 0.1f), 0 to floatArrayOf(0f, 1f)))
        val base = floatArrayOf(0.6f, 0.1f, 0.1f, 0.1f, 0.1f)
        val out = p.blend(base, floatArrayOf(1f, 0.05f))
        assert(out[2] > base[2]) { "heh hoort zwaarder te wegen" }
        assertEquals(1f, out.sum(), 1e-4f)
    }
}

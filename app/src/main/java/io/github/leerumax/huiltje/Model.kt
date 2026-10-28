package io.github.leerumax.huiltje

import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/** Lineair Dunstan-model uit training/train.py: logit = W·embedding + b, daarna softmax. */
class Head(json: String) {
    private val o = JSONObject(json)
    val classes: List<String> = o.getJSONArray("classes").let { a -> List(a.length()) { a.getString(it) } }
    private val w: Array<FloatArray> = o.getJSONArray("W").let { a ->
        Array(a.length()) { i -> a.getJSONArray(i).let { r -> FloatArray(r.length()) { r.getDouble(it).toFloat() } } }
    }
    private val b: FloatArray = o.getJSONArray("b").let { a -> FloatArray(a.length()) { a.getDouble(it).toFloat() } }
    val babyClasses: IntArray = o.getJSONArray("baby_classes").let { a -> IntArray(a.length()) { a.getInt(it) } }
    val babyThreshold: Float = o.getDouble("baby_threshold").toFloat()
    val window: Int = o.getInt("window")
    val hop: Int = o.getInt("hop")

    fun probs(e: FloatArray): FloatArray = softmax(FloatArray(w.size) { k -> b[k] + dot(w[k], e) })
}

fun dot(a: FloatArray, b: FloatArray): Float {
    var s = 0.0
    for (i in a.indices) s += a[i] * b[i]
    return s.toFloat()
}

fun softmax(z: FloatArray): FloatArray {
    val m = z.max()
    val e = DoubleArray(z.size) { exp((z[it] - m).toDouble()) }
    val s = e.sum()
    return FloatArray(z.size) { (e[it] / s).toFloat() }
}

/** Zelfde knip als training/train.py windows(): vensters van [win] met stap [hop], de laatste aangevuld met stilte. */
fun windows(x: FloatArray, win: Int, hop: Int): List<FloatArray> {
    val len = max(x.size, win)
    val n = 1 + max(0, ceil((len - win) / hop.toDouble()).toInt())
    val padded = x.copyOf((n - 1) * hop + win)
    return List(n) { padded.copyOfRange(it * hop, it * hop + win) }
}

/** Eigen bijgeleerde voorbeelden van deze baby: gewogen buren op cosinus-gelijkenis. */
class Personal(val samples: MutableList<Pair<Int, FloatArray>> = mutableListOf()) {
    fun probs(e: FloatArray, classes: Int, k: Int = 7): FloatArray? {
        if (samples.isEmpty()) return null
        val en = norm(e)
        val near = samples.map { (label, s) -> label to dot(en, norm(s)) }.sortedByDescending { it.second }.take(k)
        val p = FloatArray(classes) { 0.02f }
        for ((label, sim) in near) p[label] += max(sim, 0f)
        val s = p.sum()
        return FloatArray(classes) { p[it] / s }
    }

    /** Hoe meer eigen voorbeelden, hoe zwaarder die tellen: bij 15 stuks half om half. */
    fun blend(base: FloatArray, e: FloatArray): FloatArray {
        val p = probs(e, base.size) ?: return base
        val w = samples.size / (samples.size + 15f)
        return FloatArray(base.size) { (1 - w) * base[it] + w * p[it] }
    }

    private fun norm(v: FloatArray): FloatArray {
        val n = sqrt(dot(v, v)).coerceAtLeast(1e-9f)
        return FloatArray(v.size) { v[it] / n }
    }
}

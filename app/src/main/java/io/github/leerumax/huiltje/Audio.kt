package io.github.leerumax.huiltje

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

const val SAMPLE_RATE = 16000

/** Neemt op tot [maxSeconds] of tot [stop] true geeft; [onLevel] krijgt 0..1 voor de meter. */
@SuppressLint("MissingPermission")
fun record(maxSeconds: Int, stop: () -> Boolean, onLevel: (Float) -> Unit): FloatArray {
    val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val rec = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT, max(minBuf, SAMPLE_RATE))
    val out = ShortArray(SAMPLE_RATE * maxSeconds)
    val buf = ShortArray(SAMPLE_RATE / 10)
    var n = 0
    rec.startRecording()
    try {
        while (n < out.size && !stop()) {
            val r = rec.read(buf, 0, min(buf.size, out.size - n))
            if (r <= 0) break
            buf.copyInto(out, n, 0, r)
            n += r
            var s = 0.0
            for (i in 0 until r) s += buf[i] * buf[i].toDouble()
            onLevel((sqrt(s / r) / 8000).toFloat().coerceIn(0f, 1f))
        }
    } finally {
        rec.stop()
        rec.release()
    }
    return FloatArray(n) { out[it] / 32768f }
}

fun writeWav(file: File, pcm: FloatArray) {
    val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
    for (v in pcm) data.putShort((v.coerceIn(-1f, 1f) * 32767).toInt().toShort())
    val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    h.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVE".toByteArray())
    h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2)
        .putShort(2).putShort(16)
    h.put("data".toByteArray()).putInt(pcm.size * 2)
    file.outputStream().use { it.write(h.array()); it.write(data.array()) }
}

class Analysis(val embedding: FloatArray, val base: FloatArray, val probs: FloatArray, val babyScore: Float)

/** YAMNet (TFLite) per venster, dan het Dunstan-hoofd op het gemiddelde van de vensters waarin een baby klinkt. */
class Analyzer(ctx: Context) : AutoCloseable {
    val head = Head(ctx.assets.open("dunstan_head.json").bufferedReader().readText())
    private val tfl: Interpreter
    private val scoresIdx: Int
    private val embIdx: Int

    init {
        val fd = ctx.assets.openFd("yamnet_emb.tflite")
        val model = FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        tfl = Interpreter(model)
        val sizes = (0 until tfl.outputTensorCount).associateBy { tfl.getOutputTensor(it).shape().last() }
        scoresIdx = sizes.getValue(521)
        embIdx = sizes.getValue(1024)
    }

    /** null = geen babygeluid gehoord. */
    fun analyze(pcm: FloatArray, personal: Personal): Analysis? {
        val embs = mutableListOf<FloatArray>()
        var babyMax = 0f
        for (w in windows(pcm, head.window, head.hop)) {
            val scores = FloatArray(521)
            val emb = FloatArray(1024)
            tfl.runForMultipleInputsOutputs(arrayOf<Any>(w), mapOf(scoresIdx to scores, embIdx to emb))
            val baby = head.babyClasses.sumOf { scores[it].toDouble() }.toFloat()
            babyMax = max(babyMax, baby)
            // ponytail: halve drempel per venster zodat de staart van een geluidje meetelt; bijstellen op eigen opnames.
            if (baby >= head.babyThreshold / 2) embs += emb
        }
        if (babyMax < head.babyThreshold || embs.isEmpty()) return null
        val mean = FloatArray(1024) { j -> embs.sumOf { it[j].toDouble() }.toFloat() / embs.size }
        val base = head.probs(mean)
        return Analysis(mean, base, personal.blend(base, mean), babyMax)
    }

    override fun close() = tfl.close()
}

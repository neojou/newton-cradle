package com.neojou.newtoncradle.audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

actual fun playCollision(strength: Float) {
    val s = strength.coerceIn(0.15f, 1f)
    Thread({
        try {
            playSync(s)
        } catch (_: Throwable) {
        }
    }, "cradle-clang").apply {
        isDaemon = true
        start()
    }
}

private fun playSync(strength: Float) {
    val sampleRate = 44100f
    val n = (sampleRate * 0.20f).toInt()
    val pcm = synthesize(n, sampleRate, strength)
    val format = AudioFormat(sampleRate, 16, 1, true, false)
    val info = DataLine.Info(SourceDataLine::class.java, format)
    val line = AudioSystem.getLine(info) as SourceDataLine
    line.open(format, pcm.size)
    line.start()
    line.write(pcm, 0, pcm.size)
    line.drain()
    line.stop()
    line.close()
}

private fun synthesize(n: Int, sampleRate: Float, strength: Float): ByteArray {
    val out = ByteArray(n * 2)
    val twoPi = (2.0 * kotlin.math.PI).toFloat()
    val f0 = 1920f + 380f * strength
    val freqs = floatArrayOf(f0, f0 * 1.49f, f0 * 2.23f, f0 * 3.41f, f0 * 4.87f)
    val amps = floatArrayOf(0.40f, 0.28f, 0.16f, 0.10f, 0.06f)
    val decays = floatArrayOf(16f, 21f, 27f, 38f, 52f)
    val rng = Random.Default
    for (i in 0 until n) {
        val t = i / sampleRate
        var v = 0f
        for (k in freqs.indices) {
            v += amps[k] * sin(twoPi * freqs[k] * t) * exp(-decays[k] * t)
        }
        if (t < 0.011f) {
            v += (rng.nextFloat() * 2f - 1f) * (1f - t / 0.011f) * 0.20f
        }
        v = (v * 0.62f * strength).coerceIn(-1f, 1f)
        val iv = (v * 32767f).toInt()
        out[i * 2] = (iv and 0xFF).toByte()
        out[i * 2 + 1] = ((iv shr 8) and 0xFF).toByte()
    }
    return out
}

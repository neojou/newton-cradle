package com.neojou.newtoncradle.audio

import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread

private val wavBytes = AtomicReference<ByteArray?>(null)
private val pcm = AtomicReference<Pcm?>(null)

private data class Pcm(
    val samples: ByteArray,
    val format: AudioFormat,
)

actual fun unlockAudio() {
    // Desktop has no gesture lock.
}

actual fun loadCollisionWav(bytes: ByteArray) {
    wavBytes.set(bytes)
    runCatching { pcm.set(decodeWav(bytes)) }
}

actual fun playCollision(strength: Float) {
    val s = strength.coerceIn(0.15f, 1f)
    thread(name = "cradle-clang", isDaemon = true) {
        try {
            playSync(s)
        } catch (_: Throwable) {
        }
    }
}

private fun playSync(strength: Float) {
    val sound = pcm.get() ?: wavBytes.get()?.let { decodeWav(it).also(pcm::set) } ?: return
    val info = DataLine.Info(SourceDataLine::class.java, sound.format)
    val line = AudioSystem.getLine(info) as SourceDataLine
    line.open(sound.format, sound.samples.size)
    if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
        val gain = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
        val db = (20.0 * kotlin.math.log10((0.42 + 0.58 * strength).toDouble())).toFloat()
        gain.value = db.coerceIn(gain.minimum, gain.maximum)
    }
    line.start()
    line.write(sound.samples, 0, sound.samples.size)
    line.drain()
    line.stop()
    line.close()
}

/** Minimal PCM WAV reader (16-bit little-endian, mono or stereo). */
private fun decodeWav(bytes: ByteArray): Pcm {
    val stream = AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes))
    val base = stream.format
    val pcm16 = AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        base.sampleRate,
        16,
        base.channels,
        base.channels * 2,
        base.sampleRate,
        false,
    )
    val converted = if (base.matches(pcm16)) stream else AudioSystem.getAudioInputStream(pcm16, stream)
    val samples = converted.readAllBytes()
    return Pcm(samples, converted.format)
}

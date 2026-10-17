package com.neojou.newtoncradle.render.photo

import kotlin.math.sqrt

internal class FrameBuffer {
    var width: Int = 0
        private set
    var height: Int = 0
        private set
    var rgba: ByteArray = ByteArray(0)
        private set

    fun ensure(w: Int, h: Int) {
        if (w == width && h == height && rgba.size == w * h * 4) return
        width = w
        height = h
        rgba = ByteArray(w * h * 4)
    }

    fun putLinear(x: Int, y: Int, r: Float, g: Float, b: Float, exposure: Float = 1.12f) {
        val i = (y * width + x) * 4
        rgba[i] = toSrgbByte(r * exposure)
        rgba[i + 1] = toSrgbByte(g * exposure)
        rgba[i + 2] = toSrgbByte(b * exposure)
        rgba[i + 3] = 255.toByte()
    }

    /** Wong Kar-wai leaning grade: amber lamp, teal voids, crushed blacks, grain. */
    fun putCinematic(x: Int, y: Int, r0: Float, g0: Float, b0: Float, frame: Int) {
        var r = r0 * 1.18f
        var g = g0 * 1.04f
        var b = b0 * 0.96f
        val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val shadow = (1f - smooth(l, 0.0f, 0.07f))
        r -= 0.004f * shadow
        g += 0.006f * shadow
        b += 0.014f * shadow
        val hi = smooth(l, 0.35f, 1.6f)
        r += 0.045f * hi
        g += 0.012f * hi
        b -= 0.02f * hi
        val grain = hashGrain(x, y, frame) * 0.014f
        r = (r + grain).coerceAtLeast(0f)
        g = (g + grain * 0.92f).coerceAtLeast(0f)
        b = (b + grain * 0.8f).coerceAtLeast(0f)
        val i = (y * width + x) * 4
        rgba[i] = toSrgbByte(r)
        rgba[i + 1] = toSrgbByte(g)
        rgba[i + 2] = toSrgbByte(b)
        rgba[i + 3] = 255.toByte()
    }
}

private fun smooth(x: Float, a: Float, b: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun hashGrain(x: Int, y: Int, frame: Int): Float {
    var n = x * 374761393 + y * 668265263 + frame * 1274126177
    n = (n xor (n ushr 13)) * 1274126177
    n = n xor (n ushr 16)
    return ((n ushr 8) and 255) / 255f - 0.5f
}

internal fun toSrgbByte(linear: Float): Byte {
    val t = aces(linear).coerceIn(0f, 1f)
    return (sqrt(t) * 255.99f).toInt().toByte()
}

internal fun aces(x: Float): Float {
    val a = 2.51f
    val b = 0.03f
    val c = 2.43f
    val d = 0.59f
    val e = 0.14f
    return ((x * (a * x + b)) / (x * (c * x + d) + e))
}

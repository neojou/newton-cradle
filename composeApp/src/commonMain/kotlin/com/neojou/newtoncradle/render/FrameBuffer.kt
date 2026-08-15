package com.neojou.newtoncradle.render

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

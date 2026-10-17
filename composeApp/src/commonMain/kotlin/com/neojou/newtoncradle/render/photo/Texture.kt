package com.neojou.newtoncradle.render.photo

/**
 * Linear-ish RGB texture sampled with wrap + bilinear.
 */
class RgbaTex(val width: Int, val height: Int, val rgb: IntArray) {
    fun sample(u: Float, v: Float, out: FloatArray) {
        val wu = u - kotlin.math.floor(u)
        val wv = v - kotlin.math.floor(v)
        val x = wu * (width - 1)
        val y = wv * (height - 1)
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = if (x0 + 1 >= width) 0 else x0 + 1
        val y1 = if (y0 + 1 >= height) 0 else y0 + 1
        val fx = x - x0
        val fy = y - y0
        unpack(rgb[y0 * width + x0], c00)
        unpack(rgb[y0 * width + x1], c10)
        unpack(rgb[y1 * width + x0], c01)
        unpack(rgb[y1 * width + x1], c11)
        out[0] = mix(mix(c00[0], c10[0], fx), mix(c01[0], c11[0], fx), fy)
        out[1] = mix(mix(c00[1], c10[1], fx), mix(c01[1], c11[1], fx), fy)
        out[2] = mix(mix(c00[2], c10[2], fx), mix(c01[2], c11[2], fx), fy)
    }

    fun luma(u: Float, v: Float): Float {
        sample(u, v, tmp)
        return 0.2126f * tmp[0] + 0.7152f * tmp[1] + 0.0722f * tmp[2]
    }

    companion object {
        private val c00 = FloatArray(3)
        private val c10 = FloatArray(3)
        private val c01 = FloatArray(3)
        private val c11 = FloatArray(3)
        val tmp = FloatArray(3)
    }
}

private fun unpack(p: Int, o: FloatArray) {
    o[0] = ((p shr 16) and 0xFF) / 255f
    o[1] = ((p shr 8) and 0xFF) / 255f
    o[2] = (p and 0xFF) / 255f
}

private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

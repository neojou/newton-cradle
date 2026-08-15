package com.neojou.newtoncradle.render

import kotlin.math.exp

/**
 * Procedural studio HDR. Chrome reads as metal only when the environment
 * has bright, shaped lights — a flat gradient looks like grey plastic.
 */
internal object StudioEnv {
    fun sample(dx: Float, dy: Float, dz: Float, outR: FloatArray, outG: FloatArray, outB: FloatArray, i: Int) {
        val t = dy * 0.5f + 0.5f
        var r = lerp(0.028f, 0.145f, t)
        var g = lerp(0.025f, 0.132f, t)
        var b = lerp(0.022f, 0.128f, t)

        // Warm floor bounce
        if (dy < 0f) {
            val floor = -dy
            r += floor * 0.035f
            g += floor * 0.022f
            b += floor * 0.014f
        }

        // Broad ceiling card so chrome still reads as metal from above
        val ceiling = blob(dx, dy, dz, 0.02f, 0.93f, 0.12f, 0.72f, 0.16f, 0.58f)
        r += ceiling * 1.85f
        g += ceiling * 1.78f
        b += ceiling * 1.66f

        // Overhead softbox
        val box = blob(dx, dy, dz, 0.04f, 0.84f, 0.18f, 0.42f, 0.11f, 0.26f)
        r += box * 3.1f
        g += box * 2.95f
        b += box * 2.72f

        // Cool window, stage left
        val window = blob(dx, dy, dz, -0.74f, 0.22f, -0.28f, 0.07f, 0.32f, 0.18f)
        r += window * 0.45f
        g += window * 0.72f
        b += window * 1.18f

        // Warm strip, stage right
        val strip = blob(dx, dy, dz, 0.68f, 0.12f, 0.38f, 0.055f, 0.38f, 0.10f)
        r += strip * 1.85f
        g += strip * 1.12f
        b += strip * 0.68f

        // Front fill card
        val fill = blob(dx, dy, dz, 0.08f, 0.02f, 0.94f, 0.38f, 0.22f, 0.07f)
        r += fill * 0.42f
        g += fill * 0.43f
        b += fill * 0.46f

        // Small hot rim behind
        val rim = blob(dx, dy, dz, 0.22f, 0.38f, -0.88f, 0.12f, 0.10f, 0.08f)
        r += rim * 1.1f
        g += rim * 0.85f
        b += rim * 0.55f

        outR[i] = r
        outG[i] = g
        outB[i] = b
    }

    private fun blob(
        dx: Float, dy: Float, dz: Float,
        cx: Float, cy: Float, cz: Float,
        sx: Float, sy: Float, sz: Float,
    ): Float {
        val wx = (dx - cx) / sx
        val wy = (dy - cy) / sy
        val wz = (dz - cz) / sz
        return exp(-(wx * wx + wy * wy + wz * wz))
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)
}

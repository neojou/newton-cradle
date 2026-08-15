package com.neojou.newtoncradle.render

import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

internal const val MAT_SHADE = 7
internal const val MAT_BRASS = 8
internal const val MAT_VANITY = 9
internal const val MAT_FRAME = 10
internal const val MAT_BULB = 11

/**
 * Date Masamune crests used as gold inlay:
 * 0 = 竹に雀, 1 = 竪三つ引両, 2 = 九曜.
 */
internal object Textures {
    var wood: RgbaTex? = null
    var sasa: RgbaTex? = null
    var hikiryo: RgbaTex? = null
    var kuyou: RgbaTex? = null
}

internal fun sampleSandalwood(
    u: Float, v: Float,
    nx: Float, ny: Float, nz: Float,
    out: FloatArray,
) {
    val wood = Textures.wood
    if (wood != null) {
        wood.sample(u * 1.7f, v * 1.1f, out)
        out[0] *= 0.92f
        out[1] *= 0.68f
        out[2] *= 0.34f
    } else {
        val g = 0.55f + 0.45f * sinish(u * 28f + v * 3f)
        out[0] = 0.22f * g
        out[1] = 0.10f * g
        out[2] = 0.035f * g
    }
    applyCrests(u, v, nx, ny, nz, out)
}

private fun applyCrests(
    u: Float, v: Float,
    nx: Float, ny: Float, nz: Float,
    out: FloatArray,
) {
    val front = nz > 0.55f
    val side = abs(nx) > 0.55f
    if (!front && !side) return
    if (front) {
        stamp(Textures.sasa, u, v, 0.50f, 0.52f, 0.16f, out)
        stamp(Textures.hikiryo, u, v, 0.22f, 0.52f, 0.12f, out)
        stamp(Textures.kuyou, u, v, 0.78f, 0.52f, 0.12f, out)
    } else {
        stamp(Textures.sasa, u, v, 0.50f, 0.52f, 0.18f, out)
    }
}

private fun stamp(
    tex: RgbaTex?,
    u: Float, v: Float,
    cx: Float, cy: Float, radius: Float,
    out: FloatArray,
) {
    val du = (u - cx) / radius
    val dv = (v - cy) / radius
    val r2 = du * du + dv * dv
    if (r2 > 1f) return
    if (tex != null) {
        val su = 0.5f + du * 0.48f
        val sv = 0.5f + dv * 0.48f
        tex.sample(su, sv, RgbaTex.tmp)
        val luma = 0.2126f * RgbaTex.tmp[0] + 0.7152f * RgbaTex.tmp[1] + 0.0722f * RgbaTex.tmp[2]
        if (luma < 0.18f) return
        val t = ((luma - 0.18f) / 0.65f).coerceIn(0f, 1f)
        out[0] = out[0] * (1f - t) + 0.58f * t
        out[1] = out[1] * (1f - t) + 0.42f * t
        out[2] = out[2] * (1f - t) + 0.14f * t
    } else {
        proceduralDisk(du, dv, out)
    }
}

private fun proceduralDisk(du: Float, dv: Float, out: FloatArray) {
    val r = sqrt(du * du + dv * dv)
    if (r > 0.98f || r < 0.82f && r > 0.78f) {
        out[0] = 0.55f
        out[1] = 0.40f
        out[2] = 0.14f
    }
}

private fun sinish(x: Float): Float {
    val t = x - kotlin.math.floor(x / (2f * kotlin.math.PI.toFloat())) * (2f * kotlin.math.PI.toFloat())
    return sin(t)
}

internal fun boxUv(
    minX: Float, minY: Float, minZ: Float,
    maxX: Float, maxY: Float, maxZ: Float,
    px: Float, py: Float, pz: Float,
    nx: Float, ny: Float, nz: Float,
): Pair<Float, Float> {
    return if (abs(nz) >= abs(nx) && abs(nz) >= abs(ny)) {
        val u = (px - minX) / (maxX - minX).coerceAtLeast(1e-4f)
        val v = (py - minY) / (maxY - minY).coerceAtLeast(1e-4f)
        u to v
    } else if (abs(nx) >= abs(ny)) {
        val u = (pz - minZ) / (maxZ - minZ).coerceAtLeast(1e-4f)
        val v = (py - minY) / (maxY - minY).coerceAtLeast(1e-4f)
        u to v
    } else {
        val u = (px - minX) / (maxX - minX).coerceAtLeast(1e-4f)
        val v = (pz - minZ) / (maxZ - minZ).coerceAtLeast(1e-4f)
        u to v
    }
}

package com.neojou.newtoncradle.render

import com.neojou.newtoncradle.sim.Vec3
import kotlin.math.tan

/**
 * Pinhole camera shared by the tracer and pointer un-projection.
 */
class Camera {
    var eye = Vec3(0f, -0.85f, 27.4f)
    var target = Vec3(0f, -3.55f, 0f)
    var up = Vec3(0f, 1f, 0f)
    var fovY = 34f * (kotlin.math.PI.toFloat() / 180f)

    var forward = Vec3(0f, 0f, -1f)
        private set
    var right = Vec3(1f, 0f, 0f)
        private set
    var camUp = Vec3(0f, 1f, 0f)
        private set
    var tanHalf = 0.26f
        private set

    fun updateBasis() {
        forward = (target - eye).normalized()
        right = forward.cross(up).normalized().let { if (it.length() < 1e-5f) Vec3(1f, 0f, 0f) else it }
        camUp = right.cross(forward).normalized()
        tanHalf = tan(fovY * 0.5f)
    }

    fun rayDir(px: Float, py: Float, width: Float, height: Float): Vec3 {
        val d = FloatArray(3)
        writeRayDir(px, py, width, height, d)
        return Vec3(d[0], d[1], d[2])
    }

    fun writeRayDir(px: Float, py: Float, width: Float, height: Float, out: FloatArray) {
        val u = (2f * px / width) - 1f
        val v = 1f - (2f * py / height)
        val aspect = width / height
        val sx = u * aspect * tanHalf
        val sy = v * tanHalf
        var dx = forward.x + right.x * sx + camUp.x * sy
        var dy = forward.y + right.y * sx + camUp.y * sy
        var dz = forward.z + right.z * sx + camUp.z * sy
        val len = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-8f)
        out[0] = dx / len
        out[1] = dy / len
        out[2] = dz / len
    }

    /**
     * Intersect the camera ray through [px],[py] with the swing plane z = 0.
     */
    fun intersectSwingPlane(px: Float, py: Float, width: Float, height: Float): Vec3? {
        val dir = rayDir(px, py, width, height)
        if (dir.z > -1e-5f && dir.z < 1e-5f) return null
        val t = -eye.z / dir.z
        if (t < 0.2f) return null
        return Vec3(eye.x + dir.x * t, eye.y + dir.y * t, 0f)
    }

    fun project(p: Vec3, width: Float, height: Float): Pair<Float, Float>? {
        val w = p - eye
        val z = w.dot(forward)
        if (z < 0.2f) return null
        val x = w.dot(right)
        val y = w.dot(camUp)
        val aspect = width / height
        val u = x / (z * tanHalf * aspect)
        val v = y / (z * tanHalf)
        val sx = (u + 1f) * 0.5f * width
        val sy = (1f - v) * 0.5f * height
        return sx to sy
    }
}

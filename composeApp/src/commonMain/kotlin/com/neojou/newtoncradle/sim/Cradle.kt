package com.neojou.newtoncradle.sim

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shared world units for the cradle.
 *
 * 1 unit ≈ one steel-ball radius (~15 mm). Gravity is scaled so a ~15 cm
 * pendulum feels stately rather than frantic.
 */
object Cradle {
    const val BALLS = 5
    const val RADIUS = 1f

    const val PIVOT_Y = 4.85f
    const val BAR_Z = 2.45f
    const val STRING_LEN = 10.45f

    /** Planar swing length: sqrt(STRING_LEN² − BAR_Z²). */
    const val SWING_L = 10.1587f

    const val POST_X = 9.15f
    const val ROD_RADIUS = 0.108f
    const val POST_HALF = 0.175f

    const val BASE_TOP = -6.49f
    const val BASE_BOTTOM = -7.28f
    const val BASE_HALF_X = 10.55f
    const val BASE_HALF_Z = 3.95f
    const val FLOOR_Y = -7.28f

    /** Tuned for a ~1 s period (soothing, still physically pendulum-like). */
    const val GRAVITY = 400f

    val MAX_ANGLE: Float = 50f * (PI.toFloat() / 180f)

    const val MASS = 1f

    fun pivotX(index: Int): Float = (index - 2) * (2f * RADIUS)

    fun ballX(index: Int, theta: Float): Float =
        pivotX(index) + SWING_L * sin(theta)

    fun ballY(index: Int, theta: Float): Float =
        PIVOT_Y - SWING_L * cos(theta)

    fun speed(omega: Float): Float = SWING_L * omega
}

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z

    fun cross(o: Vec3) = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x,
    )

    fun length(): Float = sqrt(dot(this))

    fun normalized(): Vec3 {
        val len = length()
        return if (len < 1e-8f) this else this * (1f / len)
    }
}

fun ballCenter(index: Int, theta: Float): Vec3 =
    Vec3(Cradle.ballX(index, theta), Cradle.ballY(index, theta), 0f)

fun stringPivot(index: Int, side: Float): Vec3 =
    Vec3(Cradle.pivotX(index), Cradle.PIVOT_Y, Cradle.BAR_Z * side)

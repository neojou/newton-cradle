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

    const val PIVOT_Y = 8.35f
    const val BAR_Z = 2.55f
    const val STRING_LEN = 10.50f

    /** Planar swing length: sqrt(STRING_LEN² − BAR_Z²). */
    const val SWING_L = 10.1857f

    const val POST_X = 9.45f
    const val ROD_RADIUS = 0.112f
    const val POST_HALF = 0.185f

    const val BASE_TOP = -6.58f
    const val BASE_BOTTOM = -10.25f
    const val BASE_HALF_X = 14.4f
    const val BASE_HALF_Z = 7.35f
    const val FLOOR_Y = -10.25f
    const val MIRROR_Y = -6.505f
    const val BACK_WALL_Z = -11.2f

    const val LAMP_X = 18.4f
    const val LAMP_Z = 8.4f
    const val LAMP_LIGHT_Y = -5.15f
    const val VANITY_Y = -10.95f

    /** Tuned for a ~1 s period with the current swing length. */
    const val GRAVITY = 402f

    val MAX_ANGLE: Float = 90f * (PI.toFloat() / 180f)

    const val MASS = 1f

    /**
     * Centered row. [balls] == [BALLS] (5) is `(index - 2) * 2 * RADIUS`.
     */
    fun pivotX(index: Int, balls: Int = BALLS): Float =
        (index - (balls - 1) * 0.5f) * (2f * RADIUS)

    fun ballX(index: Int, theta: Float, balls: Int = BALLS): Float =
        pivotX(index, balls) + SWING_L * sin(theta)

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

fun ballCenter(index: Int, theta: Float, balls: Int = Cradle.BALLS): Vec3 =
    Vec3(Cradle.ballX(index, theta, balls), Cradle.ballY(index, theta), 0f)

fun stringPivot(index: Int, side: Float, balls: Int = Cradle.BALLS): Vec3 =
    Vec3(Cradle.pivotX(index, balls), Cradle.PIVOT_Y, Cradle.BAR_Z * side)

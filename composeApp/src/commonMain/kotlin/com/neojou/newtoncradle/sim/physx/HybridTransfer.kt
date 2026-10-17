package com.neojou.newtoncradle.sim.physx

import com.neojou.newtoncradle.sim.Cradle
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ideal equal-mass chain transfer, copied from [com.neojou.newtoncradle.sim.CradlePhysics].
 * Operates on pendulum angle and angular velocity so PhysX can apply it without
 * letting the spheres collide.
 *
 * Returns the largest |omega| that was swapped, or 0 when nothing transferred.
 */
internal object HybridTransfer {
    fun apply(theta: FloatArray, omega: FloatArray, ballCount: Int): Float {
        val right = transferRight(theta, omega, ballCount)
        val left = transferLeft(theta, omega, ballCount)
        return maxOf(right, left)
    }

    private fun transferRight(theta: FloatArray, omega: FloatArray, ballCount: Int): Float {
        var impact = 0f
        var i = 0
        while (i < ballCount - 1) {
            if (inContact(theta, i, i + 1, ballCount) && approaching(theta, omega, i, i + 1, ballCount) &&
                omega[i] > 0.05f && abs(omega[i + 1]) < 0.28f
            ) {
                var k = i + 1
                while (k < ballCount - 1 &&
                    inContact(theta, k, k + 1, ballCount) &&
                    abs(omega[k + 1]) < 0.28f
                ) {
                    k++
                }
                val w = omega[i]
                omega[i] = omega[k]
                omega[k] = w
                impact = maxOf(impact, abs(w))
                i = k
            } else {
                i++
            }
        }
        return impact
    }

    private fun transferLeft(theta: FloatArray, omega: FloatArray, ballCount: Int): Float {
        var impact = 0f
        var j = ballCount - 1
        while (j > 0) {
            if (inContact(theta, j - 1, j, ballCount) && approaching(theta, omega, j - 1, j, ballCount) &&
                omega[j] < -0.05f && abs(omega[j - 1]) < 0.28f
            ) {
                var k = j - 1
                while (k > 0 &&
                    inContact(theta, k - 1, k, ballCount) &&
                    abs(omega[k - 1]) < 0.28f
                ) {
                    k--
                }
                val w = omega[j]
                omega[j] = omega[k]
                omega[k] = w
                impact = maxOf(impact, abs(w))
                j = k
            } else {
                j--
            }
        }
        return impact
    }

    private fun inContact(theta: FloatArray, i: Int, j: Int, ballCount: Int): Boolean {
        val dx = Cradle.ballX(j, theta[j], ballCount) - Cradle.ballX(i, theta[i], ballCount)
        val dy = Cradle.ballY(j, theta[j]) - Cradle.ballY(i, theta[i])
        val minD = 2f * Cradle.RADIUS + 0.10f
        return dx * dx + dy * dy < minD * minD
    }

    private fun approaching(theta: FloatArray, omega: FloatArray, i: Int, j: Int, ballCount: Int): Boolean {
        val x0 = Cradle.ballX(i, theta[i], ballCount)
        val y0 = Cradle.ballY(i, theta[i])
        val x1 = Cradle.ballX(j, theta[j], ballCount)
        val y1 = Cradle.ballY(j, theta[j])
        val dx = x1 - x0
        val dy = y1 - y0
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-6f)
        val (vx0, vy0) = velocity(theta[i], omega[i])
        val (vx1, vy1) = velocity(theta[j], omega[j])
        val rel = (vx1 - vx0) * (dx / d) + (vy1 - vy0) * (dy / d)
        return rel < -1e-4f
    }

    private fun velocity(theta: Float, omega: Float): Pair<Float, Float> {
        val speed = Cradle.SWING_L * omega
        return speed * cos(theta) to speed * sin(theta)
    }
}

package com.neojou.newtoncradle.sim

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Five equal pendulums with perfectly elastic, equal-mass collisions.
 *
 * Sequential pairwise swaps propagate momentum through a contacting stack
 * (the ideal Newton's-cradle result). After release, kinetic energy is
 * gently renormalized to the energy at the moment of release so the motion
 * repeats without numerical drain.
 */
class CradlePhysics {
    val theta = FloatArray(Cradle.BALLS)
    val omega = FloatArray(Cradle.BALLS)

    var holding: Boolean = true
        private set
    var holdIndex: Int = 0
        private set
    var handPhase: Float = 1f
        private set

    private var energyLock = 0f
    private var lockEnergy = false

    init {
        reset()
    }

    fun reset() {
        for (i in 0 until Cradle.BALLS) {
            theta[i] = 0f
            omega[i] = 0f
        }
        theta[0] = -38f * (kotlin.math.PI.toFloat() / 180f)
        holding = true
        holdIndex = 0
        handPhase = 1f
        lockEnergy = false
        energyLock = 0f
    }

    fun snapshot(): CradleSnapshot = CradleSnapshot(
        theta = theta.copyOf(),
        omega = omega.copyOf(),
        holding = holding,
        holdIndex = holdIndex,
        handPhase = handPhase,
    )

    fun tryGrab(index: Int): Boolean {
        if (index != 0 && index != Cradle.BALLS - 1) return false
        holdIndex = index
        holding = true
        omega[index] = 0f
        handPhase = 1f
        lockEnergy = false
        return true
    }

    fun setHoldAngle(angle: Float) {
        if (!holding) return
        val clamped = if (holdIndex == 0) {
            angle.coerceIn(-Cradle.MAX_ANGLE, 0f)
        } else {
            angle.coerceIn(0f, Cradle.MAX_ANGLE)
        }
        // Keep the held ball from sinking into its neighbour.
        theta[holdIndex] = clampedWithoutOverlap(holdIndex, clamped)
        omega[holdIndex] = 0f
    }

    fun release() {
        if (!holding) return
        holding = false
        omega[holdIndex] = 0f
        energyLock = totalEnergy()
        lockEnergy = true
    }

    /**
     * Advance the simulation by [dt] seconds (clamped). Uses a fixed inner
     * step so collisions stay ordered even when a frame is long.
     */
    fun step(dt: Float) {
        val clamped = dt.coerceIn(0f, 0.05f)
        if (!holding) {
            handPhase = (handPhase - clamped / 0.42f).coerceAtLeast(0f)
        } else {
            handPhase = 1f
        }

        val inner = 1f / 360f
        val n = (clamped / inner).toInt().coerceIn(1, 18)
        val h = clamped / n
        repeat(n) {
            integrate(h)
            resolveCollisions()
        }
        if (lockEnergy) restoreEnergy()
    }

    fun angleFromWorld(x: Float, y: Float, index: Int): Float {
        val px = Cradle.pivotX(index)
        return atan2(x - px, Cradle.PIVOT_Y - y)
    }

    private fun integrate(h: Float) {
        val accel = Cradle.GRAVITY / Cradle.SWING_L
        for (i in 0 until Cradle.BALLS) {
            if (holding && i == holdIndex) continue
            omega[i] += -accel * sin(theta[i]) * h
            theta[i] += omega[i] * h
        }
    }

    private fun resolveCollisions() {
        transferChainRight()
        transferChainLeft()
        for (i in 0 until Cradle.BALLS - 1) separatePair(i, i + 1)
        for (i in 0 until Cradle.BALLS) {
            if (holding && i == holdIndex) continue
            if (kotlin.math.abs(omega[i]) < 0.018f && kotlin.math.abs(theta[i]) < 0.012f) {
                omega[i] = 0f
                theta[i] = 0f
            }
        }
    }

    /**
     * A moving ball hitting a still contacting stack should hand its
     * momentum to the far end in one step (ideal identical elastic spheres).
     */
    private fun transferChainRight() {
        var i = 0
        while (i < Cradle.BALLS - 1) {
            if (inContact(i, i + 1, slack = 0.10f) && approaching(i, i + 1) &&
                omega[i] > 0.05f && kotlin.math.abs(omega[i + 1]) < 0.28f
            ) {
                var k = i + 1
                while (k < Cradle.BALLS - 1 &&
                    inContact(k, k + 1, slack = 0.10f) &&
                    kotlin.math.abs(omega[k + 1]) < 0.28f
                ) {
                    k++
                }
                val w = omega[i]
                omega[i] = omega[k]
                omega[k] = w
                i = k
            } else {
                i++
            }
        }
    }

    private fun transferChainLeft() {
        var j = Cradle.BALLS - 1
        while (j > 0) {
            if (inContact(j - 1, j, slack = 0.10f) && approaching(j - 1, j) &&
                omega[j] < -0.05f && kotlin.math.abs(omega[j - 1]) < 0.28f
            ) {
                var k = j - 1
                while (k > 0 &&
                    inContact(k - 1, k, slack = 0.10f) &&
                    kotlin.math.abs(omega[k - 1]) < 0.28f
                ) {
                    k--
                }
                val w = omega[j]
                omega[j] = omega[k]
                omega[k] = w
                j = k
            } else {
                j--
            }
        }
    }

    private fun inContact(i: Int, j: Int, slack: Float): Boolean {
        val dx = Cradle.ballX(j, theta[j]) - Cradle.ballX(i, theta[i])
        val dy = Cradle.ballY(j, theta[j]) - Cradle.ballY(i, theta[i])
        val minD = 2f * Cradle.RADIUS + slack
        return dx * dx + dy * dy < minD * minD
    }

    private fun approaching(i: Int, j: Int): Boolean {
        val x0 = Cradle.ballX(i, theta[i])
        val y0 = Cradle.ballY(i, theta[i])
        val x1 = Cradle.ballX(j, theta[j])
        val y1 = Cradle.ballY(j, theta[j])
        val dx = x1 - x0
        val dy = y1 - y0
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-6f)
        val (vx0, vy0) = velocity(i)
        val (vx1, vy1) = velocity(j)
        val rel = (vx1 - vx0) * (dx / d) + (vy1 - vy0) * (dy / d)
        return rel < -1e-4f
    }

    private fun separatePair(i: Int, j: Int) {
        val x0 = Cradle.ballX(i, theta[i])
        val y0 = Cradle.ballY(i, theta[i])
        val x1 = Cradle.ballX(j, theta[j])
        val y1 = Cradle.ballY(j, theta[j])
        val dx = x1 - x0
        val dy = y1 - y0
        val d2 = dx * dx + dy * dy
        val minD = 2f * Cradle.RADIUS
        if (d2 >= minD * minD) return
        val d = sqrt(d2).coerceAtLeast(1e-6f)
        val nx = dx / d
        val ny = dy / d
        val push = (minD - d) * 0.5f
        val heldI = holding && i == holdIndex
        val heldJ = holding && j == holdIndex
        if (!heldI) {
            val nx0 = x0 - nx * push
            val ny0 = y0 - ny * push
            theta[i] = atan2(nx0 - Cradle.pivotX(i), Cradle.PIVOT_Y - ny0)
        }
        if (!heldJ) {
            val nx1 = x1 + nx * push
            val ny1 = y1 + ny * push
            theta[j] = atan2(nx1 - Cradle.pivotX(j), Cradle.PIVOT_Y - ny1)
        }
    }

    private fun velocity(i: Int): Pair<Float, Float> {
        val t = theta[i]
        val s = Cradle.SWING_L * omega[i]
        return s * cos(t) to s * sin(t)
    }

    private fun totalEnergy(): Float {
        var e = 0f
        for (i in 0 until Cradle.BALLS) {
            val speed = Cradle.speed(omega[i])
            e += 0.5f * Cradle.MASS * speed * speed
            e += Cradle.MASS * Cradle.GRAVITY * Cradle.ballY(i, theta[i])
        }
        return e
    }

    private fun restoreEnergy() {
        var ke = 0f
        var pe = 0f
        for (i in 0 until Cradle.BALLS) {
            val speed = Cradle.speed(omega[i])
            ke += 0.5f * Cradle.MASS * speed * speed
            pe += Cradle.MASS * Cradle.GRAVITY * Cradle.ballY(i, theta[i])
        }
        val targetKe = energyLock - pe
        if (ke > 1e-6f && targetKe > 0f) {
            val scale = sqrt(targetKe / ke)
            for (i in 0 until Cradle.BALLS) {
                omega[i] *= scale
            }
        }
    }

    private fun clampedWithoutOverlap(index: Int, angle: Float): Float {
        val neighbor = if (index == 0) 1 else index - 1
        var a = angle
        repeat(8) {
            val x0 = Cradle.ballX(index, a)
            val y0 = Cradle.ballY(index, a)
            val x1 = Cradle.ballX(neighbor, theta[neighbor])
            val y1 = Cradle.ballY(neighbor, theta[neighbor])
            val dx = x1 - x0
            val dy = y1 - y0
            val minD = 2f * Cradle.RADIUS
            if (dx * dx + dy * dy >= minD * minD) return a
            a *= 0.92f
        }
        return a
    }
}

class CradleSnapshot(
    val theta: FloatArray,
    val omega: FloatArray,
    val holding: Boolean,
    val holdIndex: Int,
    val handPhase: Float,
) {
    val isStill: Boolean
        get() {
            if (holding || handPhase > 0.08f) return false
            for (w in omega) {
                if (w > 0.035f || w < -0.035f) return false
            }
            return true
        }
}

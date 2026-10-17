package com.neojou.newtoncradle.sim

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Analytic Newton's cradle acceptance from the migration plan.
 * The simulation is the composeApp copy, with the same constants.
 */
class CradlePhysicsTest {
    @Test
    fun pullOneBallTransfersToTheFarEnd() {
        val physics = CradlePhysics()
        physics.tryGrab(0)
        physics.setHoldAngle(-PULL)
        physics.release()

        val trace = trace(physics)
        val farOmega = trace.maxOf { abs(it.omega[4]) }
        val farTheta = trace.maxOf { abs(it.theta[4]) }
        val midOmega = trace.maxOf { sample ->
            (1..3).maxOf { abs(sample.omega[it]) }
        }
        val midTheta = trace.maxOf { sample ->
            (1..3).maxOf { abs(sample.theta[it]) }
        }
        assertTrue(farOmega > 2f, "far ball never left: omega=$farOmega theta=$farTheta")
        assertTrue(farTheta > 0.35f, "far ball swing too small: theta=$farTheta")
        assertTrue(midOmega < 0.45f, "middle balls moved: omega=$midOmega theta=$midTheta")
        assertTrue(midTheta < 0.08f, "middle displacement too large: theta=$midTheta")
    }

    @Test
    fun pullTwoBallsTransfersToTwoBalls() {
        val physics = CradlePhysics()
        physics.theta[1] = -PULL
        assertTrue(physics.tryGrab(0))
        physics.setHoldAngle(-PULL)
        physics.release()

        val trace = trace(physics)
        val pairOmega = trace.maxOf { minOf(abs(it.omega[3]), abs(it.omega[4])) }
        val stayed = trace.maxOf { sample ->
            if (minOf(abs(sample.omega[3]), abs(sample.omega[4])) < 1f) {
                0f
            } else {
                maxOf(abs(sample.omega[0]), abs(sample.omega[1]), abs(sample.omega[2]))
            }
        }
        assertTrue(pairOmega > 1.5f, "outgoing pair never moved together: paired omega=$pairOmega")
        assertTrue(stayed < 0.55f, "incoming balls still moving while the pair is out: omega=$stayed")
    }

    @Test
    fun onlyTheEndBallsCanBeGrabbed() {
        val physics = CradlePhysics()
        assertTrue(physics.tryGrab(0))
        physics.release()
        assertTrue(physics.tryGrab(Cradle.BALLS - 1))
        physics.release()
        assertTrue(!physics.tryGrab(1))
        assertTrue(!physics.tryGrab(2))
        assertTrue(!physics.holding)
    }

    @Test
    fun fiveBallPivotsMatchTheOriginalLayout() {
        for (i in 0 until Cradle.BALLS) {
            val expected = (i - 2) * (2f * Cradle.RADIUS)
            val delta = kotlin.math.abs(Cradle.pivotX(i) - expected)
            assertTrue(delta < 1e-5f, "pivot $i is ${Cradle.pivotX(i)}, expected $expected")
        }
    }

    @Test
    fun pullOneOfThreeTransfersToTheFarEnd() {
        pullOneTransfers(3)
    }

    @Test
    fun pullOneOfSevenTransfersToTheFarEnd() {
        pullOneTransfers(7)
    }

    private fun pullOneTransfers(count: Int) {
        val physics = CradlePhysics(count)
        assertTrue(physics.tryGrab(0))
        physics.setHoldAngle(-PULL)
        physics.release()
        assertTrue(!physics.tryGrab(count / 2))

        val trace = trace(physics)
        val far = count - 1
        val farOmega = trace.maxOf { abs(it.omega[far]) }
        val farTheta = trace.maxOf { abs(it.theta[far]) }
        val midOmega = trace.maxOf { sample ->
            (1 until far).maxOf { abs(sample.omega[it]) }
        }
        val midTheta = trace.maxOf { sample ->
            (1 until far).maxOf { abs(sample.theta[it]) }
        }
        assertTrue(farOmega > 2f, "count=$count far ball never left: omega=$farOmega theta=$farTheta")
        assertTrue(farTheta > 0.35f, "count=$count far swing too small: theta=$farTheta")
        assertTrue(midOmega < 0.45f, "count=$count middle balls moved: omega=$midOmega theta=$midTheta")
        assertTrue(midTheta < 0.08f, "count=$count middle displacement too large: theta=$midTheta")
    }

    private fun trace(physics: CradlePhysics): List<Sample> {
        val samples = ArrayList<Sample>(STEPS)
        repeat(STEPS) {
            physics.step(DT)
            samples += Sample(physics.theta.copyOf(), physics.omega.copyOf())
        }
        return samples
    }

    private class Sample(val theta: FloatArray, val omega: FloatArray)

    private companion object {
        const val DT = 1f / 120f
        const val STEPS = 360
        val PULL = 50f * (PI.toFloat() / 180f)
    }
}

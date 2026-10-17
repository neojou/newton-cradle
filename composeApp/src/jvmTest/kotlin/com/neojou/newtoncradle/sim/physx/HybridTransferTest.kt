package com.neojou.newtoncradle.sim.physx

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HybridTransferTest {
    @Test
    fun pullOneSendsTheFarBall() {
        val count = 5
        val theta = FloatArray(count)
        val omega = FloatArray(count)
        theta[0] = 0f
        omega[0] = 5f
        val swapped = HybridTransfer.apply(theta, omega, count)
        assertTrue(swapped > 2f)
        assertEquals(0f, omega[0])
        assertEquals(5f, omega[count - 1])
        assertEquals(0f, omega[1])
        assertEquals(0f, omega[2])
        assertEquals(0f, omega[3])
    }

    @Test
    fun pullTwoSendsTheTwoFarBallsAcrossTwoContacts() {
        val count = 5
        val theta = FloatArray(count)
        val omega = FloatArray(count)
        omega[0] = 4f
        omega[1] = 4f
        HybridTransfer.apply(theta, omega, count)
        HybridTransfer.apply(theta, omega, count)
        assertTrue(omega[3] > 1.5f && omega[4] > 1.5f, "far pair ${omega.toList()}")
        assertTrue(omega[0] < 0.55f && omega[1] < 0.55f && omega[2] < 0.55f, "near side ${omega.toList()}")
    }

    @Test
    fun restingStackDoesNotTransfer() {
        val theta = FloatArray(5)
        val omega = FloatArray(5)
        val swapped = HybridTransfer.apply(theta, omega, 5)
        assertEquals(0f, swapped)
    }

    @Test
    fun separatedBallsDoNotTransfer() {
        val count = 5
        val theta = FloatArray(count)
        val omega = FloatArray(count)
        theta[0] = -50f * (PI.toFloat() / 180f)
        omega[0] = 0.2f
        val swapped = HybridTransfer.apply(theta, omega, count)
        assertEquals(0f, swapped)
        assertEquals(0.2f, omega[0])
    }
}

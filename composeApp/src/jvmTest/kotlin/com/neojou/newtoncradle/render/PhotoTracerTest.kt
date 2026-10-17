package com.neojou.newtoncradle.render

import com.neojou.newtoncradle.render.photo.RayTracer
import com.neojou.newtoncradle.sim.Cradle
import com.neojou.newtoncradle.sim.Vec3
import kotlin.test.Test
import kotlin.test.assertTrue

class PhotoTracerTest {
    @Test
    fun tracesASmallStillFrame() {
        val tracer = RayTracer()
        tracer.camera.eye = Vec3(8f, 6f, 28f)
        tracer.camera.target = Vec3(0f, -1f, 0f)
        tracer.camera.updateBasis()
        val count = Cradle.BALLS
        val x = FloatArray(count) { Cradle.ballX(it, 0f) }
        val y = FloatArray(count) { Cradle.ballY(it, 0f) }
        assertTrue(tracer.beginFrame(x, y, count, 48, 32, 0.2f, still = false))
        assertTrue(tracer.traceRows(10_000))
        assertTrue(tracer.imageWidth >= 8 && tracer.imageHeight >= 8)
        assertTrue(tracer.rgba.any { it != 0.toByte() })
    }
}

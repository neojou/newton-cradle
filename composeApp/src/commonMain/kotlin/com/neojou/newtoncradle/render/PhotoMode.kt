package com.neojou.newtoncradle.render

import com.neojou.newtoncradle.render.photo.RayTracer
import com.neojou.newtoncradle.sim.Vec3
import de.fabmax.kool.pipeline.BufferedImageData2d
import de.fabmax.kool.pipeline.MipMapping
import de.fabmax.kool.pipeline.SamplerSettings
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.scene.PerspectiveCamera
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Uint8Buffer

/**
 * CPU photo mode. While the cradle and camera are still, the analytic tracer
 * fills a texture a few scanlines at a time. Each finished pass raises the
 * quality, so the overlay gets cleaner across frames. Motion hides it.
 */
class PhotoMode(private val scene: Scene) {
    private val tracer = RayTracer()
    val texture: Texture2d = Texture2d(TexFormat.RGBA, MipMapping.Off, SamplerSettings(), "cradle-photo")

    var showing: Boolean = false
        private set
    private var pass = 0
    private var poseKey = 0
    private var frameOpen = false
    private var serial = 0

    fun reset() {
        pass = 0
        poseKey = 0
        frameOpen = false
        showing = false
    }

    /**
     * Returns true while the overlay should cover the 3D view.
     * A pose change waits one settled frame before tracing, so dragging does
     * not restart a full frame every tick.
     */
    fun tick(
        enabled: Boolean,
        settled: Boolean,
        centerX: FloatArray,
        centerY: FloatArray,
        count: Int,
    ): Boolean {
        if (!enabled || !settled || count <= 0) {
            reset()
            return false
        }
        val cam = scene.camera as? PerspectiveCamera ?: return false
        val key = poseKey(cam, centerX, centerY, count)
        if (key != poseKey) {
            poseKey = key
            pass = 0
            frameOpen = false
            showing = false
            return false
        }
        if (pass >= QUALITY.size) return showing
        val view = scene.mainRenderPass.viewport
        val viewW = if (view.width > 16) view.width else 1280
        val viewH = if (view.height > 16) view.height else 720
        if (!frameOpen) {
            val eye = cam.globalPos
            val look = cam.globalLookAt
            val up = cam.globalUp
            tracer.camera.eye = Vec3(eye.x, eye.y, eye.z)
            tracer.camera.target = Vec3(look.x, look.y, look.z)
            tracer.camera.up = Vec3(up.x, up.y, up.z)
            tracer.camera.fovY = cam.fovY.rad
            tracer.camera.updateBasis()
            val started = tracer.beginFrame(
                centerX, centerY, count,
                viewW, viewH,
                QUALITY[pass],
                still = pass == QUALITY.lastIndex,
            )
            if (!started) return false
            frameOpen = true
        }
        val rows = if (pass == QUALITY.lastIndex) 8 else 36
        val done = tracer.traceRows(rows)
        upload()
        showing = true
        if (done) {
            pass++
            frameOpen = false
        }
        return true
    }

    private fun upload() {
        val rgba = tracer.rgba
        if (rgba.isEmpty() || tracer.imageWidth < 8) return
        val buf = Uint8Buffer(rgba.size)
        for (i in rgba.indices) buf[i] = rgba[i].toUByte()
        serial++
        texture.uploadLazy(
            BufferedImageData2d(
                buf,
                tracer.imageWidth,
                tracer.imageHeight,
                TexFormat.RGBA,
                id = "photo-$serial",
            ),
        )
    }

    private fun poseKey(cam: PerspectiveCamera, x: FloatArray, y: FloatArray, count: Int): Int {
        var hash = count
        hash = hash * 31 + quant(cam.globalPos.x)
        hash = hash * 31 + quant(cam.globalPos.y)
        hash = hash * 31 + quant(cam.globalPos.z)
        hash = hash * 31 + quant(cam.globalLookAt.x)
        hash = hash * 31 + quant(cam.globalLookAt.y)
        hash = hash * 31 + quant(cam.globalLookAt.z)
        for (i in 0 until count) {
            hash = hash * 31 + quant(x[i])
            hash = hash * 31 + quant(y[i])
        }
        return hash
    }

    private fun quant(value: Float): Int = (value * 40f).toInt()

    private companion object {
        val QUALITY = floatArrayOf(0.28f, 0.62f, 0.92f)
    }
}

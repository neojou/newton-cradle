package com.neojou.newtoncradle.input

import com.neojou.newtoncradle.Cameras
import com.neojou.newtoncradle.CradleSession
import com.neojou.newtoncradle.sim.Cradle
import de.fabmax.kool.KoolContext
import de.fabmax.kool.input.InputStack
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.input.PointerState
import de.fabmax.kool.input.UniversalKeyCode
import de.fabmax.kool.math.RayF
import de.fabmax.kool.scene.Scene
import kotlin.math.abs

/**
 * Drag either end ball with the left button. Left-drag on empty space still
 * orbits, because this listener is inserted ahead of the orbit camera and
 * only consumes the pointer while a ball is held. UI surfaces push themselves
 * above this listener while the pointer is over a control.
 *
 * R resets. Space releases a held ball, or resets when nothing is held.
 * Keys 1–7 select the camera presets.
 */
class CradleControls(
    private val scene: Scene,
    private val session: CradleSession,
) : InputStack.PointerListener {
    private val ray = RayF()
    private val keys = ArrayList<InputStack.SimpleKeyListener>(9)

    init {
        keys += KeyboardInput.addKeyListener(
            UniversalKeyCode('R'),
            "cradle-reset",
            InputStack.KEY_FILTER_KEY_PRESSED,
        ) { session.reset() }
        keys += KeyboardInput.addKeyListener(
            UniversalKeyCode(' '),
            "cradle-space",
            InputStack.KEY_FILTER_KEY_PRESSED,
        ) {
            if (session.holding) session.releaseHold() else session.reset()
        }
        Cameras.all.forEachIndexed { index, preset ->
            keys += KeyboardInput.addKeyListener(
                UniversalKeyCode('1' + index),
                "cradle-cam-${preset.id}",
                InputStack.KEY_FILTER_KEY_PRESSED,
            ) { session.applyCamera(preset) }
        }
        val listeners = InputStack.defaultInputHandler.pointerListeners
        // BufferedList only accepts staged edits. Index 0 runs before the orbit camera.
        listeners.stageAdd(this, 0)
        scene.onRelease {
            listeners.stageRemove(this)
            keys.forEach { KeyboardInput.removeKeyListener(it) }
        }
    }

    override fun handlePointer(pointerState: PointerState, ctx: KoolContext) {
        val pointer = pointerState.primaryPointer
        if (!pointer.isValid || pointer.isConsumed()) return
        val view = scene.mainRenderPass.defaultView
        if (!view.camera.computePickRay(ray, pointer, view.viewport)) return

        if (pointer.isLeftButtonPressed && !session.holding) {
            val hit = swingHit() ?: return
            val index = nearestEnd(hit[0], hit[1])
            if (index >= 0) session.tryGrab(index)
        }
        if (!session.holding || !pointer.isLeftButtonDown) {
            if (session.holding && pointer.isLeftButtonReleased) session.releaseHold()
            return
        }
        pointer.consume()
        val hit = swingHit() ?: return
        session.setHoldAngle(session.angleFromWorld(hit[0], hit[1], session.holdIndex))
        if (pointer.isLeftButtonReleased) session.releaseHold()
    }

    private fun swingHit(): FloatArray? {
        val dirZ = ray.direction.z
        if (abs(dirZ) < 1e-5f) return null
        val t = -ray.origin.z / dirZ
        if (t <= 0f) return null
        return floatArrayOf(
            ray.origin.x + ray.direction.x * t,
            ray.origin.y + ray.direction.y * t,
        )
    }

    private fun nearestEnd(x: Float, y: Float): Int {
        var best = -1
        var bestD = Float.MAX_VALUE
        val reach = Cradle.RADIUS * 2.4f
        val reach2 = reach * reach
        val count = session.ballCount
        for (index in intArrayOf(0, count - 1)) {
            val bx = session.visualX[index]
            val by = session.visualY[index]
            val dx = x - bx
            val dy = y - by
            val d = dx * dx + dy * dy
            if (d <= reach2 && d < bestD) {
                bestD = d
                best = index
            }
        }
        return best
    }
}

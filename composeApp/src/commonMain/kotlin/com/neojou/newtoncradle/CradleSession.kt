package com.neojou.newtoncradle

import com.neojou.newtoncradle.render.CradleVisual
import com.neojou.newtoncradle.render.GltfCradle
import com.neojou.newtoncradle.render.PhotoMode
import com.neojou.newtoncradle.render.ProceduralCradle
import com.neojou.newtoncradle.render.ShadingPath
import com.neojou.newtoncradle.render.StudioRig
import com.neojou.newtoncradle.sim.Cradle
import com.neojou.newtoncradle.sim.CradlePhysics
import com.neojou.newtoncradle.sim.physx.PhysicsMode
import com.neojou.newtoncradle.sim.physx.PhysxCradle
import com.neojou.newtoncradle.ui.UiText
import de.fabmax.kool.modules.gltf.GltfLoadConfig
import de.fabmax.kool.modules.gltf.GltfMaterialConfig
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.scene.Model
import de.fabmax.kool.scene.OrbitInputTransform
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.logW
import kotlin.math.PI

data class CameraPreset(
    val id: String,
    val label: String,
    val yaw: Float,
    val pitch: Float,
    val zoom: Double,
    val turntable: Boolean = false,
)

object Cameras {
    val front = CameraPreset("front", UiText.CAM_FRONT, 0f, -8f, 34.0)
    val side = CameraPreset("side", UiText.CAM_SIDE, 90f, -8f, 34.0)
    val top = CameraPreset("top", UiText.CAM_TOP, 0f, -89f, 40.0)
    val hero = CameraPreset("hero", UiText.CAM_HERO, 42f, -18f, 32.0)
    val macro = CameraPreset("macro", UiText.CAM_MACRO, 18f, -6f, 14.0)
    val turn = CameraPreset("turn", UiText.CAM_TURN, 20f, -16f, 36.0, turntable = true)
    val free = CameraPreset("free", UiText.CAM_FREE, 32f, -16f, 36.0)

    val all = listOf(front, side, top, hero, macro, turn, free)

    const val TURNTABLE_DEG_PER_SEC = 25.0
}

/**
 * Stable owner of the analytic cradle. The settings panel keeps this object
 * and reads [physics] after a ball-count rebuild.
 */
class CradleSession(
    private val scene: Scene,
    val orbit: OrbitInputTransform,
    val rig: StudioRig,
    val physxAvailable: Boolean,
    initialCount: Int = Cradle.BALLS,
) {
    var physics = CradlePhysics(initialCount)
        private set
    private var procedural: ProceduralCradle
    private var gltf: GltfCradle? = null
    var visual: CradleVisual
        private set
    var physx: PhysxCradle? = null
        private set
    val shading: ShadingPath
    private val photo: PhotoMode

    val paused = mutableStateOf(false)
    val timeScale = mutableStateOf(1f)
    val pullDegrees = mutableStateOf(50f)
    val ballCountUi = mutableStateOf(initialCount.toFloat())
    val cameraId = mutableStateOf(Cameras.free.id)
    val turntable = mutableStateOf(false)
    val mode = mutableStateOf(PhysicsMode.ANALYTIC)
    val restitution = mutableStateOf(1f)
    val gapMm = mutableStateOf(0.1f)
    val showCollision = mutableStateOf(false)
    val showJoints = mutableStateOf(false)
    val modeNote = mutableStateOf("")
    val deferredOn = mutableStateOf(false)
    val ssrOn = mutableStateOf(true)
    val photoOn = mutableStateOf(false)
    val shadingNote = mutableStateOf("")
    val modelNote = mutableStateOf(UiText.MODEL_PROC)
    val photoNote = mutableStateOf("")
    val photoImage = mutableStateOf<Texture2d?>(null)
    private var ssaoEnabled = true

    val visualX = FloatArray(MAX_BALLS)
    val visualY = FloatArray(MAX_BALLS)
    val visualPivot = FloatArray(MAX_BALLS)

    val ballCount: Int get() = physics.ballCount
    val holding: Boolean get() = physx?.holding ?: physics.holding
    val holdIndex: Int get() = physx?.holdIndex ?: physics.holdIndex

    init {
        procedural = ProceduralCradle(initialCount, rig)
        visual = procedural
        shading = ShadingPath(scene, rig)
        photo = PhotoMode(scene)
        scene.addNode(procedural)
        applyCamera(Cameras.free)
        refreshVisual()
        scene.onRelease { releasePhysx() }
    }

    fun onFrame(dt: Float) {
        val live = physx
        if (live == null) {
            if (!paused.value) physics.step(dt * timeScale.value)
        } else {
            live.syncStepper(paused.value, timeScale.value)
        }
        refreshVisual()
        tickPhoto()
    }

    fun gltfConfig(): GltfLoadConfig = GltfLoadConfig(
        generateNormals = false,
        applyMaterials = true,
        removeEmptyNodes = false,
        loadAnimations = false,
        applyTransforms = false,
        materialConfig = GltfMaterialConfig(
            shadowMaps = rig.shadows,
            scrSpcAmbientOcclusionMap = rig.ao.aoMap,
            environmentMap = rig.environment,
            isDeferredShading = false,
            maxNumberOfLights = 4,
        ),
    )

    fun adoptGltf(model: Model?) {
        if (model == null) {
            setModelNote(UiText.MODEL_PROC)
            return
        }
        val bound = GltfCradle.tryCreate(model)
        if (bound == null) {
            logW { "glTF is missing NC_* cradle nodes; keeping the procedural model" }
            model.release()
            setModelNote(UiText.MODEL_PROC)
            return
        }
        gltf = bound
        setModelNote(UiText.MODEL_GLTF)
        if (ballCount == Cradle.BALLS) useVisual(bound)
    }

    fun setDeferred(enabled: Boolean) {
        val reason = shading.setDeferred(enabled, ssrOn.value, ssaoEnabled, visual.root)
        if (reason != null) {
            deferredOn.set(false)
            setShadingNote(UiText.DEFERRED_FAIL)
            return
        }
        deferredOn.set(enabled)
        setShadingNote(if (enabled && !shadingCompute) UiText.NO_COMPUTE else "")
    }

    fun setSsr(enabled: Boolean) {
        ssrOn.set(enabled)
        shading.setSsr(enabled)
    }

    fun setSsao(enabled: Boolean, radius: Float, strength: Float) {
        ssaoEnabled = enabled
        shading.setSsao(enabled, radius, strength)
    }

    fun rebuild(count: Int) {
        val n = count.coerceIn(3, 7)
        ballCountUi.set(n.toFloat())
        if (n == physics.ballCount) return
        shading.forget(procedural.root)
        procedural.root.parent?.removeNode(procedural.root)
        procedural.release()
        physics = CradlePhysics(n)
        procedural = ProceduralCradle(n, rig)
        val next: CradleVisual = if (n == Cradle.BALLS && gltf != null) gltf!! else procedural
        if (mode.value != PhysicsMode.ANALYTIC) openPhysx(mode.value)
        useVisual(next)
        applyVisibility()
        refreshVisual()
    }

    fun setMode(next: PhysicsMode) {
        if (next != PhysicsMode.ANALYTIC && !physxAvailable) {
            modeNote.set(UiText.PHYSX_UNAVAILABLE)
            return
        }
        if (next == mode.value && (next == PhysicsMode.ANALYTIC || physx != null)) return
        mode.set(next)
        physics.reset()
        if (next == PhysicsMode.ANALYTIC) {
            releasePhysx()
            modeNote.set("")
        } else {
            openPhysx(next)
            modeNote.set(if (next == PhysicsMode.PURE) UiText.PURE_HINT else "")
        }
        applyVisibility()
        refreshVisual()
    }

    fun setRestitution(value: Float) {
        restitution.set(value)
        physx?.setRestitution(value)
    }

    fun setGapMm(millimeters: Float) {
        gapMm.set(millimeters)
        if (mode.value != PhysicsMode.ANALYTIC) openPhysx(mode.value)
    }

    fun setCollisionView(enabled: Boolean) {
        showCollision.set(enabled)
        if (enabled) showJoints.set(true)
        applyVisibility()
    }

    fun reset() {
        val live = physx
        if (live == null) physics.reset() else live.requestReset()
    }

    fun releaseHold() {
        val live = physx
        if (live == null) physics.release() else live.requestRelease()
    }

    fun pullLeft() {
        if (tryGrab(0)) setHoldAngle(-pullRadians())
    }

    fun pullRight() {
        if (tryGrab(ballCount - 1)) setHoldAngle(pullRadians())
    }

    fun tryGrab(index: Int): Boolean {
        val live = physx
        return if (live == null) physics.tryGrab(index) else live.tryGrab(index)
    }

    fun setHoldAngle(angle: Float) {
        val live = physx
        if (live == null) physics.setHoldAngle(angle) else live.setHoldAngle(angle)
    }

    fun takeImpact(): Float = physx?.takeImpact() ?: physics.takeImpact()

    fun kineticEnergy(): Float = physx?.kineticEnergy() ?: physics.kineticEnergy()

    fun angleFromWorld(x: Float, y: Float, index: Int): Float {
        val live = physx
        return if (live == null) {
            physics.angleFromWorld(x, y, index)
        } else {
            kotlin.math.atan2(x - visualPivot[index], Cradle.PIVOT_Y - y)
        }
    }

    fun applyCamera(preset: CameraPreset) {
        cameraId.set(preset.id)
        turntable.set(preset.turntable)
        orbit.setRotation(preset.yaw, preset.pitch)
        orbit.setZoom(preset.zoom, 8.0, 90.0)
        orbit.setTranslation(0f, -1.5f, 0f)
    }

    private fun pullRadians(): Float = pullDegrees.value * (PI.toFloat() / 180f)

    private fun gapUnits(): Float = gapMm.value / MILLIMETERS_PER_UNIT

    private fun openPhysx(next: PhysicsMode) {
        releasePhysx()
        physx = PhysxCradle(
            scene = scene,
            ballCount = physics.ballCount,
            hybrid = next == PhysicsMode.HYBRID,
            gapUnits = gapUnits(),
            restitution = restitution.value,
        )
        applyVisibility()
    }

    private fun releasePhysx() {
        physx?.release()
        physx = null
    }

    private fun applyVisibility() {
        val collision = showCollision.value && physx != null
        visual.setDynamicsVisible(!collision)
        physx?.setCollisionVisible(collision)
    }

    private fun useVisual(next: CradleVisual) {
        if (visual !== next) {
            visual.root.parent?.removeNode(visual.root)
            visual = next
        }
        if (deferredOn.value) {
            shading.setDeferred(true, ssrOn.value, ssaoEnabled, visual.root)
        } else if (visual.root.parent == null) {
            scene.addNode(visual.root)
        }
        syncVisual()
    }

    private fun syncVisual() {
        val live = physx
        if (live == null) visual.sync(physics.theta) else visual.syncCenters(visualX, visualY, visualPivot)
    }

    private fun tickPhoto() {
        val show = photo.tick(
            enabled = photoOn.value,
            settled = sceneSettled(),
            centerX = visualX,
            centerY = visualY,
            count = ballCount,
        )
        val next = if (show) photo.texture else null
        if (photoImage.value !== next) photoImage.set(next)
        val note = when {
            !photoOn.value -> ""
            !sceneSettled() -> UiText.PHOTO_WAIT
            photo.showing && show -> UiText.PHOTO_HINT
            else -> ""
        }
        if (photoNote.value != note) photoNote.set(note)
    }

    private fun sceneSettled(): Boolean {
        if (holding || turntable.value) return false
        if (!paused.value && kineticEnergy() > 0.08f) return false
        return true
    }

    private fun setModelNote(text: String) {
        if (modelNote.value != text) modelNote.set(text)
    }

    private fun setShadingNote(text: String) {
        if (shadingNote.value != text) shadingNote.set(text)
    }

    private val shadingCompute: Boolean
        get() = de.fabmax.kool.KoolSystem.requireContext().backend.features.computeShaders

    private fun refreshVisual() {
        val n = ballCount
        val live = physx
        if (live == null) {
            for (i in 0 until n) {
                visualX[i] = Cradle.ballX(i, physics.theta[i], n)
                visualY[i] = Cradle.ballY(i, physics.theta[i])
                visualPivot[i] = Cradle.pivotX(i, n)
            }
        } else {
            live.copyVisual(visualX, visualY, visualPivot)
        }
    }

    private companion object {
        const val MAX_BALLS = 8
        const val MILLIMETERS_PER_UNIT = 15f
    }
}

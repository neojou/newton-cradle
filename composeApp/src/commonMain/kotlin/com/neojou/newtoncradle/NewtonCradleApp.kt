package com.neojou.newtoncradle

import com.neojou.newtoncradle.audio.CollisionSound
import com.neojou.newtoncradle.input.CradleControls
import com.neojou.newtoncradle.render.JointOverlay
import com.neojou.newtoncradle.render.installStudio
import com.neojou.newtoncradle.ui.installCradleUi
import de.fabmax.kool.Assets
import de.fabmax.kool.KoolContext
import de.fabmax.kool.hdriEnvironment
import de.fabmax.kool.loadAudioClip
import de.fabmax.kool.modules.gltf.loadGltfModel
import de.fabmax.kool.math.deg
import de.fabmax.kool.modules.ui2.UiScene
import de.fabmax.kool.physics.Physics
import de.fabmax.kool.pipeline.ClearColorFill
import de.fabmax.kool.pipeline.ibl.EnvironmentMap
import de.fabmax.kool.scene.PerspectiveCamera
import de.fabmax.kool.scene.orbitCamera
import de.fabmax.kool.scene.scene
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.ColorGradient
import de.fabmax.kool.util.DebugOverlay
import de.fabmax.kool.util.FrontendScope
import de.fabmax.kool.util.Time
import de.fabmax.kool.util.debugOverlay
import de.fabmax.kool.util.logE
import kotlinx.coroutines.launch

/**
 * Analytic cradle on Forward PBR, with PhysX, glTF, deferred and photo mode
 * available from the panel. The default path stays analytic and forward.
 */
fun launchApp(ctx: KoolContext) = FrontendScope.launch {
    val physxReady = try {
        Physics.loadPhysics()
        Physics.isLoaded
    } catch (error: Throwable) {
        logE { "PhysX failed to load: $error" }
        false
    }
    val hdri = Assets.hdriEnvironment("assets/hdri/studio_small_09.rgbe.png")
    val clip = Assets.loadAudioClip("assets/sounds/clang.wav").getOrNull()
    val environment = hdri.getOrNull() ?: EnvironmentMap.fromGradientColor(
        ColorGradient(
            listOf(
                0f to Color(0.04f, 0.045f, 0.05f),
                0.45f to Color(0.22f, 0.24f, 0.28f),
                0.62f to Color(0.82f, 0.84f, 0.88f),
                1f to Color(0.55f, 0.57f, 0.62f),
            ),
        ),
    )
    val sound = clip?.let(::CollisionSound)

    lateinit var session: CradleSession
    val world = scene("cradle") {
        clearColor = ClearColorFill(Color(0.05f, 0.05f, 0.055f))
        val orbit = orbitCamera {
            setZoom(36.0, 8.0, 90.0)
        }
        (camera as PerspectiveCamera).fovY = 38f.deg
        val rig = installStudio(environment)
        session = CradleSession(this, orbit, rig, physxReady)
        val joints = JointOverlay()
        addNode(joints)
        CradleControls(this, session)
        onUpdate {
            session.onFrame(Time.deltaT)
            if (session.turntable.value) {
                orbit.verticalRotation += Time.deltaT.toDouble() * Cameras.TURNTABLE_DEG_PER_SEC
            }
            if (session.physx == null) {
                session.visual.sync(session.physics.theta)
            } else {
                session.visual.syncCenters(session.visualX, session.visualY, session.visualPivot)
            }
            joints.isVisible = session.showJoints.value
            joints.sync(session.ballCount, session.visualX, session.visualY, session.visualPivot)
            sound?.playImpact(session.takeImpact())
        }
    }

    val debug = debugOverlay(DebugOverlay.Position.LOWER_RIGHT)
    debug.isVisible = false
    val ui = UiScene("ui") { }
    installCradleUi(ui, world, ctx, session, debug)

    val loaded = try {
        Assets.loadGltfModel("assets/models/newton_cradle.glb", session.gltfConfig()).getOrNull()
    } catch (error: Throwable) {
        logE { "glTF load failed: $error" }
        null
    }
    session.adoptGltf(loaded)

    ctx.scenes += world
    ctx.scenes += debug
    ctx.scenes += ui
}

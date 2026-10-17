package com.neojou.newtoncradle.render

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import de.fabmax.kool.math.spatial.BoundingBoxF
import de.fabmax.kool.modules.ksl.KslPbrShader
import de.fabmax.kool.pipeline.ao.AoPipeline
import de.fabmax.kool.pipeline.ibl.EnvironmentMap
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.skybox
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.ShadowMap
import de.fabmax.kool.util.SimpleShadowMap
import de.fabmax.kool.util.releaseWith

/**
 * Forward PBR studio rig: one HDRI, two shadowed spots, SSAO.
 *
 * The HDRI is Poly Haven `studio_small_09` (CC0), stored as RGBE PNG.
 * kool 0.19 [de.fabmax.kool.pipeline.ibl.RgbeDecoder] clamps radiance at 20,
 * so the brightest windows are compressed. Spot intensities are the planned
 * warm 50000 and cool 25000 at 45°.
 */
class StudioRig(
    val environment: EnvironmentMap,
    val ao: AoPipeline,
    val shadows: List<ShadowMap>,
) {
    fun pbr(color: Color, metallic: Float, roughness: Float): KslPbrShader = KslPbrShader {
        color { constColor(color) }
        metallic(metallic)
        roughness(roughness)
        enableImageBasedLighting(environment)
        lighting {
            shadows.forEach { addShadowMap(it) }
        }
        enableSsao(ao.aoMap)
    }
}

fun Scene.installStudio(environment: EnvironmentMap): StudioRig {
    lighting.maxNumberOfLights = 4
    val key = lighting.addSpotLight {
        setup(Vec3f(14f, 18f, 16f), Vec3f(-14f, -19.2f, -16f), 45f.deg)
        setColor(Color(1f, 0.93f, 0.84f), 50_000f)
    }
    val fill = lighting.addSpotLight {
        setup(Vec3f(-16f, 12f, 10f), Vec3f(16f, -13.2f, -10f), 45f.deg)
        setColor(Color(0.78f, 0.86f, 1f), 25_000f)
    }
    val bounds = BoundingBoxF(Vec3f(-20f, -12f, -12f), Vec3f(22f, 12f, 12f))
    val keyShadow = SimpleShadowMap(this, key).also {
        it.shadowMapLevel = 0
        it.shadowBounds = bounds
    }
    val fillShadow = SimpleShadowMap(this, fill).also {
        it.shadowMapLevel = 1
        it.shadowBounds = bounds
    }
    val ao = AoPipeline.createForward(this).also {
        it.radius = 2.4f
        it.strength = 1.15f
    }
    environment.releaseWith(this)
    skybox(environment)
    return StudioRig(environment, ao, listOf(keyShadow, fillShadow))
}

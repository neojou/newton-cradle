package com.neojou.newtoncradle.render

import de.fabmax.kool.pipeline.BlendMode
import de.fabmax.kool.pipeline.DrawShader
import de.fabmax.kool.pipeline.deferred.DeferredPipeline
import de.fabmax.kool.pipeline.deferred.DeferredPipelineConfig
import de.fabmax.kool.pipeline.deferred.deferredKslPbrShader
import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.SimpleShadowMap

/**
 * Forward PBR stays on until the panel asks for the 0.19 deferred pipeline.
 * SSR is a flag on that pipeline. Creation failure is returned as a reason
 * and the cradle stays on the forward path.
 */
class ShadingPath(
    private val scene: Scene,
    private val rig: StudioRig,
) {
    var pipeline: DeferredPipeline? = null
        private set
    var enabled: Boolean = false
        private set
    private var output: Node? = null
    private val saved = mutableMapOf<Mesh<*>, DrawShader?>()
    private val deferredShaders = mutableMapOf<String, DrawShader>()

    fun setDeferred(on: Boolean, ssr: Boolean, ssao: Boolean, cradle: Node): String? {
        if (!on) {
            pipeline?.isEnabled = false
            pipeline?.isSsrEnabled = false
            output?.isVisible = false
            restoreShaders()
            rig.shadows.forEach { it.isShadowMapEnabled = true }
            rig.ao.isEnabled = ssao
            attach(cradle, deferred = false)
            enabled = false
            return null
        }
        val pipe = try {
            ensurePipeline()
        } catch (error: Throwable) {
            enabled = false
            output?.isVisible = false
            rig.shadows.forEach { it.isShadowMapEnabled = true }
            rig.ao.isEnabled = ssao
            attach(cradle, deferred = false)
            return error.message ?: "deferred"
        }
        pipe.isEnabled = true
        pipe.isSsrEnabled = ssr
        pipe.isAoEnabled = ssao
        output?.isVisible = true
        rig.shadows.forEach { it.isShadowMapEnabled = false }
        rig.ao.isEnabled = false
        attach(cradle, deferred = true)
        applyDeferred(cradle)
        enabled = true
        return null
    }

    fun setSsr(ssr: Boolean) {
        if (enabled) pipeline?.isSsrEnabled = ssr
    }

    fun setSsao(ssao: Boolean, radius: Float, strength: Float) {
        rig.ao.radius = radius
        rig.ao.strength = strength
        rig.ao.isEnabled = ssao && !enabled
        pipeline?.let { pipe ->
            pipe.isAoEnabled = ssao && enabled
            pipe.aoPipeline?.let { ao ->
                ao.radius = radius
                ao.strength = strength
            }
        }
    }

    /** Drop saved shaders for a cradle that is about to be released. */
    fun forget(root: Node) {
        walk(root, root.name) { mesh, _ -> saved.remove(mesh) }
    }

    private fun ensurePipeline(): DeferredPipeline {
        pipeline?.let { return it }
        val pipe = DeferredPipeline(
            scene,
            DeferredPipelineConfig().apply {
                isWithAmbientOcclusion = true
                isWithScreenSpaceReflections = true
                useImageBasedLighting(rig.environment)
                maxGlobalLights = 4
            },
        )
        pipe.shadowMaps.forEachIndexed { index, map ->
            (map as? SimpleShadowMap)?.shadowMapLevel = index
        }
        val quad = pipe.createDefaultOutputQuad()
        scene.addNode(quad, 0)
        output = quad
        pipeline = pipe
        return pipe
    }

    private fun attach(node: Node, deferred: Boolean) {
        val parent: Node = if (deferred && pipeline != null) pipeline!!.sceneContent else scene
        if (node.parent === parent) return
        node.parent?.removeNode(node)
        parent.addNode(node)
    }

    private fun applyDeferred(root: Node) {
        walk(root, root.name) { mesh, name ->
            if (mesh !in saved) saved[mesh] = mesh.shader
            mesh.shader = deferredShader(name)
        }
    }

    private fun restoreShaders() {
        for ((mesh, shader) in saved) {
            mesh.shader = shader
        }
        saved.clear()
    }

    private fun deferredShader(nodeName: String): DrawShader {
        val kind = when {
            "NC_Ball" in nodeName -> "steel"
            "NC_Wire" in nodeName || "NC_Rod" in nodeName -> "rod"
            "NC_Frame" in nodeName -> "post"
            "NC_Base" in nodeName -> "wood"
            else -> "floor"
        }
        return deferredShaders.getOrPut(kind) {
            val (color, metallic, roughness) = when (kind) {
                "steel" -> Triple(Color(0.74f, 0.76f, 0.78f), 1f, 0.08f)
                "rod" -> Triple(Color(0.62f, 0.64f, 0.66f), 1f, 0.22f)
                "post" -> Triple(Color(0.22f, 0.23f, 0.25f), 0.85f, 0.38f)
                "wood" -> Triple(Color.fromHex("8C5A3C"), 0f, 0.62f)
                else -> Triple(Color.fromHex("24262B"), 0.05f, 0.88f)
            }
            deferredKslPbrShader {
                color { constColor(color) }
                metallic(metallic)
                roughness(roughness)
                pipeline { blendMode = BlendMode.DISABLED }
            }
        }
    }

    private fun walk(node: Node, owner: String, block: (Mesh<*>, String) -> Unit) {
        val name = if ("NC_" in node.name) node.name else owner
        if (node is Mesh<*>) block(node, name)
        for (child in node.children) walk(child, name, block)
    }
}

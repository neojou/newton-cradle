package com.neojou.newtoncradle

import com.neojou.newtoncradle.ui.NotoSansTcFont
import de.fabmax.kool.KoolApplication
import de.fabmax.kool.KoolConfigJs
import de.fabmax.kool.NativeAssetLoader
import de.fabmax.kool.pipeline.backend.gl.RenderBackendGl
import de.fabmax.kool.pipeline.backend.webgpu.RenderBackendWebGpu
import kotlinx.browser.window

/**
 * Browser entry. `?backend=webgl` forces WebGL2. Otherwise WebGPU is tried
 * and [KoolConfigJs.useWebGlFallback] covers browsers without it.
 */
fun main() {
    val forceWebGl = window.location.search.contains("backend=webgl")
    KoolApplication(
        config = KoolConfigJs(
            canvasName = "glCanvas",
            defaultFont = NotoSansTcFont.info,
            defaultAssetLoader = NativeAssetLoader("."),
            renderBackend = if (forceWebGl) RenderBackendGl else RenderBackendWebGpu,
            useWebGlFallback = true,
        )
    ) {
        launchApp(ctx)
    }
}

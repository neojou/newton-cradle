package com.neojou.newtoncradle

import com.neojou.newtoncradle.ui.NotoSansTcFont
import de.fabmax.kool.KoolApplication
import de.fabmax.kool.KoolConfigJvm
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.pipeline.backend.vk.RenderBackendVk

/**
 * Desktop entry. GLFW needs `-XstartOnFirstThread` on macOS; the Gradle
 * `runJvm` task adds it.
 */
fun main() = KoolApplication(
    config = KoolConfigJvm(
        renderBackend = RenderBackendVk,
        defaultFont = NotoSansTcFont.info,
        windowTitle = AppVersion.APP_NAME_EN,
        windowSize = Vec2i(1600, 900),
        useOpenGlFallback = true,
    )
) {
    launchApp(ctx)
}

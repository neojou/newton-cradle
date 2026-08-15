package com.neojou.newtoncradle

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js

/**
 * Browser / Wasm entry — mounts shared [App] into the page body.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    preventBrowserTouchScroll()
    ComposeViewport(document.body!!) {
        App()
    }
}

/**
 * Stop the mobile browser from stealing vertical swipes as page scroll
 * so a finger can hold and lift a steel ball.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun preventBrowserTouchScroll() {
    js(
        """
        const opt = { passive: false };
        const stop = (e) => { e.preventDefault(); };
        document.addEventListener('touchmove', stop, opt);
        document.addEventListener('gesturestart', stop, opt);
        document.addEventListener('gesturechange', stop, opt);
        document.addEventListener('gestureend', stop, opt);
        """
    )
}

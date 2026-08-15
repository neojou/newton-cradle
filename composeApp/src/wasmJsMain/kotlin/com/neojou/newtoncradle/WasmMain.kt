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
    installMobileAudioUnlock()
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

/**
 * iOS/Safari only unlocks Web Audio inside a native touchstart.
 * Compose pointer handlers run too late, so this is wired at the document.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun installMobileAudioUnlock() {
    js(
        """
        const unlock = () => {
          const AC = window.AudioContext || window.webkitAudioContext;
          if (!AC) return;
          const ctx = window.__ncAudio || (window.__ncAudio = new AC());
          try { ctx.resume(); } catch (e) {}
          try {
            const silent = ctx.createBuffer(1, 1, 22050);
            const src = ctx.createBufferSource();
            src.buffer = silent;
            src.connect(ctx.destination);
            src.start(0);
          } catch (e) {}
          try {
            if (!window.__ncAudioEl) {
              const el = new Audio('clang.wav');
              el.preload = 'auto';
              el.volume = 0.001;
              window.__ncAudioEl = el;
              const p = el.play();
              if (p && p.then) p.then(() => { el.pause(); el.currentTime = 0; el.volume = 1; }).catch(() => {});
            }
          } catch (e) {}
          const raw = window.__ncClangRaw;
          if (raw && !window.__ncClang) {
            ctx.decodeAudioData(raw.slice(0)).then(a => { window.__ncClang = a; }).catch(() => {});
          }
        };
        document.addEventListener('touchstart', unlock, { capture: true, passive: true });
        document.addEventListener('pointerdown', unlock, { capture: true });
        document.addEventListener('mousedown', unlock, { capture: true });
        fetch('clang.wav').then(r => r.arrayBuffer()).then(buf => {
          window.__ncClangRaw = buf;
          if (window.__ncAudio) {
            window.__ncAudio.decodeAudioData(buf.slice(0)).then(a => { window.__ncClang = a; }).catch(() => {});
          }
        }).catch(() => {});
        """
    )
}

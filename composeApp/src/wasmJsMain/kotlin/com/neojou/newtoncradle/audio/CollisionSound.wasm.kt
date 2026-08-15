package com.neojou.newtoncradle.audio

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js

@OptIn(ExperimentalWasmJsInterop::class)
actual fun unlockAudio() {
    js(
        """
        const AC = window.AudioContext || window.webkitAudioContext;
        if (!AC) return;
        const ctx = window.__ncAudio || (window.__ncAudio = new AC());
        window.__ncAudio = ctx;
        const resume = () => { try { ctx.resume(); } catch (e) {} };
        resume();

        // iOS: a silent buffer started in this gesture keeps the context alive.
        try {
          const silent = ctx.createBuffer(1, 1, 22050);
          const src = ctx.createBufferSource();
          src.buffer = silent;
          src.connect(ctx.destination);
          src.start(0);
        } catch (e) {}

        // Also poke HTMLAudio during the gesture (Safari is picky).
        try {
          if (!window.__ncAudioEl) {
            const el = new Audio('clang.wav');
            el.preload = 'auto';
            el.volume = 0.001;
            window.__ncAudioEl = el;
            const p = el.play();
            if (p && p.then) {
              p.then(() => { el.pause(); el.currentTime = 0; el.volume = 1; }).catch(() => {});
            }
          }
        } catch (e) {}

        if (!window.__ncClangLoading) {
          window.__ncClangLoading = true;
          fetch('clang.wav').then(r => r.arrayBuffer()).then(buf => {
            return ctx.decodeAudioData(buf);
          }).then(audio => {
            window.__ncClang = audio;
          }).catch(() => { window.__ncClangLoading = false; });
        }
        """
    )
}

@OptIn(ExperimentalWasmJsInterop::class)
actual fun playCollision(strength: Float) {
    js(
        """
        const s = Math.max(0.15, Math.min(1, strength));
        const ctx = window.__ncAudio;
        const buf = window.__ncClang;
        if (ctx) {
          try { ctx.resume(); } catch (e) {}
        }
        if (ctx && buf) {
          const src = ctx.createBufferSource();
          src.buffer = buf;
          src.playbackRate.value = 0.97 + 0.08 * s;
          const g = ctx.createGain();
          g.gain.value = 0.38 + 0.62 * s;
          src.connect(g);
          g.connect(ctx.destination);
          src.start();
        } else if (window.__ncAudioEl) {
          try {
            const a = window.__ncAudioEl.cloneNode(true);
            a.volume = Math.max(0.2, Math.min(1, 0.4 + 0.6 * s));
            a.playbackRate = 0.97 + 0.08 * s;
            a.play();
          } catch (e) {}
        }
        """
    )
}

actual fun loadCollisionWav(bytes: ByteArray) {
    // Wasm plays clang.wav via fetch so the browser can cache/decode it.
}

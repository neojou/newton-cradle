package com.neojou.newtoncradle.audio

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js

@OptIn(ExperimentalWasmJsInterop::class)
actual fun playCollision(strength: Float) {
    js(
        """
        const s = Math.max(0.15, Math.min(1, strength));
        const AC = window.AudioContext || window.webkitAudioContext;
        if (!AC) return;
        const ctx = window.__ncAudio || (window.__ncAudio = new AC());
        if (ctx.state === 'suspended') ctx.resume();
        const t0 = ctx.currentTime;
        const dur = 0.15 + 0.07 * s;
        const master = ctx.createGain();
        master.gain.setValueAtTime(0.24 * s, t0);
        master.gain.exponentialRampToValueAtTime(0.0008, t0 + dur);
        master.connect(ctx.destination);
        const f0 = 1920 + 380 * s;
        const freqs = [f0, f0 * 1.49, f0 * 2.23, f0 * 3.41, f0 * 4.87];
        freqs.forEach((f, i) => {
          const o = ctx.createOscillator();
          o.type = i < 2 ? 'triangle' : 'sine';
          o.frequency.setValueAtTime(f, t0);
          o.frequency.exponentialRampToValueAtTime(f * 0.93, t0 + dur);
          const g = ctx.createGain();
          g.gain.setValueAtTime(0.42 / (i + 1), t0);
          g.gain.exponentialRampToValueAtTime(0.001, t0 + dur * (0.5 + 0.08 * i));
          o.connect(g);
          g.connect(master);
          o.start(t0);
          o.stop(t0 + dur);
        });
        const nLen = Math.floor(ctx.sampleRate * 0.016);
        const buf = ctx.createBuffer(1, nLen, ctx.sampleRate);
        const data = buf.getChannelData(0);
        for (let i = 0; i < nLen; i++) data[i] = (Math.random() * 2 - 1) * (1 - i / nLen);
        const src = ctx.createBufferSource();
        src.buffer = buf;
        const ng = ctx.createGain();
        ng.gain.setValueAtTime(0.16 * s, t0);
        ng.gain.exponentialRampToValueAtTime(0.001, t0 + 0.028);
        src.connect(ng);
        ng.connect(ctx.destination);
        src.start(t0);
        """
    )
}

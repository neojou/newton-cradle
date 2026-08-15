package com.neojou.newtoncradle.audio

/**
 * Must run inside a user gesture on mobile browsers (touch down) so the
 * AudioContext is allowed to start. Safe no-op after the first call.
 */
expect fun unlockAudio()

/**
 * Play the shared steel-clang sample. [strength] is 0..1 from impact speed.
 */
expect fun playCollision(strength: Float)

/** Desktop loads the WAV from Compose resources; Wasm fetches `clang.wav`. */
expect fun loadCollisionWav(bytes: ByteArray)

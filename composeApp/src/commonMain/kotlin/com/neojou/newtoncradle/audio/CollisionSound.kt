package com.neojou.newtoncradle.audio

import de.fabmax.kool.modules.audio.AudioClip

/**
 * Plays [clang.wav] from the analytic impact strength (`CradlePhysics.takeImpact`).
 */
class CollisionSound(private val clip: AudioClip) {
    init {
        clip.minIntervalMs = 48f
    }

    fun playImpact(strength: Float) {
        if (strength <= 0f) return
        clip.volume = 0.22f + 0.78f * strength.coerceIn(0f, 1f)
        clip.currentTime = 0f
        clip.play()
    }
}

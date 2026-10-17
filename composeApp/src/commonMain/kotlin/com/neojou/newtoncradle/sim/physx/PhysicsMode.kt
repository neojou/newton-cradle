package com.neojou.newtoncradle.sim.physx

/**
 * Analytic stays the default. Hybrid is the supported PhysX path.
 * Pure is an experiment and is allowed to look wrong.
 */
enum class PhysicsMode {
    ANALYTIC,
    HYBRID,
    PURE,
}

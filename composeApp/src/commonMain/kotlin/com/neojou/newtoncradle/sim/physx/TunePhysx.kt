package com.neojou.newtoncradle.sim.physx

import de.fabmax.kool.physics.PhysicsWorld
import de.fabmax.kool.physics.RigidDynamic

/**
 * Platform knobs that kool 0.19 does not expose on [PhysicsWorld].
 * Desktop uses physx-jni `PxScene` / `PxRigidDynamic`. JS uses the
 * physx-js-webidl properties of the same names. Verified against tag v0.19.0.
 */
internal expect fun tunePhysx(world: PhysicsWorld, bodies: List<RigidDynamic>)

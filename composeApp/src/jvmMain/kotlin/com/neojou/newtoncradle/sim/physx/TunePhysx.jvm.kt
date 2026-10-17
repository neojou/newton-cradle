package com.neojou.newtoncradle.sim.physx

import de.fabmax.kool.physics.PhysicsWorld
import de.fabmax.kool.physics.PhysicsWorldImpl
import de.fabmax.kool.physics.RigidDynamic
import de.fabmax.kool.physics.RigidDynamicImpl
import physx.physics.PxRigidBodyFlagEnum
import physx.physics.PxRigidDynamic

internal actual fun tunePhysx(world: PhysicsWorld, bodies: List<RigidDynamic>) {
    val scene = (world as PhysicsWorldImpl).pxScene
    scene.bounceThresholdVelocity = 1.0e-3f
    for (body in bodies) {
        val px = (body as RigidDynamicImpl).holder.px as PxRigidDynamic
        px.setSolverIterationCounts(32, 8)
        px.sleepThreshold = 0f
        px.setRigidBodyFlag(PxRigidBodyFlagEnum.eENABLE_SPECULATIVE_CCD, true)
    }
}

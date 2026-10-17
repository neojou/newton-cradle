@file:Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")

package com.neojou.newtoncradle.sim.physx

import de.fabmax.kool.physics.PhysicsWorld
import de.fabmax.kool.physics.PhysicsWorldImpl
import de.fabmax.kool.physics.RigidDynamic
import de.fabmax.kool.physics.RigidDynamicImpl
import physx.PxRigidBodyFlagEnum
import physx.PxRigidDynamic

internal actual fun tunePhysx(world: PhysicsWorld, bodies: List<RigidDynamic>) {
    val scene = (world as PhysicsWorldImpl).pxScene
    scene.setBounceThresholdVelocity(1.0e-3f)
    for (body in bodies) {
        val px = (body as RigidDynamicImpl).holder.px as PxRigidDynamic
        px.setSolverIterationCounts(32, 8)
        px.setSleepThreshold(0f)
        px.setRigidBodyFlag(PxRigidBodyFlagEnum.eENABLE_SPECULATIVE_CCD, true)
    }
}

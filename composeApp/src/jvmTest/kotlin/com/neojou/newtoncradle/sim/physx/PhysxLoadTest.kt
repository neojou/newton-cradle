package com.neojou.newtoncradle.sim.physx

import com.neojou.newtoncradle.sim.Cradle
import de.fabmax.kool.math.MutableMat4f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.physics.Material
import de.fabmax.kool.physics.Physics
import de.fabmax.kool.physics.PhysicsWorld
import de.fabmax.kool.physics.RigidDynamic
import de.fabmax.kool.physics.Shape
import de.fabmax.kool.physics.geometry.SphereGeometry
import de.fabmax.kool.physics.joints.RevoluteJoint
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Builds one revolute ball without a window, so a bad 0.19 binding fails here.
 */
class PhysxLoadTest {
    @Test
    fun oneRevoluteBallTunes() = runBlocking {
        Physics.loadPhysics()
        assertTrue(Physics.isLoaded)
        val world = PhysicsWorld(scene = null, isContinuousCollisionDetection = false)
        world.gravity = Vec3f(0f, -Cradle.GRAVITY, 0f)
        val restY = Cradle.PIVOT_Y - Cradle.SWING_L
        val body = RigidDynamic(Cradle.MASS, MutableMat4f().translate(0f, restY, 0f))
        body.attachShape(Shape(SphereGeometry(Cradle.RADIUS), Material(0f, 0f, 1f)))
        val joint = RevoluteJoint(
            bodyA = null,
            bodyB = body,
            pivotA = Vec3f(0f, Cradle.PIVOT_Y, 0f),
            pivotB = Vec3f(0f, Cradle.SWING_L, 0f),
            axisA = Vec3f.Z_AXIS,
            axisB = Vec3f.Z_AXIS,
        )
        world.addActor(body)
        tunePhysx(world, listOf(body))
        assertTrue(body.shapes.size == 1)
        joint.release()
        world.release()
    }
}

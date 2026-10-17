package com.neojou.newtoncradle.sim.physx

import com.neojou.newtoncradle.sim.Cradle
import de.fabmax.kool.math.MutableMat4f
import de.fabmax.kool.math.PoseF
import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import de.fabmax.kool.physics.FilterData
import de.fabmax.kool.physics.Material
import de.fabmax.kool.physics.PhysicsWorld
import de.fabmax.kool.physics.RigidDynamic
import de.fabmax.kool.physics.Shape
import de.fabmax.kool.physics.geometry.SphereGeometry
import de.fabmax.kool.physics.joints.RevoluteJoint
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.InterpolatableSimulation
import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One revolute joint per ball. The string is only drawn by the procedural cradle.
 *
 * Hybrid mode filters ball–ball collisions and swaps pendulum velocities in
 * [simulateStep], which kool runs on the physics thread before each 1/60 s step.
 * Pure mode leaves contacts to PhysX. The fixed step is not wrapped.
 *
 * Joint limits of ±90° match [Cradle.MAX_ANGLE]. Whether that limit's zero is
 * exactly the hanging pose is checked by construction of the frames; the
 * resulting swing is still 待驗證 on screen.
 */
class PhysxCradle(
    scene: Scene,
    val ballCount: Int,
    val hybrid: Boolean,
    gapUnits: Float,
    restitution: Float,
) {
    val world: PhysicsWorld = PhysicsWorld(scene, isContinuousCollisionDetection = false)
    private val material: Material = Material(0f, 0f, restitution)
    private val bodies: List<RigidDynamic>
    private val joints: List<RevoluteJoint>
    private val collisionMeshes: List<Node>
    private val pivotX = FloatArray(ballCount)
    private val theta = FloatArray(ballCount)
    private val omega = FloatArray(ballCount)
    private val omegaSnap = FloatArray(ballCount)

    @Volatile
    var holding: Boolean = false
        private set

    @Volatile
    var holdIndex: Int = 0
        private set

    @Volatile
    private var holdAngle: Float = 0f

    @Volatile
    private var resetRequested: Boolean = false

    @Volatile
    private var releaseRequested: Boolean = false

    private var energyLock = 0f
    private var lockEnergy = false
    private var locksApplied = false
    private var impactStrength = 0f
    private var impactCooldown = 0f

    private val stepListener = object : InterpolatableSimulation {
        override fun simulateStep(timeStep: Float) {
            this@PhysxCradle.simulateStep(timeStep)
        }
    }

    init {
        world.gravity = Vec3f(0f, -Cradle.GRAVITY, 0f)
        val spacing = 2f * Cradle.RADIUS + gapUnits
        val builtBodies = ArrayList<RigidDynamic>(ballCount)
        val builtJoints = ArrayList<RevoluteJoint>(ballCount)
        val filter = if (hybrid) {
            FilterData {
                setCollisionGroup(BALL_GROUP)
                setCollidesWithEverything()
                clearCollidesWith(BALL_GROUP)
            }
        } else {
            null
        }
        for (i in 0 until ballCount) {
            val x = (i - (ballCount - 1) * 0.5f) * spacing
            pivotX[i] = x
            val restY = Cradle.PIVOT_Y - Cradle.SWING_L
            val body = RigidDynamic(Cradle.MASS, MutableMat4f().translate(x, restY, 0f))
            body.linearDamping = 0f
            body.angularDamping = 0f
            body.maxLinearVelocity = 1000f
            body.maxAngularVelocity = 100f
            val shape = if (filter == null) {
                Shape(SphereGeometry(Cradle.RADIUS), material)
            } else {
                Shape(SphereGeometry(Cradle.RADIUS), material, simFilterData = filter)
            }
            body.attachShape(shape)
            val joint = RevoluteJoint(
                bodyA = null,
                bodyB = body,
                pivotA = Vec3f(x, Cradle.PIVOT_Y, 0f),
                pivotB = Vec3f(0f, Cradle.SWING_L, 0f),
                axisA = Vec3f.Z_AXIS,
                axisB = Vec3f.Z_AXIS,
            )
            joint.enableLimit((-90f).deg, 90f.deg)
            world.addActor(body)
            builtBodies += body
            builtJoints += joint
        }
        bodies = builtBodies
        joints = builtJoints
        tunePhysx(world, bodies)
        collisionMeshes = bodies.mapIndexed { index, body ->
            body.toMesh(Color(0.35f, 0.82f, 0.45f)).also { mesh ->
                mesh.name = "NC_Collision_$index"
                mesh.isVisible = false
                scene.addNode(mesh)
            }
        }
        world.physicsStepListeners.stageAdd(stepListener)
    }

    fun setRestitution(value: Float) {
        material.restitution = value
    }

    fun setCollisionVisible(visible: Boolean) {
        for (mesh in collisionMeshes) mesh.isVisible = visible
    }

    fun tryGrab(index: Int): Boolean {
        if (index != 0 && index != ballCount - 1) return false
        holdIndex = index
        holdAngle = theta[index]
        holding = true
        lockEnergy = false
        return true
    }

    fun setHoldAngle(angle: Float) {
        if (!holding) return
        holdAngle = if (holdIndex == 0) {
            angle.coerceIn(-Cradle.MAX_ANGLE, 0f)
        } else {
            angle.coerceIn(0f, Cradle.MAX_ANGLE)
        }
    }

    fun requestRelease() {
        if (!holding) return
        holding = false
        releaseRequested = true
    }

    fun requestReset() {
        holding = false
        resetRequested = true
        releaseRequested = false
        lockEnergy = false
    }

    fun takeImpact(): Float {
        val strength = impactStrength
        impactStrength = 0f
        return strength
    }

    fun kineticEnergy(): Float {
        var energy = 0f
        for (i in 0 until ballCount) {
            val speed = Cradle.speed(omegaSnap[i])
            energy += 0.5f * Cradle.MASS * speed * speed
        }
        return energy
    }

    fun copyVisual(x: FloatArray, y: FloatArray, pivots: FloatArray) {
        for (i in 0 until ballCount) {
            val position = bodies[i].transform.translation
            x[i] = position.x
            y[i] = position.y
            pivots[i] = pivotX[i]
        }
    }

    fun syncStepper(paused: Boolean, timeScale: Float) {
        world.simStepper.isPaused = paused
        world.simStepper.desiredTimeFactor = timeScale
    }

    fun release() {
        world.physicsStepListeners.stageRemove(stepListener)
        for (mesh in collisionMeshes) {
            mesh.parent?.removeNode(mesh)
            mesh.release()
        }
        for (joint in joints) joint.release()
        world.release()
    }

    private fun simulateStep(dt: Float) {
        if (impactCooldown > 0f) impactCooldown -= dt
        if (!locksApplied) {
            for (body in bodies) {
                body.setAngularLockFlags(lockAngularX = true, lockAngularY = true, lockAngularZ = false)
                body.setLinearLockFlags(lockLinearX = false, lockLinearY = false, lockLinearZ = true)
            }
            locksApplied = true
        }
        if (resetRequested) {
            resetRequested = false
            applyRestPose()
            return
        }
        readState()
        if (holding) {
            applyHold()
            return
        }
        if (releaseRequested) {
            releaseRequested = false
            omega[holdIndex] = 0f
            omegaSnap[holdIndex] = 0f
            writeBody(holdIndex)
            energyLock = totalEnergy()
            lockEnergy = true
        }
        if (!hybrid) return
        val swapped = HybridTransfer.apply(theta, omega, ballCount)
        if (swapped > 0f) noteImpact(swapped)
        var changed = swapped > 0f
        if (lockEnergy) changed = restoreEnergy() || changed
        if (changed) {
            for (i in 0 until ballCount) writeBody(i)
        }
        omega.copyInto(omegaSnap)
    }

    private fun applyRestPose() {
        for (i in 0 until ballCount) {
            theta[i] = 0f
            omega[i] = 0f
            omegaSnap[i] = 0f
            bodies[i].pose = PoseF(Vec3f(pivotX[i], Cradle.PIVOT_Y - Cradle.SWING_L, 0f), QuatF.IDENTITY)
            bodies[i].linearVelocity = Vec3f.ZERO
            bodies[i].angularVelocity = Vec3f.ZERO
            bodies[i].wakeUp()
        }
        lockEnergy = false
        impactStrength = 0f
    }

    private fun applyHold() {
        val angle = holdAngle
        val index = holdIndex
        theta[index] = angle
        omega[index] = 0f
        omegaSnap[index] = 0f
        val x = pivotX[index] + Cradle.SWING_L * sin(angle)
        val y = Cradle.PIVOT_Y - Cradle.SWING_L * cos(angle)
        val degrees = angle * (180f / PI.toFloat())
        bodies[index].pose = PoseF(Vec3f(x, y, 0f), QuatF.rotation(degrees.deg, Vec3f.Z_AXIS))
        bodies[index].linearVelocity = Vec3f.ZERO
        bodies[index].angularVelocity = Vec3f.ZERO
        bodies[index].wakeUp()
    }

    private fun readState() {
        for (i in 0 until ballCount) {
            val position = bodies[i].pose.position
            theta[i] = atan2(position.x - pivotX[i], Cradle.PIVOT_Y - position.y)
            omega[i] = bodies[i].angularVelocity.z
            omegaSnap[i] = omega[i]
        }
    }

    private fun writeBody(index: Int) {
        val angle = theta[index]
        val spin = omega[index]
        val speed = Cradle.SWING_L * spin
        val body = bodies[index]
        body.linearVelocity = Vec3f(speed * cos(angle), speed * sin(angle), 0f)
        body.angularVelocity = Vec3f(0f, 0f, spin)
        body.wakeUp()
    }

    private fun totalEnergy(): Float {
        var energy = 0f
        for (i in 0 until ballCount) {
            val speed = Cradle.speed(omega[i])
            energy += 0.5f * Cradle.MASS * speed * speed
            energy += Cradle.MASS * Cradle.GRAVITY * Cradle.ballY(i, theta[i])
        }
        return energy
    }

    private fun restoreEnergy(): Boolean {
        var kinetic = 0f
        var potential = 0f
        for (i in 0 until ballCount) {
            val speed = Cradle.speed(omega[i])
            kinetic += 0.5f * Cradle.MASS * speed * speed
            potential += Cradle.MASS * Cradle.GRAVITY * Cradle.ballY(i, theta[i])
        }
        val targetKinetic = energyLock - potential
        if (kinetic <= 1e-6f || targetKinetic <= 0f) return false
        val scale = sqrt(targetKinetic / kinetic)
        if (abs(scale - 1f) < 1e-3f) return false
        for (i in 0 until ballCount) omega[i] *= scale
        return true
    }

    private fun noteImpact(omegaIncoming: Float) {
        if (impactCooldown > 0f) return
        val strength = (abs(omegaIncoming) / 9.2f).coerceIn(0.18f, 1f)
        if (strength > impactStrength) impactStrength = strength
        impactCooldown = 0.048f
    }

    private companion object {
        const val BALL_GROUP = 2
    }
}

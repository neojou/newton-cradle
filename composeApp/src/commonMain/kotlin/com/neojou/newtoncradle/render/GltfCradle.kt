package com.neojou.newtoncradle.render

import com.neojou.newtoncradle.sim.Cradle
import com.neojou.newtoncradle.sim.Vec3
import de.fabmax.kool.math.MutableQuatF
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import de.fabmax.kool.scene.Model
import de.fabmax.kool.scene.Node
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Binds a glTF cradle whose nodes use the `NC_*` names. Balls and wires are
 * authored at the origin; this writes the same pose as [ProceduralCradle].
 * Returns null when a required node is missing so the caller can keep the
 * procedural model.
 */
class GltfCradle private constructor(
    val model: Model,
    private val balls: List<Node>,
    private val wires: List<Node>,
) : CradleVisual {
    override val ballCount: Int = balls.size
    override val root: Node get() = model
    private val aim = MutableQuatF()

    override fun setDynamicsVisible(visible: Boolean) {
        for (ball in balls) ball.isVisible = visible
        for (wire in wires) wire.isVisible = visible
    }

    override fun sync(theta: FloatArray) {
        for (i in 0 until ballCount) {
            val x = Cradle.ballX(i, theta[i], ballCount)
            val y = Cradle.ballY(i, theta[i])
            balls[i].transform.setIdentity().translate(x.toDouble(), y.toDouble(), 0.0)
            placeWire(wires[i * 2], Vec3(Cradle.pivotX(i, ballCount), Cradle.PIVOT_Y, -Cradle.BAR_Z), attach(i, theta[i], -1f))
            placeWire(wires[i * 2 + 1], Vec3(Cradle.pivotX(i, ballCount), Cradle.PIVOT_Y, Cradle.BAR_Z), attach(i, theta[i], 1f))
        }
    }

    override fun syncCenters(x: FloatArray, y: FloatArray, pivotX: FloatArray) {
        for (i in 0 until ballCount) {
            balls[i].transform.setIdentity().translate(x[i].toDouble(), y[i].toDouble(), 0.0)
            val center = Vec3(x[i], y[i], 0f)
            placeWire(wires[i * 2], Vec3(pivotX[i], Cradle.PIVOT_Y, -Cradle.BAR_Z), attachTo(center, pivotX[i], -1f))
            placeWire(wires[i * 2 + 1], Vec3(pivotX[i], Cradle.PIVOT_Y, Cradle.BAR_Z), attachTo(center, pivotX[i], 1f))
        }
    }

    private fun placeWire(wire: Node, pivot: Vec3, attach: Vec3) {
        val delta = attach - pivot
        val length = delta.length().coerceAtLeast(1e-4f)
        aimFromY(delta.x / length, delta.y / length, delta.z / length)
        val mid = pivot + delta * 0.5f
        wire.transform.setCompositionOf(
            Vec3f(mid.x, mid.y, mid.z),
            aim,
            Vec3f(1f, length, 1f),
        )
    }

    private fun aimFromY(dx: Float, dy: Float, dz: Float) {
        val ax = dz
        val az = -dx
        val axisLen = sqrt(ax * ax + az * az)
        if (axisLen < 1e-5f) {
            if (dy < 0f) aim.set(180f.deg, Vec3f.X_AXIS) else aim.setIdentity()
        } else {
            val degrees = atan2(axisLen, dy) * (180f / PI.toFloat())
            aim.set(degrees.deg, Vec3f(ax / axisLen, 0f, az / axisLen))
        }
    }

    private fun attach(index: Int, theta: Float, side: Float): Vec3 {
        val ball = Vec3(Cradle.ballX(index, theta, ballCount), Cradle.ballY(index, theta), 0f)
        val pivot = Vec3(Cradle.pivotX(index, ballCount), Cradle.PIVOT_Y, Cradle.BAR_Z * side)
        return ball + (pivot - ball).normalized() * Cradle.RADIUS
    }

    private fun attachTo(center: Vec3, pivotX: Float, side: Float): Vec3 {
        val pivot = Vec3(pivotX, Cradle.PIVOT_Y, Cradle.BAR_Z * side)
        return center + (pivot - center).normalized() * Cradle.RADIUS
    }

    companion object {
        fun tryCreate(model: Model, balls: Int = Cradle.BALLS): GltfCradle? {
            val ballNodes = ArrayList<Node>(balls)
            val wireNodes = ArrayList<Node>(balls * 2)
            for (i in 0 until balls) {
                ballNodes += model.nodes["NC_Ball_$i"] ?: return null
                wireNodes += model.nodes["NC_Wire_${i}_L"] ?: return null
                wireNodes += model.nodes["NC_Wire_${i}_R"] ?: return null
            }
            model.nodes["NC_Base"] ?: return null
            model.nodes["NC_Frame"] ?: return null
            model.nodes["NC_Rods"] ?: return null
            return GltfCradle(model, ballNodes, wireNodes)
        }
    }
}

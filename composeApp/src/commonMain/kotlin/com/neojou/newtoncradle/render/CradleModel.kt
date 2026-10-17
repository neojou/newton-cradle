package com.neojou.newtoncradle.render

import com.neojou.newtoncradle.sim.Cradle
import com.neojou.newtoncradle.sim.Vec3
import com.neojou.newtoncradle.sim.ballCenter
import com.neojou.newtoncradle.sim.stringPivot
import de.fabmax.kool.math.MutableQuatF
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import de.fabmax.kool.pipeline.DrawShader
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.addTextureMesh
import de.fabmax.kool.util.Color
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Procedural stand-in for the glTF cradle. Node names match the asset spec
 * so a later glTF bind can find the same balls, frame, base and wires.
 */
class ProceduralCradle(override val ballCount: Int, rig: StudioRig) : Node("NC_Cradle"), CradleVisual {
    override val root: Node get() = this
    private val balls = ArrayList<Node>(ballCount)
    private val wires = ArrayList<Node>(ballCount * 2)
    private val aim = MutableQuatF()

    init {
        val steel = rig.pbr(Color(0.74f, 0.76f, 0.78f), metallic = 1f, roughness = 0.08f)
        val rod = rig.pbr(Color(0.62f, 0.64f, 0.66f), metallic = 1f, roughness = 0.22f)
        val post = rig.pbr(Color(0.22f, 0.23f, 0.25f), metallic = 0.85f, roughness = 0.38f)
        val wood = rig.pbr(Color.fromHex("8C5A3C"), metallic = 0f, roughness = 0.62f)
        val floor = rig.pbr(Color.fromHex("24262B"), metallic = 0.05f, roughness = 0.88f)

        addTextureMesh(name = "NC_Floor") {
            generate {
                cube {
                    size.set(80f, 0.5f, 80f)
                    origin.set(0f, Cradle.FLOOR_Y - 0.25f, 0f)
                }
            }
            shader = floor
            isCastingShadow = false
        }
        addTextureMesh(name = "NC_Base") {
            generate {
                val height = Cradle.BASE_TOP - Cradle.BASE_BOTTOM
                cube {
                    size.set(Cradle.BASE_HALF_X * 2f, height, Cradle.BASE_HALF_Z * 2f)
                    origin.set(0f, (Cradle.BASE_TOP + Cradle.BASE_BOTTOM) * 0.5f, 0f)
                }
            }
            shader = wood
        }
        addTextureMesh(name = "NC_Frame") {
            generate {
                val top = Cradle.PIVOT_Y + 0.18f
                val height = top - Cradle.BASE_TOP
                val centerY = (top + Cradle.BASE_TOP) * 0.5f
                val half = Cradle.POST_HALF
                for (x in floatArrayOf(-Cradle.POST_X, Cradle.POST_X)) {
                    for (z in floatArrayOf(-Cradle.BAR_Z, Cradle.BAR_Z)) {
                        cube {
                            size.set(half * 2f, height, half * 2f)
                            origin.set(x, centerY, z)
                        }
                    }
                }
            }
            shader = post
        }
        addTextureMesh(name = "NC_Rods") {
            generate {
                val length = 2f * (Cradle.POST_X + 0.12f)
                for (z in floatArrayOf(-Cradle.BAR_Z, Cradle.BAR_Z)) {
                    withTransform {
                        translate(0f, Cradle.PIVOT_Y, z)
                        rotate(90f.deg, Vec3f.Z_AXIS)
                        cylinder {
                            radius = Cradle.ROD_RADIUS
                            height = length
                            steps = 16
                        }
                    }
                }
            }
            shader = rod
        }

        for (i in 0 until ballCount) {
            balls += addTextureMesh(name = "NC_Ball_$i") {
                generate {
                    uvSphere {
                        radius = Cradle.RADIUS
                        steps = 32
                    }
                }
                shader = steel
            }
            wires += unitWire("NC_Wire_${i}_L", rod)
            wires += unitWire("NC_Wire_${i}_R", rod)
        }
        sync(FloatArray(ballCount))
    }

    override fun setDynamicsVisible(visible: Boolean) {
        for (ball in balls) ball.isVisible = visible
        for (wire in wires) wire.isVisible = visible
    }

    override fun sync(theta: FloatArray) {
        for (i in 0 until ballCount) {
            val x = Cradle.ballX(i, theta[i], ballCount)
            val y = Cradle.ballY(i, theta[i])
            balls[i].transform.setIdentity().translate(x.toDouble(), y.toDouble(), 0.0)
            placeWire(wires[i * 2], stringPivot(i, -1f, ballCount), attach(i, theta[i], -1f))
            placeWire(wires[i * 2 + 1], stringPivot(i, 1f, ballCount), attach(i, theta[i], 1f))
        }
    }

    /** Ball centers and hinge pivots from a PhysX pose, which may include a gap. */
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
        val ball = ballCenter(index, theta, ballCount)
        val pivot = stringPivot(index, side, ballCount)
        return ball + (pivot - ball).normalized() * Cradle.RADIUS
    }

    private fun attachTo(center: Vec3, pivotX: Float, side: Float): Vec3 {
        val pivot = Vec3(pivotX, Cradle.PIVOT_Y, Cradle.BAR_Z * side)
        return center + (pivot - center).normalized() * Cradle.RADIUS
    }

    private fun unitWire(name: String, shader: DrawShader): Node {
        return addTextureMesh(name = name) {
            generate {
                cylinder {
                    radius = 0.028f
                    height = 1f
                    steps = 8
                    topFill = false
                    bottomFill = false
                }
            }
            this.shader = shader
        }
    }
}

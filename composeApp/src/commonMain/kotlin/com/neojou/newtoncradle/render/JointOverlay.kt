package com.neojou.newtoncradle.render

import com.neojou.newtoncradle.sim.Cradle
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.addLineMesh
import de.fabmax.kool.util.Color

/**
 * Hinge axis and the line from the pivot down to each ball.
 * Positions come from the active simulation, analytic or PhysX.
 */
class JointOverlay : Node("NC_Joints") {
    private val lines = addLineMesh("NC_JointLines") { }

    fun sync(count: Int, x: FloatArray, y: FloatArray, pivot: FloatArray) {
        lines.clear()
        if (!isVisible) return
        for (i in 0 until count) {
            val px = pivot[i]
            val py = Cradle.PIVOT_Y
            lines.addLine(Vec3f(px, py, -0.55f), Vec3f(px, py, 0.55f), AXIS)
            lines.addLine(Vec3f(px, py, 0f), Vec3f(x[i], y[i], 0f), LINK)
        }
    }

    private companion object {
        val AXIS = Color(0.95f, 0.78f, 0.25f)
        val LINK = Color(0.45f, 0.9f, 0.55f)
    }
}

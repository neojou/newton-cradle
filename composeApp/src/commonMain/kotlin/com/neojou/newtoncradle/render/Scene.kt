package com.neojou.newtoncradle.render

import com.neojou.newtoncradle.sim.Cradle
import com.neojou.newtoncradle.sim.CradleSnapshot

internal const val MAT_CHROME = 0
internal const val MAT_STEEL = 1
internal const val MAT_WOOD = 2
internal const val MAT_CERAMIC = 3
internal const val MAT_FLOOR = 4
internal const val MAT_GUNMETAL = 5
internal const val MAT_MIRROR = 6

internal const val MAX_SPHERES = 24
internal const val MAX_CYLINDERS = 8
internal const val MAX_BOXES = 10

/**
 * Packed analytic primitives. Rebuilt each frame from the physics snapshot;
 * arrays are reused so the render loop does not allocate.
 */
internal class Scene {
    val sX = FloatArray(MAX_SPHERES)
    val sY = FloatArray(MAX_SPHERES)
    val sZ = FloatArray(MAX_SPHERES)
    val sR = FloatArray(MAX_SPHERES)
    val sMat = IntArray(MAX_SPHERES)
    var sphereCount = 0

    val cAx = FloatArray(MAX_CYLINDERS)
    val cAy = FloatArray(MAX_CYLINDERS)
    val cAz = FloatArray(MAX_CYLINDERS)
    val cBx = FloatArray(MAX_CYLINDERS)
    val cBy = FloatArray(MAX_CYLINDERS)
    val cBz = FloatArray(MAX_CYLINDERS)
    val cR = FloatArray(MAX_CYLINDERS)
    val cMat = IntArray(MAX_CYLINDERS)
    var cylinderCount = 0

    val bMinX = FloatArray(MAX_BOXES)
    val bMinY = FloatArray(MAX_BOXES)
    val bMinZ = FloatArray(MAX_BOXES)
    val bMaxX = FloatArray(MAX_BOXES)
    val bMaxY = FloatArray(MAX_BOXES)
    val bMaxZ = FloatArray(MAX_BOXES)
    val bMat = IntArray(MAX_BOXES)
    var boxCount = 0

    var floorY = Cradle.FLOOR_Y

    fun clearDynamic() {
        sphereCount = 0
        cylinderCount = 0
        boxCount = 0
    }

    fun addSphere(x: Float, y: Float, z: Float, r: Float, mat: Int) {
        val i = sphereCount
        if (i >= MAX_SPHERES) return
        sX[i] = x
        sY[i] = y
        sZ[i] = z
        sR[i] = r
        sMat[i] = mat
        sphereCount = i + 1
    }

    fun addCylinder(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        r: Float, mat: Int,
    ) {
        val i = cylinderCount
        if (i >= MAX_CYLINDERS) return
        cAx[i] = ax
        cAy[i] = ay
        cAz[i] = az
        cBx[i] = bx
        cBy[i] = by
        cBz[i] = bz
        cR[i] = r
        cMat[i] = mat
        cylinderCount = i + 1
    }

    fun addBox(
        minX: Float, minY: Float, minZ: Float,
        maxX: Float, maxY: Float, maxZ: Float,
        mat: Int,
    ) {
        val i = boxCount
        if (i >= MAX_BOXES) return
        bMinX[i] = minX
        bMinY[i] = minY
        bMinZ[i] = minZ
        bMaxX[i] = maxX
        bMaxY[i] = maxY
        bMaxZ[i] = maxZ
        bMat[i] = mat
        boxCount = i + 1
    }

    fun rebuild(snap: CradleSnapshot) {
        clearDynamic()

        for (i in 0 until Cradle.BALLS) {
            addSphere(
                Cradle.ballX(i, snap.theta[i]),
                Cradle.ballY(i, snap.theta[i]),
                0f,
                Cradle.RADIUS,
                MAT_CHROME,
            )
        }

        addStaticFrame()
    }

    private fun addStaticFrame() {
        val top = Cradle.BASE_TOP
        val bot = Cradle.BASE_BOTTOM
        addBox(
            -Cradle.BASE_HALF_X, bot, -Cradle.BASE_HALF_Z,
            Cradle.BASE_HALF_X, top, Cradle.BASE_HALF_Z,
            MAT_WOOD,
        )
        // Full-size mirror slab — reflects the undersides of the balls.
        addBox(
            -Cradle.BASE_HALF_X + 0.03f, top - 0.015f, -Cradle.BASE_HALF_Z + 0.03f,
            Cradle.BASE_HALF_X - 0.03f, top + 0.075f, Cradle.BASE_HALF_Z - 0.03f,
            MAT_MIRROR,
        )

        val ph = Cradle.POST_HALF
        val px = Cradle.POST_X
        val bz = Cradle.BAR_Z
        val py = Cradle.PIVOT_Y + 0.18f
        val posts = floatArrayOf(-px, px)
        val zs = floatArrayOf(-bz, bz)
        for (x in posts) {
            for (z in zs) {
                addBox(x - ph, top, z - ph, x + ph, py, z + ph, MAT_GUNMETAL)
            }
        }

        val rodY = Cradle.PIVOT_Y
        val rodR = Cradle.ROD_RADIUS
        addCylinder(-px - 0.12f, rodY, -bz, px + 0.12f, rodY, -bz, rodR, MAT_STEEL)
        addCylinder(-px - 0.12f, rodY, bz, px + 0.12f, rodY, bz, rodR, MAT_STEEL)
    }
}

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

internal const val MAX_SPHERES = 28
internal const val MAX_CYLINDERS = 12
internal const val MAX_BOXES = 24
internal const val MAX_PLANES = 4

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

    val pNx = FloatArray(MAX_PLANES)
    val pNy = FloatArray(MAX_PLANES)
    val pNz = FloatArray(MAX_PLANES)
    val pD = FloatArray(MAX_PLANES)
    val pMinX = FloatArray(MAX_PLANES)
    val pMaxX = FloatArray(MAX_PLANES)
    val pMinY = FloatArray(MAX_PLANES)
    val pMaxY = FloatArray(MAX_PLANES)
    val pMinZ = FloatArray(MAX_PLANES)
    val pMaxZ = FloatArray(MAX_PLANES)
    val pMat = IntArray(MAX_PLANES)
    var planeCount = 0

    var floorY = Cradle.FLOOR_Y

    fun clearDynamic() {
        sphereCount = 0
        cylinderCount = 0
        boxCount = 0
        planeCount = 0
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

    fun addPlane(
        nx: Float, ny: Float, nz: Float, d: Float,
        minX: Float, maxX: Float,
        minY: Float, maxY: Float,
        minZ: Float, maxZ: Float,
        mat: Int,
    ) {
        val i = planeCount
        if (i >= MAX_PLANES) return
        pNx[i] = nx
        pNy[i] = ny
        pNz[i] = nz
        pD[i] = d
        pMinX[i] = minX
        pMaxX[i] = maxX
        pMinY[i] = minY
        pMaxY[i] = maxY
        pMinZ[i] = minZ
        pMaxZ[i] = maxZ
        pMat[i] = mat
        planeCount = i + 1
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
        addVanity()
        addLamp()
    }

    private fun addStaticFrame() {
        val top = Cradle.BASE_TOP
        val bot = Cradle.BASE_BOTTOM
        addBox(
            -Cradle.BASE_HALF_X, bot, -Cradle.BASE_HALF_Z,
            Cradle.BASE_HALF_X, top, Cradle.BASE_HALF_Z,
            MAT_WOOD,
        )
        addMirrors()

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

    private fun addVanity() {
        val ty = Cradle.VANITY_Y
        val top = Cradle.BASE_BOTTOM
        addBox(-20f, ty, -9.2f, 26.5f, top, 9.6f, MAT_VANITY)
        val z = Cradle.BACK_WALL_Z
        // Mirror frame
        addBox(-17.5f, ty, z - 0.22f, 17.5f, 16.4f, z - 0.04f, MAT_FRAME)
        addBox(-17.5f, 15.6f, z - 0.04f, 17.5f, 16.4f, z + 0.18f, MAT_BRASS)
        addBox(-17.5f, ty, z - 0.04f, 17.5f, ty + 0.75f, z + 0.18f, MAT_BRASS)
        addBox(-17.5f, ty, z - 0.04f, -16.6f, 16.4f, z + 0.18f, MAT_BRASS)
        addBox(16.6f, ty, z - 0.04f, 17.5f, 16.4f, z + 0.18f, MAT_BRASS)
    }

    private fun addLamp() {
        val x = Cradle.LAMP_X
        val z = Cradle.LAMP_Z
        val table = Cradle.BASE_BOTTOM
        addBox(x - 0.85f, table, z - 0.85f, x + 0.85f, table + 0.18f, z + 0.85f, MAT_BRASS)
        addCylinder(x, table + 0.18f, z, x, Cradle.LAMP_LIGHT_Y - 0.15f, z, 0.13f, MAT_BRASS)
        addCylinder(x, Cradle.LAMP_LIGHT_Y - 0.05f, z, x, Cradle.LAMP_LIGHT_Y + 1.55f, z, 1.15f, MAT_SHADE)
        addSphere(x, Cradle.LAMP_LIGHT_Y, z, 0.38f, MAT_BULB)
    }

    /**
     * Planar mirrors used by the single-path specular walker.
     * Perfect planes do not branch: the ray is folded and continues.
     */
    private fun addMirrors() {
        val y = Cradle.MIRROR_Y
        addPlane(
            0f, 1f, 0f, y,
            -Cradle.BASE_HALF_X + 0.04f, Cradle.BASE_HALF_X - 0.04f,
            y - 0.02f, y + 0.02f,
            -Cradle.BASE_HALF_Z + 0.04f, Cradle.BASE_HALF_Z - 0.04f,
            MAT_MIRROR,
        )
        val z = Cradle.BACK_WALL_Z
        addPlane(
            0f, 0f, 1f, z,
            -16.6f, 16.6f,
            Cradle.VANITY_Y + 0.8f, 15.6f,
            z - 0.02f, z + 0.02f,
            MAT_MIRROR,
        )
        // Vanity mirror only — the table itself is wood, not a second floor mirror.
    }
}

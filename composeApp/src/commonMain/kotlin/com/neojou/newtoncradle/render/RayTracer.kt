package com.neojou.newtoncradle.render

import androidx.compose.ui.graphics.ImageBitmap
import com.neojou.newtoncradle.sim.CradleSnapshot
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Analytic software ray tracer for the cradle scene.
 *
 * Hardware RT is not available through Compose Multiplatform on Desktop/Wasm.
 * This scene is almost entirely spheres (closed-form hits) plus a few boxes
 * and cylinders, so a CPU tracer can look like product-shot chrome while
 * staying interactive. Resolution and bounce count follow [quality].
 */
class RayTracer {
    val camera = Camera().also { it.updateBasis() }

    private val scene = Scene()
    private val buffer = FrameBuffer()
    private val envR = FloatArray(1)
    private val envG = FloatArray(1)
    private val envB = FloatArray(1)
    private val dirScratch = FloatArray(3)

    fun render(
        snap: CradleSnapshot,
        viewW: Int,
        viewH: Int,
        quality: Float,
    ): ImageBitmap? {
        if (viewW < 8 || viewH < 8) return null
        scene.rebuild(snap)

        val q = quality.coerceIn(0.12f, 1f)
        val maxDim = (420f + 500f * q).toInt()
        val longest = maxOf(viewW, viewH).toFloat()
        val scale = (maxDim / longest).coerceAtMost(1f)
        val w = (viewW * scale).toInt().coerceAtLeast(240)
        val h = (viewH * scale).toInt().coerceAtLeast(140)
        buffer.ensure(w, h)

        val maxBounce = if (q > 0.38f) 2 else 1
        val aa = q > 0.68f && snap.isStill
        val wf = w.toFloat()
        val hf = h.toFloat()
        val cam = camera
        val dir = dirScratch

        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0f
                var g = 0f
                var b = 0f
                if (aa) {
                    var s = 0
                    var oy = 0
                    while (oy < 2) {
                        var ox = 0
                        while (ox < 2) {
                            cam.writeRayDir(x + 0.25f + ox * 0.5f, y + 0.25f + oy * 0.5f, wf, hf, dir)
                            trace(
                                cam.eye.x, cam.eye.y, cam.eye.z,
                                dir[0], dir[1], dir[2],
                                0, maxBounce,
                            )
                            r += envR[0]
                            g += envG[0]
                            b += envB[0]
                            s++
                            ox++
                        }
                        oy++
                    }
                    val inv = 1f / s
                    r *= inv
                    g *= inv
                    b *= inv
                } else {
                    cam.writeRayDir(x + 0.5f, y + 0.5f, wf, hf, dir)
                    trace(
                        cam.eye.x, cam.eye.y, cam.eye.z,
                        dir[0], dir[1], dir[2],
                        0, maxBounce,
                    )
                    r = envR[0]
                    g = envG[0]
                    b = envB[0]
                }
                val nx = (x + 0.5f) / wf * 2f - 1f
                val ny = (y + 0.5f) / hf * 2f - 1f
                val vig = 1f - 0.14f * (nx * nx + ny * ny)
                buffer.putLinear(x, y, r * vig, g * vig, b * vig)
            }
        }
        return imageBitmapFromRgba(w, h, buffer.rgba)
    }

    /**
     * Writes linear RGB into [envR]/[envG]/[envB] slot 0.
     */
    private fun trace(
        ox: Float, oy: Float, oz: Float,
        dx: Float, dy: Float, dz: Float,
        bounce: Int,
        maxBounce: Int,
    ) {
        var bestT = 1e30f
        var hx = 0f
        var hy = 0f
        var hz = 0f
        var nx = 0f
        var ny = 0f
        var nz = 0f
        var mat = -1

        // Spheres
        val sc = scene.sphereCount
        var i = 0
        while (i < sc) {
            val t = hitSphere(ox, oy, oz, dx, dy, dz, scene.sX[i], scene.sY[i], scene.sZ[i], scene.sR[i], bestT)
            if (t > 0f) {
                bestT = t
                hx = ox + dx * t
                hy = oy + dy * t
                hz = oz + dz * t
                val invR = 1f / scene.sR[i]
                nx = (hx - scene.sX[i]) * invR
                ny = (hy - scene.sY[i]) * invR
                nz = (hz - scene.sZ[i]) * invR
                mat = scene.sMat[i]
            }
            i++
        }

        // Secondary rays only need the balls — enough for the mirror to
        // show their undersides without tracing the whole frame again.
        val hitStatic = bounce == 0

        // Cylinders
        val cc = if (hitStatic) scene.cylinderCount else 0
        i = 0
        while (i < cc) {
            val t = hitCylinder(
                ox, oy, oz, dx, dy, dz,
                scene.cAx[i], scene.cAy[i], scene.cAz[i],
                scene.cBx[i], scene.cBy[i], scene.cBz[i],
                scene.cR[i], bestT,
            )
            if (t > 0f) {
                bestT = t
                hx = ox + dx * t
                hy = oy + dy * t
                hz = oz + dz * t
                val nrm = cylNormal(
                    hx, hy, hz,
                    scene.cAx[i], scene.cAy[i], scene.cAz[i],
                    scene.cBx[i], scene.cBy[i], scene.cBz[i],
                )
                nx = nrm[0]
                ny = nrm[1]
                nz = nrm[2]
                mat = scene.cMat[i]
            }
            i++
        }

        // Boxes
        val bc = if (hitStatic) scene.boxCount else 0
        i = 0
        while (i < bc) {
            val t = hitAabb(
                ox, oy, oz, dx, dy, dz,
                scene.bMinX[i], scene.bMinY[i], scene.bMinZ[i],
                scene.bMaxX[i], scene.bMaxY[i], scene.bMaxZ[i],
                bestT,
            )
            if (t > 0f) {
                bestT = t
                hx = ox + dx * t
                hy = oy + dy * t
                hz = oz + dz * t
                val nrm = aabbNormal(
                    hx, hy, hz,
                    scene.bMinX[i], scene.bMinY[i], scene.bMinZ[i],
                    scene.bMaxX[i], scene.bMaxY[i], scene.bMaxZ[i],
                )
                nx = nrm[0]
                ny = nrm[1]
                nz = nrm[2]
                mat = scene.bMat[i]
            }
            i++
        }

        // Floor plane
        if (hitStatic && (dy < -1e-6f || dy > 1e-6f)) {
            val t = (scene.floorY - oy) / dy
            if (t > 1e-3f && t < bestT) {
                val fx = ox + dx * t
                val fz = oz + dz * t
                if (fx * fx + fz * fz < 420f) {
                    bestT = t
                    hx = fx
                    hy = scene.floorY
                    hz = fz
                    nx = 0f
                    ny = 1f
                    nz = 0f
                    mat = MAT_FLOOR
                }
            }
        }

        if (mat < 0) {
            StudioEnv.sample(dx, dy, dz, envR, envG, envB, 0)
            return
        }

        val nl = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
        nx /= nl
        ny /= nl
        nz /= nl
        // Faceforward
        if (nx * dx + ny * dy + nz * dz > 0f) {
            nx = -nx
            ny = -ny
            nz = -nz
        }

        shade(hx, hy, hz, nx, ny, nz, dx, dy, dz, mat, bounce, maxBounce)
    }

    private fun shade(
        px: Float, py: Float, pz: Float,
        nx: Float, ny: Float, nz: Float,
        idx: Float, idy: Float, idz: Float,
        mat: Int,
        bounce: Int,
        maxBounce: Int,
    ) {
        val metal: Float
        val rough: Float
        var ar: Float
        var ag: Float
        var ab: Float
        val f0: Float
        when (mat) {
            MAT_CHROME -> {
                metal = 1f
                rough = 0.045f
                ar = 0.93f
                ag = 0.94f
                ab = 0.96f
                f0 = 0.94f
            }
            MAT_STEEL -> {
                metal = 1f
                rough = 0.10f
                ar = 0.72f
                ag = 0.73f
                ab = 0.75f
                f0 = 0.86f
            }
            MAT_GUNMETAL -> {
                metal = 0.88f
                rough = 0.18f
                ar = 0.16f
                ag = 0.16f
                ab = 0.175f
                f0 = 0.68f
            }
            MAT_MIRROR -> {
                metal = 1f
                rough = 0.02f
                ar = 1f
                ag = 1f
                ab = 1f
                f0 = 0.96f
            }
            MAT_CERAMIC -> {
                metal = 0f
                rough = 0.35f
                ar = 0.86f
                ag = 0.78f
                ab = 0.70f
                f0 = 0.08f
            }
            MAT_WOOD -> {
                metal = 0f
                rough = 0.48f
                val grain = 1f + 0.07f * sinish(px * 1.35f)
                ar = 0.078f * grain
                ag = 0.040f * grain
                ab = 0.022f * grain
                f0 = 0.045f
            }
            else -> {
                metal = 0.28f
                rough = 0.32f
                ar = 0.034f
                ag = 0.032f
                ab = 0.030f
                f0 = 0.20f
            }
        }

        val ndv = (-(nx * idx + ny * idy + nz * idz)).coerceIn(0f, 1f)
        val fres = f0 + (1f - f0) * pow5(1f - ndv)

        // Key + fill + rim (no allocation)
        var lr = 0.028f
        var lg = 0.026f
        var lb = 0.024f
        accumLight(px, py, pz, nx, ny, nz, -7.5f, 15.5f, 11.5f, 1.55f, 1.0f, 0.97f, 0.92f, castShadow = bounce == 0) { dr, dg, db ->
            lr += dr
            lg += dg
            lb += db
        }
        accumLight(px, py, pz, nx, ny, nz, 11f, 8.5f, 7f, 0.32f, 0.65f, 0.78f, 1.0f, castShadow = false) { dr, dg, db ->
            lr += dr
            lg += dg
            lb += db
        }
        accumLight(px, py, pz, nx, ny, nz, 5f, 7f, -10f, 0.42f, 1.0f, 0.82f, 0.62f, castShadow = false) { dr, dg, db ->
            lr += dr
            lg += dg
            lb += db
        }
        // Bounce from the polished base so ball undersides stay readable in the mirror
        val ground = (-ny).coerceAtLeast(0f) * 0.28f
        lr += ground * 0.42f
        lg += ground * 0.44f
        lb += ground * 0.48f

        val isMirror = mat == MAT_MIRROR
        var r: Float
        var g: Float
        var b: Float
        if (isMirror) {
            r = 0.018f * lr
            g = 0.018f * lg
            b = 0.020f * lb
        } else {
            r = ar * lr
            g = ag * lg
            b = ab * lb
        }

        val doReflect = metal > 0.2f || isMirror || (mat == MAT_FLOOR && bounce == 0)
        if (doReflect) {
            val two = 2f * (nx * idx + ny * idy + nz * idz)
            var rdx = idx - nx * two
            var rdy = idy - ny * two
            var rdz = idz - nz * two
            val rl = sqrt(rdx * rdx + rdy * rdy + rdz * rdz).coerceAtLeast(1e-6f)
            rdx /= rl
            rdy /= rl
            rdz /= rl

            val ox = px + nx * 0.0025f
            val oy = py + ny * 0.0025f
            val oz = pz + nz * 0.0025f
            val forceMirrorBounce = isMirror && bounce == 0
            if (bounce < maxBounce || forceMirrorBounce) {
                val nextMax = if (forceMirrorBounce) maxOf(maxBounce, 1) else maxBounce
                trace(ox, oy, oz, rdx, rdy, rdz, bounce + 1, nextMax)
            } else {
                StudioEnv.sample(rdx, rdy, rdz, envR, envG, envB, 0)
            }
            val rr = envR[0]
            val rg = envG[0]
            val rb = envB[0]
            if (isMirror) {
                val km = 0.94f
                r = rr * km + r * (1f - km)
                g = rg * km + g * (1f - km)
                b = rb * km + b * (1f - km)
            } else {
                val km = (fres * (0.55f + 0.45f * metal)).coerceIn(0f, 1f)
                r = r * (1f - km) + rr * ar * km
                g = g * (1f - km) + rg * ag * km
                b = b * (1f - km) + rb * ab * km
            }
        }

        envR[0] = r
        envG[0] = g
        envB[0] = b
        // silence unused
        if (rough < -1f) envR[0] = r
    }

    private inline fun accumLight(
        px: Float, py: Float, pz: Float,
        nx: Float, ny: Float, nz: Float,
        lx: Float, ly: Float, lz: Float,
        intensity: Float,
        cr: Float, cg: Float, cb: Float,
        castShadow: Boolean,
        add: (Float, Float, Float) -> Unit,
    ) {
        val vx = lx - px
        val vy = ly - py
        val vz = lz - pz
        val dist2 = vx * vx + vy * vy + vz * vz
        val dist = sqrt(dist2)
        val inv = 1f / dist
        val ldx = vx * inv
        val ldy = vy * inv
        val ldz = vz * inv
        val ndl = (nx * ldx + ny * ldy + nz * ldz).coerceAtLeast(0f)
        if (ndl <= 0f) {
            add(0f, 0f, 0f)
            return
        }
        val shadow = if (castShadow && occluded(px + nx * 0.003f, py + ny * 0.003f, pz + nz * 0.003f, ldx, ldy, ldz, dist - 0.15f)) {
            0.16f
        } else {
            1f
        }
        val att = intensity * ndl * shadow / (1f + dist2 * 0.0018f)
        add(att * cr, att * cg, att * cb)
    }

    private fun occluded(
        ox: Float, oy: Float, oz: Float,
        dx: Float, dy: Float, dz: Float,
        maxT: Float,
    ): Boolean {
        val sc = scene.sphereCount
        var i = 0
        while (i < sc) {
            val t = hitSphere(ox, oy, oz, dx, dy, dz, scene.sX[i], scene.sY[i], scene.sZ[i], scene.sR[i], maxT)
            if (t > 0f) return true
            i++
        }
        val bc = scene.boxCount
        i = 0
        while (i < bc) {
            val t = hitAabb(
                ox, oy, oz, dx, dy, dz,
                scene.bMinX[i], scene.bMinY[i], scene.bMinZ[i],
                scene.bMaxX[i], scene.bMaxY[i], scene.bMaxZ[i],
                maxT,
            )
            if (t > 0f) return true
            i++
        }
        return false
    }

    private val nTmp = FloatArray(3)

    private fun cylNormal(
        px: Float, py: Float, pz: Float,
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
    ): FloatArray {
        val bax = bx - ax
        val bay = by - ay
        val baz = bz - az
        val baba = bax * bax + bay * bay + baz * baz
        val pax = px - ax
        val pay = py - ay
        val paz = pz - az
        val h = ((pax * bax + pay * bay + paz * baz) / baba).coerceIn(0f, 1f)
        var nx = pax - bax * h
        var ny = pay - bay * h
        var nz = paz - baz * h
        val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
        nTmp[0] = nx / len
        nTmp[1] = ny / len
        nTmp[2] = nz / len
        return nTmp
    }

    private fun aabbNormal(
        px: Float, py: Float, pz: Float,
        minX: Float, minY: Float, minZ: Float,
        maxX: Float, maxY: Float, maxZ: Float,
    ): FloatArray {
        val e = 0.0012f
        when {
            px <= minX + e -> { nTmp[0] = -1f; nTmp[1] = 0f; nTmp[2] = 0f }
            px >= maxX - e -> { nTmp[0] = 1f; nTmp[1] = 0f; nTmp[2] = 0f }
            py <= minY + e -> { nTmp[0] = 0f; nTmp[1] = -1f; nTmp[2] = 0f }
            py >= maxY - e -> { nTmp[0] = 0f; nTmp[1] = 1f; nTmp[2] = 0f }
            pz <= minZ + e -> { nTmp[0] = 0f; nTmp[1] = 0f; nTmp[2] = -1f }
            else -> { nTmp[0] = 0f; nTmp[1] = 0f; nTmp[2] = 1f }
        }
        return nTmp
    }
}

private fun hitSphere(
    ox: Float, oy: Float, oz: Float,
    dx: Float, dy: Float, dz: Float,
    cx: Float, cy: Float, cz: Float,
    r: Float,
    tMax: Float,
): Float {
    val lx = ox - cx
    val ly = oy - cy
    val lz = oz - cz
    val b = lx * dx + ly * dy + lz * dz
    val c = lx * lx + ly * ly + lz * lz - r * r
    val disc = b * b - c
    if (disc < 0f) return -1f
    val s = sqrt(disc)
    var t = -b - s
    if (t < 1e-3f) t = -b + s
    if (t < 1e-3f || t >= tMax) return -1f
    return t
}

private fun hitAabb(
    ox: Float, oy: Float, oz: Float,
    dx: Float, dy: Float, dz: Float,
    minX: Float, minY: Float, minZ: Float,
    maxX: Float, maxY: Float, maxZ: Float,
    tMax: Float,
): Float {
    val invX = 1f / (if (dx == 0f) 1e-8f else dx)
    val invY = 1f / (if (dy == 0f) 1e-8f else dy)
    val invZ = 1f / (if (dz == 0f) 1e-8f else dz)
    var tx1 = (minX - ox) * invX
    var tx2 = (maxX - ox) * invX
    if (tx1 > tx2) {
        val tmp = tx1
        tx1 = tx2
        tx2 = tmp
    }
    var ty1 = (minY - oy) * invY
    var ty2 = (maxY - oy) * invY
    if (ty1 > ty2) {
        val tmp = ty1
        ty1 = ty2
        ty2 = tmp
    }
    var tz1 = (minZ - oz) * invZ
    var tz2 = (maxZ - oz) * invZ
    if (tz1 > tz2) {
        val tmp = tz1
        tz1 = tz2
        tz2 = tmp
    }
    val tmin = maxOf(tx1, ty1, tz1)
    val tmax = minOf(tx2, ty2, tz2)
    if (tmax < tmin) return -1f
    val t = if (tmin > 1e-3f) tmin else tmax
    if (t < 1e-3f || t >= tMax) return -1f
    return t
}

private fun hitCylinder(
    ox: Float, oy: Float, oz: Float,
    dx: Float, dy: Float, dz: Float,
    ax: Float, ay: Float, az: Float,
    bx: Float, by: Float, bz: Float,
    ra: Float,
    tMax: Float,
): Float {
    val bax = bx - ax
    val bay = by - ay
    val baz = bz - az
    val ocx = ox - ax
    val ocy = oy - ay
    val ocz = oz - az
    val baba = bax * bax + bay * bay + baz * baz
    val bard = bax * dx + bay * dy + baz * dz
    val baoc = bax * ocx + bay * ocy + baz * ocz
    val k2 = baba - bard * bard
    if (k2 < 1e-8f && k2 > -1e-8f) return -1f
    val k1 = baba * (ocx * dx + ocy * dy + ocz * dz) - baoc * bard
    val k0 = baba * (ocx * ocx + ocy * ocy + ocz * ocz) - baoc * baoc - ra * ra * baba
    val h = k1 * k1 - k2 * k0
    if (h < 0f) return -1f
    val sh = sqrt(h)
    var t = (-k1 - sh) / k2
    val y = baoc + t * bard
    if (y > 0f && y < baba && t > 1e-3f && t < tMax) return t
    t = (((if (y < 0f) 0f else baba) - baoc) / bard)
    if (abs(k1 + k2 * t) < sh && t > 1e-3f && t < tMax) return t
    return -1f
}

private fun pow5(x: Float): Float {
    val x2 = x * x
    return x2 * x2 * x
}

private fun sinish(x: Float): Float {
    val t = x - kotlin.math.floor(x / (2f * kotlin.math.PI.toFloat())) * (2f * kotlin.math.PI.toFloat())
    return kotlin.math.sin(t)
}

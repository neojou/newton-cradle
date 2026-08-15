package com.neojou.newtoncradle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neojou.newtoncradle.Res
import com.neojou.newtoncradle.audio.loadCollisionWav
import com.neojou.newtoncradle.audio.playCollision
import com.neojou.newtoncradle.audio.unlockAudio
import com.neojou.newtoncradle.render.RayTracer
import com.neojou.newtoncradle.render.Textures
import com.neojou.newtoncradle.render.decodeImageBytes
import com.neojou.newtoncradle.render.isWasmTarget
import org.jetbrains.compose.resources.ExperimentalResourceApi
import com.neojou.newtoncradle.sim.Cradle
import com.neojou.newtoncradle.sim.CradlePhysics
import com.neojou.newtoncradle.sim.CradleSnapshot
import com.neojou.newtoncradle.sim.Vec3
import com.neojou.newtoncradle.sim.ballCenter
import com.neojou.newtoncradle.sim.stringPivot
import com.neojou.tools.LogLevel
import com.neojou.tools.MyLog
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.time.TimeSource

private const val TAG = "CradleView"

@Composable
fun CradleViewport(
    physics: CradlePhysics,
    modifier: Modifier = Modifier,
) {
    val tracer = remember { RayTracer() }
    val governor = remember { QualityGovernor(if (isWasmTarget()) 0.34f else 0.48f) }
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    val minTouchPx = with(density) { 56.dp.toPx() }
    val touchFriendly = isWasmTarget()
    val viewSizeRef = remember { mutableStateOf(IntSize(640, 400)) }
    val snapRef = remember { mutableStateOf(physics.snapshot()) }

    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    var snap by remember { mutableStateOf(physics.snapshot()) }
    var hintVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        @OptIn(ExperimentalResourceApi::class)
        runCatching {
            Textures.wood = decodeImageBytes(Res.readBytes("files/sandalwood.jpg"))
            Textures.sasa = decodeImageBytes(Res.readBytes("files/kamon_sasa.jpg"))
            Textures.hikiryo = decodeImageBytes(Res.readBytes("files/kamon_hikiryo.jpg"))
            Textures.kuyou = decodeImageBytes(Res.readBytes("files/kamon_kuyou.jpg"))
            loadCollisionWav(Res.readBytes("files/clang.wav"))
        }
        if (!isWasmTarget()) focusRequester.requestFocus()
        var lastNs = 0L
        var accLog = 0f
        var frames = 0
        while (true) {
            withFrameNanos { now ->
                val dt = if (lastNs == 0L) {
                    1f / 60f
                } else {
                    ((now - lastNs).coerceAtMost(80_000_000L) / 1_000_000_000f)
                }
                lastNs = now
                val mark = TimeSource.Monotonic.markNow()
                physics.step(dt)
                val impact = physics.takeImpact()
                if (impact > 0f) playCollision(impact)
                val s = physics.snapshot()
                snap = s
                snapRef.value = s
                val size = viewSizeRef.value
                val img = tracer.render(s, size.width, size.height, governor.quality)
                if (img != null) frame = img
                val frameMs = mark.elapsedNow().inWholeNanoseconds / 1_000_000f
                governor.report(frameMs)
                accLog += dt
                frames++
                if (accLog >= 2.5f) {
                    MyLog.add(
                        TAG,
                        "fps≈${frames / accLog}  q=${governor.quality}  frameMs≈$frameMs",
                        LogLevel.DEBUG,
                    )
                    accLog = 0f
                    frames = 0
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .focusRequester(focusRequester)
            .focusable()
            .onSizeChanged { viewSizeRef.value = it }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                when (event.key) {
                    Key.Spacebar -> {
                        if (physics.holding) physics.release() else physics.reset()
                        hintVisible = false
                        true
                    }
                    Key.R -> {
                        physics.reset()
                        hintVisible = true
                        true
                    }
                    else -> false
                }
            },
    ) {
        val image = frame
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "Newton's Cradle",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cam = tracer.camera
            val silver = Color(0.72f, 0.73f, 0.75f, 0.88f)
            for (i in 0 until Cradle.BALLS) {
                val ball = ballCenter(i, snap.theta[i])
                val left = stringPivot(i, -1f)
                val right = stringPivot(i, 1f)
                val attachL = attachOnSphere(ball, left)
                val attachR = attachOnSphere(ball, right)
                val pBallL = cam.project(attachL, w, h)
                val pBallR = cam.project(attachR, w, h)
                val pL = cam.project(left, w, h)
                val pR = cam.project(right, w, h)
                if (pL != null && pBallL != null) {
                    drawLine(
                        color = silver,
                        start = Offset(pL.first, pL.second),
                        end = Offset(pBallL.first, pBallL.second),
                        strokeWidth = 1.35f,
                        cap = StrokeCap.Round,
                    )
                }
                if (pR != null && pBallR != null) {
                    drawLine(
                        color = silver,
                        start = Offset(pR.first, pR.second),
                        end = Offset(pBallR.first, pBallR.second),
                        strokeWidth = 1.35f,
                        cap = StrokeCap.Round,
                    )
                }
            }

            if (snap.handPhase > 0.04f) {
                val hi = snap.holdIndex
                val ball = ballCenter(hi, snap.theta[hi])
                val p = cam.project(ball, w, h)
                val rim = cam.project(Vec3(ball.x + Cradle.RADIUS, ball.y, ball.z), w, h)
                if (p != null) {
                    val rPx = if (rim != null) abs(rim.first - p.first) else 36f
                    val side = if (hi == 0) 1f else -1f
                    val retract = 1f - snap.handPhase
                    val pinch = Offset(
                        p.first - side * rPx * retract * 1.8f,
                        p.second - rPx * retract * 0.35f,
                    )
                    drawStudioHand(
                        pinch = pinch,
                        ballRadius = rPx,
                        angle = snap.theta[hi],
                        side = side,
                        alpha = snap.handPhase,
                    )
                }
            }
        }

        if (hintVisible || physics.holding) {
            val label = if (physics.holding) {
                if (touchFriendly) "按住往上拉到水平，放開後擺動" else "可拉到水平 90°，放開後擺動 · Space 放下 · R 重置"
            } else {
                if (touchFriendly) "用手指按住兩側鋼球往上拉" else "拖曳兩側鋼球，最高可拉到水平 · Space / R 重置"
            }
            Text(
                text = label,
                color = Color(0xCCD4AF77),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp),
            )
        }

        // Topmost hit target so Image/Canvas cannot swallow the finger.
        Box(
            Modifier
                .fillMaxSize()
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(minTouchPx, touchFriendly) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        unlockAudio()
                        val size = viewSizeRef.value
                        val idx = nearestEndBall(
                            offset = down.position,
                            viewSize = size,
                            snap = snapRef.value,
                            tracer = tracer,
                            minHitPx = minTouchPx,
                            generous = touchFriendly,
                        )
                        if (idx < 0 && !physics.holding) {
                            return@awaitEachGesture
                        }
                        if (idx >= 0) physics.tryGrab(idx)
                        applyDrag(down.position, size, physics, tracer)
                        hintVisible = false
                        down.consume()
                        drag(down.id) { change ->
                            change.consume()
                            if (physics.holding) {
                                applyDrag(change.position, viewSizeRef.value, physics, tracer)
                            }
                        }
                        if (physics.holding) physics.release()
                    }
                },
        )
    }
}

private class QualityGovernor(initial: Float) {
    var quality: Float = initial
        private set

    fun report(frameMs: Float) {
        quality = when {
            frameMs > 32f -> (quality * 0.90f).coerceAtLeast(0.26f)
            frameMs > 26f -> (quality * 0.97f).coerceAtLeast(0.26f)
            frameMs < 18f -> (quality + 0.03f).coerceAtMost(1f)
            else -> quality
        }
    }
}

private fun applyDrag(
    offset: Offset,
    viewSize: IntSize,
    physics: CradlePhysics,
    tracer: RayTracer,
) {
    val hit = tracer.camera.intersectSwingPlane(
        offset.x,
        offset.y,
        viewSize.width.toFloat(),
        viewSize.height.toFloat(),
    ) ?: return
    physics.setHoldAngle(physics.angleFromWorld(hit.x, hit.y, physics.holdIndex))
}

private fun nearestEndBall(
    offset: Offset,
    viewSize: IntSize,
    snap: CradleSnapshot,
    tracer: RayTracer,
    minHitPx: Float,
    generous: Boolean,
): Int {
    val w = viewSize.width.toFloat()
    val h = viewSize.height.toFloat()
    if (w < 8f || h < 8f) return -1
    var best = -1
    var bestD = Float.MAX_VALUE
    val ends = intArrayOf(0, Cradle.BALLS - 1)
    val pad = if (generous) 2.8f else 1.55f
    for (i in ends) {
        val c = ballCenter(i, snap.theta[i])
        val p = tracer.camera.project(c, w, h) ?: continue
        val d = hypot(p.first - offset.x, p.second - offset.y)
        val rim = tracer.camera.project(Vec3(c.x + Cradle.RADIUS, c.y, c.z), w, h)
        val radius = if (rim != null) abs(rim.first - p.first) else 40f
        val hitR = maxOf(radius * pad, minHitPx)
        if (d < hitR && d < bestD) {
            bestD = d
            best = i
        }
    }
    if (best >= 0) return best
    if (!generous) return -1
    // Finger often lands beside a small projected ball — take the nearer end
    // if the touch is still in the cradle neighbourhood.
    val neighbourhood = maxOf(minHitPx * 2.4f, minOf(w, h) * 0.28f)
    for (i in ends) {
        val c = ballCenter(i, snap.theta[i])
        val p = tracer.camera.project(c, w, h) ?: continue
        val d = hypot(p.first - offset.x, p.second - offset.y)
        if (d < neighbourhood && d < bestD) {
            bestD = d
            best = i
        }
    }
    return best
}

private fun attachOnSphere(ball: Vec3, pivot: Vec3): Vec3 {
    val dir = (pivot - ball).normalized()
    return ball + dir * Cradle.RADIUS
}

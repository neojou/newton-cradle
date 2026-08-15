package com.neojou.newtoncradle.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

/**
 * Side-view studio hand that pinches a ball. Drawn in 2D so it stays readable
 * at every angle; rotation follows the pendulum.
 *
 * Local space: origin at the pinch, +X toward the wrist, +Y up. [side] flips
 * for the right-hand ball.
 */
internal fun DrawScope.drawStudioHand(
    pinch: Offset,
    ballRadius: Float,
    angle: Float,
    side: Float,
    alpha: Float,
) {
    if (alpha < 0.02f || ballRadius < 2f) return
    val s = ballRadius / 42f
    translate(pinch.x, pinch.y) {
        rotateRad(angle) {
            scale(scaleX = s * side, scaleY = s) {
                drawHandLocal(alpha)
            }
        }
    }
}

private fun DrawScope.drawHandLocal(alpha: Float) {
    val skin = Color(0.90f, 0.76f, 0.64f, alpha)
    val skinDeep = Color(0.72f, 0.52f, 0.40f, alpha)
    val skinHi = Color(0.98f, 0.90f, 0.82f, alpha * 0.85f)
    val line = Color(0.42f, 0.28f, 0.20f, alpha * 0.35f)
    val nail = Color(0.95f, 0.88f, 0.84f, alpha * 0.9f)

    // Forearm
    val arm = Path().apply {
        moveTo(-168f, -28f)
        quadraticTo(-210f, -8f, -228f, 18f)
        quadraticTo(-200f, 48f, -150f, 40f)
        lineTo(-78f, 36f)
        quadraticTo(-62f, 8f, -78f, -22f)
        close()
    }
    drawPath(
        arm,
        brush = Brush.linearGradient(
            listOf(skinDeep, skin),
            start = Offset(-220f, 40f),
            end = Offset(-80f, -30f),
        ),
    )

    // Palm
    val palm = Path().apply {
        moveTo(-92f, -48f)
        quadraticTo(-40f, -70f, 6f, -38f)
        quadraticTo(22f, -8f, 8f, 28f)
        quadraticTo(-18f, 58f, -70f, 50f)
        quadraticTo(-108f, 20f, -92f, -48f)
        close()
    }
    drawPath(
        palm,
        brush = Brush.radialGradient(
            colors = listOf(skinHi, skin, skinDeep),
            center = Offset(-48f, -8f),
            radius = 90f,
        ),
    )

    // Thumb (below the pinch)
    val thumb = Path().apply {
        moveTo(-28f, 22f)
        quadraticTo(8f, 48f, 36f, 52f)
        quadraticTo(58f, 40f, 52f, 22f)
        quadraticTo(28f, 8f, -8f, 10f)
        close()
    }
    drawPath(thumb, color = skin)
    drawOval(
        color = nail,
        topLeft = Offset(38f, 26f),
        size = Size(16f, 12f),
    )

    // Index (above the pinch)
    val index = Path().apply {
        moveTo(-24f, -28f)
        quadraticTo(10f, -58f, 40f, -50f)
        quadraticTo(62f, -36f, 54f, -18f)
        quadraticTo(24f, -8f, -6f, -6f)
        close()
    }
    drawPath(index, color = skin)
    drawOval(
        color = nail,
        topLeft = Offset(40f, -42f),
        size = Size(15f, 11f),
    )

    // Folded middle / ring suggestion
    val folded = Path().apply {
        moveTo(-40f, -8f)
        quadraticTo(-8f, -4f, 4f, 10f)
        quadraticTo(-6f, 22f, -36f, 16f)
        close()
    }
    drawPath(folded, color = skinDeep.copy(alpha = alpha * 0.85f))

    val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(arm, color = line, style = stroke)
    drawPath(palm, color = line, style = stroke)
    drawPath(thumb, color = line, style = stroke)
    drawPath(index, color = line, style = stroke)
}

package com.brioni.snake.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import com.brioni.snake.game.FoodEffect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The on-disc symbols that identify each Phase 6.2 special (power-up / hazard).
 *
 * These are the single source of truth shared by the in-game board renderer
 * ([GameBoard]) and the first-run tutorial ([com.brioni.snake.ui.onboarding]),
 * so a Lightning bolt, a Star, a Freeze snowflake, a Jackpot "$", a Quake crack,
 * an Explosion burst and a Snail spiral look identical wherever a player meets
 * them. They draw in the local coordinate space centred at ([cx], [cy]) with a
 * nominal radius [r], in a single [color], so the caller controls placement,
 * size and ink. [drawGlyph] is the shared text-glyph helper (the "?" mystery
 * food and the "$" jackpot).
 */

/** Dispatches the on-disc icon for each special effect. */
internal fun DrawScope.drawSpecialSymbol(
    effect: FoodEffect,
    cx: Float,
    cy: Float,
    r: Float,
    color: Color,
    textMeasurer: TextMeasurer,
) {
    when (effect) {
        is FoodEffect.Haste -> drawBolt(cx, cy, r, color)
        is FoodEffect.Slow -> drawSpiral(cx, cy, r, color)
        is FoodEffect.Ghost -> drawStarShape(cx, cy, r, points = 5, innerRatio = 0.45f, color = color)
        is FoodEffect.Freeze -> drawSnowflake(cx, cy, r, color)
        is FoodEffect.Jackpot -> drawGlyph(textMeasurer, "$", cx, cy, r * 1.7f, color)
        is FoodEffect.Burst -> drawStarShape(cx, cy, r, points = 8, innerRatio = 0.42f, color = color)
        is FoodEffect.Quake -> drawCrack(cx, cy, r, color)
        is FoodEffect.TimeBonus -> drawClock(cx, cy, r, color, plus = true)
        is FoodEffect.TimePenalty -> drawClock(cx, cy, r, color, plus = false)
        is FoodEffect.ExtraLife -> drawSnakeHeadIcon(cx, cy, r, color)
        else -> Unit
    }
}

/** Draws [glyph] centred at ([centerX], [centerY]), sized to [sizePx], in the game's style. */
internal fun DrawScope.drawGlyph(
    textMeasurer: TextMeasurer,
    glyph: String,
    centerX: Float,
    centerY: Float,
    sizePx: Float,
    color: Color,
) {
    val layout = textMeasurer.measure(
        text = glyph,
        style = TextStyle(color = color, fontSize = sizePx.toSp(), fontWeight = FontWeight.Black),
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(centerX - layout.size.width / 2f, centerY - layout.size.height / 2f),
    )
}

/** A tiny snake head (rounded square + upward eyes) - the extra-life bonus icon. */
internal fun DrawScope.drawSnakeHeadIcon(cx: Float, cy: Float, r: Float, color: Color) {
    val side = r * 1.25f
    drawRoundRect(
        color = color,
        topLeft = Offset(cx - side / 2f, cy - side / 2f),
        size = Size(side, side),
        cornerRadius = CornerRadius(side * 0.3f, side * 0.3f),
    )
    val eyeRadius = side * 0.14f
    for (sign in intArrayOf(-1, 1)) {
        val ex = cx + sign * side * 0.2f
        val ey = cy - side * 0.14f
        drawCircle(Color.White, eyeRadius, Offset(ex, ey))
        drawCircle(color, eyeRadius * 0.5f, Offset(ex, ey - eyeRadius * 0.2f))
    }
}

/** A clock face with a small +/- badge - the Time Attack bonus / penalty blocks. */
internal fun DrawScope.drawClock(cx: Float, cy: Float, r: Float, color: Color, plus: Boolean) {
    val ring = r * 0.74f
    drawCircle(color, ring, Offset(cx, cy), style = Stroke(width = r * 0.16f))
    // Hands: one up, one to the right.
    drawLine(color, Offset(cx, cy), Offset(cx, cy - ring * 0.58f), strokeWidth = r * 0.15f, cap = StrokeCap.Round)
    drawLine(color, Offset(cx, cy), Offset(cx + ring * 0.44f, cy), strokeWidth = r * 0.15f, cap = StrokeCap.Round)
    // +/- badge at the bottom-right.
    val bx = cx + r * 0.62f
    val by = cy + r * 0.62f
    val s = r * 0.32f
    drawLine(color, Offset(bx - s, by), Offset(bx + s, by), strokeWidth = r * 0.17f, cap = StrokeCap.Round)
    if (plus) drawLine(color, Offset(bx, by - s), Offset(bx, by + s), strokeWidth = r * 0.17f, cap = StrokeCap.Round)
}

/** An n-pointed star (used for Star and the spiky Explosion burst). */
internal fun DrawScope.drawStarShape(cx: Float, cy: Float, r: Float, points: Int, innerRatio: Float, color: Color) {
    val path = Path()
    val verts = points * 2
    for (i in 0 until verts) {
        val rr = if (i % 2 == 0) r else r * innerRatio
        val ang = -PI / 2 + i * PI / points
        val x = cx + (cos(ang) * rr).toFloat()
        val y = cy + (sin(ang) * rr).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

internal fun DrawScope.drawBolt(cx: Float, cy: Float, r: Float, color: Color) {
    val pts = listOf(
        -0.10f to -0.62f, 0.30f to -0.62f, 0.00f to -0.10f,
        0.26f to -0.10f, -0.16f to 0.62f, -0.02f to 0.06f, -0.30f to 0.06f,
    )
    val path = Path()
    pts.forEachIndexed { i, (px, py) ->
        val x = cx + px * r
        val y = cy + py * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

internal fun DrawScope.drawSnowflake(cx: Float, cy: Float, r: Float, color: Color) {
    val stroke = r * 0.16f
    for (k in 0 until 3) {
        val ang = k * PI / 3
        val dx = cos(ang).toFloat()
        val dy = sin(ang).toFloat()
        drawLine(
            color,
            Offset(cx - dx * r, cy - dy * r),
            Offset(cx + dx * r, cy + dy * r),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        // Two small V-branches near each tip.
        for (sign in intArrayOf(-1, 1)) {
            val tipX = cx + dx * r * sign
            val tipY = cy + dy * r * sign
            val bAng = ang + sign * 0.9
            val bx = cos(bAng).toFloat() * r * 0.32f
            val by = sin(bAng).toFloat() * r * 0.32f
            drawLine(color, Offset(tipX, tipY), Offset(tipX - bx, tipY - by), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
        }
    }
}

internal fun DrawScope.drawSpiral(cx: Float, cy: Float, r: Float, color: Color) {
    val path = Path()
    val steps = 64
    val turns = 2.4
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val ang = t * turns * 2 * PI
        val rr = r * (0.92f - 0.78f * t)
        val x = cx + (cos(ang) * rr).toFloat()
        val y = cy + (sin(ang) * rr).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(width = r * 0.14f, cap = StrokeCap.Round))
}

internal fun DrawScope.drawCrack(cx: Float, cy: Float, r: Float, color: Color) {
    val pts = listOf(
        -0.7f to -0.1f, -0.35f to 0.28f, -0.05f to -0.22f,
        0.25f to 0.22f, 0.62f to -0.16f,
    )
    for (i in 0 until pts.size - 1) {
        val (ax, ay) = pts[i]
        val (bx, by) = pts[i + 1]
        drawLine(
            color,
            Offset(cx + ax * r, cy + ay * r),
            Offset(cx + bx * r, cy + by * r),
            strokeWidth = r * 0.16f,
            cap = StrokeCap.Round,
        )
    }
}

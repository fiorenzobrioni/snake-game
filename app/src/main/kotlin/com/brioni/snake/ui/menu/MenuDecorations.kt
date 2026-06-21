package com.brioni.snake.ui.menu

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.brioni.snake.ui.game.SkinPalette
import kotlin.math.PI
import kotlin.math.sin

/**
 * A small, brand-defining snake drawn just beneath the main-menu title - a
 * compact emblem rather than the old full-screen gliding decoration. It reuses
 * the gameplay vocabulary (rounded-rect segments with a glowing eyed head,
 * mirroring [com.brioni.snake.ui.game.GameBoard]) recoloured from the selected
 * [palette], so the menu reflects the player's chosen skin. A gentle, slow
 * slither keeps it alive without drawing attention away from the buttons.
 */
@Composable
fun TitleSnake(palette: SkinPalette, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "titleSnake")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )

    Canvas(modifier = modifier) {
        drawTitleSnake(palette, phase)
    }
}

/**
 * Draws the short title snake centred in the canvas: a chain of rounded-rect
 * segments following a shallow horizontal S-curve, with a glowing eyed head
 * leading on the right. [phase] gently slithers the whole body.
 */
private fun DrawScope.drawTitleSnake(palette: SkinPalette, phase: Float) {
    val segments = 7
    val cell = size.height * 0.70f
    val step = cell * 0.86f // slight overlap so the body reads as continuous
    val baseY = size.height * 0.5f
    val amplitude = size.height * 0.16f
    val totalWidth = step * (segments - 1)
    val startX = (size.width - totalWidth) / 2f
    val wave = phase * 2f * PI.toFloat()

    // Draw tail-first so the head overlaps the neck.
    for (i in 0 until segments) {
        val isHead = i == segments - 1
        val cx = startX + step * i
        val cy = baseY + sin(wave + i * 0.6f) * amplitude
        // Body tapers in opacity toward the tail; the head stays the brightest.
        val alpha = if (isHead) 1f else 0.55f + 0.35f * (i.toFloat() / (segments - 1))

        if (isHead && palette.useGlow) {
            val glowRadius = cell * 1.4f
            val pulse = 0.8f + 0.2f * sin(wave * 1.5f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(palette.headGlow.copy(alpha = 0.55f * pulse), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = glowRadius,
                ),
                radius = glowRadius,
                center = Offset(cx, cy),
            )
        }

        drawTitleSegment(isHead, cx, cy, cell, palette, alpha)
        if (isHead) {
            // The head points along the body's local slope, so the eyes lead the turn.
            val slope = sin(wave + i * 0.6f) - sin(wave + (i - 1) * 0.6f)
            drawTitleEyes(cx, cy, cell, dirX = 1f, dirY = slope.coerceIn(-1f, 1f), palette)
        }
    }
}

/** A single rounded-rect snake segment, centred at [cx],[cy] - mirrors the in-game style. */
private fun DrawScope.drawTitleSegment(
    isHead: Boolean,
    cx: Float,
    cy: Float,
    cell: Float,
    palette: SkinPalette,
    alpha: Float,
) {
    val inset = cell * 0.06f
    val side = cell - 2 * inset
    val topLeft = Offset(cx - side / 2f, cy - side / 2f)
    val corner = cell * palette.cornerFactor
    val radius = CornerRadius(corner, corner)
    val fill = (if (isHead) palette.snakeHead else palette.snakeBody).copy(alpha = alpha)
    drawRoundRect(color = fill, topLeft = topLeft, size = Size(side, side), cornerRadius = radius)
    drawRoundRect(
        color = palette.snakeOutline.copy(alpha = alpha),
        topLeft = topLeft,
        size = Size(side, side),
        cornerRadius = radius,
        style = Stroke(width = cell * 0.06f),
    )
}

/** Two white eyes with dark pupils, looking along ([dirX],[dirY]) - mirrors GameBoard.drawEyes. */
private fun DrawScope.drawTitleEyes(
    cx: Float,
    cy: Float,
    cell: Float,
    dirX: Float,
    dirY: Float,
    palette: SkinPalette,
) {
    val len = kotlin.math.hypot(dirX, dirY).coerceAtLeast(0.0001f)
    val fx = dirX / len
    val fy = dirY / len
    val perpX = -fy
    val perpY = fx
    val forward = cell * 0.16f
    val spread = cell * 0.2f
    val eyeRadius = cell * 0.12f
    val pupilRadius = cell * 0.06f
    for (sign in intArrayOf(-1, 1)) {
        val ex = cx + fx * forward + perpX * spread * sign
        val ey = cy + fy * forward + perpY * spread * sign
        drawCircle(Color.White, eyeRadius, Offset(ex, ey))
        drawCircle(
            palette.snakeEye,
            pupilRadius,
            Offset(ex + fx * cell * 0.03f, ey + fy * cell * 0.03f),
        )
    }
}

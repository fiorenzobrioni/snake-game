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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.brioni.snake.ui.game.Particle
import com.brioni.snake.ui.game.SkinPalette
import com.brioni.snake.ui.game.emitEatBurst
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Discreet, looping decorations layered *behind* the main-menu content: a
 * stylised snake gliding through the empty space above the title, and two
 * offset particle bursts low on the screen, beneath the buttons.
 *
 * The look reuses the gameplay vocabulary — rounded-rect snake segments with a
 * glowing eyed head ([com.brioni.snake.ui.game.GameBoard]) and the eat-burst
 * particle system ([emitEatBurst]) — recoloured from the selected [palette], so
 * the menu reflects the player's chosen skin. Everything is kept low-opacity so
 * the title and buttons stay perfectly legible, and is drawn with plain Canvas
 * primitives (a radial-gradient glow instead of the AGSL shader) so this file is
 * self-contained and cheap.
 */
@Composable
fun MenuDecorations(palette: SkinPalette, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "menuDecorations")
    // A single 0..1 phase drives the snake slither, the head-glow pulse and the
    // looping bursts; offsetting it per element keeps them out of lockstep.
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )

    // The two bursts are generated once and replayed continuously from the phase.
    val burstA = remember(palette) { decorBurst(palette.growMedium, seed = 1) }
    val burstB = remember(palette) { decorBurst(palette.shrinkLarge, seed = 7) }

    Canvas(modifier = modifier) {
        drawDecorSnake(palette, phase)
        // Offset the second burst's phase so the two read as staggered, not twinned.
        drawDecorBurst(burstA, centerX = size.width * 0.30f, centerY = size.height * 0.86f, t = frac(phase))
        drawDecorBurst(burstB, centerX = size.width * 0.70f, centerY = size.height * 0.90f, t = frac(phase + 0.5f))
    }
}

/** Fractional part, so a phase offset wraps cleanly into 0..1. */
private fun frac(v: Float): Float = v - kotlin.math.floor(v)

/**
 * Draws the decorative snake in the top region (~6%..20% of the height), as a
 * chain of rounded-rect segments following a horizontal S-curve, with a glowing
 * eyed head leading the way. [phase] gently slithers the whole body.
 */
private fun DrawScope.drawDecorSnake(palette: SkinPalette, phase: Float) {
    val segments = 9
    val cell = size.width * 0.072f
    val step = cell * 0.92f // slight overlap so the body reads as continuous
    val baseY = size.height * 0.13f
    val amplitude = cell * 1.15f
    // Centre the chain horizontally; the head sits on the right, leading.
    val totalWidth = step * (segments - 1)
    val startX = (size.width - totalWidth) / 2f
    val wave = phase * 2f * PI.toFloat()

    // Draw tail-first so the head overlaps the neck.
    for (i in 0 until segments) {
        val isHead = i == segments - 1
        val cx = startX + step * i
        val cy = baseY + sin(wave + i * 0.7f) * amplitude
        // Body fades slightly toward the tail; the head stays the brightest.
        val alpha = if (isHead) 0.9f else 0.34f + 0.30f * (i.toFloat() / (segments - 1))

        if (isHead && palette.useGlow) {
            val glowRadius = cell * 1.5f
            val pulse = 0.8f + 0.2f * sin(wave * 1.5f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.headGlow.copy(alpha = 0.45f * pulse),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = glowRadius,
                ),
                radius = glowRadius,
                center = Offset(cx, cy),
            )
        }

        drawDecorSegment(isHead, cx, cy, cell, palette, alpha)
        if (isHead) {
            // The head points along the body's local slope, so the eyes lead the turn.
            val slope = sin(wave + i * 0.7f) - sin(wave + (i - 1) * 0.7f)
            drawDecorEyes(cx, cy, cell, dirX = 1f, dirY = slope.coerceIn(-1f, 1f), palette)
        }
    }
}

/** A single rounded-rect snake segment, centred at [cx],[cy] — mirrors the in-game style. */
private fun DrawScope.drawDecorSegment(
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

/** Two white eyes with dark pupils, looking along ([dirX],[dirY]) — mirrors GameBoard.drawEyes. */
private fun DrawScope.drawDecorEyes(
    cx: Float,
    cy: Float,
    cell: Float,
    dirX: Float,
    dirY: Float,
    palette: SkinPalette,
) {
    // Normalise the look direction so the eyes seat consistently regardless of slope.
    val len = kotlin.math.hypot(dirX, dirY).coerceAtLeast(0.0001f)
    val fx = dirX / len
    val fy = dirY / len
    val perpX = -fy
    val perpY = fx
    val forward = cell * 0.16f
    val spread = cell * 0.2f
    val eyeRadius = cell * 0.11f
    val pupilRadius = cell * 0.055f
    for (sign in intArrayOf(-1, 1)) {
        val ex = cx + fx * forward + perpX * spread * sign
        val ey = cy + fy * forward + perpY * spread * sign
        drawCircle(Color.White.copy(alpha = 0.9f), eyeRadius, Offset(ex, ey))
        drawCircle(
            palette.snakeEye.copy(alpha = 0.9f),
            pupilRadius,
            Offset(ex + fx * cell * 0.03f, ey + fy * cell * 0.03f),
        )
    }
}

/** Generates a one-off radial burst (in cell space) to be replayed from a phase. */
private fun decorBurst(color: Color, seed: Int): List<Particle> {
    val particles = mutableListOf<Particle>()
    emitEatBurst(particles, centerX = 0f, centerY = 0f, color = color, span = 1, random = Random(seed))
    return particles
}

/**
 * Replays a pre-built [burst] centred at [centerX],[centerY] (pixels) for a
 * normalised time [t] in 0..1: particles fly out along their velocity and fade,
 * looping seamlessly. Kept low-opacity so it never competes with the buttons.
 */
private fun DrawScope.drawDecorBurst(burst: List<Particle>, centerX: Float, centerY: Float, t: Float) {
    // Map cell-space velocities to a tasteful pixel reach for the menu.
    val reach = size.width * 0.018f
    val alpha = (1f - t) * 0.5f
    if (alpha <= 0.01f) return
    burst.forEach { p ->
        val px = centerX + p.vx * t * reach
        val py = centerY + p.vy * t * reach
        drawCircle(
            color = p.color.copy(alpha = alpha),
            radius = p.radiusCells * reach,
            center = Offset(px, py),
        )
    }
}

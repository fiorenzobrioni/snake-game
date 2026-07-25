package com.callbackdev.snake.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.callbackdev.snake.R
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The one thing the player can *do* besides steer: the **Shed** ability button,
 * pinned in the board's bottom corner.
 *
 * It is drawn rather than assembled from Material parts so it belongs to the
 * board: a charge ring that fills as the player eats (the same visual language as
 * the HUD's growth ring), a glassy token body, and a hand-drawn glyph of a tail
 * being cut loose. While charging it is quiet and unclickable - a stray tap
 * during play must cost nothing, and with tap-to-turn steering it has to let the
 * touch through to the board. The moment it fills it lights up, breathes, and
 * throws a soft halo, so the escape valve announces itself without a word.
 */
@Composable
fun AbilityButton(
    charge: Float,
    ready: Boolean,
    reduceMotion: Boolean,
    onUse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = SpecialVisuals.ShedColor
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed && ready) 0.90f else 1f,
        label = "shedPress",
    )
    // The ring eases toward the new charge instead of stepping per bite.
    val fill by animateFloatAsState(
        targetValue = charge.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 260, easing = LinearEasing),
        label = "shedCharge",
    )
    // Ready: a slow breath plus a one-shot pop on the tick it fills.
    val breathTransition = rememberInfiniteTransition(label = "shedBreath")
    val breath by breathTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shedBreathValue",
    )
    val pulse = if (!ready || reduceMotion) 0f else breath
    val pop = remember { Animatable(1f) }
    LaunchedEffect(ready) {
        if (ready && !reduceMotion) {
            pop.snapTo(1.35f)
            pop.animateTo(1f, spring(dampingRatio = 0.4f))
        }
    }

    val description = if (ready) {
        stringResource(R.string.ability_shed_ready)
    } else {
        stringResource(R.string.ability_shed_charging, (fill * 100).roundToInt())
    }

    Box(
        modifier = modifier
            .size(ButtonSize)
            .graphicsLayer {
                val s = press * pop.value
                scaleX = s
                scaleY = s
                alpha = if (ready) 1f else 0.55f
            }
            // Only clickable once charged, so an accidental tap while it is still
            // filling reaches the board (tap-to-turn) instead of being swallowed.
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = accent, bounded = false),
                enabled = ready,
                role = Role.Button,
                onClick = onUse,
            )
            .semantics { contentDescription = description },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawShedToken(accent = accent, fill = fill, ready = ready, pulse = pulse)
        }
    }
}

private val ButtonSize = 64.dp

/**
 * The token: halo, charge ring, body, bevel and the tail-cut glyph. Kept as one
 * DrawScope function so the whole button is a single canvas pass - and shared with
 * the onboarding tour, which teaches the ability with the real renderer rather
 * than a lookalike.
 */
internal fun DrawScope.drawShedToken(accent: Color, fill: Float, ready: Boolean, pulse: Float) {
    val r = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val ringWidth = r * 0.13f
    val bodyRadius = r - ringWidth * 1.9f

    // Halo: only once charged, breathing with the pulse.
    if (ready) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.34f + 0.16f * pulse), Color.Transparent),
                center = center,
                radius = r * (1.05f + 0.12f * pulse),
            ),
            radius = r * (1.05f + 0.12f * pulse),
            center = center,
        )
    }

    // Grounding disc, so the token reads over any terrain.
    drawCircle(color = Color(0xFF0A0E10).copy(alpha = 0.78f), radius = bodyRadius, center = center)
    // Body: top-lit glass, brighter and tinted once ready.
    drawCircle(
        brush = Brush.verticalGradient(
            colors = if (ready) {
                listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.16f))
            } else {
                listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.02f))
            },
            startY = center.y - bodyRadius,
            endY = center.y + bodyRadius,
        ),
        radius = bodyRadius,
        center = center,
    )
    // Bevel: a bright top arc and a soft bottom shadow give the token depth.
    drawArc(
        color = Color.White.copy(alpha = if (ready) 0.45f else 0.20f),
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(center.x - bodyRadius, center.y - bodyRadius),
        size = Size(bodyRadius * 2f, bodyRadius * 2f),
        style = Stroke(width = r * 0.055f, cap = StrokeCap.Round),
    )

    // Charge ring: an unlit track with the earned arc drawn over it, starting at
    // twelve o'clock and running clockwise like a stopwatch.
    val ringRadius = r - ringWidth / 2f
    drawCircle(
        color = Color.White.copy(alpha = 0.14f),
        radius = ringRadius,
        center = center,
        style = Stroke(width = ringWidth),
    )
    if (fill > 0.001f) {
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(accent.copy(alpha = 0.75f), accent, accent.copy(alpha = 0.75f)),
                center = center,
            ),
            startAngle = -90f,
            sweepAngle = 360f * fill,
            useCenter = false,
            topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
            size = Size(ringRadius * 2f, ringRadius * 2f),
            style = Stroke(width = ringWidth, cap = StrokeCap.Round),
        )
    }

    drawShedGlyph(center = center, radius = bodyRadius, accent = accent, ready = ready, pulse = pulse)
}

/**
 * The glyph: a tapering three-piece tail crossed by a cut, with two sparks flying
 * off the severed end. It says "cut this loose" in the game's own vocabulary -
 * the pieces are drawn as the snake's own rounded segments.
 */
private fun DrawScope.drawShedGlyph(
    center: Offset,
    radius: Float,
    accent: Color,
    ready: Boolean,
    pulse: Float,
) {
    val ink = if (ready) Color.White else Color.White.copy(alpha = 0.72f)
    val unit = radius * 0.30f
    // A tail running down-right, its pieces shrinking toward the tip.
    rotate(degrees = -28f, pivot = center) {
        val start = Offset(center.x - unit * 1.5f, center.y - unit * 0.9f)
        val sizes = floatArrayOf(1f, 0.78f, 0.54f)
        sizes.forEachIndexed { i, scale ->
            val side = unit * scale
            val cx = start.x + unit * 1.15f * i
            val cy = start.y + unit * 0.95f * i
            drawRoundRect(
                color = ink.copy(alpha = 1f - i * 0.22f),
                topLeft = Offset(cx - side / 2f, cy - side / 2f),
                size = Size(side, side),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(side * 0.34f),
            )
        }
    }

    // The cut: a bright slash across the tail, with a gap at the crossing so the
    // two halves read as separated rather than struck through.
    val cutLen = radius * 1.55f
    val angle = Math.toRadians(34.0)
    val dx = cos(angle).toFloat() * cutLen / 2f
    val dy = sin(angle).toFloat() * cutLen / 2f
    val cutColor = if (ready) accent.copy(alpha = 0.95f + 0.05f * pulse) else accent.copy(alpha = 0.6f)
    val path = Path().apply {
        moveTo(center.x - dx, center.y + dy)
        lineTo(center.x + dx, center.y - dy)
    }
    drawPath(
        path = path,
        color = cutColor,
        style = Stroke(
            width = radius * 0.11f,
            cap = StrokeCap.Round,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                floatArrayOf(radius * 0.30f, radius * 0.16f),
                0f,
            ),
        ),
    )

    // Two sparks flying off the severed tip - the release, not an impact.
    if (ready) {
        val sparkBase = Offset(center.x + dx * 0.62f, center.y - dy * 0.62f)
        drawCircle(color = accent, radius = radius * (0.055f + 0.02f * pulse), center = sparkBase)
        drawCircle(
            color = accent.copy(alpha = 0.6f),
            radius = radius * 0.04f,
            center = Offset(sparkBase.x + radius * 0.16f, sparkBase.y - radius * 0.20f),
        )
    }
}

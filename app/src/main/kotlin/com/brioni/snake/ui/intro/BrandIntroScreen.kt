package com.brioni.snake.ui.intro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.brioni.snake.R
import com.brioni.snake.ui.theme.Orbitron
import kotlinx.coroutines.delay
import kotlin.math.min

// Visual interval the intro is on screen before it auto-advances to the menu.
// Tuned so the wordmark settles for a beat after the entrance sweep (~1.4s) and
// holds before fading out, without overstaying its welcome (it's tap-to-skip).
private const val INTRO_DURATION_MS = 3200L

// 80s neon (left half) vs. modern flat (right half) — the dual aesthetic.
private val NeonMagenta = Color(0xFFFF2D95)
private val NeonCyan = Color(0xFF00E5FF)
private val ModernLime = Color(0xFF7CFC00)

// Left-half warm "phosphor" backdrop; right-half clean board gradient.
private val RetroTop = Color(0xFF1E1407)
private val RetroBottom = Color(0xFF0C0903)
private val ModernTop = Color(0xFF141A20)
private val ModernBottom = Color(0xFF0A0E13)

private val GridAmber = Color(0xFFFFB000)
private val Scanline = Color(0xFF000000)

/**
 * The first thing the player sees on a cold launch: a short, skippable brand
 * intro that splits the "SNAKE" wordmark down the middle — the **left half** in
 * an 80s arcade style (neon gradient, CRT scanlines, a synthwave grid) and the
 * **right half** in a clean modern flat style — to signal the game's roots.
 *
 * A light sweep crosses the wordmark on entrance. The screen auto-advances after
 * [INTRO_DURATION_MS] and a tap skips it immediately; either way it calls
 * [onFinished] exactly once. All effects are drawn procedurally on a [Canvas]
 * (no shaders), so it renders identically on every supported API level.
 */
@Composable
fun BrandIntroScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val title = stringResource(R.string.game_title)
    val tagline = stringResource(R.string.intro_tagline)

    // Fire onFinished once, whether by tap or by the auto-advance timer.
    var done by remember { mutableStateOf(false) }
    val latestOnFinished by rememberUpdatedState(onFinished)
    val finish = remember {
        {
            if (!done) {
                done = true
                latestOnFinished()
            }
        }
    }

    // Entrance: the wordmark scales up and fades in.
    val entrance = remember { Animatable(0f) }
    // Sweep: a highlight bar travels left→right across the wordmark once.
    val sweep = remember { Animatable(-0.15f) }
    // A gentle, never-ending breathing pulse to keep the logo alive.
    val pulseTransition = rememberInfiniteTransition(label = "introPulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.99f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "introPulseScale",
    )

    LaunchedEffectsForIntro(entrance, sweep, finish)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { finish() } },
    ) {
        val w = size.width
        val h = size.height
        val mid = w / 2f

        drawBackdrops(mid)
        clipRect(right = mid) {
            drawSynthwaveGrid(mid)
            drawScanlines(mid)
        }

        // Measure the wordmark once per style. The neon variant carries a cyan
        // glow; the modern variant a soft lime glow. Both share the same top-left
        // so the two halves line up across the seam.
        val base = w * 0.205f
        val neon = measurer.measure(
            text = title,
            style = TextStyle(
                fontFamily = Orbitron,
                fontWeight = FontWeight.Bold,
                fontSize = base.toSp(),
                letterSpacing = (base * 0.04f).toSp(),
                brush = Brush.horizontalGradient(listOf(NeonMagenta, NeonCyan)),
                shadow = Shadow(NeonCyan, Offset.Zero, base * 0.22f),
            ),
        )
        val modern = measurer.measure(
            text = title,
            style = TextStyle(
                fontFamily = Orbitron,
                fontWeight = FontWeight.Bold,
                fontSize = base.toSp(),
                letterSpacing = (base * 0.04f).toSp(),
                color = ModernLime,
                shadow = Shadow(ModernLime.copy(alpha = 0.45f), Offset.Zero, base * 0.10f),
            ),
        )
        val tw = neon.size.width.toFloat()
        val th = neon.size.height.toFloat()
        val fit = min(1f, (w * 0.84f) / tw)
        val alpha = entrance.value
        val s = fit * (0.88f + 0.12f * entrance.value) * pulse
        val left = (w - tw) / 2f
        val top = (h - th) / 2f - h * 0.03f

        // Subtitle, centred under the wordmark in dim modern lettering.
        val subSize = w * 0.034f
        val sub = measurer.measure(
            text = tagline,
            style = TextStyle(
                fontFamily = Orbitron,
                fontWeight = FontWeight.Normal,
                fontSize = subSize.toSp(),
                letterSpacing = (subSize * 0.18f).toSp(),
                color = Color(0xFFECEFF1),
            ),
        )

        scale(s, s, pivot = Offset(w / 2f, h / 2f)) {
            clipRect(right = mid) { drawText(neon, topLeft = Offset(left, top), alpha = alpha) }
            clipRect(left = mid) { drawText(modern, topLeft = Offset(left, top), alpha = alpha) }
            drawText(
                textLayoutResult = sub,
                topLeft = Offset((w - sub.size.width) / 2f, top + th + h * 0.012f),
                alpha = alpha * 0.7f,
            )
        }

        // Subtle seam where the two eras meet.
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, NeonCyan.copy(alpha = 0.18f * alpha), Color.Transparent),
            ),
            topLeft = Offset(mid - 1f, 0f),
            size = Size(2f, h),
        )

        // The entrance sweep, drawn over everything.
        val sx = sweep.value * w
        if (sx in -w..(2f * w)) {
            val bw = w * 0.09f
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.16f * alpha),
                    1f to Color.Transparent,
                    startX = sx - bw,
                    endX = sx + bw,
                ),
                topLeft = Offset(sx - bw, 0f),
                size = Size(bw * 2f, h),
            )
        }
    }
}

/** Two-tone full-screen backdrop: warm 80s on the left, clean modern on the right. */
private fun DrawScope.drawBackdrops(mid: Float) {
    clipRect(right = mid) {
        drawRect(Brush.verticalGradient(listOf(RetroTop, RetroBottom)))
    }
    clipRect(left = mid) {
        drawRect(Brush.verticalGradient(listOf(ModernTop, ModernBottom)))
    }
}

/** A faint synthwave grid receding to a vanishing point in the lower-left. */
private fun DrawScope.drawSynthwaveGrid(mid: Float) {
    val h = size.height
    val horizonY = h * 0.60f
    val vp = Offset(mid * 0.42f, horizonY)
    val grid = GridAmber.copy(alpha = 0.18f)

    val verticals = 9
    for (k in 0..verticals) {
        val bx = (k / verticals.toFloat()) * (mid * 1.2f) - mid * 0.1f
        drawLine(grid, vp, Offset(bx, h), strokeWidth = 1.2f)
    }
    val rows = 7
    for (r in 1..rows) {
        val t = r / rows.toFloat()
        val y = horizonY + (h - horizonY) * (t * t)
        drawLine(grid, Offset(0f, y), Offset(mid, y), strokeWidth = 1.2f)
    }
}

/** Low-alpha CRT scanlines across the 80s half. */
private fun DrawScope.drawScanlines(mid: Float) {
    val h = size.height
    val gap = 5.dp.toPx()
    val line = Scanline.copy(alpha = 0.13f)
    var y = 0f
    while (y < h) {
        drawRect(line, topLeft = Offset(0f, y), size = Size(mid, gap * 0.5f))
        y += gap
    }
}

/** Hosts the one-shot launch animations + the auto-advance timer. */
@Composable
private fun LaunchedEffectsForIntro(
    entrance: Animatable<Float, AnimationVector1D>,
    sweep: Animatable<Float, AnimationVector1D>,
    finish: () -> Unit,
) {
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        delay(280)
        sweep.animateTo(1.15f, tween(durationMillis = 1100, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        delay(INTRO_DURATION_MS)
        finish()
    }
}

package com.brioni.snake.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import com.brioni.snake.game.FoodEffect
import com.brioni.snake.game.isHazard
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The premium power-up / hazard **token** and the on-disc symbols that identify
 * each Phase 6.2 special.
 *
 * [drawSpecialToken] is the single source of truth for the whole piece - the
 * material-specific frame ([SkinPalette.specialStyle]) plus the embossed glyph -
 * shared by the in-game board renderer ([GameBoard]) and the first-run tutorial
 * ([com.brioni.snake.ui.onboarding]), so a token looks identical wherever a
 * player meets it. The effect's identity accent (from [SpecialVisuals]) is
 * constant across skins; only the frame's *look* changes per skin.
 *
 * The glyphs ([drawSpecialSymbol]) draw in the local coordinate space centred at
 * ([cx], [cy]) with a nominal radius [r], in a single [color], so the token can
 * emboss them by drawing a dark drop under a light face. [drawGlyph] is the
 * shared text-glyph helper (the "?" mystery food and the "$" jackpot).
 */

/** Universal danger red for the hazard token bezel (skin-independent). */
private val HazardTokenColor = Color(0xFFFF2A1E)

/**
 * Draws a complete premium special token centred at ([cx], [cy]) with token
 * radius [radius], in the current [palette]'s material. Layers: a depth cue
 * (glow on glow skins / drop shadow on flat skins), a material body fill, a
 * material rim/bevel, a discreet notched danger bezel for hazards (pulsing with
 * [pulse]) and finally the effect's embossed glyph.
 */
internal fun DrawScope.drawSpecialToken(
    cx: Float,
    cy: Float,
    radius: Float,
    effect: FoodEffect,
    palette: SkinPalette,
    textMeasurer: TextMeasurer,
    pulse: Float = 1f,
) {
    val accent = SpecialVisuals.accent(effect)
    val hazard = effect.isHazard
    val style = palette.specialStyle
    val corner = style.tokenCorner
    val haz = HazardTokenColor
    val center = Offset(cx, cy)

    fun tokenPath(centerX: Float, centerY: Float, rr: Float): Path {
        val p = Path()
        if (corner == null) {
            p.addOval(Rect(centerX - rr, centerY - rr, centerX + rr, centerY + rr))
        } else {
            val cr = rr * corner
            p.addRoundRect(RoundRect(centerX - rr, centerY - rr, centerX + rr, centerY + rr, cr, cr))
        }
        return p
    }

    val body = tokenPath(cx, cy, radius)

    // 1) Depth: a soft accent/danger glow on glow skins, a drop shadow on flat skins.
    if (palette.useGlow) {
        val hc = if (hazard) haz else accent
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(hc.copy(alpha = 0.34f), Color.Transparent),
                center = center,
                radius = radius * 1.7f,
            ),
            radius = radius * 1.7f,
            center = center,
        )
    } else {
        drawPath(
            tokenPath(cx + radius * 0.07f, cy + radius * 0.11f, radius),
            color = Color.Black.copy(alpha = 0.30f),
        )
    }

    // 2) Body fill: material-specific gradient.
    val bodyBrush = when (style) {
        SpecialStyle.Neon -> Brush.radialGradient(
            colors = listOf(darken(accent, 0.6f).copy(alpha = 0.92f), Color(0xFF05070C).copy(alpha = 0.95f)),
            center = center,
            radius = radius,
        )
        SpecialStyle.Glass -> Brush.verticalGradient(
            colors = listOf(lighten(accent, 0.35f).copy(alpha = 0.85f), darken(accent, 0.15f).copy(alpha = 0.80f)),
            startY = cy - radius,
            endY = cy + radius,
        )
        else -> Brush.verticalGradient(
            colors = listOf(lighten(accent, 0.24f), darken(accent, 0.24f)),
            startY = cy - radius,
            endY = cy + radius,
        )
    }
    drawPath(body, brush = bodyBrush)

    // 3) Rim / bevel, per material.
    when (style) {
        SpecialStyle.Neon -> {
            drawPath(body, color = lighten(accent, 0.4f).copy(alpha = 0.95f), style = Stroke(width = radius * 0.09f))
            drawPath(body, color = accent.copy(alpha = 0.35f), style = Stroke(width = radius * 0.20f))
        }
        SpecialStyle.Pixel -> {
            drawPath(body, color = darken(accent, 0.4f), style = Stroke(width = radius * 0.12f))
            // A single chunky corner highlight - the pixel-art "light source".
            drawRect(
                color = lighten(accent, 0.4f).copy(alpha = 0.7f),
                topLeft = Offset(cx - radius * 0.78f, cy - radius * 0.78f),
                size = Size(radius * 0.42f, radius * 0.13f),
            )
        }
        SpecialStyle.Ember ->
            drawPath(body, color = darken(accent, 0.55f), style = Stroke(width = radius * 0.13f))
        else -> {
            drawPath(body, color = darken(accent, 0.42f).copy(alpha = 0.85f), style = Stroke(width = radius * 0.09f))
            // A faint top rim light, clipped to the token so it hugs the upper edge.
            clipPath(body) {
                drawPath(
                    tokenPath(cx, cy - radius * 0.03f, radius),
                    color = Color.White.copy(alpha = 0.40f),
                    style = Stroke(width = radius * 0.05f),
                )
            }
        }
    }

    // 4) Hazard bezel: a discreet notched red ring (+ a faint aura on glow skins),
    //    the steady counterpart to the eat-imminent telegraph. Pulses with [pulse].
    if (hazard) {
        val rr = radius * 1.2f
        if (palette.useGlow) {
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to haz.copy(alpha = 0f),
                    0.75f to haz.copy(alpha = 0.25f * pulse),
                    1.0f to haz.copy(alpha = 0f),
                    center = center,
                    radius = rr * 1.18f,
                ),
                radius = rr * 1.18f,
                center = center,
            )
        }
        val notches = 10
        val step = 360f / notches
        val sweep = step * 0.5f
        val startOffset = 6.9f // ~0.12 rad, so the gaps do not align with the axes
        val ringSize = Size(rr * 2f, rr * 2f)
        val ringTopLeft = Offset(cx - rr, cy - rr)
        val ringColor = haz.copy(alpha = pulse.coerceIn(0f, 1f))
        for (i in 0 until notches) {
            drawArc(
                color = ringColor,
                startAngle = i * step + startOffset,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = radius * 0.12f),
            )
        }
    }

    // 5) The effect glyph, embossed: a dark drop shadow under a light/ink face.
    val gr = radius * 0.58f
    val face = when {
        hazard -> Color(0xFFFFF2EF)
        style == SpecialStyle.Neon -> lighten(accent, 0.5f)
        style == SpecialStyle.Ember -> Color(0xFFFFF1D6)
        else -> Color(0xFF12181F)
    }
    drawSpecialSymbol(effect, cx, cy + gr * 0.05f, gr, Color.Black.copy(alpha = 0.28f), textMeasurer)
    drawSpecialSymbol(effect, cx, cy, gr, face, textMeasurer)
}

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
        is FoodEffect.Freeze -> drawCrystal(cx, cy, r, color)
        is FoodEffect.Jackpot -> drawGlyph(textMeasurer, "$", cx, cy, r * 1.7f, color)
        is FoodEffect.Burst -> drawStarShape(cx, cy, r, points = 8, innerRatio = 0.42f, color = color)
        is FoodEffect.Quake -> drawCrack(cx, cy, r, color)
        is FoodEffect.TimeBonus -> drawClock(cx, cy, r, color)
        is FoodEffect.TimePenalty -> drawClock(cx, cy, r, color)
        is FoodEffect.ExtraLife -> drawHeart(cx, cy, r, color)
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

/** A heart - the extra-life bonus icon. */
internal fun DrawScope.drawHeart(cx: Float, cy: Float, r: Float, color: Color) {
    val s = r * 1.05f
    val path = Path().apply {
        moveTo(cx, cy + s * 0.72f)
        cubicTo(cx - s * 1.3f, cy - s * 0.10f, cx - s * 0.55f, cy - s * 0.95f, cx, cy - s * 0.35f)
        cubicTo(cx + s * 0.55f, cy - s * 0.95f, cx + s * 1.3f, cy - s * 0.10f, cx, cy + s * 0.72f)
        close()
    }
    drawPath(path, color)
}

/**
 * A cut ice crystal / gem (the Freeze power-up): a faceted diamond outline with a
 * girdle and crown/pavilion ridges. Drawn monochrome (stroked) so the token can
 * emboss it, and reads as "ice/cold" through its faceted-gem silhouette.
 */
internal fun DrawScope.drawCrystal(cx: Float, cy: Float, r: Float, color: Color) {
    val hw = r * 0.60f
    val shoulderY = cy - r * 0.30f
    val top = Offset(cx, cy - r)
    val right = Offset(cx + hw, shoulderY)
    val bottom = Offset(cx, cy + r)
    val left = Offset(cx - hw, shoulderY)
    val stroke = r * 0.13f
    val outline = Path().apply {
        moveTo(top.x, top.y)
        lineTo(right.x, right.y)
        lineTo(bottom.x, bottom.y)
        lineTo(left.x, left.y)
        close()
    }
    drawPath(outline, color, style = Stroke(width = stroke, join = StrokeJoin.Round))
    // Internal facets: the girdle across the shoulders, plus the ridges to the tip.
    val facet = stroke * 0.7f
    drawLine(color, left, right, strokeWidth = facet, cap = StrokeCap.Round)
    drawLine(color, top, bottom, strokeWidth = facet, cap = StrokeCap.Round)
    drawLine(color, left, bottom, strokeWidth = facet, cap = StrokeCap.Round)
    drawLine(color, right, bottom, strokeWidth = facet, cap = StrokeCap.Round)
}

/**
 * A clock face - the Time Attack bonus / penalty blocks. The gain-vs-lose meaning
 * is carried entirely by the block's green/red accent colour, so no +/- badge is drawn.
 */
internal fun DrawScope.drawClock(cx: Float, cy: Float, r: Float, color: Color) {
    val ring = r * 0.74f
    drawCircle(color, ring, Offset(cx, cy), style = Stroke(width = r * 0.16f))
    // Hands: one up, one to the right.
    drawLine(color, Offset(cx, cy), Offset(cx, cy - ring * 0.58f), strokeWidth = r * 0.15f, cap = StrokeCap.Round)
    drawLine(color, Offset(cx, cy), Offset(cx + ring * 0.44f, cy), strokeWidth = r * 0.15f, cap = StrokeCap.Round)
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

/** Mixes [c] toward white by [f] (0..1), preserving alpha - for highlights/sheen. */
private fun lighten(c: Color, f: Float): Color = Color(
    red = c.red + (1f - c.red) * f,
    green = c.green + (1f - c.green) * f,
    blue = c.blue + (1f - c.blue) * f,
    alpha = c.alpha,
)

/** Darkens [c] toward black by fraction [f] (0 = unchanged, 1 = black). */
private fun darken(c: Color, f: Float): Color = Color(
    red = c.red * (1f - f),
    green = c.green * (1f - f),
    blue = c.blue * (1f - f),
    alpha = c.alpha,
)

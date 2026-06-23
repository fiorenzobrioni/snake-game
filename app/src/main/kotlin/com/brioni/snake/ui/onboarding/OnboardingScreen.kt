package com.brioni.snake.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.brioni.snake.R
import com.brioni.snake.data.Settings
import com.brioni.snake.data.SettingsRepository
import com.brioni.snake.game.BoardScale
import com.brioni.snake.game.ControlScheme
import com.brioni.snake.game.FoodEffect
import com.brioni.snake.game.Level
import com.brioni.snake.game.isHazard
import com.brioni.snake.ui.AnimatedShaderBackground
import com.brioni.snake.ui.components.SnakeButton
import com.brioni.snake.ui.game.SkinPalette
import com.brioni.snake.ui.game.SpecialVisuals
import com.brioni.snake.ui.game.drawGlyph
import com.brioni.snake.ui.game.drawSpecialSymbol
import com.brioni.snake.ui.game.paletteFor
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * First-run tutorial (Step 6.9.16, redesigned): a polished, skippable 4-card pager
 * that introduces the objective, the controls, the food types and the power-ups /
 * hazards, so a new player is not dropped cold. Shown once on first launch (gated by
 * [SettingsRepository.onboardingCompleted]) and re-openable from Settings.
 *
 * The redesign aims for a premium, *dark and minimal* feel that matches the cold-launch
 * brand intro and the live menus: the whole screen sits on the animated AGSL backdrop
 * ([AnimatedShaderBackground]) - drifting glows over a near-black gradient - and each
 * card's artwork lives in a framed "glass" mini-board so the look reads as one family
 * with the game. Every card scrolls vertically, so the richer copy and the legends
 * never clip on short screens.
 *
 * Each card pairs a hand-drawn Canvas illustration (skin-coloured pieces on a neutral
 * dark board) with an Orbitron title and a fuller body, plus detailed legend rows on
 * the controls / food / specials cards. The specials card draws the real in-game
 * power-up / hazard discs (shared [drawSpecialSymbol]), so a colour and a symbol always
 * mean the same thing in the tutorial as in play. Animation stays restrained: a subtle
 * parallax on the artwork as pages slide and an animated page indicator; the
 * screen-level blur-dissolve from the App shell covers entry and exit. [onFinished] is
 * invoked once, on the final "Get started", on Skip, or on system-back.
 */
@Composable
fun OnboardingScreen(
    repo: SettingsRepository,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by repo.settings.collectAsState(
        initial = Settings(Level.Beginner, BoardScale.Classic, ControlScheme.Swipe),
    )
    val palette = remember(settings.skin) { paletteFor(settings.skin) }
    val pageCount = 4
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == pageCount - 1
    val textMeasurer = rememberTextMeasurer()

    // System-back acts as "skip" - the player has seen enough.
    BackHandler { onFinished() }

    Box(modifier = modifier.fillMaxSize()) {
        // A self-contained dark, minimal backdrop so the tutorial always reads on the
        // brand surface - the same drifting-glows shader the intro and menus use -
        // regardless of the active theme.
        AnimatedShaderBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar: a low-emphasis Skip, hidden on the last page (where the primary
            // button already finishes).
            Box(modifier = Modifier.fillMaxWidth().height(44.dp)) {
                if (!lastPage) {
                    TextButton(
                        onClick = onFinished,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_skip),
                            style = MaterialTheme.typography.titleSmall,
                            color = BodyDim,
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { page ->
                // Parallax: how far this page is from the settled position (-1..1 while
                // dragging), used to drift and fade the artwork for a touch of depth.
                val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                OnboardingCard(page = page, palette = palette, parallax = offset, textMeasurer = textMeasurer)
            }

            PageIndicator(
                pageCount = pageCount,
                currentPage = pagerState.currentPage,
                activeColor = palette.snakeHead,
                inactiveColor = Color.White.copy(alpha = 0.20f),
                modifier = Modifier.padding(vertical = 18.dp),
            )

            SnakeButton(
                onClick = {
                    if (lastPage) onFinished()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Text(
                    text = stringResource(if (lastPage) R.string.onboarding_done else R.string.onboarding_next),
                )
            }
        }
    }
}

/**
 * One tutorial card: a framed illustration over a vertically scrollable column of
 * title, body and (per page) a detailed legend. Scrolling guarantees the richer copy
 * never clips on short screens.
 */
@Composable
private fun OnboardingCard(page: Int, palette: SkinPalette, parallax: Float, textMeasurer: TextMeasurer) {
    val (titleRes, bodyRes) = when (page) {
        0 -> R.string.onboarding_objective_title to R.string.onboarding_objective_body
        1 -> R.string.onboarding_controls_title to R.string.onboarding_controls_body
        2 -> R.string.onboarding_food_title to R.string.onboarding_food_body
        else -> R.string.onboarding_specials_title to R.string.onboarding_specials_body
    }
    val titleColor = palette.snakeHead

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // A little breathing room so the artwork is never flush against the top bar.
        Spacer(Modifier.height(8.dp))

        // The framed mini-board illustration. The parallax fades/drifts it subtly so
        // the page change reads with a little depth without being busy.
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(1.4f)
                .graphicsLayer {
                    val k = (1f - abs(parallax)).coerceIn(0f, 1f)
                    translationX = parallax * 70f
                    alpha = 0.35f + 0.65f * k
                    scaleX = 0.92f + 0.08f * k
                    scaleY = 0.92f + 0.08f * k
                }
                .clip(RoundedCornerShape(24.dp)),
        ) {
            drawMinimalBoard(palette)
            when (page) {
                0 -> drawObjectiveArt(palette, textMeasurer)
                1 -> drawControlsArt(palette)
                2 -> drawFoodArt(palette, textMeasurer)
                else -> drawSpecialsArt(palette, textMeasurer)
            }
        }

        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = titleColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 26.dp, start = 8.dp, end = 8.dp),
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = BodyText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp),
        )

        when (page) {
            1 -> ControlsLegend(palette, modifier = Modifier.padding(top = 20.dp))
            2 -> FoodLegend(palette, textMeasurer, modifier = Modifier.padding(top = 20.dp))
            3 -> SpecialsLegend(palette, textMeasurer, modifier = Modifier.padding(top = 20.dp))
        }

        Spacer(Modifier.height(8.dp))
    }
}

// --- Legends -------------------------------------------------------------------

/** The three control schemes, each a glyph badge over a name and a one-line how-to. */
@Composable
private fun ControlsLegend(palette: SkinPalette, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InfoRow(
            title = stringResource(R.string.onboarding_controls_swipe),
            description = stringResource(R.string.onboarding_controls_swipe_desc),
            titleColor = palette.snakeHead,
        ) { ControlBadge(ControlGlyph.Swipe, palette.snakeHead) }
        InfoRow(
            title = stringResource(R.string.onboarding_controls_tap),
            description = stringResource(R.string.onboarding_controls_tap_desc),
            titleColor = palette.snakeHead,
        ) { ControlBadge(ControlGlyph.Tap, palette.snakeHead) }
        InfoRow(
            title = stringResource(R.string.onboarding_controls_dpad),
            description = stringResource(R.string.onboarding_controls_dpad_desc),
            titleColor = palette.snakeHead,
        ) { ControlBadge(ControlGlyph.Dpad, palette.snakeHead) }
    }
}

/** Grow / shrink / mystery food, each a real food piece over a name and a description. */
@Composable
private fun FoodLegend(palette: SkinPalette, textMeasurer: TextMeasurer, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InfoRow(
            title = stringResource(R.string.onboarding_food_grow),
            description = stringResource(R.string.onboarding_food_grow_desc),
            titleColor = palette.growMedium,
        ) { FoodBadge(palette.growMedium, palette, textMeasurer) }
        InfoRow(
            title = stringResource(R.string.onboarding_food_shrink),
            description = stringResource(R.string.onboarding_food_shrink_desc),
            titleColor = palette.shrinkMedium,
        ) { FoodBadge(palette.shrinkMedium, palette, textMeasurer) }
        InfoRow(
            title = stringResource(R.string.onboarding_food_mystery),
            description = stringResource(R.string.onboarding_food_mystery_desc),
            titleColor = palette.growMystery,
        ) { FoodBadge(palette.growMystery, palette, textMeasurer, mystery = true) }
    }
}

/** Power-ups then hazards, each a real special disc over a name and a description. */
@Composable
private fun SpecialsLegend(palette: SkinPalette, textMeasurer: TextMeasurer, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LegendHeader(stringResource(R.string.onboarding_powerups), palette.snakeHead)
        SpecialRow(FoodEffect.Haste(0L), stringResource(R.string.effect_lightning), stringResource(R.string.onboarding_lightning_desc), palette, textMeasurer)
        SpecialRow(FoodEffect.Ghost(0L), stringResource(R.string.effect_star), stringResource(R.string.onboarding_star_desc), palette, textMeasurer)
        SpecialRow(FoodEffect.Freeze(0L), stringResource(R.string.effect_freeze), stringResource(R.string.onboarding_freeze_desc), palette, textMeasurer)
        SpecialRow(FoodEffect.Jackpot(0, 0), stringResource(R.string.onboarding_jackpot), stringResource(R.string.onboarding_jackpot_desc), palette, textMeasurer)

        Spacer(Modifier.height(4.dp))
        LegendHeader(stringResource(R.string.onboarding_hazards), HazardRing)
        SpecialRow(FoodEffect.Quake(0L), stringResource(R.string.effect_quake), stringResource(R.string.onboarding_quake_desc), palette, textMeasurer)
        SpecialRow(FoodEffect.Burst(0L), stringResource(R.string.onboarding_explosion), stringResource(R.string.onboarding_explosion_desc), palette, textMeasurer)
        SpecialRow(FoodEffect.Slow(0L), stringResource(R.string.effect_snail), stringResource(R.string.onboarding_snail_desc), palette, textMeasurer)
    }
}

/** A small section header above a group of legend rows. */
@Composable
private fun LegendHeader(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.fillMaxWidth().padding(start = 2.dp, bottom = 2.dp),
    )
}

/** A special-effect legend row: the real in-game disc over its name and description. */
@Composable
private fun SpecialRow(
    effect: FoodEffect,
    title: String,
    description: String,
    palette: SkinPalette,
    textMeasurer: TextMeasurer,
) {
    InfoRow(
        title = title,
        description = description,
        titleColor = SpecialVisuals.accent(effect),
    ) { SpecialBadge(effect, palette, textMeasurer) }
}

/**
 * A legend row: a fixed-size badge (drawn by [badge]) on the left, a bold accented
 * [title] over a dimmer [description] on the right. The shared shape keeps the
 * controls / food / specials lists visually consistent.
 */
@Composable
private fun InfoRow(
    title: String,
    description: String,
    titleColor: Color,
    modifier: Modifier = Modifier,
    badge: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) { badge() }
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = titleColor,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = BodyDim,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// --- Badges --------------------------------------------------------------------

/** A food piece badge: a haloed disc (glow skins) or a rounded square (flat skins). */
@Composable
private fun FoodBadge(
    color: Color,
    palette: SkinPalette,
    textMeasurer: TextMeasurer,
    mystery: Boolean = false,
) {
    Canvas(modifier = Modifier.size(40.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension * 0.34f
        drawFood(cx, cy, r, color, palette)
        if (mystery) drawGlyph(textMeasurer, "?", cx, cy, r * 1.25f, Color.White)
    }
}

/** A special-piece badge mirroring the in-game disc + symbol (+ caution ring for hazards). */
@Composable
private fun SpecialBadge(effect: FoodEffect, palette: SkinPalette, textMeasurer: TextMeasurer) {
    val accent = SpecialVisuals.accent(effect)
    Canvas(modifier = Modifier.size(40.dp)) {
        drawSpecialDisc(accent, palette, hazard = effect.isHazard)
        drawSpecialSymbol(effect, size.width / 2f, size.height / 2f, size.minDimension * 0.30f, SpecialInk, textMeasurer)
    }
}

/** Which scheme a [ControlBadge] illustrates. */
private enum class ControlGlyph { Swipe, Tap, Dpad }

/** A control-scheme badge: a faint disc with a scheme glyph in [accent]. */
@Composable
private fun ControlBadge(glyph: ControlGlyph, accent: Color) {
    Canvas(modifier = Modifier.size(40.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension * 0.42f
        drawCircle(accent.copy(alpha = 0.12f), radius = r, center = Offset(cx, cy))
        drawCircle(accent.copy(alpha = 0.35f), radius = r, center = Offset(cx, cy), style = Stroke(width = r * 0.10f))
        when (glyph) {
            ControlGlyph.Swipe -> drawSwipeGlyph(cx, cy, r * 0.62f, accent)
            ControlGlyph.Tap -> drawTapGlyph(cx, cy, r * 0.66f, accent)
            ControlGlyph.Dpad -> drawDpadGlyph(cx, cy, r * 0.64f, accent)
        }
    }
}

// --- Canvas artwork ------------------------------------------------------------

/** Body text colours, fixed light so they always read on the dark backdrop. */
private val BodyText = Color(0xFFD7DEE6)
private val BodyDim = Color(0xFF9AA4B0)

/** Near-black ink for the symbol drawn on a special's bright disc. */
private val SpecialInk = Color(0xFF10151C)

/** A warm red used for the hazard "caution" rings, mirroring the in-game telegraph. */
private val HazardRing = Color(0xFFE5564B)

/** Neutral dark board colours - intentionally minimal, independent of the skin's board. */
private val BoardTopDark = Color(0xFF121A22)
private val BoardBottomDark = Color(0xFF0A0E13)
private val BoardGrid = Color(0x0DFFFFFF)

/**
 * The framed, dark, minimal mini-board every illustration sits on. Keeps the tutorial
 * artwork dark and uncluttered (a faint grid, a soft accent rim and a corner-darkening
 * vignette) so the bright skin-coloured pieces pop, matching the intro / menu mood
 * rather than each skin's own (sometimes warm) board.
 */
private fun DrawScope.drawMinimalBoard(palette: SkinPalette) {
    val r = size.minDimension * 0.10f
    val radius = CornerRadius(r, r)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(BoardTopDark, BoardBottomDark)),
        cornerRadius = radius,
    )
    val step = size.width / 12f
    var x = step
    while (x < size.width - 1f) {
        drawLine(BoardGrid, Offset(x, 0f), Offset(x, size.height), 1.2f)
        x += step
    }
    var y = step
    while (y < size.height - 1f) {
        drawLine(BoardGrid, Offset(0f, y), Offset(size.width, y), 1.2f)
        y += step
    }
    // Corner-darkening vignette for depth.
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = size.maxDimension * 0.62f,
        ),
        cornerRadius = radius,
    )
    // A soft accent rim picks up the skin without lighting the whole board.
    drawRoundRect(
        color = palette.snakeHead.copy(alpha = 0.22f),
        cornerRadius = radius,
        style = Stroke(width = size.minDimension * 0.012f),
    )
}

/** A rounded body/obstacle-style piece, shaped by the skin's corner factor. */
private fun DrawScope.drawPiece(cx: Float, cy: Float, half: Float, fill: Color, palette: SkinPalette) {
    val tl = Offset(cx - half, cy - half)
    val whole = half * 2f
    val rad = CornerRadius(whole * palette.cornerFactor, whole * palette.cornerFactor)
    drawRoundRect(fill, tl, Size(whole, whole), rad)
    drawRoundRect(palette.snakeOutline, tl, Size(whole, whole), rad, style = Stroke(width = half * 0.22f))
}

/** A food piece: a haloed disc on glow skins, a rounded square on flat skins. */
private fun DrawScope.drawFood(cx: Float, cy: Float, radius: Float, color: Color, palette: SkinPalette) {
    if (palette.useGlow) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.5f), Color.Transparent),
                center = Offset(cx, cy),
                radius = radius * 2.2f,
            ),
            radius = radius * 2.2f,
            center = Offset(cx, cy),
        )
        drawCircle(color, radius = radius, center = Offset(cx, cy))
    } else {
        drawPiece(cx, cy, radius, color, palette)
    }
}

/**
 * A static special disc, mirroring the in-game [com.brioni.snake.ui.game] rendering:
 * a haloed disc on glow skins or a rounded square on flat skins, with a dashed red
 * "caution" ring when [hazard].
 */
private fun DrawScope.drawSpecialDisc(accent: Color, palette: SkinPalette, hazard: Boolean) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = size.minDimension * 0.34f
    val center = Offset(cx, cy)
    if (palette.useGlow) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.5f), Color.Transparent),
                center = center,
                radius = radius * 2.1f,
            ),
            radius = radius * 2.1f,
            center = center,
        )
        drawCircle(color = accent, radius = radius, center = center)
        drawCircle(Color.Black.copy(alpha = 0.18f), radius = radius, center = center, style = Stroke(width = radius * 0.12f))
        if (hazard) {
            val ringRadius = radius * 1.34f
            val dash = ringRadius * 0.52f
            drawCircle(
                color = HazardRing,
                radius = ringRadius,
                center = center,
                style = Stroke(
                    width = radius * 0.16f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.55f)),
                ),
            )
        }
    } else {
        val tl = Offset(cx - radius, cy - radius)
        val sz = Size(radius * 2f, radius * 2f)
        val cr = radius * 2f * palette.cornerFactor
        val rad = CornerRadius(cr, cr)
        drawRoundRect(color = accent, topLeft = tl, size = sz, cornerRadius = rad)
        drawRoundRect(Color.Black.copy(alpha = 0.18f), tl, sz, rad, style = Stroke(width = radius * 0.12f))
        if (hazard) {
            val pad = radius * 0.3f
            val rtl = Offset(tl.x - pad, tl.y - pad)
            val rsz = Size(sz.width + 2 * pad, sz.height + 2 * pad)
            val rcr = cr + pad
            val dash = rsz.width * 0.18f
            drawRoundRect(
                color = HazardRing,
                topLeft = rtl,
                size = rsz,
                cornerRadius = CornerRadius(rcr, rcr),
                style = Stroke(
                    width = radius * 0.16f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.55f)),
                ),
            )
        }
    }
}

/** Page 0: a short snake heading toward a piece of food. */
private fun DrawScope.drawObjectiveArt(palette: SkinPalette, textMeasurer: TextMeasurer) {
    val cell = size.height / 5f
    val midY = size.height * 0.5f
    val half = cell * 0.42f
    val startX = size.width * 0.26f
    // Body segments, then the head, then food ahead.
    for (i in 0..2) {
        drawPiece(startX + i * cell, midY, half, palette.snakeBody, palette)
    }
    val headX = startX + 3 * cell
    if (palette.useGlow) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.headGlow.copy(alpha = 0.4f), Color.Transparent),
                center = Offset(headX, midY),
                radius = half * 2.4f,
            ),
            radius = half * 2.4f,
            center = Offset(headX, midY),
        )
    }
    drawPiece(headX, midY, half, palette.snakeHead, palette)
    // Eyes looking right (travel direction).
    val eyeR = half * 0.26f
    for (sign in intArrayOf(-1, 1)) {
        val ex = headX + half * 0.35f
        val ey = midY + half * 0.42f * sign
        drawCircle(Color.White, eyeR, Offset(ex, ey))
        drawCircle(palette.snakeEye, eyeR * 0.5f, Offset(ex + half * 0.08f, ey))
    }
    drawFood(headX + cell * 1.7f, midY, half * 0.92f, palette.growMedium, palette)
}

/** Page 1: a central piece with four directional arrows - "you steer". */
private fun DrawScope.drawControlsArt(palette: SkinPalette) {
    val accent = palette.snakeHead
    val cx = size.width / 2f
    val cy = size.height / 2f
    val unit = size.height / 6f
    drawPiece(cx, cy, unit * 0.5f, palette.snakeHead, palette)
    val reach = unit * 1.9f
    val a = unit * 0.5f // arrowhead half-width
    val tip = unit * 0.45f // arrowhead length
    // Up, right, down, left arrowheads pointing outward from the centre.
    drawArrow(Offset(cx, cy - reach), 0, a, tip, accent)
    drawArrow(Offset(cx + reach, cy), 1, a, tip, accent)
    drawArrow(Offset(cx, cy + reach), 2, a, tip, accent)
    drawArrow(Offset(cx - reach, cy), 3, a, tip, accent)
}

/** A filled triangular arrowhead at [tipPoint] pointing in [dir] (0=up,1=right,2=down,3=left). */
private fun DrawScope.drawArrow(tipPoint: Offset, dir: Int, halfW: Float, len: Float, color: Color) {
    val path = Path()
    when (dir) {
        0 -> { // up
            path.moveTo(tipPoint.x, tipPoint.y)
            path.lineTo(tipPoint.x - halfW, tipPoint.y + len)
            path.lineTo(tipPoint.x + halfW, tipPoint.y + len)
        }
        1 -> { // right
            path.moveTo(tipPoint.x, tipPoint.y)
            path.lineTo(tipPoint.x - len, tipPoint.y - halfW)
            path.lineTo(tipPoint.x - len, tipPoint.y + halfW)
        }
        2 -> { // down
            path.moveTo(tipPoint.x, tipPoint.y)
            path.lineTo(tipPoint.x - halfW, tipPoint.y - len)
            path.lineTo(tipPoint.x + halfW, tipPoint.y - len)
        }
        else -> { // left
            path.moveTo(tipPoint.x, tipPoint.y)
            path.lineTo(tipPoint.x + len, tipPoint.y - halfW)
            path.lineTo(tipPoint.x + len, tipPoint.y + halfW)
        }
    }
    path.close()
    drawPath(path, color)
}

/** Page 2: a row of food growing in size, a warm shrink piece and a mystery "?". */
private fun DrawScope.drawFoodArt(palette: SkinPalette, textMeasurer: TextMeasurer) {
    val midY = size.height * 0.5f
    val unit = size.width / 6f
    val items = listOf(
        Triple(unit * 0.34f, palette.growSmall, false),
        Triple(unit * 0.46f, palette.growMedium, false),
        Triple(unit * 0.60f, palette.growLarge, false),
        Triple(unit * 0.50f, palette.shrinkMedium, false),
        Triple(unit * 0.52f, palette.growMystery, true),
    )
    items.forEachIndexed { i, (r, color, mystery) ->
        val cx = unit * (1.0f + i * 1.0f)
        drawFood(cx, midY, r, color, palette)
        if (mystery) drawGlyph(textMeasurer, "?", cx, midY, r * 1.25f, Color.White)
    }
}

/** Page 3: a power-up (Lightning), a power-up (Star) and a hazard (Explosion) disc. */
private fun DrawScope.drawSpecialsArt(palette: SkinPalette, textMeasurer: TextMeasurer) {
    val midY = size.height * 0.5f
    val effects = listOf(
        0.24f to FoodEffect.Haste(0L),
        0.50f to FoodEffect.Ghost(0L),
        0.76f to FoodEffect.Burst(0L),
    )
    val discR = size.minDimension * 0.18f
    effects.forEach { (fx, effect) ->
        val cx = size.width * fx
        val accent = SpecialVisuals.accent(effect)
        drawSpecialDiscAt(cx, midY, discR, accent, palette, effect.isHazard)
        drawSpecialSymbol(effect, cx, midY, discR * 0.86f, SpecialInk, textMeasurer)
    }
}

/** [drawSpecialDisc] at an explicit centre/radius (the page-3 hero uses three of them). */
private fun DrawScope.drawSpecialDiscAt(
    cx: Float,
    cy: Float,
    radius: Float,
    accent: Color,
    palette: SkinPalette,
    hazard: Boolean,
) {
    val center = Offset(cx, cy)
    if (palette.useGlow) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.5f), Color.Transparent),
                center = center,
                radius = radius * 2.1f,
            ),
            radius = radius * 2.1f,
            center = center,
        )
        drawCircle(color = accent, radius = radius, center = center)
        drawCircle(Color.Black.copy(alpha = 0.18f), radius = radius, center = center, style = Stroke(width = radius * 0.12f))
        if (hazard) {
            val ringRadius = radius * 1.34f
            val dash = ringRadius * 0.52f
            drawCircle(
                color = HazardRing,
                radius = ringRadius,
                center = center,
                style = Stroke(
                    width = radius * 0.16f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.55f)),
                ),
            )
        }
    } else {
        val tl = Offset(cx - radius, cy - radius)
        val sz = Size(radius * 2f, radius * 2f)
        val cr = radius * 2f * palette.cornerFactor
        val rad = CornerRadius(cr, cr)
        drawRoundRect(color = accent, topLeft = tl, size = sz, cornerRadius = rad)
        drawRoundRect(Color.Black.copy(alpha = 0.18f), tl, sz, rad, style = Stroke(width = radius * 0.12f))
        if (hazard) {
            val pad = radius * 0.3f
            val rtl = Offset(tl.x - pad, tl.y - pad)
            val rsz = Size(sz.width + 2 * pad, sz.height + 2 * pad)
            val rcr = cr + pad
            val dash = rsz.width * 0.18f
            drawRoundRect(
                color = HazardRing,
                topLeft = rtl,
                size = rsz,
                cornerRadius = CornerRadius(rcr, rcr),
                style = Stroke(
                    width = radius * 0.16f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.55f)),
                ),
            )
        }
    }
}

// --- Control-scheme glyphs -----------------------------------------------------

/** A finger-flick: a diagonal arrow with a short motion trail. */
private fun DrawScope.drawSwipeGlyph(cx: Float, cy: Float, r: Float, color: Color) {
    val start = Offset(cx - r * 0.8f, cy + r * 0.8f)
    val end = Offset(cx + r * 0.8f, cy - r * 0.8f)
    drawLine(color, start, end, strokeWidth = r * 0.22f, cap = StrokeCap.Round)
    // Arrowhead at the end.
    val head = Path().apply {
        moveTo(end.x, end.y)
        lineTo(end.x - r * 0.5f, end.y + r * 0.12f)
        lineTo(end.x - r * 0.12f, end.y + r * 0.5f)
        close()
    }
    drawPath(head, color)
}

/** Tap-to-turn: two small rounded buttons with left / right chevrons. */
private fun DrawScope.drawTapGlyph(cx: Float, cy: Float, r: Float, color: Color) {
    val w = r * 0.62f
    val h = r * 1.2f
    for (sign in intArrayOf(-1, 1)) {
        val bx = cx + sign * r * 0.6f
        drawRoundRect(
            color = color,
            topLeft = Offset(bx - w / 2f, cy - h / 2f),
            size = Size(w, h),
            cornerRadius = CornerRadius(w * 0.35f, w * 0.35f),
        )
        // A chevron cut in ink, pointing outward.
        val chevron = Path().apply {
            if (sign < 0) {
                moveTo(bx + w * 0.12f, cy - h * 0.18f)
                lineTo(bx - w * 0.14f, cy)
                lineTo(bx + w * 0.12f, cy + h * 0.18f)
            } else {
                moveTo(bx - w * 0.12f, cy - h * 0.18f)
                lineTo(bx + w * 0.14f, cy)
                lineTo(bx - w * 0.12f, cy + h * 0.18f)
            }
        }
        drawPath(chevron, SpecialInk, style = Stroke(width = r * 0.12f, cap = StrokeCap.Round))
    }
}

/** A four-way D-pad: a plus made of two rounded bars. */
private fun DrawScope.drawDpadGlyph(cx: Float, cy: Float, r: Float, color: Color) {
    val arm = r * 1.3f
    val thick = r * 0.42f
    val rad = CornerRadius(thick * 0.4f, thick * 0.4f)
    drawRoundRect(
        color = color,
        topLeft = Offset(cx - thick / 2f, cy - arm / 2f),
        size = Size(thick, arm),
        cornerRadius = rad,
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(cx - arm / 2f, cy - thick / 2f),
        size = Size(arm, thick),
        cornerRadius = rad,
    )
}

// --- Page indicator ------------------------------------------------------------

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val active = index == currentPage
            val width by animateDpAsState(
                targetValue = if (active) 24.dp else 8.dp,
                animationSpec = tween(260),
                label = "dotWidth",
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (active) activeColor else inactiveColor),
            )
        }
        Spacer(Modifier.width(0.dp))
    }
}

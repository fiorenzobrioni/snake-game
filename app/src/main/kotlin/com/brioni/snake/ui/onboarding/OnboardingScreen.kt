package com.brioni.snake.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.brioni.snake.R
import com.brioni.snake.data.Settings
import com.brioni.snake.data.SettingsRepository
import com.brioni.snake.game.BoardScale
import com.brioni.snake.game.ControlScheme
import com.brioni.snake.game.Level
import com.brioni.snake.ui.components.SnakeButton
import com.brioni.snake.ui.game.SkinPalette
import com.brioni.snake.ui.game.paletteFor
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * First-run tutorial (Step 6.9.16): a brief, skippable 4-card pager introducing the
 * objective, the controls, the food types and the power-ups / hazards, so a new
 * player is not dropped cold. Shown once on first launch (gated by
 * [SettingsRepository.onboardingCompleted]) and re-openable from Settings.
 *
 * Each card pairs a hand-drawn Canvas illustration - a framed mini-board styled from
 * the player's active [com.brioni.snake.game.Skin] - with an Orbitron title and a
 * short body, plus a colour legend on the food / specials cards. Animation is
 * deliberately restrained: a subtle parallax on the artwork as pages slide and an
 * animated page indicator; the screen-level blur-dissolve from the App shell covers
 * entry and exit. [onFinished] is invoked once, on the final "Get started", on Skip,
 * or on system-back.
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

    // System-back acts as "skip" - the player has seen enough.
    BackHandler { onFinished() }

    Column(
        modifier = modifier
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
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
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
            OnboardingCard(page = page, palette = palette, parallax = offset)
        }

        PageIndicator(
            pageCount = pageCount,
            currentPage = pagerState.currentPage,
            activeColor = MaterialTheme.colorScheme.primary,
            inactiveColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f),
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

/** One tutorial card: a framed illustration, a title, a body, and an optional legend. */
@Composable
private fun OnboardingCard(page: Int, palette: SkinPalette, parallax: Float) {
    val (titleRes, bodyRes) = when (page) {
        0 -> R.string.onboarding_objective_title to R.string.onboarding_objective_body
        1 -> R.string.onboarding_controls_title to R.string.onboarding_controls_body
        2 -> R.string.onboarding_food_title to R.string.onboarding_food_body
        else -> R.string.onboarding_specials_title to R.string.onboarding_specials_body
    }
    val accent = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
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
                    scaleX = 0.9f + 0.1f * k
                    scaleY = 0.9f + 0.1f * k
                }
                .clip(RoundedCornerShape(20.dp)),
        ) {
            drawMiniBoard(palette)
            when (page) {
                0 -> drawObjectiveArt(palette)
                1 -> drawControlsArt(palette, accent)
                2 -> drawFoodArt(palette)
                else -> drawSpecialsArt(palette, accent)
            }
        }

        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 28.dp, start = 8.dp, end = 8.dp),
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp),
        )

        when (page) {
            2 -> FoodLegend(palette, modifier = Modifier.padding(top = 18.dp))
            3 -> SpecialsLegend(palette, modifier = Modifier.padding(top = 18.dp))
        }
    }
}

// --- Legends -------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoodLegend(palette: SkinPalette, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LegendChip(palette.growMedium, stringResource(R.string.onboarding_food_grow), palette)
        LegendChip(palette.shrinkMedium, stringResource(R.string.onboarding_food_shrink), palette)
        LegendChip(palette.growMystery, stringResource(R.string.onboarding_food_mystery), palette)
    }
}

@Composable
private fun SpecialsLegend(palette: SkinPalette, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LegendGroup(
            header = stringResource(R.string.onboarding_powerups),
            chips = listOf(
                stringResource(R.string.effect_lightning),
                stringResource(R.string.effect_star),
                stringResource(R.string.effect_freeze),
                stringResource(R.string.onboarding_jackpot),
            ),
            color = palette.special,
            palette = palette,
            hazard = false,
        )
        LegendGroup(
            header = stringResource(R.string.onboarding_hazards),
            chips = listOf(
                stringResource(R.string.effect_quake),
                stringResource(R.string.onboarding_explosion),
                stringResource(R.string.effect_snail),
            ),
            color = palette.special,
            palette = palette,
            hazard = true,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegendGroup(
    header: String,
    chips: List<String>,
    color: Color,
    palette: SkinPalette,
    hazard: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = header,
            style = MaterialTheme.typography.labelLarge,
            color = if (hazard) HazardRing else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips.forEach { LegendChip(color, it, palette, hazard = hazard) }
        }
    }
}

/** A small colour swatch (matching the skin's piece shape) over a label. */
@Composable
private fun LegendChip(color: Color, label: String, palette: SkinPalette, hazard: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.size(26.dp)) {
            val c = size.minDimension / 2f
            val center = Offset(c, c)
            if (palette.useGlow) {
                drawCircle(color, radius = c * 0.74f, center = center)
            } else {
                val side = size.minDimension * 0.78f
                val tl = Offset((size.width - side) / 2f, (size.height - side) / 2f)
                val rad = CornerRadius(side * palette.cornerFactor, side * palette.cornerFactor)
                drawRoundRect(color, tl, Size(side, side), rad)
            }
            if (hazard) {
                drawCircle(
                    color = HazardRing,
                    radius = c * 0.94f,
                    center = center,
                    style = Stroke(width = c * 0.16f),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
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

// --- Canvas artwork ------------------------------------------------------------

/** A warm red used for the hazard "caution" rings, mirroring the in-game telegraph. */
private val HazardRing = Color(0xFFE5564B)

/** The framed, skin-styled mini-board every illustration is drawn on. */
private fun DrawScope.drawMiniBoard(palette: SkinPalette) {
    val radius = CornerRadius(size.minDimension * 0.10f, size.minDimension * 0.10f)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(palette.boardTop, palette.boardBottom)),
        cornerRadius = radius,
    )
    val step = size.width / 12f
    var x = step
    while (x < size.width - 1f) {
        drawLine(palette.gridLine, Offset(x, 0f), Offset(x, size.height), 1.5f)
        x += step
    }
    var y = step
    while (y < size.height - 1f) {
        drawLine(palette.gridLine, Offset(0f, y), Offset(size.width, y), 1.5f)
        y += step
    }
    drawRoundRect(
        color = palette.boardBorder,
        cornerRadius = radius,
        style = Stroke(width = size.minDimension * 0.03f),
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

/** Page 0: a short snake heading toward a piece of food. */
private fun DrawScope.drawObjectiveArt(palette: SkinPalette) {
    val cell = size.height / 5f
    val midY = size.height * 0.5f
    val half = cell * 0.42f
    val startX = size.width * 0.26f
    // Body segments, then the head, then food ahead.
    for (i in 0..2) {
        drawPiece(startX + i * cell, midY, half, palette.snakeBody, palette)
    }
    val headX = startX + 3 * cell
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
private fun DrawScope.drawControlsArt(palette: SkinPalette, accent: Color) {
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

/** Page 2: a row of food growing in size, then a warm shrink piece. */
private fun DrawScope.drawFoodArt(palette: SkinPalette) {
    val midY = size.height * 0.5f
    val unit = size.width / 6f
    val items = listOf(
        unit * 0.34f to palette.growSmall,
        unit * 0.46f to palette.growMedium,
        unit * 0.60f to palette.growLarge,
        unit * 0.50f to palette.shrinkMedium,
    )
    items.forEachIndexed { i, (r, color) ->
        drawFood(unit * (1.1f + i * 1.25f), midY, r, color, palette)
    }
}

/** Page 3: a power-up star flanked by a power-up disc and a hazard piece. */
private fun DrawScope.drawSpecialsArt(palette: SkinPalette, accent: Color) {
    val midY = size.height * 0.5f
    val unit = size.height / 5f
    // Central star (power-up).
    val starOuter = unit * 1.15f
    if (palette.useGlow) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.special.copy(alpha = 0.45f), Color.Transparent),
                center = Offset(size.width / 2f, midY),
                radius = starOuter * 2f,
            ),
            radius = starOuter * 2f,
            center = Offset(size.width / 2f, midY),
        )
    }
    drawPath(starPath(size.width / 2f, midY, starOuter, starOuter * 0.46f, 5), palette.special)
    // Left: a power-up piece.
    drawFood(size.width * 0.22f, midY, unit * 0.62f, palette.special, palette)
    // Right: a hazard piece with a caution ring.
    val hx = size.width * 0.78f
    drawFood(hx, midY, unit * 0.62f, palette.special, palette)
    drawCircle(HazardRing, radius = unit * 0.95f, center = Offset(hx, midY), style = Stroke(width = unit * 0.16f))
}

/** A [points]-pointed star centred at ([cx],[cy]) between [outer] and [inner] radii. */
private fun starPath(cx: Float, cy: Float, outer: Float, inner: Float, points: Int): Path {
    val path = Path()
    val step = Math.PI / points
    var angle = -Math.PI / 2.0
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outer else inner
        val x = cx + (r * cos(angle)).toFloat()
        val y = cy + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        angle += step
    }
    path.close()
    return path
}

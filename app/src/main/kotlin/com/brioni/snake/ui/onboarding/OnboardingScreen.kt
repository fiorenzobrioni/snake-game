package com.brioni.snake.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.brioni.snake.game.GameMode
import com.brioni.snake.game.Level
import com.brioni.snake.ui.AnimatedShaderBackground
import com.brioni.snake.ui.components.MenuIcons
import com.brioni.snake.ui.components.SnakeButton
import com.brioni.snake.ui.game.SkinPalette
import com.brioni.snake.ui.game.SnakeEmblem
import com.brioni.snake.ui.game.SpecialVisuals
import com.brioni.snake.ui.game.drawGlyph
import com.brioni.snake.ui.game.drawSpecialToken
import com.brioni.snake.ui.game.paletteFor
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

/**
 * First-run tour (redesigned for 1.2.0): a skippable 5-card pager that sells the
 * game the way a Play-Store title should - welcome & goal, the food language, the
 * specials, the four modes and the daily meta loop - so a new player lands in the
 * menu already knowing why to come back tomorrow. Shown once on first launch
 * (gated by [SettingsRepository.onboardingCompleted]) and re-openable from
 * Settings.
 *
 * Design notes, following the usual mobile-onboarding practices:
 * - **Five focused cards, one idea each.** The old dedicated "how to steer" page
 *   is gone - steering is one glanceable chip row on the welcome card (the swipe
 *   default just works), which freed room for what a player actually cannot
 *   guess: the mode roster and the daily/meta loop.
 * - **Skippable at every step** (low-emphasis Skip up top; the last page swaps it
 *   for the primary "Start playing"), an animated page indicator for orientation,
 *   and system-back pages *backwards* (finishing only from the first card).
 * - **Live, branded artwork.** The hero card runs the real in-game snake renderer
 *   ([SnakeEmblem]) slithering in the player's skin; food and special tokens are
 *   drawn by the exact in-game renderers, so every colour and symbol taught here
 *   means the same thing on the board. All motion freezes under reduce-motion.
 * - **One visual family.** Cards are glass panels with gradient rims over the
 *   brand's animated AGSL backdrop ([AnimatedShaderBackground]), the same look
 *   the menus and buttons wear. Every page scrolls vertically so the copy never
 *   clips on short screens.
 *
 * [onFinished] is invoked once - on the final "Start playing", on Skip, or on
 * system-back from the first page.
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
    val pageCount = 5
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == pageCount - 1
    val textMeasurer = rememberTextMeasurer()

    // A shared clock drives the live artwork (the hero slither, the pulsing
    // food). Under reduce-motion it never advances, so everything holds still.
    var timeSeconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(settings.reduceMotion) {
        if (!settings.reduceMotion) {
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { now -> timeSeconds = (now - start) / 1_000_000_000f }
            }
        }
    }

    // System-back steps one page back; only the first page hands off (as skip).
    BackHandler {
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        } else {
            onFinished()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // A self-contained dark, minimal backdrop so the tour always reads on the
        // brand surface - the same drifting-glows shader the intro and menus use -
        // regardless of the active theme.
        AnimatedShaderBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar: a low-emphasis Skip, hidden on the last page (where the
            // primary button already finishes).
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
                // Parallax: how far this page is from the settled position (-1..1
                // while dragging), used to drift and fade the hero artwork.
                val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                when (page) {
                    0 -> WelcomePage(palette, offset, timeSeconds)
                    1 -> FoodPage(palette, offset, textMeasurer, timeSeconds)
                    2 -> SpecialsPage(palette, offset, textMeasurer)
                    3 -> ModesPage(palette)
                    else -> MetaPage(palette)
                }
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

// --- Pages -----------------------------------------------------------------------

/** Page 0: the hook - the live slithering snake, the goal and the steering chips. */
@Composable
private fun WelcomePage(palette: SkinPalette, parallax: Float, time: Float) {
    PageColumn {
        GlassCard(
            palette = palette,
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .aspectRatio(1.5f)
                .heroParallax(parallax),
        ) {
            // The real in-game snake, in the player's skin, slithering in place.
            SnakeEmblem(
                palette = palette,
                time = time,
                waveAmplitude = 0.11f,
                cellFraction = 0.17f,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        PageTitle(stringResource(R.string.onboarding_welcome_title), palette.snakeHead)
        PageBody(stringResource(R.string.onboarding_welcome_body))

        // The three steering styles as one glanceable chip row - enough to start
        // (swipe just works), with Settings named for the full choice.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ControlChip(ControlGlyph.Swipe, stringResource(R.string.onboarding_controls_swipe), palette.snakeHead, Modifier.weight(1f))
            ControlChip(ControlGlyph.Tap, stringResource(R.string.onboarding_controls_tap), palette.snakeHead, Modifier.weight(1f))
            ControlChip(ControlGlyph.Dpad, stringResource(R.string.onboarding_controls_dpad), palette.snakeHead, Modifier.weight(1f))
        }
        Text(
            text = stringResource(R.string.onboarding_welcome_controls_hint),
            style = MaterialTheme.typography.bodySmall,
            color = BodyDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp, start = 8.dp, end = 8.dp),
        )
    }
}

/** Page 1: the food language - grow, shrink, mystery - over a live pulsing row. */
@Composable
private fun FoodPage(palette: SkinPalette, parallax: Float, textMeasurer: TextMeasurer, time: Float) {
    PageColumn {
        GlassCard(
            palette = palette,
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(2.1f)
                .heroParallax(parallax),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawFoodArt(palette, textMeasurer, time)
            }
        }
        PageTitle(stringResource(R.string.onboarding_food_title), palette.snakeHead)
        PageBody(stringResource(R.string.onboarding_food_body))
        FoodLegend(palette, textMeasurer, modifier = Modifier.padding(top = 20.dp))
    }
}

/** Page 2: power-ups and hazards, drawn by the exact in-game token renderer. */
@Composable
private fun SpecialsPage(palette: SkinPalette, parallax: Float, textMeasurer: TextMeasurer) {
    PageColumn {
        GlassCard(
            palette = palette,
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(2.1f)
                .heroParallax(parallax),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawSpecialsArt(palette, textMeasurer)
            }
        }
        PageTitle(stringResource(R.string.onboarding_specials_title), palette.snakeHead)
        PageBody(stringResource(R.string.onboarding_specials_body))
        SpecialsLegend(palette, textMeasurer, modifier = Modifier.padding(top = 20.dp))
    }
}

/** Page 3: the mode roster - each mode a card with its own drawn glyph and accent. */
@Composable
private fun ModesPage(palette: SkinPalette) {
    PageColumn {
        PageTitle(stringResource(R.string.onboarding_modes_title), palette.snakeHead)
        PageBody(stringResource(R.string.onboarding_modes_body))
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModeCard(
                glyph = ModeGlyph.Endless,
                name = GameMode.Endless.displayName,
                description = stringResource(R.string.onboarding_mode_endless_desc),
                accent = palette.snakeHead,
            )
            ModeCard(
                glyph = ModeGlyph.TimeAttack,
                name = GameMode.TimeAttack.displayName,
                description = stringResource(R.string.onboarding_mode_time_attack_desc),
                accent = SpecialVisuals.FeverColor,
            )
            ModeCard(
                glyph = ModeGlyph.Campaign,
                name = GameMode.Levels.displayName,
                description = stringResource(R.string.onboarding_mode_campaign_desc),
                accent = CampaignViolet,
            )
            ModeCard(
                glyph = ModeGlyph.Zen,
                name = GameMode.Zen.displayName,
                description = stringResource(R.string.onboarding_mode_zen_desc),
                accent = SpecialVisuals.ZenColor,
            )
        }
    }
}

/** Page 4: the daily meta loop - why a player comes back tomorrow. */
@Composable
private fun MetaPage(palette: SkinPalette) {
    PageColumn {
        PageTitle(stringResource(R.string.onboarding_meta_title), palette.snakeHead)
        PageBody(stringResource(R.string.onboarding_meta_body))
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoRow(
                title = stringResource(R.string.onboarding_meta_daily),
                description = stringResource(R.string.onboarding_meta_daily_desc),
                titleColor = DailyBlue,
            ) { IconBadge(MenuIcons.Calendar, DailyBlue) }
            InfoRow(
                title = stringResource(R.string.onboarding_meta_missions),
                description = stringResource(R.string.onboarding_meta_missions_desc),
                titleColor = MissionGreen,
            ) { TargetBadge(MissionGreen) }
            InfoRow(
                title = stringResource(R.string.onboarding_meta_achievements),
                description = stringResource(R.string.onboarding_meta_achievements_desc),
                titleColor = TrophyGold,
            ) { IconBadge(MenuIcons.Medal, TrophyGold) }
            InfoRow(
                title = stringResource(R.string.onboarding_meta_skins),
                description = stringResource(R.string.onboarding_meta_skins_desc),
                titleColor = SkinViolet,
            ) { SwatchBadge() }
        }
        Text(
            text = stringResource(R.string.onboarding_replay_hint),
            style = MaterialTheme.typography.bodySmall,
            color = BodyDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

// --- Page scaffolding --------------------------------------------------------------

/**
 * The shared page skeleton: a vertically scrollable, centred column, so richer
 * copy and the legends never clip on short screens (and still centre when short).
 */
@Composable
private fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(8.dp))
        content()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PageTitle(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 26.dp, start = 8.dp, end = 8.dp),
    )
}

@Composable
private fun PageBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = BodyText,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp),
    )
}

/** The pager-drag parallax applied to a page's hero card: drift, fade, settle. */
private fun Modifier.heroParallax(parallax: Float): Modifier = graphicsLayer {
    val k = (1f - abs(parallax)).coerceIn(0f, 1f)
    translationX = parallax * 70f
    alpha = 0.35f + 0.65f * k
    scaleX = 0.92f + 0.08f * k
    scaleY = 0.92f + 0.08f * k
}

/**
 * The hero "glass" panel the artwork lives on: a dark board-toned gradient with
 * a faint grid, a corner-darkening vignette and the same gradient rim the menu
 * buttons and tiles wear (tinted by the skin), so the tour reads as one family
 * with the live menus.
 */
@Composable
private fun GlassCard(
    palette: SkinPalette,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val rim = Brush.verticalGradient(
        listOf(palette.snakeHead.copy(alpha = 0.50f), palette.snakeHead.copy(alpha = 0.12f)),
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(BoardTopDark, BoardBottomDark)))
            .border(BorderStroke(1.5.dp, rim), shape),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // A faint board grid + vignette give the glass panel depth without
            // stealing attention from the bright pieces on top.
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
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.maxDimension * 0.62f,
                ),
            )
        }
        content()
    }
}

// --- Legends -------------------------------------------------------------------

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
 * [title] over a dimmer [description] on the right. A soft glass fill and hairline
 * rim keep every list on the tour visually consistent with the menus.
 */
@Composable
private fun InfoRow(
    title: String,
    description: String,
    titleColor: Color,
    modifier: Modifier = Modifier,
    badge: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
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

/** One game-mode card: an accent-rimmed glass row with a drawn glyph badge. */
@Composable
private fun ModeCard(glyph: ModeGlyph, name: String, description: String, accent: Color) {
    val shape = RoundedCornerShape(14.dp)
    val rim = Brush.verticalGradient(
        listOf(accent.copy(alpha = 0.45f), accent.copy(alpha = 0.10f)),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(BorderStroke(1.dp, rim), shape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(44.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension * 0.46f
            drawCircle(accent.copy(alpha = 0.12f), radius = r, center = Offset(cx, cy))
            when (glyph) {
                ModeGlyph.Endless -> drawEndlessGlyph(cx, cy, r, accent)
                ModeGlyph.TimeAttack -> drawStopwatchGlyph(cx, cy, r, accent)
                ModeGlyph.Campaign -> drawFlagGlyph(cx, cy, r, accent)
                ModeGlyph.Zen -> drawEnsoGlyph(cx, cy, r, accent)
            }
        }
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
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

/** A special-piece badge drawn by the exact in-game token renderer ([drawSpecialToken]). */
@Composable
private fun SpecialBadge(effect: FoodEffect, palette: SkinPalette, textMeasurer: TextMeasurer) {
    Canvas(modifier = Modifier.size(40.dp)) {
        drawSpecialToken(
            cx = size.width / 2f,
            cy = size.height / 2f,
            radius = size.minDimension * 0.28f,
            effect = effect,
            palette = palette,
            textMeasurer = textMeasurer,
        )
    }
}

/** A meta-page badge for one of the hand-drawn menu vector glyphs. */
@Composable
private fun IconBadge(icon: ImageVector, accent: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** The missions badge: a drawn target - two rings and a bull's-eye dot. */
@Composable
private fun TargetBadge(accent: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f
            drawCircle(accent, radius = r * 0.95f, center = c, style = Stroke(width = r * 0.18f))
            drawCircle(accent, radius = r * 0.55f, center = c, style = Stroke(width = r * 0.18f))
            drawCircle(accent, radius = r * 0.18f, center = c)
        }
    }
}

/** The skins badge: three overlapping skin-head swatch discs (Classic/Neon/Ember). */
@Composable
private fun SwatchBadge() {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SkinViolet.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(28.dp)) {
            val cy = size.height / 2f
            val r = size.minDimension * 0.26f
            val colors = listOf(Color(0xFF7CFC00), Color(0xFF00E5FF), Color(0xFFFF7A1A))
            colors.forEachIndexed { i, c ->
                val cx = size.width * (0.26f + 0.24f * i)
                drawCircle(SpecialInk, radius = r * 1.14f, center = Offset(cx, cy))
                drawCircle(c, radius = r, center = Offset(cx, cy))
            }
        }
    }
}

/** Which scheme a [ControlChip] illustrates. */
private enum class ControlGlyph { Swipe, Tap, Dpad }

/** Which mode a [ModeCard] glyph illustrates. */
private enum class ModeGlyph { Endless, TimeAttack, Campaign, Zen }

/** A compact steering chip: a glyph over its scheme name, in a glass pill. */
@Composable
private fun ControlChip(glyph: ControlGlyph, label: String, accent: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(modifier = Modifier.size(30.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension * 0.42f
            when (glyph) {
                ControlGlyph.Swipe -> drawSwipeGlyph(cx, cy, r, accent)
                ControlGlyph.Tap -> drawTapGlyph(cx, cy, r, accent)
                ControlGlyph.Dpad -> drawDpadGlyph(cx, cy, r, accent)
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = BodyText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp),
        )
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

/** Neutral dark glass-card colours - intentionally minimal, independent of the skin. */
private val BoardTopDark = Color(0xFF121A22)
private val BoardBottomDark = Color(0xFF0A0E13)
private val BoardGrid = Color(0x0DFFFFFF)

/** Mode accents without an in-game identity colour of their own. */
private val CampaignViolet = Color(0xFFB388FF)

/** Meta-page accents: one hue per pillar of the daily loop. */
private val DailyBlue = Color(0xFF4FC3F7)
private val MissionGreen = Color(0xFF69F0AE)
private val TrophyGold = Color(0xFFFFC400)
private val SkinViolet = Color(0xFFB388FF)

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
 * The food-page hero: the grow sizes swelling left to right, a warm shrink piece
 * and a mystery "?", each breathing gently on its own phase (still when [time]
 * is frozen by reduce-motion).
 */
private fun DrawScope.drawFoodArt(palette: SkinPalette, textMeasurer: TextMeasurer, time: Float) {
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
        val pulse = 1f + 0.05f * sin(time * 2.1f + i * 1.6f)
        drawFood(cx, midY, r * pulse, color, palette)
        if (mystery) drawGlyph(textMeasurer, "?", cx, midY, r * pulse * 1.25f, Color.White)
    }
}

/** The specials-page hero: a power-up (Lightning), a power-up (Star) and a hazard (Explosion). */
private fun DrawScope.drawSpecialsArt(palette: SkinPalette, textMeasurer: TextMeasurer) {
    val midY = size.height * 0.5f
    val effects = listOf(
        0.24f to FoodEffect.Haste(0L),
        0.50f to FoodEffect.Ghost(0L),
        0.76f to FoodEffect.Burst(0L),
    )
    val radius = size.minDimension * 0.27f
    effects.forEach { (fx, effect) ->
        drawSpecialToken(size.width * fx, midY, radius, effect, palette, textMeasurer)
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

// --- Mode glyphs -----------------------------------------------------------------

/** Endless: an infinity loop - two linked stroked rings. */
private fun DrawScope.drawEndlessGlyph(cx: Float, cy: Float, r: Float, color: Color) {
    val loop = r * 0.44f
    val stroke = Stroke(width = r * 0.20f, cap = StrokeCap.Round)
    drawCircle(color, radius = loop, center = Offset(cx - loop * 0.92f, cy), style = stroke)
    drawCircle(color, radius = loop, center = Offset(cx + loop * 0.92f, cy), style = stroke)
}

/** Time Attack: a stopwatch - dial, crown button and a hand racing up-right. */
private fun DrawScope.drawStopwatchGlyph(cx: Float, cy: Float, r: Float, color: Color) {
    val dialC = Offset(cx, cy + r * 0.10f)
    val dialR = r * 0.62f
    val stroke = Stroke(width = r * 0.18f, cap = StrokeCap.Round)
    drawCircle(color, radius = dialR, center = dialC, style = stroke)
    // Crown button on top.
    drawRoundRect(
        color = color,
        topLeft = Offset(cx - r * 0.14f, cy - r * 0.86f),
        size = Size(r * 0.28f, r * 0.24f),
        cornerRadius = CornerRadius(r * 0.08f, r * 0.08f),
    )
    // The hand, frozen mid-sprint.
    drawLine(
        color = color,
        start = dialC,
        end = Offset(dialC.x + dialR * 0.52f, dialC.y - dialR * 0.52f),
        strokeWidth = r * 0.16f,
        cap = StrokeCap.Round,
    )
}

/** Campaign: a checkpoint flag on its pole. */
private fun DrawScope.drawFlagGlyph(cx: Float, cy: Float, r: Float, color: Color) {
    val poleX = cx - r * 0.42f
    drawLine(
        color = color,
        start = Offset(poleX, cy - r * 0.78f),
        end = Offset(poleX, cy + r * 0.80f),
        strokeWidth = r * 0.16f,
        cap = StrokeCap.Round,
    )
    val flag = Path().apply {
        moveTo(poleX, cy - r * 0.74f)
        lineTo(poleX + r * 1.06f, cy - r * 0.42f)
        lineTo(poleX, cy - r * 0.10f)
        close()
    }
    drawPath(flag, color)
}

/** Zen: an enso - the open, hand-drawn circle of calm. */
private fun DrawScope.drawEnsoGlyph(cx: Float, cy: Float, r: Float, color: Color) {
    val d = r * 1.32f
    drawArc(
        color = color,
        startAngle = -60f,
        sweepAngle = 300f,
        useCenter = false,
        topLeft = Offset(cx - d / 2f, cy - d / 2f),
        size = Size(d, d),
        style = Stroke(width = r * 0.20f, cap = StrokeCap.Round),
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

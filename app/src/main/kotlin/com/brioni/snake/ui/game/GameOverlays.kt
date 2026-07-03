package com.brioni.snake.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import com.brioni.snake.ui.components.SnakeButton
import com.brioni.snake.ui.components.SnakeOutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.brioni.snake.R
import com.brioni.snake.game.BoardScale
import com.brioni.snake.game.GameMode
import com.brioni.snake.game.Level
import com.brioni.snake.game.SnakeSpeed

/** Translucent full-screen scrim shared by every overlay. */
@Composable
private fun OverlayScrim(
    alpha: Float = 0.72f,
    content: @Composable () -> Unit,
) {
    // The board behind is dark, so under the light theme we use a near-opaque
    // light panel (instead of the dark translucent scrim) to keep the overlay
    // text readable.
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val scrimColor = if (isLightTheme) {
        MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
    } else {
        Color.Black.copy(alpha = alpha)
    }
    // Vertically scrollable so a tall overlay (many selectors / a long recap +
    // achievement + mission banner) never clips its buttons off the bottom edge on
    // short screens. With Arrangement.Center it still centres when the content fits.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scrimColor)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

/** Pre-game menu: title, level + board-scale selection, and Play. */
@Composable
fun ReadyOverlay(
    selectedMode: GameMode,
    selectedLevel: Level,
    selectedSnakeSpeed: SnakeSpeed,
    selectedScale: BoardScale,
    campaignCheckpoint: Int,
    campaignStartLevel: Int,
    onModeSelected: (GameMode) -> Unit,
    onLevelSelected: (Level) -> Unit,
    onSnakeSpeedSelected: (SnakeSpeed) -> Unit,
    onScaleSelected: (BoardScale) -> Unit,
    onCampaignStartSelected: (Int) -> Unit,
    onPlay: () -> Unit,
) {
    OverlayScrim(alpha = 0.55f) {
        Text(
            text = stringResource(R.string.game_title),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        ChipSection(title = stringResource(R.string.menu_mode)) {
            GameMode.entries.forEach { gameMode ->
                FilterChip(
                    selected = gameMode == selectedMode,
                    onClick = { onModeSelected(gameMode) },
                    label = { Text(gameMode.displayName) },
                )
            }
        }

        // Campaign checkpoints: once the player has reached past level 1, the
        // run may start from any reached level. Starting past 1 is a practice
        // run (records count from a Level 1 start), which the caption states.
        if (selectedMode == GameMode.Levels && campaignCheckpoint > 1) {
            ChipSection(
                title = stringResource(R.string.menu_campaign_start),
                caption = if (campaignStartLevel > 1) {
                    stringResource(R.string.menu_campaign_practice_note)
                } else {
                    null
                },
            ) {
                (1..campaignCheckpoint).forEach { levelIndex ->
                    FilterChip(
                        selected = levelIndex == campaignStartLevel,
                        onClick = { onCampaignStartSelected(levelIndex) },
                        label = { Text(levelIndex.toString()) },
                    )
                }
            }
        }

        // Campaign mode has its own speed curve and shaped boards: the difficulty
        // selector stays in place but is disabled (and ignored by the ViewModel)
        // while it is active, so the menu layout never reflows.
        val levelSelectable = selectedMode != GameMode.Levels
        ChipSection(
            title = stringResource(R.string.menu_level),
            enabled = levelSelectable,
            // Endless: spell out what the difficulty actually changes — the
            // obstacle density and where its speed ramp starts.
            caption = if (selectedMode == GameMode.Endless) {
                stringResource(
                    R.string.menu_endless_level_hint,
                    selectedLevel.obstacleCount,
                    1 + selectedLevel.endlessTierHeadStart,
                )
            } else {
                null
            },
        ) {
            Level.entries.forEach { level ->
                FilterChip(
                    selected = level == selectedLevel,
                    onClick = { onLevelSelected(level) },
                    label = { Text(level.label) },
                    enabled = levelSelectable,
                )
            }
        }

        // Snake speed is independent of the level's obstacle layout. Endless ramps
        // its own pace and Levels paces by its speed cycle, so the selector is
        // disabled (and ignored) in those modes - the layout never reflows. In
        // Time Attack the pace also declares its score multiplier on each chip.
        val speedSelectable = selectedMode == GameMode.TimeAttack
        ChipSection(
            title = stringResource(R.string.menu_snake_speed),
            enabled = speedSelectable,
            caption = if (speedSelectable) stringResource(R.string.menu_speed_multiplier_hint) else null,
        ) {
            SnakeSpeed.entries.forEach { speed ->
                FilterChip(
                    selected = speed == selectedSnakeSpeed,
                    onClick = { onSnakeSpeedSelected(speed) },
                    label = {
                        Text(
                            if (speedSelectable) "${speed.label} · ${speed.timeAttackFactorLabel}" else speed.label,
                        )
                    },
                    enabled = speedSelectable,
                )
            }
        }

        ChipSection(title = stringResource(R.string.menu_board_scale)) {
            BoardScale.entries.forEach { scale ->
                FilterChip(
                    selected = scale == selectedScale,
                    onClick = { onScaleSelected(scale) },
                    label = { Text(scale.label) },
                )
            }
        }

        SnakeButton(
            onClick = onPlay,
            modifier = Modifier
                .padding(top = 24.dp)
                .widthIn(min = 200.dp),
        ) {
            Text(stringResource(R.string.action_play), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ChipSection(
    title: String,
    enabled: Boolean = true,
    /** An optional explanatory line under the chips (what this choice changes). */
    caption: String? = null,
    chips: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.38f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            chips()
        }
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * Campaign mode: the staged-level banner with the 3-2-1 countdown — shown at game
 * start, on every level advance and after a life loss ([isRespawn]). The board
 * behind it already wears the next level's shape, so the scrim is kept light.
 */
@Composable
fun LevelIntroOverlay(
    levelIndex: Int,
    levelCount: Int,
    levelName: String,
    speedCycle: Int,
    lives: Int,
    countdown: Int,
    isRespawn: Boolean,
) {
    OverlayScrim(alpha = 0.55f) {
        // The title pops in once per staged level (or respawn). It is split over
        // two fixed lines ("Level X" / "Speed Y") so it can never wrap
        // unpredictably on narrow boards or with multi-digit numbers.
        val titleIn = remember(levelIndex, speedCycle, isRespawn) { Animatable(0f) }
        LaunchedEffect(titleIn) {
            titleIn.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                scaleX = 0.6f + 0.4f * titleIn.value
                scaleY = 0.6f + 0.4f * titleIn.value
                alpha = titleIn.value.coerceIn(0f, 1f)
            },
        ) {
            Text(
                text = stringResource(R.string.level_intro_level, levelIndex, levelCount),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            // The designed name of this level, between "Level X" and "Speed Y".
            Text(
                text = levelName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = stringResource(R.string.level_intro_speed, speedCycle),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (isRespawn) {
            Text(
                text = stringResource(R.string.level_intro_ready),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        val livesDescription = stringResource(R.string.hud_lives, lives)
        Text(
            text = "♥".repeat(lives.coerceAtLeast(0)),
            style = MaterialTheme.typography.titleLarge,
            color = SpecialVisuals.ExtraLifeColor,
            modifier = Modifier
                .padding(top = 8.dp)
                .semantics { contentDescription = livesDescription },
        )

        // Countdown digit: re-pops each second inside an expanding, fading ring.
        val pulse = remember { Animatable(1f) }
        LaunchedEffect(countdown) {
            if (countdown > 0) {
                pulse.snapTo(0f)
                pulse.animateTo(1f, tween(durationMillis = 600, easing = FastOutSlowInEasing))
            }
        }
        if (countdown > 0) {
            Box(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .size(140.dp),
                contentAlignment = Alignment.Center,
            ) {
                val ringColor = MaterialTheme.colorScheme.primary
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val t = pulse.value
                    drawCircle(
                        color = ringColor.copy(alpha = (1f - t) * 0.8f),
                        radius = size.minDimension / 2f * (0.4f + 0.6f * t),
                        style = Stroke(width = size.minDimension * 0.04f),
                    )
                }
                Text(
                    text = countdown.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.graphicsLayer {
                        val t = pulse.value
                        scaleX = 1.6f - 0.6f * t
                        scaleY = 1.6f - 0.6f * t
                        alpha = (0.4f + 0.6f * t).coerceIn(0f, 1f)
                    },
                )
            }
        }
    }
}

/**
 * Pause-resume countdown: deliberately **no scrim** - the whole point is that
 * the board (and the head locator beacon the renderer pulses during it) stays
 * fully visible while 3-2-1 ticks, so the player re-finds the snake and plans
 * the first move. Just the digit in a pulsing ring over a small translucent
 * disc (for readability wherever it lands), plus a "Get ready!" caption.
 * Colours are fixed light-on-dark: the board behind is always the dark arcade
 * surface, in both themes.
 */
@Composable
fun ResumeCountdownOverlay(countdown: Int) {
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(durationMillis = 600, easing = FastOutSlowInEasing))
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (countdown > 0) {
            Box(
                modifier = Modifier.size(132.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // A grounding disc so the digit reads over any board content,
                    // then the expanding ring re-fired on each tick.
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.38f),
                        radius = size.minDimension / 2f * 0.52f,
                    )
                    val t = pulse.value
                    drawCircle(
                        color = Color.White.copy(alpha = (1f - t) * 0.8f),
                        radius = size.minDimension / 2f * (0.4f + 0.6f * t),
                        style = Stroke(width = size.minDimension * 0.04f),
                    )
                }
                Text(
                    text = countdown.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.graphicsLayer {
                        val t = pulse.value
                        scaleX = 1.6f - 0.6f * t
                        scaleY = 1.6f - 0.6f * t
                        alpha = (0.4f + 0.6f * t).coerceIn(0f, 1f)
                    },
                )
            }
            Text(
                text = stringResource(R.string.level_intro_ready),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** Shown while paused; resume, go back to game setup, or bail to the menu. */
@Composable
fun PausedOverlay(onResume: () -> Unit, onSetup: () -> Unit, onMenu: () -> Unit) {
    OverlayScrim {
        Text(
            text = stringResource(R.string.paused_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        SnakeButton(
            onClick = onResume,
            modifier = Modifier.padding(top = 24.dp).widthIn(min = 200.dp),
        ) {
            Text(stringResource(R.string.action_resume))
        }
        SnakeOutlinedButton(
            onClick = onSetup,
            modifier = Modifier.padding(top = 12.dp).widthIn(min = 200.dp),
        ) {
            Text(stringResource(R.string.action_game_setup))
        }
        SnakeOutlinedButton(
            onClick = onMenu,
            modifier = Modifier.padding(top = 12.dp).widthIn(min = 200.dp),
        ) {
            Text(stringResource(R.string.action_menu))
        }
    }
}

/** Final screen: score, best, replay, back to game setup, or return to the menu. */
@Composable
fun GameOverOverlay(
    score: Int,
    bestScore: Int,
    isNewBest: Boolean,
    unlocked: List<String>,
    unlockedSkins: List<String> = emptyList(),
    onPlayAgain: () -> Unit,
    onSetup: () -> Unit,
    onMenu: () -> Unit,
    showBest: Boolean = true,
    /** True after a Campaign practice start: the run's score was not recorded. */
    practiceRun: Boolean = false,
    summary: RunSummary? = null,
    missions: List<MissionProgress> = emptyList(),
) {
    OverlayScrim {
        Text(
            text = stringResource(R.string.game_over_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.game_over_score, score),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (showBest) {
            Text(
                text = if (isNewBest) {
                    stringResource(R.string.new_highscore)
                } else {
                    stringResource(R.string.highscore_label, bestScore)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isNewBest) FontWeight.Bold else FontWeight.Normal,
                color = if (isNewBest) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (practiceRun) {
            Text(
                text = stringResource(R.string.game_over_practice),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (summary != null) {
            RunRecap(summary)
        }
        if (missions.isNotEmpty()) {
            val anyJustCompleted = missions.any { it.justCompleted }
            Column(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Celebrate when this run just cleared a goal, otherwise just label
                // the day's mission list so the player always sees their progress.
                Text(
                    text = if (anyJustCompleted) {
                        stringResource(R.string.mission_completed)
                    } else {
                        stringResource(R.string.missions_title)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
                missions.forEach { mission ->
                    Row(
                        modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (mission.done) "✓" else "○",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (mission.done) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        )
                        Text(
                            text = mission.description,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (mission.justCompleted) FontWeight.Bold else FontWeight.Normal,
                            color = if (mission.done) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
            }
        }
        if (unlocked.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.achievement_unlocked),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                unlocked.forEach { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        if (unlockedSkins.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.skin_unlocked),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                unlockedSkins.forEach { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        SnakeButton(
            onClick = onPlayAgain,
            modifier = Modifier.padding(top = 24.dp).widthIn(min = 200.dp),
        ) {
            Text(stringResource(R.string.action_play_again))
        }
        SnakeOutlinedButton(
            onClick = onSetup,
            modifier = Modifier.padding(top = 12.dp).widthIn(min = 200.dp),
        ) {
            Text(stringResource(R.string.action_game_setup))
        }
        SnakeOutlinedButton(
            onClick = onMenu,
            modifier = Modifier.padding(top = 12.dp).widthIn(min = 200.dp),
        ) {
            Text(stringResource(R.string.action_menu))
        }
    }
}

/**
 * The game-over run recap (Step 6.9.2): a small stat card listing what the run
 * achieved - foods eaten, best combo, time survived and max length, plus the
 * deepest level reached in Campaign. A calm, readable counterpart to the
 * achievement banner above it.
 */
@Composable
private fun RunRecap(summary: RunSummary) {
    Column(
        modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .widthIn(max = 360.dp)
            .background(
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.recap_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        RecapRow(stringResource(R.string.recap_foods), summary.foodsEaten.toString())
        RecapRow(stringResource(R.string.recap_combo), stringResource(R.string.recap_combo_value, summary.maxCombo))
        RecapRow(stringResource(R.string.recap_time), formatRunDuration(summary.durationMs))
        RecapRow(stringResource(R.string.recap_length), summary.maxLength.toString())
        if (summary.isCampaign) {
            RecapRow(
                stringResource(R.string.recap_level),
                stringResource(R.string.records_level_speed, summary.deepestLevel, summary.deepestSpeed),
            )
        }
    }
}

/** One label/value line of the [RunRecap] card. */
@Composable
private fun RecapRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** Formats a run duration as minutes:seconds (seconds zero-padded). */
@Composable
private fun formatRunDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    return stringResource(R.string.recap_time_value, totalSeconds / 60L, totalSeconds % 60L)
}

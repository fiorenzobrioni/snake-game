package com.brioni.snake.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scrimColor)
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
    onModeSelected: (GameMode) -> Unit,
    onLevelSelected: (Level) -> Unit,
    onSnakeSpeedSelected: (SnakeSpeed) -> Unit,
    onScaleSelected: (BoardScale) -> Unit,
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

        // Campaign mode has its own speed curve and shaped boards: the difficulty
        // selector stays in place but is disabled (and ignored by the ViewModel)
        // while it is active, so the menu layout never reflows.
        val levelSelectable = selectedMode != GameMode.Levels
        ChipSection(title = stringResource(R.string.menu_level), enabled = levelSelectable) {
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
        // disabled (and ignored) in those modes - the layout never reflows.
        val speedSelectable = selectedMode == GameMode.TimeAttack
        ChipSection(title = stringResource(R.string.menu_snake_speed), enabled = speedSelectable) {
            SnakeSpeed.entries.forEach { speed ->
                FilterChip(
                    selected = speed == selectedSnakeSpeed,
                    onClick = { onSnakeSpeedSelected(speed) },
                    label = { Text(speed.label) },
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

        Button(
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
private fun ChipSection(title: String, enabled: Boolean = true, chips: @Composable () -> Unit) {
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
                text = stringResource(R.string.level_intro_level, levelIndex),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                textAlign = TextAlign.Center,
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
        Button(
            onClick = onResume,
            modifier = Modifier.padding(top = 24.dp).widthIn(min = 200.dp),
        ) {
            Text(stringResource(R.string.action_resume))
        }
        OutlinedButton(
            onClick = onSetup,
            modifier = Modifier.padding(top = 12.dp).widthIn(min = 200.dp),
        ) {
            Text(stringResource(R.string.action_game_setup))
        }
        OutlinedButton(
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
    onPlayAgain: () -> Unit,
    onSetup: () -> Unit,
    onMenu: () -> Unit,
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
        Button(
            onClick = onPlayAgain,
            modifier = Modifier.padding(top = 24.dp).widthIn(min = 200.dp),
        ) {
            Text(stringResource(R.string.action_play_again))
        }
        OutlinedButton(
            onClick = onSetup,
            modifier = Modifier.padding(top = 12.dp).widthIn(min = 200.dp),
        ) {
            Text(stringResource(R.string.action_game_setup))
        }
        OutlinedButton(
            onClick = onMenu,
            modifier = Modifier.padding(top = 12.dp).widthIn(min = 200.dp),
        ) {
            Text(stringResource(R.string.action_menu))
        }
    }
}

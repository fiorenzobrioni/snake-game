package com.brioni.snake.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.brioni.snake.R
import com.brioni.snake.game.BoardScale
import com.brioni.snake.game.GameMode
import com.brioni.snake.game.Level

/** Translucent full-screen scrim shared by every overlay. */
@Composable
private fun OverlayScrim(
    alpha: Float = 0.72f,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = alpha))
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
    selectedScale: BoardScale,
    onModeSelected: (GameMode) -> Unit,
    onLevelSelected: (Level) -> Unit,
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

        ChipSection(title = stringResource(R.string.menu_level)) {
            Level.entries.forEach { level ->
                FilterChip(
                    selected = level == selectedLevel,
                    onClick = { onLevelSelected(level) },
                    label = { Text(level.label) },
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
private fun ChipSection(title: String, chips: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips()
        }
    }
}

/** Shown while paused; resume or bail to the menu. */
@Composable
fun PausedOverlay(onResume: () -> Unit, onMenu: () -> Unit) {
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
            onClick = onMenu,
            modifier = Modifier.padding(top = 12.dp).widthIn(min = 200.dp),
        ) {
            Text(stringResource(R.string.action_menu))
        }
    }
}

/** Final screen: score, best, replay, or return to the menu. */
@Composable
fun GameOverOverlay(
    score: Int,
    bestScore: Int,
    isNewBest: Boolean,
    unlocked: List<String>,
    onPlayAgain: () -> Unit,
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
            onClick = onMenu,
            modifier = Modifier.padding(top = 12.dp).widthIn(min = 200.dp),
        ) {
            Text(stringResource(R.string.action_menu))
        }
    }
}

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
import com.brioni.snake.game.BoardSize
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

/** Pre-game menu: title, level + board-size selection, and Play. */
@Composable
fun ReadyOverlay(
    selectedLevel: Level,
    selectedBoard: BoardSize,
    onLevelSelected: (Level) -> Unit,
    onBoardSelected: (BoardSize) -> Unit,
    onPlay: () -> Unit,
) {
    OverlayScrim(alpha = 0.55f) {
        Text(
            text = stringResource(R.string.game_title),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        ChipSection(title = stringResource(R.string.menu_level)) {
            Level.entries.forEach { level ->
                FilterChip(
                    selected = level == selectedLevel,
                    onClick = { onLevelSelected(level) },
                    label = { Text(level.label) },
                )
            }
        }

        ChipSection(title = stringResource(R.string.menu_board_size)) {
            BoardSize.entries.forEach { size ->
                FilterChip(
                    selected = size == selectedBoard,
                    onClick = { onBoardSelected(size) },
                    label = { Text(size.label) },
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

/** Final screen: score, replay, or return to the menu. */
@Composable
fun GameOverOverlay(score: Int, onPlayAgain: () -> Unit, onMenu: () -> Unit) {
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

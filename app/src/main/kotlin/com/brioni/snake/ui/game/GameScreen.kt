package com.brioni.snake.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brioni.snake.R
import com.brioni.snake.game.GameStatus

/**
 * Top-level gameplay screen. Lays out the HUD, the [GameBoard] and the D-pad in
 * a portrait column, with the menu / pause / game-over overlays stacked on top.
 * All state and timing live in [GameViewModel]; this composable only renders
 * state and forwards intents.
 */
@Composable
fun GameScreen(
    modifier: Modifier = Modifier,
    viewModel: GameViewModel = viewModel(),
) {
    val state = viewModel.state
    val playing = state.status == GameStatus.Running || state.status == GameStatus.Paused

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Hud(
                score = state.score,
                levelLabel = state.level.displayName,
                boardLabel = state.board.displayName,
                showPause = state.status == GameStatus.Running,
                onPause = viewModel::togglePause,
            )

            val boardModifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp)
                .let { base ->
                    if (state.status == GameStatus.Running) {
                        base.swipeToSteer(onSwipe = viewModel::setDirection)
                    } else {
                        base
                    }
                }
            GameBoard(state = state, modifier = boardModifier)

            if (playing) {
                DirectionPad(
                    onDirection = viewModel::setDirection,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
            }
        }

        when (state.status) {
            GameStatus.Ready -> ReadyOverlay(
                selectedLevel = viewModel.level,
                selectedBoard = viewModel.board,
                onLevelSelected = viewModel::selectLevel,
                onBoardSelected = viewModel::selectBoard,
                onPlay = viewModel::start,
            )

            GameStatus.Paused -> PausedOverlay(
                onResume = viewModel::togglePause,
                onMenu = viewModel::toMenu,
            )

            GameStatus.GameOver -> GameOverOverlay(
                score = state.score,
                onPlayAgain = viewModel::playAgain,
                onMenu = viewModel::toMenu,
            )

            GameStatus.Running -> Unit
        }
    }
}

@Composable
private fun Hud(
    score: Int,
    levelLabel: String,
    boardLabel: String,
    showPause: Boolean,
    onPause: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.hud_score, score),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "$levelLabel · $boardLabel",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
        if (showPause) {
            TextButton(onClick = onPause) {
                Text(stringResource(R.string.action_pause))
            }
        }
    }
}

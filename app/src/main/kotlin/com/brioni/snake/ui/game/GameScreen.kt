package com.brioni.snake.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brioni.snake.R
import com.brioni.snake.game.GameStatus
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Top-level gameplay screen. Lays out the HUD, the [GameBoard] and the D-pad in
 * a portrait column, with the menu / pause / game-over overlays stacked on top.
 * State and timing live in [GameViewModel]; this composable renders state,
 * forwards intents and owns purely-visual effects (the game-over screen shake).
 */
@Composable
fun GameScreen(
    modifier: Modifier = Modifier,
    viewModel: GameViewModel = viewModel(),
) {
    val state = viewModel.state
    val playing = state.status == GameStatus.Running || state.status == GameStatus.Paused

    // Screen shake on death (step 2.7): a single 0→1 ramp drives a damped wobble.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(viewModel.deathEventId) {
        if (viewModel.deathEventId > 0) {
            shake.snapTo(0f)
            shake.animateTo(1f, tween(durationMillis = 450, easing = LinearEasing))
        }
    }
    val amplitudePx = with(LocalDensity.current) { 10.dp.toPx() }
    val shakeT = shake.value
    val damp = 1f - shakeT
    val shakeX = (sin(shakeT * Math.PI * 10).toFloat() * amplitudePx * damp)
    val shakeY = (cos(shakeT * Math.PI * 9).toFloat() * amplitudePx * damp)

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Hud(
                score = state.score,
                levelLabel = state.level.displayName,
                boardLabel = state.board.displayName,
                showPause = state.status == GameStatus.Running,
                onPause = viewModel::togglePause,
            )

            var boardModifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 6.dp)
                .offset { IntOffset(shakeX.roundToInt(), shakeY.roundToInt()) }
            if (state.status == GameStatus.Running) {
                boardModifier = boardModifier.swipeToSteer(onSwipe = viewModel::setDirection)
            }
            GameBoard(
                state = state,
                previousSnake = viewModel.previousSnake,
                tickId = viewModel.tickId,
                tickMillis = state.level.tickMillis,
                running = state.status == GameStatus.Running,
                eatEvent = viewModel.eatEvent,
                eatEventId = viewModel.eatEventId,
                modifier = boardModifier,
            )

            if (playing) {
                DirectionPad(
                    onDirection = viewModel::setDirection,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
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

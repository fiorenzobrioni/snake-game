package com.brioni.snake.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.brioni.snake.R
import com.brioni.snake.game.ControlScheme
import com.brioni.snake.game.DEFAULT_ASPECT
import com.brioni.snake.game.GameStatus
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Top-level gameplay screen. Lays out the HUD, the [GameBoard] and the active
 * control scheme in a portrait column, with the menu / pause / game-over
 * overlays stacked on top. State and timing live in [GameViewModel]; this
 * composable renders state, forwards intents and owns purely-visual effects
 * (game-over screen shake, pause blur, rolling score). [onExitToMenu] routes the
 * overlay "Menu" actions back to the app's main menu screen.
 */
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onExitToMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = viewModel.state
    val playing = state.status == GameStatus.Running || state.status == GameStatus.Paused
    val textMeasurer = rememberTextMeasurer()

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

    // Pause blur (step 3.4): API 31+ blurs the frozen board; below it no-ops and
    // the overlay scrim still distinguishes the paused state.
    val blurRadius by animateDpAsState(
        targetValue = if (state.status == GameStatus.Paused) 14.dp else 0.dp,
        label = "pauseBlur",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().blur(blurRadius)) {
            Hud(
                score = state.score,
                combo = state.combo,
                levelLabel = state.level.displayName,
                boardLabel = "${viewModel.scale.displayName} · ${state.board.width}×${state.board.height}",
                showPause = state.status == GameStatus.Running,
                onPause = viewModel::togglePause,
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 6.dp),
            ) {
                // Feed the measured play-area aspect ratio back to the VM, which
                // (only while Ready) resizes the board to fill the screen. Done
                // in a keyed side effect so it can't loop during composition.
                val aspect = if (maxHeight > 0.dp) maxWidth / maxHeight else DEFAULT_ASPECT
                LaunchedEffect(aspect) { viewModel.onPlayAreaMeasured(aspect) }

                var boardModifier: Modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(shakeX.roundToInt(), shakeY.roundToInt()) }
                if (state.status == GameStatus.Running && viewModel.controlScheme == ControlScheme.Swipe) {
                    boardModifier = boardModifier.swipeToSteer(onSwipe = viewModel::setDirection)
                }
                GameBoard(
                    state = state,
                    previousSnake = viewModel.previousSnake,
                    tickTimeNanos = viewModel.tickTimeNanos,
                    tickMillis = state.level.tickMillis,
                    running = state.status == GameStatus.Running,
                    eatEvent = viewModel.eatEvent,
                    eatEventId = viewModel.eatEventId,
                    textMeasurer = textMeasurer,
                    modifier = boardModifier,
                )
            }

            if (playing) {
                ControlRegion(
                    scheme = viewModel.controlScheme,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
        }

        when (state.status) {
            GameStatus.Ready -> ReadyOverlay(
                selectedLevel = viewModel.level,
                selectedScale = viewModel.scale,
                onLevelSelected = viewModel::selectLevel,
                onScaleSelected = viewModel::selectScale,
                onPlay = viewModel::start,
            )

            GameStatus.Paused -> PausedOverlay(
                onResume = viewModel::togglePause,
                onMenu = { viewModel.toMenu(); onExitToMenu() },
            )

            GameStatus.GameOver -> GameOverOverlay(
                score = state.score,
                bestScore = viewModel.bestScore,
                isNewBest = viewModel.isNewBest,
                onPlayAgain = viewModel::playAgain,
                onMenu = { viewModel.toMenu(); onExitToMenu() },
            )

            GameStatus.Running -> Unit
        }
    }
}

/** Renders the bottom controls for the active [scheme]. */
@Composable
private fun ControlRegion(
    scheme: ControlScheme,
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
) {
    when (scheme) {
        ControlScheme.TwoButton -> RelativeControls(
            onLeft = viewModel::turnLeft,
            onRight = viewModel::turnRight,
            modifier = modifier,
        )

        ControlScheme.DPad -> DirectionPad(
            onDirection = viewModel::setDirection,
            modifier = modifier,
        )

        // Swipe steers directly on the board; no bottom buttons needed.
        ControlScheme.Swipe -> Unit
    }
}

@Composable
private fun Hud(
    score: Int,
    combo: Int,
    levelLabel: String,
    boardLabel: String,
    showPause: Boolean,
    onPause: () -> Unit,
) {
    // Rolling score counter (step 3.6).
    val animatedScore by animateIntAsState(targetValue = score, animationSpec = tween(300), label = "score")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.hud_score, animatedScore),
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
        if (combo > 1) {
            Text(
                text = stringResource(R.string.hud_combo, combo),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        if (showPause) {
            TextButton(onClick = onPause) {
                Text(stringResource(R.string.action_pause))
            }
        }
    }
}

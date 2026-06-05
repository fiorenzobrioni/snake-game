package com.brioni.snake.ui.game

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.brioni.snake.R
import com.brioni.snake.audio.GameAudio
import com.brioni.snake.game.ControlScheme
import com.brioni.snake.game.DEFAULT_ASPECT
import com.brioni.snake.game.GameMode
import com.brioni.snake.game.GameStatus
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
    audio: GameAudio,
    onExitToMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = viewModel.state
    val playing = state.status == GameStatus.Running || state.status == GameStatus.Paused
    val textMeasurer = rememberTextMeasurer()

    // Back (incl. accidental edge-swipe gestures during play) pauses a running
    // game instead of dropping to the menu with the loop still ticking; from any
    // other state it returns to the menu, stopping the loop cleanly.
    BackHandler {
        if (state.status == GameStatus.Running) {
            audio.playPause(); viewModel.togglePause()
        } else {
            audio.playUiClick(); viewModel.toMenu(); onExitToMenu()
        }
    }

    // Screen shake on death (step 2.7): a single 0→1 ramp drives a damped wobble.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(viewModel.deathEventId) {
        if (viewModel.deathEventId > 0) {
            shake.snapTo(0f)
            shake.animateTo(1f, tween(durationMillis = 450, easing = LinearEasing))
        }
    }
    // A lighter mid-game shake reused by earthquakes / explosions (step 6.2).
    val quake = remember { Animatable(0f) }
    LaunchedEffect(viewModel.shakeEventId) {
        if (viewModel.shakeEventId > 0) {
            quake.snapTo(0f)
            quake.animateTo(1f, tween(durationMillis = 380, easing = LinearEasing))
        }
    }
    val amplitudePx = with(LocalDensity.current) { 10.dp.toPx() }
    val quakeAmpPx = with(LocalDensity.current) { 7.dp.toPx() }
    val shakeT = shake.value
    val damp = 1f - shakeT
    val quakeT = quake.value
    val quakeDamp = 1f - quakeT
    // Use sin on both axes so the offset is exactly 0 at rest (cos(0)=1 left the
    // board shifted ~17dp down when idle, pushing its bottom off-screen on the
    // first game until a death animation drove the damping term to zero).
    val shakeX = sin(shakeT * Math.PI * 10).toFloat() * amplitudePx * damp +
        sin(quakeT * Math.PI * 14).toFloat() * quakeAmpPx * quakeDamp
    val shakeY = sin(shakeT * Math.PI * 9).toFloat() * amplitudePx * damp +
        sin(quakeT * Math.PI * 13).toFloat() * quakeAmpPx * quakeDamp

    // Pause blur (step 3.4): API 31+ blurs the frozen board; below it no-ops and
    // the overlay scrim still distinguishes the paused state.
    val blurRadius by animateDpAsState(
        targetValue = if (state.status == GameStatus.Paused) 14.dp else 0.dp,
        label = "pauseBlur",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().blur(blurRadius)) {
            val timeLabel = if (state.mode == GameMode.TimeAttack && playing) {
                val secs = (state.timeRemainingMs / 1000).toInt()
                "%d:%02d".format(secs / 60, secs % 60)
            } else {
                null
            }
            Hud(
                score = state.score,
                combo = state.combo,
                levelLabel = if (state.mode == GameMode.Classic) state.level.displayName else state.mode.displayName,
                boardLabel = "${viewModel.scale.displayName} · ${state.board.width}×${state.board.height}",
                timeLabel = timeLabel,
                showPause = state.status == GameStatus.Running,
                onPause = { audio.playPause(); viewModel.togglePause() },
            )

            EffectTimersRow(effects = state.effectTimers)

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

                // Optional CRT post-filter (step 5.4): an AGSL RenderEffect over
                // the whole board layer, on API 33+ when enabled in Settings.
                val crtEffect = if (
                    viewModel.crtEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ) {
                    remember(constraints.maxWidth, constraints.maxHeight) {
                        val shader = RuntimeShader(Shaders.CRT)
                        shader.setFloatUniform(
                            "resolution",
                            constraints.maxWidth.toFloat(),
                            constraints.maxHeight.toFloat(),
                        )
                        RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
                    }
                } else {
                    null
                }

                var boardModifier: Modifier = Modifier.fillMaxSize()
                if (crtEffect != null) {
                    boardModifier = boardModifier.graphicsLayer { renderEffect = crtEffect }
                }
                boardModifier = boardModifier.offset { IntOffset(shakeX.roundToInt(), shakeY.roundToInt()) }
                if (state.status == GameStatus.Running && viewModel.controlScheme == ControlScheme.Swipe) {
                    boardModifier = boardModifier.swipeToSteer(onSwipe = viewModel::setDirection)
                }
                GameBoard(
                    state = state,
                    previousSnake = viewModel.previousSnake,
                    tickTimeNanos = viewModel.tickTimeNanos,
                    tickMillis = state.tickIntervalMillis,
                    running = state.status == GameStatus.Running,
                    eatEvent = viewModel.eatEvent,
                    eatEventId = viewModel.eatEventId,
                    textMeasurer = textMeasurer,
                    palette = viewModel.palette,
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
                selectedMode = viewModel.mode,
                selectedLevel = viewModel.level,
                selectedScale = viewModel.scale,
                onModeSelected = { audio.playUiClick(); viewModel.selectMode(it) },
                onLevelSelected = { audio.playUiClick(); viewModel.selectLevel(it) },
                onScaleSelected = { audio.playUiClick(); viewModel.selectScale(it) },
                onPlay = { audio.playUiClick(); viewModel.start() },
            )

            GameStatus.Paused -> PausedOverlay(
                onResume = { audio.playPause(); viewModel.togglePause() },
                onMenu = { audio.playUiClick(); viewModel.toMenu(); onExitToMenu() },
            )

            GameStatus.GameOver -> GameOverOverlay(
                score = state.score,
                bestScore = viewModel.bestScore,
                isNewBest = viewModel.isNewBest,
                unlocked = viewModel.newlyUnlocked.map { it.title },
                onPlayAgain = { audio.playUiClick(); viewModel.playAgain() },
                onMenu = { audio.playUiClick(); viewModel.toMenu(); onExitToMenu() },
            )

            GameStatus.Running -> Unit
        }
    }
}

/**
 * Fixed vertical slot for the effect-timer chips. The height is reserved
 * unconditionally — even with no effects running — so the board below (which
 * fills the remaining `weight(1f)` space) keeps a constant size. Otherwise the
 * row would appear/disappear with each power-up and visibly resize the board,
 * making the snake seem to jump.
 */
private val EffectTimersRowHeight = 34.dp

/** A row of countdown chips for the timed effects currently running. */
@Composable
private fun EffectTimersRow(effects: List<com.brioni.snake.game.ActiveEffect>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EffectTimersRowHeight)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        effects.forEach { effect -> EffectChip(effect) }
    }
}

@Composable
private fun EffectChip(effect: com.brioni.snake.game.ActiveEffect) {
    val color = SpecialVisuals.accent(effect.kind)
    val label = when (effect.kind) {
        com.brioni.snake.game.EffectKind.Haste -> stringResource(R.string.effect_lightning)
        com.brioni.snake.game.EffectKind.Slow -> stringResource(R.string.effect_snail)
        com.brioni.snake.game.EffectKind.Ghost -> stringResource(R.string.effect_star)
        com.brioni.snake.game.EffectKind.Freeze -> stringResource(R.string.effect_freeze)
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .height(3.dp)
                .width(52.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color.copy(alpha = 0.25f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(effect.fraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
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
    timeLabel: String?,
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
        if (timeLabel != null) {
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(end = 12.dp),
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

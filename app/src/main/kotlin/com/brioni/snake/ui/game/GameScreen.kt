package com.brioni.snake.ui.game

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.brioni.snake.R
import com.brioni.snake.audio.GameAudio
import com.brioni.snake.game.BackBehavior
import com.brioni.snake.game.ControlScheme
import com.brioni.snake.game.DEFAULT_ASPECT
import com.brioni.snake.game.Direction
import com.brioni.snake.game.EffectKind
import com.brioni.snake.game.GameMode
import com.brioni.snake.game.GameStatus
import com.brioni.snake.game.LevelsMode
import kotlin.math.roundToInt
import kotlin.math.cos
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

    // Back handling during a *running* game depends on the Back-during-play
    // setting; from any other state Back always returns to the menu, stopping the
    // loop cleanly. We use a predictive handler so that, when "Keep playing" is on
    // and the player steers by swipe, the back gesture's edge (left/right) can be
    // fed to the snake as a turn instead of being lost. The last gesture event is
    // captured to read that edge; a Back *button* press carries no edge and is
    // simply ignored while keeping play going.
    PredictiveBackHandler { progress ->
        var lastEvent: BackEventCompat? = null
        try {
            progress.collect { lastEvent = it }
            // Back committed.
            if (state.status == GameStatus.Running) {
                if (viewModel.backBehavior == BackBehavior.KeepPlaying) {
                    if (viewModel.controlScheme == ControlScheme.Swipe) {
                        lastEvent?.let { event ->
                            // Left-edge gesture swipes inward (rightward) → steer Right;
                            // right-edge gesture swipes leftward → steer Left.
                            val direction = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) {
                                Direction.Right
                            } else {
                                Direction.Left
                            }
                            viewModel.onSwipe(direction)
                        }
                    }
                    // Otherwise: ignore the Back entirely and keep playing.
                } else {
                    audio.playPause(); viewModel.togglePause()
                }
            } else {
                viewModel.toSetup(); onExitToMenu()
            }
        } catch (_: kotlin.coroutines.cancellation.CancellationException) {
            // The back gesture was cancelled (swiped partway then released): do nothing.
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
    // The earthquake hazard is a *sustained* shake: while its timed effect runs the
    // board jitters continuously, easing in/out at the edges of the effect. This is
    // the malus - it makes the board hard to read for the whole duration.
    val quakeActive = state.status == GameStatus.Running && state.hasEffect(EffectKind.Quake)
    val sustainedAmp by animateFloatAsState(
        targetValue = if (quakeActive) quakeAmpPx else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "quakeSustain",
    )
    val quakeWobble = rememberInfiniteTransition(label = "quakeWobble")
    val wobble by quakeWobble.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1000, easing = LinearEasing)),
        label = "wobble",
    )
    val shakeT = shake.value
    val damp = 1f - shakeT
    val quakeT = quake.value
    val quakeDamp = 1f - quakeT
    // Use sin on both axes so the offset is exactly 0 at rest (cos(0)=1 left the
    // board shifted ~17dp down when idle, pushing its bottom off-screen on the
    // first game until a death animation drove the damping term to zero).
    // Accessibility: reduce-motion flattens every board shake to zero.
    val motionScale = if (viewModel.reduceMotion) 0f else 1f
    val shakeX = (
        sin(shakeT * Math.PI * 10).toFloat() * amplitudePx * damp +
            sin(quakeT * Math.PI * 14).toFloat() * quakeAmpPx * quakeDamp +
            sin(wobble * 2 * Math.PI * 12).toFloat() * sustainedAmp
        ) * motionScale
    val shakeY = (
        sin(shakeT * Math.PI * 9).toFloat() * amplitudePx * damp +
            sin(quakeT * Math.PI * 13).toFloat() * quakeAmpPx * quakeDamp +
            cos(wobble * 2 * Math.PI * 11).toFloat() * sustainedAmp
        ) * motionScale

    // A brief danger flash on a near-miss / grace dodge (suppressed by reduce-motion).
    val nearMissFlash = remember { Animatable(0f) }
    LaunchedEffect(viewModel.nearMissEventId) {
        if (viewModel.nearMissEventId > 0 && !viewModel.reduceMotion) {
            nearMissFlash.snapTo(0.7f)
            nearMissFlash.animateTo(0f, tween(durationMillis = 320, easing = LinearEasing))
        }
    }

    // Pause blur (step 3.4): blurs the frozen board behind the overlay scrim.
    val blurRadius by animateDpAsState(
        targetValue = if (state.status == GameStatus.Paused) 14.dp else 0.dp,
        label = "pauseBlur",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().blur(blurRadius)) {
            val inLevels = state.mode == GameMode.Levels
            val onBoard = playing || state.status == GameStatus.LevelIntro
            // The auxiliary HUD slot: the Time Attack clock, or the Levels-mode
            // count of foods still to eat before the next level.
            val timeLabel = when {
                state.mode == GameMode.TimeAttack && playing -> {
                    val secs = (state.timeRemainingMs / 1000).toInt()
                    "%d:%02d".format(secs / 60, secs % 60)
                }
                inLevels && onBoard ->
                    stringResource(R.string.hud_next_level, (LevelsMode.LEVEL_FOOD_GOAL - state.levelFoodsEaten).coerceAtLeast(0))
                else -> null
            }
            Hud(
                score = state.score,
                combo = state.combo,
                statusLabel = buildString {
                    if (viewModel.activeChallenge != null) {
                        val tag = if (viewModel.isDailyChallenge) {
                            stringResource(R.string.daily_hud_prefix)
                        } else {
                            stringResource(R.string.random_hud_prefix)
                        }
                        append(tag).append(" · ")
                    }
                    append(
                        when {
                            inLevels -> stringResource(R.string.hud_level_speed, state.levelIndex, state.speedCycle) +
                                " · " + viewModel.scale.displayName
                            else -> "${state.mode.displayName} · ${state.level.displayName} · ${viewModel.scale.displayName}"
                        },
                    )
                },
                timeLabel = timeLabel,
                lives = if (inLevels && onBoard) state.lives else 0,
                length = if (onBoard) state.snake.size else 0,
                palette = viewModel.palette,
                showPause = state.status == GameStatus.Running,
                reduceMotion = viewModel.reduceMotion,
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
                // the whole board layer, when enabled in Settings.
                val crtEffect = if (viewModel.crtEnabled) {
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
                    boardModifier = boardModifier.swipeToSteer(onSwipe = viewModel::onSwipe)
                }
                // The board interior stays dark, but its frame follows the theme:
                // a branded green border on the light surround, the skin's subtle
                // border in dark mode.
                val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
                val boardBorderColor = if (isLightTheme) {
                    MaterialTheme.colorScheme.primary
                } else {
                    viewModel.palette.boardBorder
                }
                GameBoard(
                    state = state,
                    previousSnake = viewModel.previousSnake,
                    tickTimeNanos = viewModel.tickTimeNanos,
                    tickMillis = state.tickIntervalMillis,
                    running = state.status == GameStatus.Running,
                    eatEvent = viewModel.eatEvent,
                    eatEventId = viewModel.eatEventId,
                    floatingText = viewModel.floatingText,
                    floatingTextId = viewModel.floatingTextId,
                    hazardWarn = viewModel.hazardWarn,
                    hazardWarnId = viewModel.hazardWarnId,
                    teleportEvent = viewModel.teleportEvent,
                    teleportEventId = viewModel.teleportEventId,
                    textMeasurer = textMeasurer,
                    palette = viewModel.palette,
                    borderColor = boardBorderColor,
                    outsideColor = MaterialTheme.colorScheme.background,
                    reduceMotion = viewModel.reduceMotion,
                    modifier = boardModifier,
                )
                // Danger frame flash, tracking the board's shake offset.
                if (nearMissFlash.value > 0f) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .offset { IntOffset(shakeX.roundToInt(), shakeY.roundToInt()) }
                            .border(
                                width = 3.dp,
                                color = NearMissFlashColor.copy(alpha = nearMissFlash.value),
                                shape = RoundedCornerShape(14.dp),
                            ),
                    )
                }
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
                selectedSnakeSpeed = viewModel.snakeSpeed,
                selectedScale = viewModel.scale,
                onModeSelected = { viewModel.selectMode(it) },
                onLevelSelected = { viewModel.selectLevel(it) },
                onSnakeSpeedSelected = { viewModel.selectSnakeSpeed(it) },
                onScaleSelected = { viewModel.selectScale(it) },
                onPlay = { viewModel.start() },
            )

            GameStatus.LevelIntro -> LevelIntroOverlay(
                levelIndex = state.levelIndex,
                speedCycle = state.speedCycle,
                lives = state.lives,
                countdown = viewModel.introCountdown,
                isRespawn = viewModel.introIsRespawn,
            )

            GameStatus.Paused -> PausedOverlay(
                onResume = { audio.playPause(); viewModel.togglePause() },
                onSetup = { viewModel.toSetup() },
                onMenu = { viewModel.toSetup(); onExitToMenu() },
            )

            GameStatus.GameOver -> GameOverOverlay(
                score = state.score,
                bestScore = viewModel.bestScore,
                isNewBest = viewModel.isNewBest,
                // A Random challenge is a one-off: no best to show.
                showBest = !viewModel.isRandomChallenge,
                summary = viewModel.lastSummary,
                unlocked = viewModel.newlyUnlocked.map { it.title },
                onPlayAgain = { viewModel.playAgain() },
                onSetup = { viewModel.toSetup() },
                onMenu = { viewModel.toSetup(); onExitToMenu() },
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
/** The warning colour used by the near-miss / grace-dodge danger flash. */
private val NearMissFlashColor = Color(0xFFFF6E40)

/** The combo multiplier's colour, warming through tiers as the streak climbs. */
@Composable
private fun comboTierColor(combo: Int): Color = when {
    combo >= 8 -> Color(0xFFFF5252)
    combo >= 5 -> Color(0xFFFFA000)
    combo >= 3 -> Color(0xFFFFD54F)
    else -> MaterialTheme.colorScheme.tertiary
}

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
        com.brioni.snake.game.EffectKind.Quake -> stringResource(R.string.effect_quake)
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

/**
 * The score/status header. Its height is deliberately constant: the board below
 * fills the remaining space, so any HUD growth would visibly resize the board
 * mid-game. Two fixed single-line rows (no text ever wraps — the score shrinks
 * its font instead, the labels ellipsize) and the pause-button slot is always
 * reserved (alpha-hidden when inactive) so no state change reflows the layout.
 */
@Composable
private fun Hud(
    score: Int,
    combo: Int,
    statusLabel: String,
    timeLabel: String?,
    lives: Int,
    length: Int,
    palette: SkinPalette,
    showPause: Boolean,
    reduceMotion: Boolean,
    onPause: () -> Unit,
) {
    // Rolling score counter (step 3.6).
    val animatedScore by animateIntAsState(targetValue = score, animationSpec = tween(300), label = "score")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShrinkToFitText(
                text = stringResource(R.string.hud_score, animatedScore),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (combo > 1) {
                // Combo "juice": the multiplier punches in on each bump and warms
                // through a colour ramp (white → gold → orange → red) as it climbs.
                val pulse = remember { Animatable(1f) }
                LaunchedEffect(combo) {
                    if (!reduceMotion) {
                        pulse.snapTo(1.3f)
                        pulse.animateTo(1f, spring(dampingRatio = 0.42f))
                    }
                }
                Text(
                    text = stringResource(R.string.hud_combo, combo),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = comboTierColor(combo),
                    maxLines = 1,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value },
                )
            }
            TextButton(
                onClick = onPause,
                enabled = showPause,
                modifier = Modifier.alpha(if (showPause) 1f else 0f),
            ) {
                Text(
                    text = stringResource(R.string.action_pause),
                    // Match the Score's branded Orbitron font (titleSmall keeps the
                    // button-sized scale while sharing the Score's typeface).
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (length > 0) {
                // Current snake length: a small snake glyph (head + a tail block in
                // the active skin) followed by the count, replacing the old "Len"
                // label. The glyph is capped to the text line height so it never
                // grows the fixed-height second row - and so never steals space
                // from the board below.
                val lengthDescription = stringResource(R.string.hud_length, length)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .semantics { contentDescription = lengthDescription },
                ) {
                    SnakeLengthGlyph(
                        palette = palette,
                        modifier = Modifier
                            .height(14.dp)
                            .width(30.dp),
                    )
                    Text(
                        text = length.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                        maxLines = 1,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            if (lives > 0) {
                // Levels mode: the remaining snakes/lives. The row pops briefly
                // when a heart is banked so an extra life never goes unnoticed.
                val heartsPop = remember { Animatable(1f) }
                LaunchedEffect(lives) {
                    heartsPop.snapTo(1.6f)
                    heartsPop.animateTo(1f, tween(durationMillis = 450))
                }
                val livesDescription = stringResource(R.string.hud_lives, lives)
                Text(
                    text = "♥".repeat(lives),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SpecialVisuals.ExtraLifeColor,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .graphicsLayer {
                            scaleX = heartsPop.value
                            scaleY = heartsPop.value
                        }
                        .semantics { contentDescription = livesDescription },
                )
            }
            if (timeLabel != null) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

/**
 * A tiny two-segment snake - a tail block followed by an eyed head, facing right
 * - drawn in the active [palette]'s colours in the same block style as the board
 * snake. Used as the HUD length glyph in place of a "Len" label. It draws inside
 * whatever bounds the caller gives it (sized to the text line height), so it
 * never enlarges the HUD or shrinks the board.
 */
@Composable
private fun SnakeLengthGlyph(palette: SkinPalette, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val h = size.height
        val cell = h // each segment is a square the height of the glyph
        val gap = h * 0.12f

        fun block(left: Float, color: Color) {
            val inset = cell * 0.08f
            val side = cell - 2 * inset
            val topLeft = Offset(left + inset, (h - side) / 2f)
            val corner = CornerRadius(cell * palette.cornerFactor, cell * palette.cornerFactor)
            drawRoundRect(color = color, topLeft = topLeft, size = Size(side, side), cornerRadius = corner)
            drawRoundRect(
                color = palette.snakeOutline,
                topLeft = topLeft,
                size = Size(side, side),
                cornerRadius = corner,
                style = Stroke(width = cell * 0.08f),
            )
        }

        block(0f, palette.snakeBody)
        val headLeft = cell + gap
        block(headLeft, palette.snakeHead)

        // Two eyes on the head, looking right (the snake's heading).
        val cx = headLeft + cell / 2f
        val cy = h / 2f
        val eyeRadius = cell * 0.11f
        for (sign in intArrayOf(-1, 1)) {
            val ex = cx + cell * 0.16f
            val ey = cy + sign * cell * 0.18f
            drawCircle(Color.White, eyeRadius, Offset(ex, ey))
            drawCircle(palette.snakeEye, eyeRadius * 0.5f, Offset(ex + cell * 0.04f, ey))
        }
    }
}

/**
 * A single-line text that steps its font size down (never below half) instead
 * of wrapping or clipping when the available width runs out — keyed on the
 * text length so it re-grows when the content gets shorter again.
 */
@Composable
private fun ShrinkToFitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    var scale by remember(text.length) { mutableFloatStateOf(1f) }
    Text(
        text = text,
        style = style,
        color = color,
        fontWeight = FontWeight.Bold,
        fontSize = style.fontSize * scale,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { if (it.hasVisualOverflow && scale > 0.5f) scale *= 0.92f },
        modifier = modifier,
    )
}

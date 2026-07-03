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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
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

    // Time Attack Fever Time: the double-points finale must be *felt* — the board
    // frame smoulders (pulsing amber), the HUD clock turns hot and the music
    // steps its tempo up until the run ends (reset on dispose so no other screen
    // ever inherits the faster track).
    val feverActive = state.status == GameStatus.Running && state.inFeverTime
    DisposableEffect(feverActive) {
        audio.setMusicTempo(if (feverActive) FEVER_MUSIC_TEMPO else 1f)
        onDispose { audio.setMusicTempo(1f) }
    }
    val feverTransition = rememberInfiniteTransition(label = "feverGlow")
    val feverPulse by feverTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 640, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "feverPulse",
    )
    val feverGlow = when {
        !feverActive -> 0f
        viewModel.reduceMotion -> 0.7f // steady glow, no pulsing
        else -> feverPulse
    }

    // Endless speed-tier surge: a one-shot golden flare of the board frame each
    // time the ramp steps up, so the pace change is visible where the eyes are.
    val surgeFlash = remember { Animatable(0f) }
    LaunchedEffect(viewModel.bannerEventId) {
        if (viewModel.bannerEventId > 0 && !viewModel.reduceMotion &&
            viewModel.bannerEvent?.kind == BannerKind.SpeedUp
        ) {
            surgeFlash.snapTo(1f)
            surgeFlash.animateTo(0f, tween(durationMillis = 700, easing = LinearEasing))
        }
    }

    // Pause blur (step 3.4): blurs the frozen board behind the overlay scrim.
    // It lifts during the resume countdown - the whole point of the 3-2-1 is
    // re-finding the snake, so the board must be as sharp as during play (the
    // animated 14dp→0 makes the resume read as "snapping back into focus").
    val blurRadius by animateDpAsState(
        targetValue = if (state.status == GameStatus.Paused && viewModel.resumeCountdown == 0) 14.dp else 0.dp,
        label = "pauseBlur",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().blur(blurRadius)) {
            val inLevels = state.mode == GameMode.Levels
            val onBoard = playing || state.status == GameStatus.LevelIntro
            // The auxiliary HUD slot: the Time Attack clock, the Levels-mode
            // count of foods still to eat, or the live Endless speed tier.
            val timeLabel = when {
                state.mode == GameMode.TimeAttack && playing -> {
                    val secs = (state.timeRemainingMs / 1000).toInt()
                    "%d:%02d".format(secs / 60, secs % 60)
                }
                inLevels && onBoard ->
                    stringResource(R.string.hud_next_level, (LevelsMode.LEVEL_FOOD_GOAL - state.levelFoodsEaten).coerceAtLeast(0))
                state.mode == GameMode.Endless && playing ->
                    stringResource(R.string.hud_endless_speed, state.endlessSpeedTier)
                else -> null
            }
            Hud(
                score = state.score,
                combo = state.combo,
                statusLabel = buildString {
                    if (viewModel.activeChallenge != null) {
                        val tag = when {
                            viewModel.isDailyChallenge -> stringResource(R.string.daily_hud_prefix)
                            viewModel.replayDay != null -> stringResource(R.string.replay_hud_prefix)
                            else -> stringResource(R.string.random_hud_prefix)
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
                    // Time Attack: surface the declared pace score multiplier.
                    if (state.mode == GameMode.TimeAttack && viewModel.snakeSpeed.timeAttackScoreFactor > 1f) {
                        append(" · ").append(viewModel.snakeSpeed.timeAttackFactorLabel)
                    }
                },
                timeLabel = timeLabel,
                feverActive = feverActive,
                lives = if (inLevels && onBoard) state.lives else 0,
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
                if (state.status == GameStatus.Running) {
                    when (viewModel.controlScheme) {
                        ControlScheme.Swipe -> boardModifier = boardModifier.swipeToSteer(
                            thresholdPx = swipeThresholdPx(viewModel.swipeSensitivity),
                            onSwipe = viewModel::onSwipe,
                        )
                        ControlScheme.TapTurn -> boardModifier = boardModifier.tapToTurn(
                            onLeft = viewModel::turnLeft,
                            onRight = viewModel::turnRight,
                        )
                        else -> Unit
                    }
                }
                // The board interior stays dark, but its frame follows the theme:
                // a branded green border on the light surround; in dark mode it
                // frames the *stage* - the selected terrain's accent (the skin's
                // own border when the Arcade floor is active).
                val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
                val boardBorderColor = if (isLightTheme) {
                    MaterialTheme.colorScheme.primary
                } else {
                    terrainBoardBorder(viewModel.terrain, viewModel.palette)
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
                    bodyBurst = viewModel.bodyBurst,
                    bodyBurstId = viewModel.bodyBurstId,
                    textMeasurer = textMeasurer,
                    palette = viewModel.palette,
                    terrain = viewModel.terrain,
                    borderColor = boardBorderColor,
                    outsideColor = MaterialTheme.colorScheme.background,
                    reduceMotion = viewModel.reduceMotion,
                    resumeHighlight = viewModel.resumeCountdown > 0,
                    // Near-miss danger flash: drawn by the renderer along the
                    // board's exact frame (sharp corners, shaped Levels outlines,
                    // terrain-accented) and inheriting the board's shake.
                    dangerFlash = nearMissFlash.value,
                    feverGlow = feverGlow,
                    surgeFlash = surgeFlash.value,
                    // Keep particles/redraw alive through the death-burst and
                    // level-vanish transitions (after `running` has gone false)
                    // and while the resume countdown pulses the head beacon.
                    effectsActive = state.status == GameStatus.Running ||
                        viewModel.deathAnimating || viewModel.levelVanishing ||
                        viewModel.resumeCountdown > 0,
                    modifier = boardModifier,
                )

                // Centred in-run announcements (Fever Time / speed step / record):
                // a short punch-in banner over the top of the board.
                AnnouncementBanner(
                    event = viewModel.bannerEvent,
                    eventId = viewModel.bannerEventId,
                    reduceMotion = viewModel.reduceMotion,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 20.dp),
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
                selectedSnakeSpeed = viewModel.snakeSpeed,
                selectedScale = viewModel.scale,
                campaignCheckpoint = viewModel.campaignCheckpoint,
                campaignStartLevel = viewModel.campaignStartLevel,
                onModeSelected = { viewModel.selectMode(it) },
                onLevelSelected = { viewModel.selectLevel(it) },
                onSnakeSpeedSelected = { viewModel.selectSnakeSpeed(it) },
                onScaleSelected = { viewModel.selectScale(it) },
                onCampaignStartSelected = { viewModel.selectCampaignStartLevel(it) },
                onPlay = { viewModel.start() },
            )

            GameStatus.LevelIntro -> LevelIntroOverlay(
                levelIndex = state.levelIndex,
                levelCount = LevelsMode.LEVEL_COUNT,
                levelName = LevelsMode.nameFor(state.levelIndex),
                speedCycle = state.speedCycle,
                lives = state.lives,
                countdown = viewModel.introCountdown,
                isRespawn = viewModel.introIsRespawn,
            )

            // Resume runs through a 3-2-1 countdown: the paused menu clears and
            // the board stays fully visible (with the head beacon pulsing) so
            // the player re-finds the snake before motion restarts.
            GameStatus.Paused -> if (viewModel.resumeCountdown > 0) {
                ResumeCountdownOverlay(countdown = viewModel.resumeCountdown)
            } else {
                PausedOverlay(
                    onResume = { audio.playPause(); viewModel.resumeFromPause() },
                    onSetup = { viewModel.toSetup() },
                    onMenu = { viewModel.toSetup(); onExitToMenu() },
                )
            }

            // Hold the overlay back while the snake bursts apart (deathAnimating);
            // reduce-motion skips the burst so the overlay shows instantly.
            GameStatus.GameOver -> if (!viewModel.deathAnimating) {
                GameOverOverlay(
                    score = state.score,
                    bestScore = viewModel.bestScore,
                    isNewBest = viewModel.isNewBest,
                    // A Random challenge is a one-off and a Campaign practice
                    // start is unrecorded: neither has a best to show.
                    showBest = !viewModel.isRandomChallenge && !viewModel.lastRunFromCheckpoint,
                    practiceRun = viewModel.lastRunFromCheckpoint,
                    summary = viewModel.lastSummary,
                    unlocked = viewModel.newlyUnlocked.map { it.title },
                    unlockedSkins = viewModel.newlyUnlockedSkins.map { it.displayName },
                    missions = viewModel.missionsProgress,
                    onPlayAgain = { viewModel.playAgain() },
                    onSetup = { viewModel.toSetup() },
                    onMenu = { viewModel.toSetup(); onExitToMenu() },
                )
            }

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
        ControlScheme.DPad -> DirectionPad(
            onDirection = viewModel::setDirection,
            palette = viewModel.palette,
            modifier = modifier,
        )

        // Swipe and Tap-to-turn both steer directly on the board; no bottom
        // buttons needed.
        ControlScheme.Swipe, ControlScheme.TapTurn -> Unit
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
    feverActive: Boolean,
    lives: Int,
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
                // Fever Time turns the clock hot and pops it once on entry, so
                // the finale reads on the HUD as well as on the board frame.
                val feverPop = remember { Animatable(1f) }
                LaunchedEffect(feverActive) {
                    if (feverActive && !reduceMotion) {
                        feverPop.snapTo(1.5f)
                        feverPop.animateTo(1f, spring(dampingRatio = 0.45f))
                    }
                }
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (feverActive) SpecialVisuals.FeverColor else MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .graphicsLayer { scaleX = feverPop.value; scaleY = feverPop.value },
                )
            }
        }
    }
}

/** How much the gameplay track speeds up while Fever Time runs. */
private const val FEVER_MUSIC_TEMPO = 1.12f

/**
 * A short, centred in-run announcement ("Fever ×2!", "Speed 5!", "New record!"):
 * punches in over the top of the board, holds a beat and fades. Under
 * reduce-motion it appears and disappears without the punch. One banner at a
 * time — a newer event simply restarts the animation with the new text.
 */
@Composable
private fun AnnouncementBanner(
    event: BannerEvent?,
    eventId: Int,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(eventId) {
        if (eventId > 0 && event != null) {
            if (reduceMotion) {
                progress.snapTo(1f)
                kotlinx.coroutines.delay(BANNER_HOLD_MS)
                progress.snapTo(0f)
            } else {
                progress.snapTo(0f)
                progress.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 900f))
                kotlinx.coroutines.delay(BANNER_HOLD_MS)
                progress.animateTo(0f, tween(durationMillis = 340, easing = FastOutSlowInEasing))
            }
        }
    }
    val visible = progress.value > 0.01f && event != null
    if (!visible) return
    val (text, color) = when (event!!.kind) {
        BannerKind.Fever -> stringResource(R.string.banner_fever) to SpecialVisuals.FeverColor
        BannerKind.SpeedUp -> stringResource(R.string.banner_speed_up, event.value) to SpecialVisuals.SurgeColor
        BannerKind.NewRecord -> stringResource(R.string.banner_new_record) to SpecialVisuals.RecordColor
    }
    Box(
        modifier = modifier.graphicsLayer {
            val t = progress.value
            scaleX = 0.7f + 0.3f * t
            scaleY = 0.7f + 0.3f * t
            alpha = t.coerceIn(0f, 1f)
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 18.dp, vertical = 6.dp),
        )
    }
}

/** How long an announcement banner holds before fading. */
private const val BANNER_HOLD_MS = 1100L

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

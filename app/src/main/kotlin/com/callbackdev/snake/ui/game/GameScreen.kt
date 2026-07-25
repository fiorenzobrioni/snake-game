package com.callbackdev.snake.ui.game

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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.StrokeCap
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
import com.callbackdev.snake.R
import com.callbackdev.snake.audio.GameAudio
import com.callbackdev.snake.ui.components.ShrinkToFitText
import com.callbackdev.snake.game.BackBehavior
import com.callbackdev.snake.game.ControlScheme
import com.callbackdev.snake.game.DEFAULT_ASPECT
import com.callbackdev.snake.game.Direction
import com.callbackdev.snake.game.EffectKind
import com.callbackdev.snake.game.EndlessWave
import com.callbackdev.snake.game.GameMode
import com.callbackdev.snake.game.GameState
import com.callbackdev.snake.game.GameStatus
import com.callbackdev.snake.game.LevelsMode
import kotlin.math.ceil
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

    // Back from the *paused* state would silently end a live run, so it asks
    // first (see QuitRunDialog). Dropped automatically if the state moves on
    // (e.g. the player resumes from the pause overlay behind the dialog).
    var showQuitConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(state.status) {
        if (state.status != GameStatus.Paused) showQuitConfirm = false
    }

    // Back handling during a *running* game depends on the Back-during-play
    // setting; while *paused* a live run is at stake, so Back asks for
    // confirmation instead of quitting silently; from any other state (setup,
    // game over) Back returns to the menu directly - there is no progress to
    // lose. We use a predictive handler so that, when "Keep playing" is on
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
            } else if (state.status == GameStatus.Paused) {
                // A pending resume countdown aborts back to the pause overlay,
                // then the dialog asks whether to really abandon the run.
                viewModel.cancelResume()
                showQuitConfirm = true
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

    // Zen: the board frame "breathes" - a slow, calm teal pulse (~5 s cycle)
    // that tells the eye the edges are open doorways, not walls. Under
    // reduce-motion it holds a steady soft glow instead.
    val zenActive = state.mode == GameMode.Zen &&
        (playing || state.status == GameStatus.GameOver)
    val zenTransition = rememberInfiniteTransition(label = "zenBreath")
    val zenBreath by zenTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "zenBreathPulse",
    )
    val zenGlow = when {
        !zenActive -> 0f
        viewModel.reduceMotion -> 0.6f
        else -> zenBreath
    }

    // Risk bonus: the frame smoulders while the snake is filling the board, its
    // intensity tracking the live multiplier. It breathes with the same slow
    // pulse as the Fever heat (steady under reduce-motion), so the two read as
    // the same family of "the stakes just went up" cues.
    val riskLevel = if (state.status == GameStatus.Running && state.inRiskZone) {
        ((state.riskFactor - GameState.RISK_ALERT_FACTOR) /
            (GameState.MAX_RISK_FACTOR - GameState.RISK_ALERT_FACTOR)).coerceIn(0f, 1f)
    } else {
        0f
    }
    val riskTarget by animateFloatAsState(
        targetValue = riskLevel,
        animationSpec = tween(durationMillis = 400),
        label = "riskLevel",
    )
    // A slower, heavier breath than the Fever flicker: this is dread, not heat.
    val riskTransition = rememberInfiniteTransition(label = "riskBreath")
    val riskPulse by riskTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "riskPulse",
    )
    val riskGlow = riskTarget * (if (viewModel.reduceMotion) 0.8f else riskPulse)

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
            // During setup the HUD carries nothing worth reading (score 0, and the
            // status line only repeats the selectors), and it would show through
            // the overlay above its pinned header. Alpha-hidden rather than
            // removed, so the space stays reserved and the board never resizes
            // between setup and play.
            Hud(
                modifier = Modifier.alpha(if (state.status == GameStatus.Ready) 0f else 1f),
                score = state.score,
                combo = state.combo,
                // Auto-growth: the body is a clock, so the HUD carries its face -
                // the live length plus a ring filling toward the next free segment.
                // Risk bonus: shown from the alert threshold up, so the HUD stays
                // calm while the multiplier is still near x1.
                riskFactor = if (playing && state.inRiskZone) state.riskFactor else 0f,
                showGrowth = state.autoGrowthIntervalTicks > 0 && onBoard,
                growthFraction = state.autoGrowthFraction,
                snakeLength = state.snake.size,
                growthEventId = viewModel.autoGrowEventId,
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
                            // Zen pins its difficulty (no obstacles by design),
                            // so the level would be noise: mode, pace and board.
                            state.mode == GameMode.Zen ->
                                "${state.mode.displayName} · ${viewModel.snakeSpeed.displayName} · ${viewModel.scale.displayName}"
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

            EffectTimersRow(
                effects = state.effectTimers,
                // The Endless wave rides in the same reserved row as the power-up
                // timers: it is exactly that, a timer the player must play around.
                wave = if (playing) state.activeWave else null,
                waveFraction = state.waveFraction,
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
                    // Ghost replay of the best run in this slot (Step 6.9.12).
                    ghostRun = viewModel.ghostRun,
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
                    // Risk bonus: the frame smoulders once the body is filling
                    // the arena, so the multiplier is felt and not just read.
                    riskGlow = riskGlow,
                    surgeFlash = surgeFlash.value,
                    zenGlow = zenGlow,
                    // Keep particles/redraw alive through the death-burst and
                    // level-vanish transitions (after `running` has gone false)
                    // and while the resume countdown pulses the head beacon.
                    effectsActive = state.status == GameStatus.Running ||
                        viewModel.deathAnimating || viewModel.levelVanishing ||
                        viewModel.resumeCountdown > 0,
                    modifier = boardModifier,
                )

                // The Shed ability lives in the board's bottom corner, where a
                // thumb already rests: pinned over the board rather than in the
                // control row, so it costs the board no height in any scheme.
                if (state.status == GameStatus.Running) {
                    // The button lives over the board, so it gets out of the way
                    // before the snake arrives - with enough warning to react to
                    // whatever is underneath it.
                    //
                    // The trigger distance is derived from the button's real
                    // footprint *in cells*, not from a fixed cell count: the button
                    // is a fixed 52dp while a cell shrinks with the board scale, so
                    // a constant "5 cells" covered the button on Explorer and barely
                    // its centre on Colossal (where the snake was already under it
                    // before it faded). Dividing by the measured cell size makes the
                    // clearance the same *physical* distance on every scale, and
                    // ABILITY_CLEARANCE_FACTOR then buys the reaction time.
                    val cellSize = minOf(maxWidth / state.board.width, maxHeight / state.board.height)
                    val clearance = if (cellSize > 0.dp) {
                        ceil((AbilityButtonReach / cellSize) * ABILITY_CLEARANCE_FACTOR).toInt()
                    } else {
                        ABILITY_CLEARANCE_FALLBACK_CELLS
                    }
                    val head = state.head
                    val inCorner = head.x >= state.board.width - clearance &&
                        head.y >= state.board.height - clearance
                    AbilityButton(
                        charge = state.abilityFraction,
                        ready = state.abilityReady,
                        reduceMotion = viewModel.reduceMotion,
                        dimmed = inCorner,
                        onUse = { viewModel.useAbility() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = 8.dp),
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

        // Centred in-run announcements (Fever Time / speed step / wave / record):
        // pinned over the **HUD**, not over the board. They are transient, and the
        // score line they briefly cover can wait a second - the playfield cannot,
        // so the board stays visible in full while they punch in.
        AnnouncementBanner(
            event = viewModel.bannerEvent,
            eventId = viewModel.bannerEventId,
            reduceMotion = viewModel.reduceMotion,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp),
        )

        when (state.status) {
            GameStatus.Ready -> ReadyOverlay(
                selectedMode = viewModel.mode,
                selectedLevel = viewModel.level,
                selectedSnakeSpeed = viewModel.snakeSpeed,
                selectedGrowthRate = viewModel.growthRate,
                selectedScale = viewModel.scale,
                // Read off the staged board, so the caption quotes the rhythm the
                // player will actually get at the selected scale.
                growthIntervalTicks = state.autoGrowthIntervalTicks,
                campaignCheckpoint = viewModel.campaignCheckpoint,
                campaignStartLevel = viewModel.campaignStartLevel,
                onModeSelected = { viewModel.selectMode(it) },
                onLevelSelected = { viewModel.selectLevel(it) },
                onSnakeSpeedSelected = { viewModel.selectSnakeSpeed(it) },
                onGrowthRateSelected = { viewModel.selectGrowthRate(it) },
                onScaleSelected = { viewModel.selectScale(it) },
                onCampaignStartSelected = { viewModel.selectCampaignStartLevel(it) },
                onPlay = { viewModel.start() },
                // Same exit as the system Back from setup: nothing is at stake here.
                onBack = { viewModel.toSetup(); onExitToMenu() },
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
                    newRank = viewModel.newRank?.displayName,
                    missions = viewModel.missionsProgress,
                    onPlayAgain = { viewModel.playAgain() },
                    onSetup = { viewModel.toSetup() },
                    onMenu = { viewModel.toSetup(); onExitToMenu() },
                )
            }

            GameStatus.Running -> Unit
        }

        if (showQuitConfirm && state.status == GameStatus.Paused) {
            QuitRunDialog(
                onQuit = {
                    showQuitConfirm = false
                    viewModel.toSetup()
                    onExitToMenu()
                },
                onKeepPlaying = { showQuitConfirm = false },
            )
        }
    }
}

/**
 * The live **risk multiplier**: how much every point is being scaled by the share
 * of the board the snake is filling. It only appears past the alert threshold -
 * below that the number is near x1 and would be noise - and it warms from amber
 * to crimson as the board closes in, so the reward and the danger are the same
 * reading. Sits beside the combo because both are score multipliers.
 */
@Composable
private fun RiskChip(factor: Float, reduceMotion: Boolean) {
    val hot = ((factor - GameState.RISK_ALERT_FACTOR) /
        (GameState.MAX_RISK_FACTOR - GameState.RISK_ALERT_FACTOR)).coerceIn(0f, 1f)
    val color = lerpColor(SpecialVisuals.FeverColor, SpecialVisuals.RiskColor, hot)
    // A slow throb that quickens with the danger; frozen under reduce-motion.
    val transition = rememberInfiniteTransition(label = "riskChip")
    val throb by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = (1100 - 500 * hot).toInt(), easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "riskThrob",
    )
    val scale = if (reduceMotion) 1f else throb
    val label = "x" + ((factor * 10).roundToInt() / 10f).toString().trimEnd('0').trimEnd('.')
    val description = stringResource(R.string.hud_risk_description, label)
    Text(
        text = stringResource(R.string.hud_risk, label),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .padding(end = 8.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics { contentDescription = description },
    )
}

/** Linear blend between two colours (only used for the risk chip's warm ramp). */
private fun lerpColor(from: Color, to: Color, t: Float): Color = Color(
    red = from.red + (to.red - from.red) * t,
    green = from.green + (to.green - from.green) * t,
    blue = from.blue + (to.blue - from.blue) * t,
    alpha = 1f,
)

/**
 * The auto-growth readout: the live snake length beside a ring that fills toward
 * the next free segment, so the pressure is always legible without a number the
 * player has to decode. It pops once each time a segment lands ([eventId]), which
 * is the only cue growth gets - a sound or a haptic every few seconds would nag.
 * Sized and padded to keep the HUD's fixed height (the board must never resize).
 */
@Composable
private fun GrowthMeter(
    fraction: Float,
    length: Int,
    eventId: Int,
    reduceMotion: Boolean,
) {
    // Smooth the per-tick steps; the wrap back to 0 is covered by the pop below.
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 180, easing = LinearEasing),
        label = "growthMeter",
    )
    val pop = remember { Animatable(1f) }
    LaunchedEffect(eventId) {
        if (eventId > 0 && !reduceMotion) {
            pop.snapTo(1.45f)
            pop.animateTo(1f, spring(dampingRatio = 0.45f))
        }
    }
    val ring = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f)
    val description = stringResource(R.string.hud_length, length)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = 8.dp)
            .semantics { contentDescription = description },
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = size.minDimension * 0.18f
                val inset = stroke / 2f
                drawCircle(
                    color = track,
                    radius = size.minDimension / 2f - inset,
                    style = Stroke(width = stroke),
                )
                drawArc(
                    color = ring,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = length.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** The combo multiplier's colour, warming through tiers as the streak climbs. */
@Composable
private fun comboTierColor(combo: Int): Color = when {
    combo >= 8 -> Color(0xFFFF5252)
    combo >= 5 -> Color(0xFFFFA000)
    combo >= 3 -> Color(0xFFFFD54F)
    else -> MaterialTheme.colorScheme.tertiary
}

private val EffectTimersRowHeight = 34.dp

/**
 * A row of countdown chips for the timed effects currently running, in a fixed
 * vertical slot: the height is reserved unconditionally - even with no effects
 * running - so the board below (which fills the remaining `weight(1f)` space)
 * keeps a constant size. Otherwise the row would appear/disappear with each
 * power-up and visibly resize the board, making the snake seem to jump.
 */
@Composable
private fun EffectTimersRow(
    effects: List<com.callbackdev.snake.game.ActiveEffect>,
    wave: EndlessWave?,
    waveFraction: Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EffectTimersRowHeight)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The wave first: it is the loudest thing happening to the board.
        if (wave != null) WaveChip(wave = wave, fraction = waveFraction)
        effects.forEach { effect -> EffectChip(effect) }
    }
}

/**
 * The running Endless wave, as a countdown chip in the timer row: the wave's name
 * over a bar that drains as it passes, in the wave's own colour. Same shape as the
 * power-up timer chips, because it is the same promise - this will end, plan for it.
 */
@Composable
private fun WaveChip(wave: EndlessWave, fraction: Float) {
    val color = when (wave) {
        EndlessWave.Feast -> SpecialVisuals.FeastColor
        EndlessWave.Drought -> SpecialVisuals.DroughtColor
        EndlessWave.Hailstorm -> SpecialVisuals.HailColor
    }
    val label = when (wave) {
        EndlessWave.Feast -> stringResource(R.string.wave_feast)
        EndlessWave.Drought -> stringResource(R.string.wave_drought)
        EndlessWave.Hailstorm -> stringResource(R.string.wave_hailstorm)
    }
    TimerChip(label = label, color = color, fraction = 1f - fraction.coerceIn(0f, 1f))
}

@Composable
private fun EffectChip(effect: com.callbackdev.snake.game.ActiveEffect) {
    val color = SpecialVisuals.accent(effect.kind)
    val label = when (effect.kind) {
        com.callbackdev.snake.game.EffectKind.Haste -> stringResource(R.string.effect_lightning)
        com.callbackdev.snake.game.EffectKind.Slow -> stringResource(R.string.effect_snail)
        com.callbackdev.snake.game.EffectKind.Ghost -> stringResource(R.string.effect_star)
        com.callbackdev.snake.game.EffectKind.Freeze -> stringResource(R.string.effect_freeze)
        com.callbackdev.snake.game.EffectKind.Quake -> stringResource(R.string.effect_quake)
    }
    TimerChip(label = label, color = color, fraction = effect.fraction)
}

/** A labelled chip over a draining bar - the shared body of the timer row's chips. */
@Composable
private fun TimerChip(label: String, color: Color, fraction: Float) {
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
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
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
    modifier: Modifier = Modifier,
    score: Int,
    combo: Int,
    riskFactor: Float,
    statusLabel: String,
    timeLabel: String?,
    feverActive: Boolean,
    lives: Int,
    showGrowth: Boolean,
    growthFraction: Float,
    snakeLength: Int,
    growthEventId: Int,
    showPause: Boolean,
    reduceMotion: Boolean,
    onPause: () -> Unit,
) {
    // Rolling score counter (step 3.6).
    val animatedScore by animateIntAsState(targetValue = score, animationSpec = tween(300), label = "score")
    Column(
        modifier = modifier
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
            if (riskFactor > 1f) {
                RiskChip(factor = riskFactor, reduceMotion = reduceMotion)
            }
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
            if (showGrowth) {
                GrowthMeter(
                    fraction = growthFraction,
                    length = snakeLength,
                    eventId = growthEventId,
                    reduceMotion = reduceMotion,
                )
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
        BannerKind.ShedReady -> stringResource(R.string.banner_shed_ready) to SpecialVisuals.ShedColor
        BannerKind.Wave -> when (event.wave) {
            EndlessWave.Feast -> stringResource(R.string.banner_wave_feast) to SpecialVisuals.FeastColor
            EndlessWave.Drought -> stringResource(R.string.banner_wave_drought) to SpecialVisuals.DroughtColor
            EndlessWave.Hailstorm -> stringResource(R.string.banner_wave_hailstorm) to SpecialVisuals.HailColor
            null -> "" to SpecialVisuals.FeastColor
        }
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

/**
 * The Shed button's reach from the board's bottom-end corner: its own size plus the
 * padding that insets it. Converted to cells at the measured cell size, this is how
 * many cells the button actually sits on.
 */
private val AbilityButtonReach = 60.dp

/**
 * How much further out than its own footprint the Shed button starts fading. The
 * button has to be gone *before* the head arrives, with room to read - and react to
 * - whatever it was covering, so the clearance is a comfortable multiple rather
 * than a hair's breadth.
 */
private const val ABILITY_CLEARANCE_FACTOR = 2.8f

/** Used only if the play area has not been measured yet (a degenerate first frame). */
private const val ABILITY_CLEARANCE_FALLBACK_CELLS = 9

/** How long an announcement banner holds before fading. */
private const val BANNER_HOLD_MS = 1100L


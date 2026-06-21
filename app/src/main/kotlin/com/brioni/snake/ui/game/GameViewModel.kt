package com.brioni.snake.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.brioni.snake.audio.GameHaptics
import com.brioni.snake.audio.GameSfx
import com.brioni.snake.data.SettingsRepository
import com.brioni.snake.game.Achievement
import com.brioni.snake.game.BackBehavior
import com.brioni.snake.game.BoardDimensions
import com.brioni.snake.game.BoardScale
import com.brioni.snake.game.ControlScheme
import com.brioni.snake.game.Challenge
import com.brioni.snake.game.EffectKind
import com.brioni.snake.game.DEFAULT_ASPECT
import com.brioni.snake.game.Direction
import com.brioni.snake.game.FoodEffect
import com.brioni.snake.game.GameEngine
import com.brioni.snake.game.GameEvent
import com.brioni.snake.game.GameMode
import com.brioni.snake.game.GameState
import com.brioni.snake.game.GameStatus
import com.brioni.snake.game.Level
import com.brioni.snake.game.LevelsMode
import com.brioni.snake.game.Position
import com.brioni.snake.game.RunStats
import com.brioni.snake.game.Skin
import com.brioni.snake.game.SnakeSpeed
import com.brioni.snake.game.SpecialFrequency
import com.brioni.snake.game.boardFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.math.max

/** Which particle burst the renderer should spawn for an [EatEvent]. */
enum class BurstStyle {
    /** Outward explosion — growing food, jackpot, explosion. */
    Eat,

    /** Inward implosion — shrinking food, earthquake. */
    Implode,

    /** Gentle upward fade — an ignored food that timed out and vanished. */
    Vanish,
}

/**
 * A board event, surfaced to the renderer to spawn a particle burst. [style]
 * selects which burst to play at [cell].
 */
data class EatEvent(val cell: Position, val span: Int, val color: Color, val style: BurstStyle)

/**
 * A short floating label to spawn on the board (e.g. "+5s" / "-3s" for the Time
 * Attack clock blocks), placed at [cell] (its [span] centres the text).
 */
data class FloatingTextEvent(val cell: Position, val span: Int, val text: String, val color: Color)

/**
 * A one-tick hazard telegraph: the renderer flashes a danger warning over the
 * [span]-cell square at [cell] the tick before the snake would eat the hazard.
 */
data class HazardWarnEvent(val cell: Position, val span: Int)

/**
 * Holds the [GameState] and drives the tick loop. All game rules live in
 * [GameEngine]; this class owns the timing coroutine, surfaces state to Compose
 * and publishes the data the renderer needs for inter-tick interpolation
 * ([previousSnake] + [tickTimeNanos]) and effects ([eatEvent]/[deathEventId]).
 *
 * Player preferences (level, board scale, control scheme) and highscores are
 * read from and written back to [repo]; the board's concrete dimensions are
 * computed from the selected [scale] and the measured play-area aspect ratio.
 */
class GameViewModel(
    private val repo: SettingsRepository,
    private val sfx: GameSfx = GameSfx.None,
    private val haptics: GameHaptics = GameHaptics.None,
) : ViewModel() {

    private var engine = GameEngine()

    /** The selected granularity preset; concrete dims derive from it + the aspect. */
    var scale by mutableStateOf(DEFAULT_SCALE)
        private set

    /** The selected snake pace (loaded from settings); drives the tick interval. */
    var snakeSpeed by mutableStateOf(SnakeSpeed.DEFAULT)
        private set

    /** Active steering scheme (loaded from settings). */
    var controlScheme by mutableStateOf(DEFAULT_CONTROL)
        private set

    /** What the Back gesture does during active play (loaded from settings). */
    var backBehavior by mutableStateOf(BackBehavior.DEFAULT)
        private set

    /** Whether the retro CRT post-filter is enabled (loaded from settings). */
    var crtEnabled by mutableStateOf(false)
        private set

    /** Accessibility: damp screen shake, particle bursts and near-miss flashes (setting). */
    var reduceMotion by mutableStateOf(false)
        private set

    /** Active visual skin (loaded from settings); drives the renderer [palette]. */
    var skin by mutableStateOf(Skin.Classic)
        private set

    /** Whether harmful specials (earthquake/explosion/snail) may spawn (setting). */
    var hazardsEnabled by mutableStateOf(true)
        private set

    /** How often specials (power-ups / hazards) spawn (setting). */
    var specialFrequency by mutableStateOf(SpecialFrequency.Standard)
        private set

    /** Active play mode; highscores are tracked per (mode, level, scale). */
    var mode by mutableStateOf(GameMode.Endless)
        private set

    /** The colour palette + style flags for the active [skin]. */
    val palette: SkinPalette get() = paletteFor(skin)

    var state by mutableStateOf(engine.setup(DEFAULT_LEVEL, boardFor(DEFAULT_SCALE, DEFAULT_ASPECT)))
        private set

    /** The snake as it was before the most recent tick, for smooth motion. */
    var previousSnake by mutableStateOf(state.snake)
        private set

    /**
     * `System.nanoTime()` of the most recent tick (or reset). Updated atomically
     * with [state]/[previousSnake] so the renderer can derive the interpolation
     * fraction from a single consistent snapshot.
     */
    var tickTimeNanos by mutableLongStateOf(System.nanoTime())
        private set

    /** Latest eat event and a monotonic id so repeats are observable. */
    var eatEvent: EatEvent? = null
        private set
    var eatEventId by mutableIntStateOf(0)
        private set

    /** Latest floating-text event and a monotonic id so repeats are observable. */
    var floatingText: FloatingTextEvent? = null
        private set
    var floatingTextId by mutableIntStateOf(0)
        private set

    /** Bumped when the snake dies, so the UI can trigger a screen shake. */
    var deathEventId by mutableIntStateOf(0)
        private set

    /** Bumped on earthquake/explosion so the UI can shake the board mid-game. */
    var shakeEventId by mutableIntStateOf(0)
        private set

    /** Bumped on a near-miss / grace dodge so the UI can flash a brief danger cue. */
    var nearMissEventId by mutableIntStateOf(0)
        private set

    /** Latest hazard telegraph and a monotonic id so repeats are observable. */
    var hazardWarn: HazardWarnEvent? = null
        private set
    var hazardWarnId by mutableIntStateOf(0)
        private set

    /** Best score for the current (level, scale), and whether the last run beat it. */
    var bestScore by mutableIntStateOf(0)
        private set
    var isNewBest by mutableStateOf(false)
        private set

    /** Achievements unlocked by the most recent run (for the game-over banner). */
    var newlyUnlocked by mutableStateOf<List<Achievement>>(emptyList())
        private set

    /** Levels mode: seconds left on the intro countdown (0 when not counting). */
    var introCountdown by mutableIntStateOf(0)
        private set

    /** Levels mode: true when the current intro follows a life loss. */
    var introIsRespawn by mutableStateOf(false)
        private set

    // Per-run accumulators feeding the achievement check at game over.
    private var runFoodsEaten = 0
    private var runMaxCombo = 0
    private var runUsedExplosion = false
    private var runUsedStar = false
    private var runUsedJackpot = false
    private var runStartMs = 0L
    private var runMaxLevel = 1
    private var runMaxCycle = 1
    private var runMaxDepth = 0
    private var runLivesLost = 0
    private var runFlawlessLap = false
    private var runExtraLives = 0
    private var runMaxLength = 0

    private var loop: Job? = null
    private var bestJob: Job? = null
    private var introJob: Job? = null
    private var lastAspect = DEFAULT_ASPECT

    /** Set by Quick Play: start the run as soon as the board has been measured. */
    private var pendingQuickStart = false

    /**
     * Non-null while a seeded **challenge** run (Daily or Random) is configured
     * (vs a normal run): it pins the mode/level/scale and seeds the engine. The
     * settings sync is suspended and the score routing adapts. Cleared by [toSetup].
     */
    var activeChallenge by mutableStateOf<Challenge?>(null)
        private set

    /** For a Daily challenge only: the epoch day, used to route the score + best. */
    private var dailyEpochDay: Long? = null

    /** True while the active challenge is a one-off Random run (no stored best). */
    val isRandomChallenge: Boolean get() = activeChallenge != null && dailyEpochDay == null

    /** True while the active challenge is the date-seeded Daily run. */
    val isDailyChallenge: Boolean get() = dailyEpochDay != null

    /** Set by [requestDailyStart] / [requestRandomStart]; consumed once measured. */
    private var pendingChallenge: Challenge? = null

    /** Wall-clock of the last near-miss haptic, for throttling (see [NEAR_MISS_MIN_GAP_NANOS]). */
    private var lastNearMissNanos = 0L

    val level: Level get() = state.level
    val board: BoardDimensions get() = state.board

    /** Current score multiplier (consecutive-eat streak), for the HUD. */
    val combo: Int get() = state.combo

    init {
        // Seed level/scale/scheme from persisted settings; only re-apply while
        // Ready so a mid-game preference change can't disturb a live board.
        viewModelScope.launch {
            repo.settings.collect { settings ->
                controlScheme = settings.controlScheme
                backBehavior = settings.backBehavior
                crtEnabled = settings.crtEnabled
                reduceMotion = settings.reduceMotion
                skin = settings.skin
                // The Daily Challenge pins the spawn-affecting toggles so the run is
                // identical for everyone; only sync them from settings outside it.
                if (activeChallenge == null) {
                    hazardsEnabled = settings.hazardsEnabled
                    specialFrequency = settings.specialFrequency
                }
                // While a challenge is configured its fixed mode/level/scale must
                // stick, so skip the persisted-settings sync below.
                if (state.status == GameStatus.Ready && activeChallenge == null) {
                    // Levels mode ignores the difficulty selector: it is pinned
                    // to its score level so this collector can't keep resetting.
                    val targetLevel = if (settings.mode == GameMode.Levels) LevelsMode.SCORE_LEVEL else settings.level
                    val levelChanged = targetLevel != state.level
                    val modeChanged = settings.mode != mode
                    val speedChanged = settings.snakeSpeed != snakeSpeed
                    mode = settings.mode
                    scale = settings.scale
                    snakeSpeed = settings.snakeSpeed
                    if (levelChanged || modeChanged) {
                        resetTo(engine.setup(targetLevel, boardFor(scale, lastAspect), mode, snakeSpeed))
                    } else if (speedChanged) {
                        // Pace only: no board/obstacle rebuild needed, just restamp
                        // the speed so the loop reads it from the next tick on.
                        state = state.copy(snakeSpeed = snakeSpeed)
                        reconfigureBoard()
                    } else {
                        reconfigureBoard()
                    }
                    refreshBest()
                }
            }
        }
    }

    fun selectLevel(level: Level) {
        if (state.status != GameStatus.Ready) return
        if (mode == GameMode.Levels) return // the selector is disabled and ignored
        viewModelScope.launch { repo.setLevel(level) }
        resetTo(engine.setup(level, state.board, mode, snakeSpeed))
        refreshBest()
    }

    fun selectSnakeSpeed(speed: SnakeSpeed) {
        if (state.status != GameStatus.Ready) return
        snakeSpeed = speed
        viewModelScope.launch { repo.setSnakeSpeed(speed) }
        // Pace is read live from the state each tick, so a restamp is enough.
        state = state.copy(snakeSpeed = speed)
    }

    fun selectMode(newMode: GameMode) {
        if (state.status != GameStatus.Ready) return
        mode = newMode
        viewModelScope.launch { repo.setGameMode(newMode) }
        // Levels pins its score level; leaving it, the settings collector
        // restores the user's persisted difficulty right after this reset.
        val level = if (newMode == GameMode.Levels) LevelsMode.SCORE_LEVEL else state.level
        resetTo(engine.setup(level, state.board, newMode, snakeSpeed))
        refreshBest()
    }

    fun selectScale(scale: BoardScale) {
        if (state.status != GameStatus.Ready) return
        this.scale = scale
        viewModelScope.launch { repo.setScale(scale) }
        reconfigureBoard()
        refreshBest()
    }

    /**
     * Called by the UI when the play area is (re)measured. Resizes the board to
     * fill it, but only while [GameStatus.Ready]; ignored once a game starts so
     * dimensions stay locked during play. Idempotent against measurement jitter.
     * Also the gate for a pending Quick Play start: the run only launches once the
     * board has been fitted to the device.
     */
    fun onPlayAreaMeasured(aspectRatio: Float) {
        if (state.status != GameStatus.Ready) return
        if (aspectRatio > 0f && aspectRatio != lastAspect) {
            lastAspect = aspectRatio
            reconfigureBoard()
        }
        val challenge = pendingChallenge
        if (challenge != null) {
            pendingChallenge = null
            startChallenge(challenge)
            return
        }
        if (pendingQuickStart) {
            pendingQuickStart = false
            start()
        }
    }

    /**
     * Menu "Play" (Quick Play): launch with the current persisted settings,
     * skipping the Custom setup overlay. Deferred until [onPlayAreaMeasured] so the
     * board fits the device before the run locks its dimensions.
     */
    fun requestQuickStart() {
        if (state.status == GameStatus.Ready) pendingQuickStart = true
    }

    /**
     * Launch [epochDay]'s **Daily** Challenge: the score and best are routed to the
     * per-day daily store. Deferred to [onPlayAreaMeasured] so the board is fitted
     * to the device first.
     */
    fun requestDailyStart(epochDay: Long) {
        if (state.status != GameStatus.Ready) return
        dailyEpochDay = epochDay
        activeChallenge = Challenge.forDay(epochDay)
        pendingChallenge = activeChallenge
    }

    /**
     * Launch a one-off **Random** challenge ([challenge] generated by the screen):
     * same pinning/seeding as the daily, but no stored best or streak.
     */
    fun requestRandomStart(challenge: Challenge) {
        if (state.status != GameStatus.Ready) return
        dailyEpochDay = null
        activeChallenge = challenge
        pendingChallenge = challenge
    }

    /** Reseeds the engine and starts the [challenge] run on the measured board. */
    private fun startChallenge(challenge: Challenge) {
        // A fresh seeded engine makes the obstacle layout and the food sequence
        // the challenge's (for a given board size).
        engine = GameEngine(Random(challenge.seed))
        mode = challenge.mode
        scale = challenge.scale
        snakeSpeed = SnakeSpeed.DEFAULT
        // Pin the spawn-affecting toggles so a seeded run is reproducible,
        // applying the modifier (Bonus Rush, Frenzy, ...) on top.
        hazardsEnabled = challenge.modifier.hazardsOverride ?: true
        specialFrequency = challenge.modifier.specialFrequencyOverride ?: SpecialFrequency.Standard
        resetTo(engine.setup(challenge.level, boardFor(scale, lastAspect), mode, snakeSpeed))
        resetTo(engine.start(state))
        isNewBest = false
        refreshBest()
        beginRun()
        afterReset()
    }

    private fun reconfigureBoard() {
        val dims = boardFor(scale, lastAspect)
        if (dims != state.board) resetTo(engine.setup(state.level, dims, mode, snakeSpeed))
    }

    fun start() {
        // Re-fit the board to the latest measured play area before locking it in,
        // so the very first run can't start from a stale/too-tall measurement.
        reconfigureBoard()
        resetTo(engine.start(state))
        isNewBest = false
        beginRun()
        afterReset()
    }

    fun setDirection(direction: Direction) {
        state = engine.changeDirection(state, direction)
    }

    fun turnLeft() {
        state = engine.turnLeft(state)
    }

    fun turnRight() {
        state = engine.turnRight(state)
    }

    fun togglePause() {
        state = engine.togglePause(state)
        if (state.status == GameStatus.Running) runLoop() else stopLoop()
    }

    fun playAgain() {
        // A Daily Challenge replays the exact same seeded run (retry for a better
        // score); a normal run just starts a fresh game with the current config.
        activeChallenge?.let { startChallenge(it); return }
        resetTo(engine.newGame(state.level, state.board, mode, snakeSpeed))
        isNewBest = false
        beginRun()
        afterReset()
    }

    /** Routes a freshly reset game: Levels opens with its intro, others just run. */
    private fun afterReset() {
        if (state.status == GameStatus.LevelIntro) {
            introIsRespawn = false
            startIntro()
        } else {
            runLoop()
        }
    }

    /**
     * Levels mode: runs the 3-2-1 countdown over the staged board, then hands
     * back to the engine ([GameEngine.beginLevel]) and restarts the loop.
     */
    private fun startIntro() {
        introJob?.cancel()
        introJob = viewModelScope.launch {
            introCountdown = INTRO_SECONDS
            while (introCountdown > 0) {
                delay(1_000)
                introCountdown--
            }
            resetTo(engine.beginLevel(state))
            runLoop()
        }
    }

    private fun cancelIntro() {
        introJob?.cancel()
        introJob = null
        introCountdown = 0
        introIsRespawn = false
    }

    /** Resets the per-run achievement accumulators at the start of a run. */
    private fun beginRun() {
        runFoodsEaten = 0
        runMaxCombo = 0
        runUsedExplosion = false
        runUsedStar = false
        runUsedJackpot = false
        runStartMs = System.currentTimeMillis()
        runMaxLevel = 1
        runMaxCycle = 1
        runMaxDepth = levelDepth(state.levelIndex, state.speedCycle)
        runLivesLost = 0
        runFlawlessLap = false
        runExtraLives = 0
        runMaxLength = state.snake.size
        newlyUnlocked = emptyList()
    }

    /**
     * Ends the current run and returns the game screen to the pre-game setup
     * ([GameStatus.Ready]). Used both by the game-over "Game setup" action and
     * when leaving for the main menu (so the next visit starts at setup).
     */
    fun toSetup() {
        stopLoop()
        cancelIntro()
        pendingQuickStart = false
        pendingChallenge = null
        if (activeChallenge != null) {
            // Leaving a challenge run: drop the seeded engine and restore the player's
            // persisted Custom setup (the settings collector won't re-emit on its own).
            activeChallenge = null
            dailyEpochDay = null
            engine = GameEngine()
            viewModelScope.launch {
                val s = repo.settings.first()
                mode = s.mode
                scale = s.scale
                snakeSpeed = s.snakeSpeed
                // Restore the spawn toggles the daily had pinned (the settings
                // collector only re-emits on an actual change).
                hazardsEnabled = s.hazardsEnabled
                specialFrequency = s.specialFrequency
                val level = if (mode == GameMode.Levels) LevelsMode.SCORE_LEVEL else s.level
                resetTo(engine.setup(level, boardFor(scale, lastAspect), mode, snakeSpeed))
                refreshBest()
            }
            return
        }
        resetTo(engine.setup(state.level, state.board, mode, snakeSpeed))
        refreshBest()
    }

    /** Replaces the state and resets interpolation bookkeeping to it. */
    private fun resetTo(newState: GameState) {
        previousSnake = newState.snake
        state = newState
        tickTimeNanos = System.nanoTime()
    }

    private fun runLoop() {
        stopLoop()
        loop = viewModelScope.launch {
            while (state.status == GameStatus.Running) {
                // Read the *effective* interval each iteration so Lightning/Snail/
                // Freeze actually change the pace mid-run.
                delay(state.tickIntervalMillis)
                if (state.status == GameStatus.Running) advance()
            }
        }
    }

    /** One simulation step, reacting to the events the engine emitted. */
    private fun advance() {
        val before = state
        val after = engine.tick(before, hazardsEnabled, specialFrequency)
        runMaxLength = max(runMaxLength, after.snake.size)

        after.lastEvents.forEach { event ->
            when (event) {
                is GameEvent.Ate -> {
                    eatEvent = EatEvent(event.food.position, event.food.span, palette.foodColor(event.food), BurstStyle.Eat)
                    eatEventId++
                    val grown = (event.food.effect as? FoodEffect.Grow)?.segments ?: 0
                    if (grown > 0) {
                        floatingText = FloatingTextEvent(event.food.position, event.food.span, "+$grown", palette.foodColor(event.food))
                        floatingTextId++
                    }
                    sfx.ate(event.food, event.combo)
                    haptics.eat()
                    runFoodsEaten++
                    runMaxCombo = max(runMaxCombo, event.combo)
                }
                is GameEvent.Shrunk -> {
                    eatEvent = EatEvent(event.food.position, event.food.span, palette.foodColor(event.food), BurstStyle.Implode)
                    eatEventId++
                    if (event.removed > 0) {
                        floatingText = FloatingTextEvent(event.food.position, event.food.span, "-${event.removed}", palette.foodColor(event.food))
                        floatingTextId++
                    }
                    sfx.shrunk(event.food)
                    haptics.eat()
                }
                GameEvent.Died -> {
                    deathEventId++
                    sfx.died()
                    haptics.death()
                    onGameOver(after.score)
                }
                GameEvent.NearMiss -> {
                    // Throttled so hugging an edge gives an occasional tick/flash, not a buzz.
                    val now = System.nanoTime()
                    if (now - lastNearMissNanos >= NEAR_MISS_MIN_GAP_NANOS) {
                        haptics.nearMiss()
                        nearMissEventId++
                        lastNearMissNanos = now
                    }
                }
                GameEvent.GraceDodge -> {
                    // A whisker from death: a firmer cue and a flash sell the save.
                    // Suppress the near-miss tick that would otherwise pile on right after.
                    haptics.special()
                    nearMissEventId++
                    lastNearMissNanos = System.nanoTime()
                }
                is GameEvent.HazardImminent -> {
                    // One tick before the snake would eat a hazard: telegraph it
                    // with a warning flash (the renderer gates it under reduce-motion)
                    // and a pre-haptic, so the strike never lands unannounced.
                    hazardWarn = HazardWarnEvent(event.food.position, event.food.span)
                    hazardWarnId++
                    haptics.hazardWarning()
                }
                is GameEvent.Exploded -> {
                    // Explosion: outward burst at the blast + a board shake.
                    eatEvent = EatEvent(event.food.position, event.food.span, palette.special, BurstStyle.Eat)
                    eatEventId++
                    shakeEventId++
                    sfx.special(event.food)
                    haptics.special()
                    runUsedExplosion = true
                }
                is GameEvent.JackpotHit -> {
                    eatEvent = EatEvent(event.food.position, event.food.span, palette.foodColor(event.food), BurstStyle.Eat)
                    eatEventId++
                    sfx.special(event.food)
                    haptics.special()
                    runUsedJackpot = true
                    runFoodsEaten++
                }
                is GameEvent.TimeGained -> {
                    // Time Attack: a green burst + a rising "+Ns" callout.
                    eatEvent = EatEvent(event.food.position, event.food.span, SpecialVisuals.TimeBonusColor, BurstStyle.Eat)
                    eatEventId++
                    floatingText = FloatingTextEvent(event.food.position, event.food.span, "+${event.seconds}s", SpecialVisuals.TimeBonusColor)
                    floatingTextId++
                    sfx.special(event.food)
                    haptics.special()
                }
                is GameEvent.TimeLost -> {
                    // Time Attack: a red implosion + a "-Ns" callout + a sting shake.
                    eatEvent = EatEvent(event.food.position, event.food.span, SpecialVisuals.TimePenaltyColor, BurstStyle.Implode)
                    eatEventId++
                    floatingText = FloatingTextEvent(event.food.position, event.food.span, "-${event.seconds}s", SpecialVisuals.TimePenaltyColor)
                    floatingTextId++
                    shakeEventId++
                    sfx.special(event.food)
                    haptics.special()
                }
                is GameEvent.FoodVanished -> {
                    // An ignored food timed out: a quiet upward fade, no sound.
                    eatEvent = EatEvent(event.food.position, event.food.span, palette.foodColor(event.food), BurstStyle.Vanish)
                    eatEventId++
                }
                is GameEvent.EffectStarted -> {
                    sfx.special(event.food)
                    haptics.special()
                    if (event.kind == EffectKind.Ghost) runUsedStar = true
                }
                is GameEvent.EffectExpired -> Unit
                is GameEvent.LevelAdvanced -> {
                    sfx.levelUp()
                    haptics.levelUp()
                    runMaxLevel = max(runMaxLevel, event.levelIndex)
                    runMaxCycle = max(runMaxCycle, event.speedCycle)
                    runMaxDepth = max(runMaxDepth, levelDepth(event.levelIndex, event.speedCycle))
                    // First full lap cleared with no life lost so far: a flawless lap.
                    if (event.speedCycle >= 2 && runLivesLost == 0) runFlawlessLap = true
                }
                is GameEvent.LifeLost -> {
                    // A non-final crash: the lighter quake shake, not the death one.
                    shakeEventId++
                    sfx.lifeLost()
                    haptics.lifeLost()
                    runLivesLost++
                }
                is GameEvent.LifeGained -> {
                    eatEvent = EatEvent(event.food.position, event.food.span, SpecialVisuals.ExtraLifeColor, BurstStyle.Eat)
                    eatEventId++
                    val text = if (event.capped) "+${LevelsMode.LIFE_CAP_BONUS}" else "+1♥"
                    floatingText = FloatingTextEvent(event.food.position, event.food.span, text, SpecialVisuals.ExtraLifeColor)
                    floatingTextId++
                    sfx.lifeGained()
                    haptics.special()
                    if (!event.capped) runExtraLives++
                }
            }
        }

        if (after.status == GameStatus.LevelIntro) {
            // Levels: the tick staged the next board (advance or respawn). Use
            // resetTo — not the interpolation commit — so the renderer doesn't
            // tween the old snake across the board to the new spawn.
            resetTo(after)
            introIsRespawn = after.lastEvents.any { it is GameEvent.LifeLost }
            startIntro()
            return
        }

        // Commit the interpolation snapshot atomically: previous, current, time.
        previousSnake = before.snake
        state = after
        tickTimeNanos = System.nanoTime()
    }

    /** Routes a board swipe: steers by the swiped absolute [direction]. */
    fun onSwipe(direction: Direction) {
        setDirection(direction)
    }

    private fun onGameOver(score: Int) {
        isNewBest = score > bestScore
        val stats = RunStats(
            mode = mode,
            score = score,
            maxCombo = runMaxCombo,
            durationMs = System.currentTimeMillis() - runStartMs,
            foodsEaten = runFoodsEaten,
            usedExplosion = runUsedExplosion,
            usedStar = runUsedStar,
            usedJackpot = runUsedJackpot,
            maxLevelReached = if (mode == GameMode.Levels) runMaxLevel else 0,
            maxSpeedCycle = runMaxCycle,
            maxLevelDepth = if (mode == GameMode.Levels) runMaxDepth else 0,
            flawlessLap = mode == GameMode.Levels && runFlawlessLap,
            extraLivesGained = runExtraLives,
            maxSnakeLength = runMaxLength,
        )
        // Persist; the bestJob collector reflects the new value back into state.
        // The Daily keeps its own per-day best (and streak); a Random challenge is
        // a one-off and stores nothing; a normal run uses the (mode, level, scale) records.
        val epochDay = dailyEpochDay
        when {
            epochDay != null -> viewModelScope.launch { repo.submitDailyScore(epochDay, score) }
            activeChallenge != null -> Unit // Random challenge: not recorded.
            else -> {
                viewModelScope.launch { repo.submitScore(mode, state.level, scale, score) }
                if (mode == GameMode.Levels) {
                    // Levels: also track how deep the run got, for the Records screen.
                    val completed = (state.speedCycle - 1) * LevelsMode.LEVEL_COUNT + (state.levelIndex - 1)
                    viewModelScope.launch { repo.submitLevelsProgress(scale, completed) }
                }
            }
        }
        // Evaluate achievements against the run and surface any new unlocks.
        viewModelScope.launch {
            val already = repo.unlockedAchievements().first()
            val earned = Achievement.earnedBy(stats, already)
            if (earned.isNotEmpty()) {
                repo.addUnlockedAchievements(earned.map { it.name })
                newlyUnlocked = earned
            }
        }
    }

    /**
     * Tracks the best score for the current run: a Daily reads its per-day best, a
     * Random challenge has none (0), else the (mode, level, scale) record.
     */
    private fun refreshBest() {
        bestJob?.cancel()
        val epochDay = dailyEpochDay
        val randomChallenge = isRandomChallenge
        val level = state.level
        val currentScale = scale
        val currentMode = mode
        bestJob = viewModelScope.launch {
            when {
                epochDay != null -> repo.dailyBest(epochDay).collect { bestScore = it }
                randomChallenge -> bestScore = 0
                else -> repo.highScore(currentMode, level, currentScale).collect { bestScore = it }
            }
        }
    }

    /**
     * Levels mode: the deepest 1-based linear progress of a (level, cycle) pair,
     * `(speedCycle - 1) * LEVEL_COUNT + levelIndex` - so Level 10 of Speed 1 is 10,
     * Level 10 of Speed 2 is 20, and so on. Feeds the tower-climb achievements.
     */
    private fun levelDepth(levelIndex: Int, speedCycle: Int): Int =
        (speedCycle - 1) * LevelsMode.LEVEL_COUNT + levelIndex

    private fun stopLoop() {
        loop?.cancel()
        loop = null
    }

    override fun onCleared() {
        stopLoop()
        cancelIntro()
    }

    companion object {
        private val DEFAULT_LEVEL = Level.Beginner
        private val DEFAULT_SCALE = BoardScale.Classic
        private val DEFAULT_CONTROL = ControlScheme.Swipe

        /** Levels mode: seconds counted down by the intro overlay. */
        private const val INTRO_SECONDS = 3

        fun factory(
            repo: SettingsRepository,
            sfx: GameSfx = GameSfx.None,
            haptics: GameHaptics = GameHaptics.None,
        ) = viewModelFactory {
            initializer { GameViewModel(repo, sfx, haptics) }
        }

        /** Minimum gap between near-miss haptics, so riding an edge does not buzz every tick. */
        private const val NEAR_MISS_MIN_GAP_NANOS = 220_000_000L
    }
}

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
import com.brioni.snake.audio.GameSfx
import com.brioni.snake.data.SettingsRepository
import com.brioni.snake.game.Achievement
import com.brioni.snake.game.BackBehavior
import com.brioni.snake.game.BoardDimensions
import com.brioni.snake.game.BoardScale
import com.brioni.snake.game.ControlScheme
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
import com.brioni.snake.game.ViewMode
import com.brioni.snake.game.boardFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
) : ViewModel() {

    private val engine = GameEngine()

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

    /** Whether the 3D barrier's electric/plasma flow is enabled (setting). */
    var electricWalls by mutableStateOf(true)
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

    /** The board presentation (setting): flat 2D, follow chase-cam, or fixed-north. */
    var viewMode by mutableStateOf(ViewMode.TwoD)
        private set

    /** True while either 3D view is selected as the standing setting. */
    val threeDWorldEnabled: Boolean get() = viewMode.is3D

    /** Active play mode; highscores are tracked per (mode, level, scale). */
    var mode by mutableStateOf(GameMode.Classic)
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

    /**
     * True while the 3D (chase-cam) *hazard* is on screen — from the tilt-in until
     * the tilt-out completes. Distinct from the effect timer so it can bracket the
     * cinematic. The whole 3D World mode is handled separately via [threeDActive].
     */
    var threeDHazardActive by mutableStateOf(false)
        private set

    /**
     * Whether the board should render (and steer) in the 3D chase-cam: the timed
     * hazard, or the "3D World" setting that plays every mode in 3D. Gates the
     * relative-controls override and the perspective renderer.
     */
    val threeDActive: Boolean get() = threeDWorldEnabled || threeDHazardActive

    /**
     * Whether steering should be heading-relative (left/right turns) rather than
     * absolute. True for the rotating perspective views (chase-cam hazard or the
     * "3D" follow view), but **false** for the north-locked "3D Fixed" view, whose
     * board never rotates - there swipe/D-pad behave exactly like the flat 2D board.
     */
    val relativeSteering: Boolean get() = threeDActive && !viewMode.fixedNorth

    /**
     * Bumped when the 3D cinematic should play (tilt-in on start, tilt-out on
     * expiry). The screen observes it to drive the camera-blend animation, the
     * same id-counter pattern as [deathEventId] / [shakeEventId].
     */
    var cinematicId by mutableIntStateOf(0)
        private set

    /**
     * Transient, UI-only freeze of the tick loop while a 3D tilt animation plays.
     * Not [GameStatus.Paused] (no overlay/blur) and not a model field — the model
     * stays unaware of the camera. Holding the loop also pauses the effect-timer
     * aging, so the 3D duration counts only real play time.
     */
    private var cinematicHold = false

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
    private var runExtraLives = 0
    private var runMaxLength = 0

    private var loop: Job? = null
    private var bestJob: Job? = null
    private var introJob: Job? = null
    private var lastAspect = DEFAULT_ASPECT

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
                electricWalls = settings.electricWallsEnabled
                skin = settings.skin
                hazardsEnabled = settings.hazardsEnabled
                specialFrequency = settings.specialFrequency
                viewMode = settings.viewMode
                if (state.status == GameStatus.Ready) {
                    // Keep the not-yet-started board's 3D flag in sync with the
                    // toggle so the pace/spawn rules match before play begins.
                    if (state.threeDWorld != threeDWorldEnabled) {
                        state = state.copy(threeDWorld = threeDWorldEnabled)
                    }
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

    /** Start-screen selector: the board view (persisted across runs). */
    fun selectViewMode(mode: ViewMode) {
        if (state.status != GameStatus.Ready) return
        viewMode = mode
        // Reflect immediately so the not-yet-started board carries the flag; the
        // settings collector will re-apply the same value once DataStore emits.
        state = state.copy(threeDWorld = mode.is3D)
        viewModelScope.launch { repo.setViewMode(mode) }
    }

    /**
     * Called by the UI when the play area is (re)measured. Resizes the board to
     * fill it, but only while [GameStatus.Ready]; ignored once a game starts so
     * dimensions stay locked during play. Idempotent against measurement jitter.
     */
    fun onPlayAreaMeasured(aspectRatio: Float) {
        if (state.status != GameStatus.Ready) return
        if (aspectRatio <= 0f || aspectRatio == lastAspect) return
        lastAspect = aspectRatio
        reconfigureBoard()
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
        resetTo(engine.setup(state.level, state.board, mode, snakeSpeed))
        refreshBest()
    }

    /** Replaces the state and resets interpolation bookkeeping to it. */
    private fun resetTo(newState: GameState) {
        previousSnake = newState.snake
        // Stamp the current "3D World" toggle onto the run so the model eases the
        // pace and suppresses the redundant 3D food for the whole game.
        state = newState.copy(threeDWorld = threeDWorldEnabled)
        tickTimeNanos = System.nanoTime()
        // Any reset (setup / new game / level stage) clears the hazard cinematic.
        threeDHazardActive = false
        cinematicHold = false
    }

    private fun runLoop() {
        stopLoop()
        loop = viewModelScope.launch {
            while (state.status == GameStatus.Running) {
                // Read the *effective* interval each iteration so Lightning/Snail/
                // Freeze actually change the pace mid-run.
                delay(state.tickIntervalMillis)
                // The loop keeps spinning during a 3D tilt cinematic but skips the
                // simulation, so the snake and the effect timers freeze until the
                // camera has settled (see [endCinematicHold]).
                if (state.status == GameStatus.Running && !cinematicHold) advance()
            }
        }
    }

    /** One simulation step, reacting to the events the engine emitted. */
    private fun advance() {
        val before = state
        val after = engine.tick(before, hazardsEnabled, specialFrequency)
        runMaxLength = max(runMaxLength, after.snake.size)

        var threeDStarted = false
        var threeDExpired = false
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
                }
                GameEvent.Died -> {
                    deathEventId++
                    sfx.died()
                    onGameOver(after.score)
                }
                is GameEvent.Exploded -> {
                    // Explosion: outward burst at the blast + a board shake.
                    eatEvent = EatEvent(event.food.position, event.food.span, palette.special, BurstStyle.Eat)
                    eatEventId++
                    shakeEventId++
                    sfx.special(event.food)
                    runUsedExplosion = true
                }
                is GameEvent.JackpotHit -> {
                    eatEvent = EatEvent(event.food.position, event.food.span, palette.foodColor(event.food), BurstStyle.Eat)
                    eatEventId++
                    sfx.special(event.food)
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
                }
                is GameEvent.TimeLost -> {
                    // Time Attack: a red implosion + a "-Ns" callout + a sting shake.
                    eatEvent = EatEvent(event.food.position, event.food.span, SpecialVisuals.TimePenaltyColor, BurstStyle.Implode)
                    eatEventId++
                    floatingText = FloatingTextEvent(event.food.position, event.food.span, "-${event.seconds}s", SpecialVisuals.TimePenaltyColor)
                    floatingTextId++
                    shakeEventId++
                    sfx.special(event.food)
                }
                is GameEvent.FoodVanished -> {
                    // An ignored food timed out: a quiet upward fade, no sound.
                    eatEvent = EatEvent(event.food.position, event.food.span, palette.foodColor(event.food), BurstStyle.Vanish)
                    eatEventId++
                }
                is GameEvent.EffectStarted -> {
                    sfx.special(event.food)
                    if (event.kind == EffectKind.Ghost) runUsedStar = true
                    if (event.kind == EffectKind.ThreeD) threeDStarted = true
                }
                is GameEvent.EffectExpired -> {
                    if (event.kind == EffectKind.ThreeD) threeDExpired = true
                }
                is GameEvent.LevelAdvanced -> {
                    sfx.levelUp()
                    runMaxLevel = max(runMaxLevel, event.levelIndex)
                    runMaxCycle = max(runMaxCycle, event.speedCycle)
                    runMaxDepth = max(runMaxDepth, levelDepth(event.levelIndex, event.speedCycle))
                }
                is GameEvent.LifeLost -> {
                    // A non-final crash: the lighter quake shake, not the death one.
                    shakeEventId++
                    sfx.lifeLost()
                }
                is GameEvent.LifeGained -> {
                    eatEvent = EatEvent(event.food.position, event.food.span, SpecialVisuals.ExtraLifeColor, BurstStyle.Eat)
                    eatEventId++
                    val text = if (event.capped) "+${LevelsMode.LIFE_CAP_BONUS}" else "+1♥"
                    floatingText = FloatingTextEvent(event.food.position, event.food.span, text, SpecialVisuals.ExtraLifeColor)
                    floatingTextId++
                    sfx.lifeGained()
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

        // 3D cinematic brackets. Enter only on the rising edge (a second 3D eaten
        // while already active just refreshes the timer — no re-tilt). Both edges
        // freeze the loop until the screen's blend animation calls back.
        if (threeDStarted && !threeDHazardActive) {
            threeDHazardActive = true
            cinematicHold = true
            cinematicId++
        }
        if (threeDExpired) {
            cinematicHold = true
            cinematicId++
        }
    }

    /**
     * Called by the screen when a 3D tilt animation finishes: releases the loop
     * freeze so play resumes. Safe to call after the game already left Running
     * (the loop has stopped; the flag is simply cleared for the next run).
     */
    fun endCinematicHold() {
        cinematicHold = false
    }

    /** Clears the 3D hazard state once the tilt-out has restored the flat view. */
    fun clearThreeD() {
        threeDHazardActive = false
    }

    /**
     * Routes a board swipe. In a rotating 3D view a horizontal swipe is a heading-
     * relative turn (left/right) and vertical swipes are ignored; otherwise (2D or
     * the north-locked 3D Fixed view) it steers by the swiped absolute [direction].
     * Reading [relativeSteering] here (not at wiring time) keeps a single, never-
     * swapped gesture detector correct in every view.
     */
    fun onSwipe(direction: Direction) {
        if (relativeSteering) {
            when (direction) {
                Direction.Left -> turnLeft()
                Direction.Right -> turnRight()
                Direction.Up, Direction.Down -> Unit
            }
        } else {
            setDirection(direction)
        }
    }

    private fun onGameOver(score: Int) {
        // Death during 3D: drop the cinematic state so the game-over overlay shows
        // the flat board (the screen snaps the camera blend back to 0 on status).
        threeDHazardActive = false
        cinematicHold = false
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
            extraLivesGained = runExtraLives,
            maxSnakeLength = runMaxLength,
        )
        // Persist; the bestJob collector reflects the new value back into state.
        viewModelScope.launch { repo.submitScore(mode, state.level, scale, score) }
        if (mode == GameMode.Levels) {
            // Levels: also track how deep the run got, for the Records screen.
            val completed = (state.speedCycle - 1) * LevelsMode.LEVEL_COUNT + (state.levelIndex - 1)
            viewModelScope.launch { repo.submitLevelsProgress(scale, completed) }
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

    /** Tracks the best score for the current (level, scale) via a single collector. */
    private fun refreshBest() {
        bestJob?.cancel()
        val level = state.level
        val currentScale = scale
        val currentMode = mode
        bestJob = viewModelScope.launch {
            repo.highScore(currentMode, level, currentScale).collect { bestScore = it }
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

        fun factory(repo: SettingsRepository, sfx: GameSfx = GameSfx.None) = viewModelFactory {
            initializer { GameViewModel(repo, sfx) }
        }
    }
}

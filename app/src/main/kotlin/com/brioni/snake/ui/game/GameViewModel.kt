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
import com.brioni.snake.game.BoardDimensions
import com.brioni.snake.game.BoardScale
import com.brioni.snake.game.ControlScheme
import com.brioni.snake.game.EffectKind
import com.brioni.snake.game.DEFAULT_ASPECT
import com.brioni.snake.game.Direction
import com.brioni.snake.game.GameEngine
import com.brioni.snake.game.GameEvent
import com.brioni.snake.game.GameMode
import com.brioni.snake.game.GameState
import com.brioni.snake.game.GameStatus
import com.brioni.snake.game.Level
import com.brioni.snake.game.Position
import com.brioni.snake.game.RunStats
import com.brioni.snake.game.Skin
import com.brioni.snake.game.boardFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * A food-eaten event, surfaced to the renderer to spawn a particle burst.
 * [implode] selects the inward (shrink) burst over the outward (grow) one.
 */
data class EatEvent(val cell: Position, val span: Int, val color: Color, val implode: Boolean)

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

    /** Active steering scheme (loaded from settings). */
    var controlScheme by mutableStateOf(DEFAULT_CONTROL)
        private set

    /** Whether the retro CRT post-filter is enabled (loaded from settings). */
    var crtEnabled by mutableStateOf(false)
        private set

    /** Active visual skin (loaded from settings); drives the renderer [palette]. */
    var skin by mutableStateOf(Skin.Classic)
        private set

    /** Whether harmful specials (earthquake/explosion/snail) may spawn (setting). */
    var hazardsEnabled by mutableStateOf(true)
        private set

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

    /** Bumped when the snake dies, so the UI can trigger a screen shake. */
    var deathEventId by mutableIntStateOf(0)
        private set

    /** Bumped on earthquake/explosion so the UI can shake the board mid-game. */
    var shakeEventId by mutableIntStateOf(0)
        private set

    /** Best score for the current (level, scale), and whether the last run beat it. */
    var bestScore by mutableIntStateOf(0)
        private set
    var isNewBest by mutableStateOf(false)
        private set

    /** Achievements unlocked by the most recent run (for the game-over banner). */
    var newlyUnlocked by mutableStateOf<List<Achievement>>(emptyList())
        private set

    // Per-run accumulators feeding the achievement check at game over.
    private var runFoodsEaten = 0
    private var runMaxCombo = 0
    private var runUsedExplosion = false
    private var runUsedStar = false
    private var runUsedJackpot = false
    private var runStartMs = 0L

    private var loop: Job? = null
    private var bestJob: Job? = null
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
                crtEnabled = settings.crtEnabled
                skin = settings.skin
                hazardsEnabled = settings.hazardsEnabled
                if (state.status == GameStatus.Ready) {
                    val levelChanged = settings.level != state.level
                    val modeChanged = settings.mode != mode
                    mode = settings.mode
                    scale = settings.scale
                    if (levelChanged || modeChanged) {
                        resetTo(engine.setup(settings.level, boardFor(scale, lastAspect), mode))
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
        viewModelScope.launch { repo.setLevel(level) }
        resetTo(engine.setup(level, state.board, mode))
        refreshBest()
    }

    fun selectMode(newMode: GameMode) {
        if (state.status != GameStatus.Ready) return
        mode = newMode
        viewModelScope.launch { repo.setGameMode(newMode) }
        resetTo(engine.setup(state.level, state.board, newMode))
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
     */
    fun onPlayAreaMeasured(aspectRatio: Float) {
        if (state.status != GameStatus.Ready) return
        if (aspectRatio <= 0f || aspectRatio == lastAspect) return
        lastAspect = aspectRatio
        reconfigureBoard()
    }

    private fun reconfigureBoard() {
        val dims = boardFor(scale, lastAspect)
        if (dims != state.board) resetTo(engine.setup(state.level, dims, mode))
    }

    fun start() {
        resetTo(engine.start(state))
        isNewBest = false
        beginRun()
        runLoop()
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
        resetTo(engine.newGame(state.level, state.board, mode))
        isNewBest = false
        beginRun()
        runLoop()
    }

    /** Resets the per-run achievement accumulators at the start of a run. */
    private fun beginRun() {
        runFoodsEaten = 0
        runMaxCombo = 0
        runUsedExplosion = false
        runUsedStar = false
        runUsedJackpot = false
        runStartMs = System.currentTimeMillis()
        newlyUnlocked = emptyList()
    }

    fun toMenu() {
        stopLoop()
        resetTo(engine.setup(state.level, state.board, mode))
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
        val after = engine.tick(before, hazardsEnabled)

        after.lastEvents.forEach { event ->
            when (event) {
                is GameEvent.Ate -> {
                    eatEvent = EatEvent(event.food.position, event.food.span, palette.foodColor(event.food), implode = false)
                    eatEventId++
                    sfx.ate(event.food, event.combo)
                    runFoodsEaten++
                    runMaxCombo = max(runMaxCombo, event.combo)
                }
                is GameEvent.Shrunk -> {
                    eatEvent = EatEvent(event.food.position, event.food.span, palette.foodColor(event.food), implode = true)
                    eatEventId++
                    sfx.shrunk(event.food)
                }
                GameEvent.Died -> {
                    deathEventId++
                    sfx.died()
                    onGameOver(after.score)
                }
                is GameEvent.Quaked -> {
                    // Earthquake: implode burst at the head + a board shake.
                    eatEvent = EatEvent(after.head, 1, palette.foodColor(event.food), implode = true)
                    eatEventId++
                    shakeEventId++
                    sfx.special(event.food)
                }
                is GameEvent.Exploded -> {
                    // Explosion: outward burst at the blast + a board shake.
                    eatEvent = EatEvent(event.food.position, event.food.span, palette.special, implode = false)
                    eatEventId++
                    shakeEventId++
                    sfx.special(event.food)
                    runUsedExplosion = true
                }
                is GameEvent.JackpotHit -> {
                    eatEvent = EatEvent(event.food.position, event.food.span, palette.foodColor(event.food), implode = false)
                    eatEventId++
                    sfx.special(event.food)
                    runUsedJackpot = true
                    runFoodsEaten++
                }
                is GameEvent.EffectStarted -> {
                    sfx.special(event.food)
                    if (event.kind == EffectKind.Ghost) runUsedStar = true
                }
                is GameEvent.EffectExpired -> Unit
            }
        }

        // Commit the interpolation snapshot atomically: previous, current, time.
        previousSnake = before.snake
        state = after
        tickTimeNanos = System.nanoTime()
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
        )
        // Persist; the bestJob collector reflects the new value back into state.
        viewModelScope.launch { repo.submitScore(mode, state.level, scale, score) }
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

    private fun stopLoop() {
        loop?.cancel()
        loop = null
    }

    override fun onCleared() {
        stopLoop()
    }

    companion object {
        private val DEFAULT_LEVEL = Level.Beginner
        private val DEFAULT_SCALE = BoardScale.Classic
        private val DEFAULT_CONTROL = ControlScheme.Swipe

        fun factory(repo: SettingsRepository, sfx: GameSfx = GameSfx.None) = viewModelFactory {
            initializer { GameViewModel(repo, sfx) }
        }
    }
}

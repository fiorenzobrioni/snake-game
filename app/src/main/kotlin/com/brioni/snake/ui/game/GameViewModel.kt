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
import com.brioni.snake.game.BoardDimensions
import com.brioni.snake.game.BoardScale
import com.brioni.snake.game.ControlScheme
import com.brioni.snake.game.DEFAULT_ASPECT
import com.brioni.snake.game.Direction
import com.brioni.snake.game.GameEngine
import com.brioni.snake.game.GameEvent
import com.brioni.snake.game.GameState
import com.brioni.snake.game.GameStatus
import com.brioni.snake.game.Level
import com.brioni.snake.game.Position
import com.brioni.snake.game.boardFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    /** Best score for the current (level, scale), and whether the last run beat it. */
    var bestScore by mutableIntStateOf(0)
        private set
    var isNewBest by mutableStateOf(false)
        private set

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
                if (state.status == GameStatus.Ready) {
                    val levelChanged = settings.level != state.level
                    scale = settings.scale
                    if (levelChanged) {
                        resetTo(engine.setup(settings.level, boardFor(scale, lastAspect)))
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
        resetTo(engine.setup(level, state.board))
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
        if (dims != state.board) resetTo(engine.setup(state.level, dims))
    }

    fun start() {
        resetTo(engine.start(state))
        isNewBest = false
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
        resetTo(engine.newGame(state.level, state.board))
        isNewBest = false
        runLoop()
    }

    fun toMenu() {
        stopLoop()
        resetTo(engine.setup(state.level, state.board))
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
                delay(state.level.tickMillis)
                if (state.status == GameStatus.Running) advance()
            }
        }
    }

    /** One simulation step, reacting to the events the engine emitted. */
    private fun advance() {
        val before = state
        val after = engine.tick(before)

        after.lastEvents.forEach { event ->
            when (event) {
                is GameEvent.Ate -> {
                    eatEvent = EatEvent(event.food.position, event.food.span, GameColors.foodColor(event.food), implode = false)
                    eatEventId++
                    sfx.ate(event.food, event.combo)
                }
                is GameEvent.Shrunk -> {
                    eatEvent = EatEvent(event.food.position, event.food.span, GameColors.foodColor(event.food), implode = true)
                    eatEventId++
                    sfx.shrunk(event.food)
                }
                GameEvent.Died -> {
                    deathEventId++
                    sfx.died()
                    onGameOver(after.score)
                }
            }
        }

        // Commit the interpolation snapshot atomically: previous, current, time.
        previousSnake = before.snake
        state = after
        tickTimeNanos = System.nanoTime()
    }

    private fun onGameOver(score: Int) {
        isNewBest = score > bestScore
        // Persist; the bestJob collector reflects the new value back into state.
        viewModelScope.launch { repo.submitScore(state.level, scale, score) }
    }

    /** Tracks the best score for the current (level, scale) via a single collector. */
    private fun refreshBest() {
        bestJob?.cancel()
        val level = state.level
        val currentScale = scale
        bestJob = viewModelScope.launch {
            repo.highScore(level, currentScale).collect { bestScore = it }
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

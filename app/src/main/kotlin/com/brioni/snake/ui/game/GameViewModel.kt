package com.brioni.snake.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brioni.snake.game.BoardSize
import com.brioni.snake.game.Direction
import com.brioni.snake.game.GameEngine
import com.brioni.snake.game.GameEvent
import com.brioni.snake.game.GameState
import com.brioni.snake.game.GameStatus
import com.brioni.snake.game.Level
import com.brioni.snake.game.Position
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
 * ([previousSnake] + [tickId]) and effects ([eatEvent]/[deathEventId]).
 */
class GameViewModel : ViewModel() {

    private val engine = GameEngine()

    var state by mutableStateOf(engine.setup(DEFAULT_LEVEL, DEFAULT_BOARD))
        private set

    /** The snake as it was before the most recent tick, for smooth motion. */
    var previousSnake by mutableStateOf(state.snake)
        private set

    /**
     * `System.nanoTime()` of the most recent tick (or reset). Updated atomically
     * with [state]/[previousSnake] so the renderer can derive the interpolation
     * fraction from a single consistent snapshot — no separately-updated
     * fraction state that could lag a frame behind a committed move.
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

    private var loop: Job? = null

    val level: Level get() = state.level
    val board: BoardSize get() = state.board

    /** Current score multiplier (consecutive-eat streak), for the HUD. */
    val combo: Int get() = state.combo

    fun selectLevel(level: Level) {
        if (state.status == GameStatus.Ready) resetTo(engine.setup(level, state.board))
    }

    fun selectBoard(board: BoardSize) {
        if (state.status == GameStatus.Ready) resetTo(engine.setup(state.level, board))
    }

    fun start() {
        resetTo(engine.start(state))
        runLoop()
    }

    fun setDirection(direction: Direction) {
        state = engine.changeDirection(state, direction)
    }

    fun togglePause() {
        state = engine.togglePause(state)
        if (state.status == GameStatus.Running) runLoop() else stopLoop()
    }

    fun playAgain() {
        resetTo(engine.newGame(state.level, state.board))
        runLoop()
    }

    fun toMenu() {
        stopLoop()
        resetTo(engine.setup(state.level, state.board))
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
                }
                is GameEvent.Shrunk -> {
                    eatEvent = EatEvent(event.food.position, event.food.span, GameColors.foodColor(event.food), implode = true)
                    eatEventId++
                }
                GameEvent.Died -> deathEventId++
            }
        }

        // Commit the interpolation snapshot atomically: previous, current, time.
        previousSnake = before.snake
        state = after
        tickTimeNanos = System.nanoTime()
    }

    private fun stopLoop() {
        loop?.cancel()
        loop = null
    }

    override fun onCleared() {
        stopLoop()
    }

    private companion object {
        val DEFAULT_LEVEL = Level.Beginner
        val DEFAULT_BOARD = BoardSize.Classic
    }
}

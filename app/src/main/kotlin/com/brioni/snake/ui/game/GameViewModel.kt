package com.brioni.snake.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brioni.snake.game.BoardSize
import com.brioni.snake.game.Direction
import com.brioni.snake.game.FoodType
import com.brioni.snake.game.GameEngine
import com.brioni.snake.game.GameState
import com.brioni.snake.game.GameStatus
import com.brioni.snake.game.Level
import com.brioni.snake.game.Position
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A food-eaten event, surfaced to the renderer to spawn a particle burst. */
data class EatEvent(val cell: Position, val type: FoodType, val span: Int)

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

    /** Increments once per simulation tick; the renderer keys interpolation on it. */
    var tickId by mutableIntStateOf(0)
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
        state = newState
        previousSnake = newState.snake
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

    /** One simulation step, deriving eat/death events from the transition. */
    private fun advance() {
        val before = state
        val after = engine.tick(before)

        if (after.score > before.score) {
            val eaten = before.foods.firstOrNull { it.occupies(after.head) }
            if (eaten != null) {
                eatEvent = EatEvent(after.head, eaten.type, eaten.span)
                eatEventId++
            }
        }
        if (after.status == GameStatus.GameOver && before.status == GameStatus.Running) {
            deathEventId++
        }

        previousSnake = before.snake
        state = after
        tickId++
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

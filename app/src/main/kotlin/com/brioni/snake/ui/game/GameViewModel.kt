package com.brioni.snake.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brioni.snake.game.BoardSize
import com.brioni.snake.game.Direction
import com.brioni.snake.game.GameEngine
import com.brioni.snake.game.GameState
import com.brioni.snake.game.GameStatus
import com.brioni.snake.game.Level
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Holds the [GameState] and drives the tick loop. All game rules live in
 * [GameEngine]; this class only owns the timing coroutine and surfaces state to
 * Compose. Survives configuration changes via [ViewModel]/[viewModelScope].
 */
class GameViewModel : ViewModel() {

    private val engine = GameEngine()

    var state by mutableStateOf(engine.setup(DEFAULT_LEVEL, DEFAULT_BOARD))
        private set

    private var loop: Job? = null

    val level: Level get() = state.level
    val board: BoardSize get() = state.board

    /** Pick a level from the menu (only meaningful before the game starts). */
    fun selectLevel(level: Level) {
        if (state.status == GameStatus.Ready) state = engine.setup(level, state.board)
    }

    /** Pick a board size from the menu (only before the game starts). */
    fun selectBoard(board: BoardSize) {
        if (state.status == GameStatus.Ready) state = engine.setup(state.level, board)
    }

    /** Begin a run from the Ready menu. */
    fun start() {
        state = engine.start(state)
        runLoop()
    }

    /** Steer the snake; 180° reversals are rejected by the engine. */
    fun setDirection(direction: Direction) {
        state = engine.changeDirection(state, direction)
    }

    fun togglePause() {
        state = engine.togglePause(state)
        if (state.status == GameStatus.Running) runLoop() else stopLoop()
    }

    /** Restart immediately with the same level/board. */
    fun playAgain() {
        state = engine.newGame(state.level, state.board)
        runLoop()
    }

    /** Return to the configuration menu, keeping the current selections. */
    fun toMenu() {
        stopLoop()
        state = engine.setup(state.level, state.board)
    }

    private fun runLoop() {
        stopLoop()
        loop = viewModelScope.launch {
            while (state.status == GameStatus.Running) {
                delay(state.level.tickMillis)
                if (state.status == GameStatus.Running) {
                    state = engine.tick(state)
                }
            }
        }
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

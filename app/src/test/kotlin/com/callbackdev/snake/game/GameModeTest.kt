package com.callbackdev.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameModeTest {

    private val engine = GameEngine(Random(1))
    private val board = BoardDimensions(18, 26)

    @Test
    fun `endless interval ramps faster the longer you play`() {
        val base = GameState(
            board = board, level = Level.Beginner,
            snake = listOf(Position(5, 5), Position(4, 5), Position(3, 5)),
            direction = Direction.Right, pendingDirection = Direction.Right,
            foods = emptyList(), obstacles = emptySet(), score = 0, pendingGrowth = 0,
            status = GameStatus.Running, mode = GameMode.Endless,
        )
        val early = base.copy(playedMs = 0).tickIntervalMillis
        val later = base.copy(playedMs = GameState.ENDLESS_TIER_MS * 4).tickIntervalMillis
        assertTrue("endless speeds up over time", later < early)
        // Never drops below the floor.
        val veryLate = base.copy(playedMs = 100 * GameState.ENDLESS_TIER_MS).tickIntervalMillis
        assertEquals(GameState.MIN_TICK_MS.coerceAtLeast(GameState.ENDLESS_FLOOR_MS.toLong()), veryLate)
    }

    @Test
    fun `time attack ends when the clock runs out`() {
        val state = GameState(
            board = board, level = Level.Beginner,
            snake = listOf(Position(5, 5), Position(4, 5), Position(3, 5)),
            direction = Direction.Right, pendingDirection = Direction.Right,
            foods = emptyList(), obstacles = emptySet(), score = 0, pendingGrowth = 0,
            status = GameStatus.Running, mode = GameMode.TimeAttack,
            playedMs = GameState.TIME_ATTACK_MS - 10,
        )
        val next = engine.tick(state)
        assertEquals(GameStatus.GameOver, next.status)
        assertTrue(next.lastEvents.contains(GameEvent.Died))
    }

    @Test
    fun `played time accumulates and endless never times out`() {
        // Head at (5,5) heading Right keeps clear of the walls for these ticks.
        var state = GameState(
            board = board, level = Level.Beginner,
            snake = listOf(Position(5, 5), Position(4, 5), Position(3, 5)),
            direction = Direction.Right, pendingDirection = Direction.Right,
            foods = emptyList(), obstacles = emptySet(), score = 0, pendingGrowth = 0,
            status = GameStatus.Running, mode = GameMode.Endless,
        )
        repeat(8) { state = engine.tick(state) }
        assertEquals(GameStatus.Running, state.status)
        assertTrue(state.playedMs > 0)
        // Even past the time-attack budget, Endless keeps running (until a crash).
        val long = state.copy(playedMs = GameState.TIME_ATTACK_MS + 1)
        assertEquals(GameStatus.Running, engine.tick(long).status)
    }

    @Test
    fun `setup carries the mode`() {
        assertEquals(GameMode.Endless, engine.setup(Level.Warrior, board, GameMode.Endless).mode)
    }
}

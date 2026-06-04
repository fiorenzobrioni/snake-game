package com.brioni.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Unit tests for the pure-Kotlin model. States are constructed directly so
 * movement/growth/collision logic is exercised without randomness; food spawn
 * is tested with a seeded [Random] for determinism.
 */
class GameEngineTest {

    private val engine = GameEngine(Random(42))

    /** A minimal running state: head at (5,5), 3 cells, no food/obstacles. */
    private fun runningState(
        direction: Direction = Direction.Right,
        foods: List<Food> = emptyList(),
        obstacles: Set<Position> = emptySet(),
        pendingGrowth: Int = 0,
    ) = GameState(
        board = BoardDimensions(18, 26),
        level = Level.Beginner,
        snake = listOf(Position(5, 5), Position(4, 5), Position(3, 5)),
        direction = direction,
        pendingDirection = direction,
        foods = foods,
        obstacles = obstacles,
        score = 0,
        pendingGrowth = pendingGrowth,
        status = GameStatus.Running,
    )

    @Test
    fun setupPlacesThreeSegmentSnakeHeadingUp() {
        val state = engine.setup(Level.Beginner, BoardDimensions(18, 26))
        assertEquals(3, state.snake.size)
        assertEquals(Direction.Up, state.direction)
        assertEquals(GameStatus.Ready, state.status)
        assertTrue(state.foods.isEmpty())
    }

    @Test
    fun startSpawnsExpectedFoodCountAndRuns() {
        val state = engine.newGame(Level.Beginner, BoardDimensions(18, 26))
        assertEquals(GameStatus.Running, state.status)
        assertEquals(GameEngine.FOOD_COUNT, state.foods.size)
    }

    @Test
    fun tickMovesHeadAndKeepsLength() {
        val next = engine.tick(runningState(Direction.Right))
        assertEquals(Position(6, 5), next.head)
        assertEquals(3, next.snake.size)
        assertEquals(GameStatus.Running, next.status)
    }

    @Test
    fun queuedGrowthIsPaidOnePerTick() {
        var state = runningState(pendingGrowth = 2)
        state = engine.tick(state)
        assertEquals(4, state.snake.size) // tail kept, +1
        assertEquals(1, state.pendingGrowth)
        state = engine.tick(state)
        assertEquals(5, state.snake.size)
        assertEquals(0, state.pendingGrowth)
        state = engine.tick(state)
        assertEquals(5, state.snake.size) // length now stable
    }

    @Test
    fun eatingGrowsSnakeAndScores() {
        val food = Food(Position(6, 5), FoodType.Red, growth = 4)
        val next = engine.tick(runningState(Direction.Right, foods = listOf(food)))
        assertEquals(4 * 10, next.score)
        assertEquals(4, next.snake.size) // +1 this tick, 3 more queued
        assertEquals(3, next.pendingGrowth)
        assertFalse(next.foods.any { it.position == Position(6, 5) })
        assertEquals(GameEngine.FOOD_COUNT, next.foods.size) // refilled
    }

    @Test
    fun hittingWallEndsGame() {
        val state = GameState(
            board = BoardDimensions(18, 26),
            level = Level.Beginner,
            snake = listOf(Position(0, 5), Position(1, 5), Position(2, 5)),
            direction = Direction.Left,
            pendingDirection = Direction.Left,
            foods = emptyList(),
            obstacles = emptySet(),
            score = 0,
            pendingGrowth = 0,
            status = GameStatus.Running,
        )
        assertEquals(GameStatus.GameOver, engine.tick(state).status)
    }

    @Test
    fun hittingObstacleEndsGame() {
        val state = runningState(Direction.Right, obstacles = setOf(Position(6, 5)))
        assertEquals(GameStatus.GameOver, engine.tick(state).status)
    }

    @Test
    fun runningIntoOwnBodyEndsGame() {
        // Coiled snake; turning Right drives the head onto the tail cell (6,5).
        // pendingGrowth keeps the tail in place so the collision stands.
        val state = GameState(
            board = BoardDimensions(18, 26),
            level = Level.Beginner,
            snake = listOf(
                Position(5, 5), Position(5, 6), Position(6, 6), Position(6, 5),
            ),
            direction = Direction.Up,
            pendingDirection = Direction.Right,
            foods = emptyList(),
            obstacles = emptySet(),
            score = 0,
            pendingGrowth = 1, // keep the tail so the collision cell stays occupied
            status = GameStatus.Running,
        )
        assertEquals(GameStatus.GameOver, engine.tick(state).status)
    }

    @Test
    fun reversalIntoSelfIsBlocked() {
        val state = runningState(Direction.Right)
        val attempted = engine.changeDirection(state, Direction.Left)
        assertEquals(Direction.Right, attempted.pendingDirection)
    }

    @Test
    fun perpendicularTurnIsAccepted() {
        val state = runningState(Direction.Right)
        assertEquals(Direction.Up, engine.changeDirection(state, Direction.Up).pendingDirection)
    }

    @Test
    fun twoQuickTurnsCannotComposeIntoReversal() {
        // Heading Right: Up is accepted, then a same-tick Down must be rejected
        // because it reverses the still-committed Right... actually Down is the
        // reversal of Up, but validation is against the committed direction
        // (Right), so Down (not the opposite of Right) would be accepted — and
        // is safe, since the head only ever advances one axis per tick.
        var state = runningState(Direction.Right)
        state = engine.changeDirection(state, Direction.Up)
        assertEquals(Direction.Up, state.pendingDirection)
        // The reversal that matters — Left — stays blocked regardless.
        state = engine.changeDirection(state, Direction.Left)
        assertEquals(Direction.Up, state.pendingDirection)
    }

    @Test
    fun foodNeverSpawnsOnBorderOrOccupiedCells() {
        val state = engine.newGame(Level.Legend, BoardDimensions(14, 20))
        val snake = state.snake.toSet()
        state.foods.forEach { food ->
            food.cells().forEach { cell ->
                assertTrue("food off the left/top border", cell.x >= 1 && cell.y >= 1)
                assertTrue("food off the right border", cell.x < state.board.width - 1)
                assertTrue("food off the bottom border", cell.y < state.board.height - 1)
                assertFalse("food not on snake", cell in snake)
                assertFalse("food not on obstacle", cell in state.obstacles)
            }
        }
    }

    @Test
    fun pauseTogglesBothWays() {
        val running = runningState()
        val paused = engine.togglePause(running)
        assertEquals(GameStatus.Paused, paused.status)
        assertEquals(GameStatus.Running, engine.togglePause(paused).status)
    }

    @Test
    fun tickIsNoOpWhenNotRunning() {
        val ready = engine.setup(Level.Beginner, BoardDimensions(18, 26))
        assertEquals(ready, engine.tick(ready))
        val paused = ready.copy(status = GameStatus.Paused)
        assertEquals(paused, engine.tick(paused))
    }

    @Test
    fun blueFoodGrowthStaysWithinPortedRange() {
        // Sample the table heavily; Blue growth must land in 2..24 inclusive.
        val table = (0 until 5000).map { FoodTable.roll(Random(it.toLong())) }
        val blue = table.filter { it.type == FoodType.Blue }
        assertNotNull(blue)
        blue.forEach { assertTrue(it.growth in 2..24) }
    }
}

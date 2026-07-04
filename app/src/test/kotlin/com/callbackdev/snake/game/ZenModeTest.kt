package com.callbackdev.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The Zen mode rules: a toroidal arena (all four edges wrap), no obstacles and
 * no specials, a fixed player-chosen pace, a stretched flow-combo window, and
 * death only by the snake's own body.
 */
class ZenModeTest {

    private val engine = GameEngine(Random(21))
    private val board = BoardDimensions(18, 26)

    private fun zenState(
        snake: List<Position> = listOf(Position(5, 5), Position(4, 5), Position(3, 5)),
        direction: Direction = Direction.Right,
        foods: List<Food> = emptyList(),
        speed: SnakeSpeed = SnakeSpeed.Relaxed,
    ) = GameState(
        board = board, level = ZenMode.SCORE_LEVEL, snakeSpeed = speed,
        snake = snake, direction = direction, pendingDirection = direction,
        foods = foods, obstacles = emptySet(), score = 0, pendingGrowth = 0,
        status = GameStatus.Running, mode = GameMode.Zen,
    )

    @Test
    fun `all four edges wrap instead of killing`() {
        // Right edge → x wraps to 0.
        var next = engine.tick(zenState(snake = listOf(Position(17, 5), Position(16, 5), Position(15, 5)), direction = Direction.Right))
        assertEquals(GameStatus.Running, next.status)
        assertEquals(Position(0, 5), next.head)
        // Left edge → x wraps to width-1.
        next = engine.tick(zenState(snake = listOf(Position(0, 5), Position(1, 5), Position(2, 5)), direction = Direction.Left))
        assertEquals(GameStatus.Running, next.status)
        assertEquals(Position(17, 5), next.head)
        // Top edge → y wraps to height-1.
        next = engine.tick(zenState(snake = listOf(Position(5, 0), Position(5, 1), Position(5, 2)), direction = Direction.Up))
        assertEquals(GameStatus.Running, next.status)
        assertEquals(Position(5, 25), next.head)
        // Bottom edge → y wraps to 0.
        next = engine.tick(zenState(snake = listOf(Position(5, 25), Position(5, 24), Position(5, 23)), direction = Direction.Down))
        assertEquals(GameStatus.Running, next.status)
        assertEquals(Position(5, 0), next.head)
    }

    @Test
    fun `only the snake's own body ends the run`() {
        // A tight clockwise coil: head at (5,5) turning down into its own body.
        val coiled = zenState(
            snake = listOf(
                Position(5, 5), Position(4, 5), Position(4, 6), Position(5, 6), Position(6, 6), Position(6, 5),
            ),
            direction = Direction.Right,
        ).copy(pendingDirection = Direction.Down, graceAvailable = false)
        val next = engine.tick(coiled)
        assertEquals(GameStatus.GameOver, next.status)
        assertTrue(next.lastEvents.contains(GameEvent.Died))
    }

    @Test
    fun `setup builds an open board even on the hardest difficulty`() {
        val state = engine.setup(Level.Legend, board, GameMode.Zen)
        assertTrue(state.obstacles.isEmpty())
        assertTrue(state.walls.isEmpty())
        assertTrue(state.gates.isEmpty())
        assertTrue(state.teleports.isEmpty())
    }

    @Test
    fun `specials never spawn in zen`() {
        var state = engine.start(engine.setup(ZenMode.SCORE_LEVEL, board, GameMode.Zen))
        // Deep past every unlock gate, at the most special-happy frequency.
        state = state.copy(elapsedTicks = 5_000)
        repeat(80) {
            state = state.copy(
                snake = listOf(Position(5, 5), Position(4, 5), Position(3, 5)),
                direction = Direction.Right, pendingDirection = Direction.Right,
            )
            state = engine.tick(state, specialFrequency = SpecialFrequency.Frenzy)
            assertTrue(state.foods.none { it.category == FoodCategory.Special })
        }
    }

    @Test
    fun `the pace is the selected speed and never ramps`() {
        val calm = zenState(speed = SnakeSpeed.Relaxed)
        assertEquals(SnakeSpeed.Relaxed.tickMillis, calm.tickIntervalMillis)
        // Ten minutes in: identical pace, and no speed-tier announcements ever.
        val late = calm.copy(playedMs = 600_000)
        assertEquals(SnakeSpeed.Relaxed.tickMillis, late.tickIntervalMillis)
        assertTrue(engine.tick(late).lastEvents.none { it is GameEvent.SpeedTierUp })
        // The player's rhythm choice is respected.
        assertEquals(SnakeSpeed.Turbo.tickMillis, zenState(speed = SnakeSpeed.Turbo).tickIntervalMillis)
    }

    @Test
    fun `the flow combo window is stretched`() {
        val food = Food(
            position = Position(6, 5), category = FoodCategory.Grow,
            tier = FoodTier.Small, size = FoodSize.Standard, effect = FoodEffect.Grow(2),
        )
        val zen = engine.tick(zenState(foods = listOf(food)))
        val expected = 1 + (GameEngine.COMBO_WINDOW_TICKS * ZenMode.COMBO_WINDOW_FACTOR).toInt()
        assertEquals(expected, zen.comboDeadlineTick)
    }

    @Test
    fun `hugging the edge is not a near miss`() {
        // Sliding along the top edge: on a torus the edge is a doorway, so the
        // near-miss cue (haptic tick + red flash) must stay silent.
        val hugging = zenState(
            snake = listOf(Position(5, 0), Position(4, 0), Position(3, 0)),
            direction = Direction.Right,
        )
        val next = engine.tick(hugging)
        assertEquals(GameStatus.Running, next.status)
        assertFalse(next.lastEvents.contains(GameEvent.NearMiss))
    }

    @Test
    fun `zen never times out`() {
        val long = zenState().copy(playedMs = GameState.TIME_ATTACK_MS + 1)
        assertEquals(GameStatus.Running, engine.tick(long).status)
        assertFalse(long.inFeverTime)
    }
}

package com.callbackdev.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Rules of the auto-growth pass: the snake gaining segments on its own
 * ([GrowthRate]), the score multiplier that declares it, and the food-table
 * rebalance that gives the player a brake.
 */
class AutoGrowthTest {

    private val engine = GameEngine(Random(42))
    private val board = BoardDimensions(19, 32) // the reference board

    /** A long empty board and a straight, food-free run, so only growth moves the length. */
    private fun runningState(
        growthRate: GrowthRate,
        mode: GameMode = GameMode.Endless,
        board: BoardDimensions = this.board,
    ) = GameState(
        board = board,
        level = Level.Beginner,
        snake = listOf(Position(2, 5), Position(1, 5), Position(0, 5)),
        direction = Direction.Right,
        pendingDirection = Direction.Right,
        foods = emptyList(),
        obstacles = emptySet(),
        score = 0,
        pendingGrowth = 0,
        status = GameStatus.Running,
        growthRate = growthRate,
        mode = mode,
        graceAvailable = true,
    )

    // --- The growth clock ----------------------------------------------------

    @Test
    fun `growth off leaves the length to the food alone`() {
        var state = runningState(GrowthRate.Off)
        assertEquals(0, state.autoGrowthIntervalTicks)
        repeat(15) { state = engine.tick(state) }
        assertEquals(3, state.snake.size)
        assertEquals(0, state.growthProgress)
    }

    @Test
    fun `the tuned intervals apply as-is on the reference board`() {
        GrowthRate.entries.forEach { rate ->
            assertEquals(rate.baseIntervalTicks, rate.intervalTicksFor(board))
        }
    }

    @Test
    fun `a faster growth setting grows more often`() {
        val intervals = GrowthRate.entries.map { runningState(it).autoGrowthIntervalTicks }
        assertEquals(0, intervals.first()) // Off: no clock at all
        // Every step after Off shortens the interval.
        intervals.drop(1).zipWithNext().forEach { (slower, faster) -> assertTrue(faster < slower) }
    }

    @Test
    fun `a segment lands on the interval and the clock wraps`() {
        val interval = runningState(GrowthRate.Steady).autoGrowthIntervalTicks
        // One step short of the threshold: the tail still drops as usual.
        var state = runningState(GrowthRate.Steady).copy(growthProgress = interval - 2)
        state = engine.tick(state)
        assertEquals(3, state.snake.size)
        assertEquals(interval - 1, state.growthProgress)
        assertTrue(state.lastEvents.none { it is GameEvent.AutoGrew })

        // The threshold tick: the tail is kept, the clock wraps, the event fires.
        state = engine.tick(state)
        assertEquals(4, state.snake.size)
        assertEquals(0, state.growthProgress)
        assertEquals(0, state.pendingGrowth) // granted and paid on the same tick
        assertEquals(4, state.lastEvents.filterIsInstance<GameEvent.AutoGrew>().single().length)

        // And the clock keeps running from zero.
        state = engine.tick(state)
        assertEquals(4, state.snake.size)
        assertEquals(1, state.growthProgress)
    }

    @Test
    fun `growth is silent on the tick the snake dies`() {
        // Head one cell from the right wall with the clock about to fire, and no
        // grace dodge banked: the fatal tick reports the death, not the growth.
        val interval = runningState(GrowthRate.Relentless).autoGrowthIntervalTicks
        val state = runningState(GrowthRate.Relentless).copy(
            snake = listOf(Position(board.width - 1, 5), Position(board.width - 2, 5)),
            growthProgress = interval - 1,
            graceAvailable = false,
        )
        val next = engine.tick(state)
        assertEquals(GameStatus.GameOver, next.status)
        assertNull(next.lastEvents.filterIsInstance<GameEvent.AutoGrew>().firstOrNull())
    }

    @Test
    fun `a grace dodge keeps the growth clock and carries the owed segment`() {
        val interval = runningState(GrowthRate.Relentless).autoGrowthIntervalTicks
        val state = runningState(GrowthRate.Relentless).copy(
            snake = listOf(Position(board.width - 1, 5), Position(board.width - 2, 5)),
            growthProgress = interval - 1,
            graceAvailable = true,
        )
        val next = engine.tick(state)
        assertEquals(GameStatus.Running, next.status)
        assertNotNull(next.lastEvents.filterIsInstance<GameEvent.GraceDodge>().firstOrNull())
        // The move was cancelled, so the segment is still owed, not lost.
        assertEquals(1, next.pendingGrowth)
        assertEquals(0, next.growthProgress)
    }

    // --- Board and mode scaling ---------------------------------------------

    @Test
    fun `bigger boards grow the snake more often, smaller ones less`() {
        val cozy = runningState(GrowthRate.Steady, board = BoardDimensions(13, 22)).autoGrowthIntervalTicks
        val reference = runningState(GrowthRate.Steady).autoGrowthIntervalTicks
        val colossal = runningState(GrowthRate.Steady, board = BoardDimensions(35, 58)).autoGrowthIntervalTicks
        assertTrue("cozy grows more slowly", cozy > reference)
        assertTrue("colossal grows faster", colossal < reference)
        assertTrue("never absurdly fast", colossal >= GrowthRate.MIN_INTERVAL_TICKS)
    }

    @Test
    fun `zen grows more gently than the other modes`() {
        val endless = runningState(GrowthRate.Steady, mode = GameMode.Endless).autoGrowthIntervalTicks
        val zen = runningState(GrowthRate.Steady, mode = GameMode.Zen).autoGrowthIntervalTicks
        assertTrue(zen > endless)
    }

    @Test
    fun `a campaign level staging restarts the growth clock`() {
        val state = engine
            .setup(LevelsMode.SCORE_LEVEL, board, GameMode.Levels, growthRate = GrowthRate.Relentless)
            .copy(
                status = GameStatus.Running,
                foods = listOf(
                    Food(
                        position = Position(board.width / 2, board.height / 2 - 1),
                        category = FoodCategory.Grow,
                        tier = FoodTier.Small,
                        size = FoodSize.Standard,
                        effect = FoodEffect.Grow(1),
                    ),
                ),
                levelFoodsEaten = LevelsMode.LEVEL_FOOD_GOAL - 1,
                growthProgress = 3,
            )
        val next = engine.tick(state)
        assertEquals(GameStatus.LevelIntro, next.status)
        assertEquals(0, next.growthProgress)
    }

    // --- Scoring -------------------------------------------------------------

    @Test
    fun `the growth setting declares a score multiplier`() {
        fun scoreOf(rate: GrowthRate): Int {
            val state = runningState(rate).copy(
                foods = listOf(
                    Food(
                        position = Position(3, 5),
                        category = FoodCategory.Grow,
                        tier = FoodTier.Medium,
                        size = FoodSize.Standard,
                        effect = FoodEffect.Grow(2),
                    ),
                ),
            )
            return engine.tick(state).score
        }
        val base = scoreOf(GrowthRate.Off)
        assertEquals(2 * GameEngine.GROW_POINTS_PER_SEGMENT, base)
        assertEquals(base, scoreOf(GrowthRate.Off)) // x1: the classic baseline
        assertTrue(scoreOf(GrowthRate.Steady) > base)
        assertTrue(scoreOf(GrowthRate.Relentless) > scoreOf(GrowthRate.Steady))
    }

    @Test
    fun `trimming a long snake pays more than trimming a short one`() {
        fun shrinkScore(length: Int): Int {
            val body = (0 until length).map { Position(10 - it, 5) }
            val state = runningState(GrowthRate.Steady).copy(
                snake = body,
                foods = listOf(
                    Food(
                        position = Position(11, 5),
                        category = FoodCategory.Shrink,
                        tier = FoodTier.Medium,
                        size = FoodSize.Standard,
                        effect = FoodEffect.Shrink(3),
                    ),
                ),
            )
            return engine.tick(state).score
        }
        assertTrue(shrinkScore(40) > shrinkScore(4))
    }

    // --- The food table's brake ---------------------------------------------

    @Test
    fun `shrinking food is available from the first tick when growth is on`() {
        // Without auto-growth the early game is grow-only (the shrink gate).
        val early = (0 until 400).map {
            FoodTable.roll(Random(it.toLong()), elapsedTicks = 1, level = Level.Beginner)
        }
        assertTrue(early.none { it.category == FoodCategory.Shrink })

        // With it on, the brake is there from the start.
        val withGrowth = (0 until 400).map {
            FoodTable.roll(Random(it.toLong()), elapsedTicks = 1, level = Level.Beginner, autoGrowth = true)
        }
        assertTrue(withGrowth.count { it.category == FoodCategory.Shrink } > 100)
    }

    @Test
    fun `a shrink piece trims at least as much as a grow piece adds`() {
        // The point of the rebalance: eating shrink has to be able to keep up with
        // both the growth clock and the grow pieces.
        val specs = (0 until 3000).map {
            FoodTable.roll(Random(it.toLong()), elapsedTicks = 1, level = Level.Beginner, autoGrowth = true)
        }
        val grow = specs.mapNotNull { (it.effect as? FoodEffect.Grow)?.segments }
        val shrink = specs.mapNotNull { (it.effect as? FoodEffect.Shrink)?.segments }
        assertTrue(grow.isNotEmpty() && shrink.isNotEmpty())
        assertTrue(shrink.average() > grow.average())
        assertTrue("grow pieces stay small", grow.max() <= 8) // 4 (Huge) x 2 (Maxi)
    }
}

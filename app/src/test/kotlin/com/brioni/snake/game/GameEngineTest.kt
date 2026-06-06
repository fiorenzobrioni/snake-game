package com.brioni.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /** A grow food the snake walks onto next tick (head moves (5,5) → (6,5)). */
    private fun growFood(segments: Int) = Food(
        position = Position(6, 5),
        category = FoodCategory.Grow,
        tier = FoodTier.Medium,
        size = FoodSize.Standard,
        effect = FoodEffect.Grow(segments),
    )

    private fun shrinkFood(segments: Int, size: FoodSize = FoodSize.Standard) = Food(
        position = Position(6, 5),
        category = FoodCategory.Shrink,
        tier = FoodTier.Medium,
        size = size,
        effect = FoodEffect.Shrink(segments),
    )

    @Test
    fun eatingGrowsSnakeAndScores() {
        val next = engine.tick(runningState(Direction.Right, foods = listOf(growFood(4))))
        assertEquals(4 * 10, next.score) // first eat → combo x1
        assertEquals(4, next.snake.size) // +1 this tick, 3 more queued
        assertEquals(3, next.pendingGrowth)
        assertFalse(next.foods.any { it.position == Position(6, 5) })
        assertEquals(GameEngine.FOOD_COUNT, next.foods.size) // refilled
        assertTrue(next.lastEvents.any { it is GameEvent.Ate })
    }

    @Test
    fun comboMultiplierScalesGrowScore() {
        // A streak already at x1, still within its window: the next grow scores x2.
        val state = runningState(Direction.Right, foods = listOf(growFood(3)))
            .copy(combo = 1, comboDeadlineTick = 100, elapsedTicks = 10)
        val next = engine.tick(state)
        assertEquals(2, next.combo)
        assertEquals(3 * 10 * 2, next.score)
    }

    @Test
    fun lapsedStreakResetsComboToOne() {
        val state = runningState(Direction.Right, foods = listOf(growFood(3)))
            .copy(combo = 4, comboDeadlineTick = 5, elapsedTicks = 10)
        val next = engine.tick(state)
        assertEquals(1, next.combo) // deadline passed → fresh streak
        assertEquals(3 * 10 * 1, next.score)
    }

    @Test
    fun shrinkTrimsTailButNeverBelowFloor() {
        // A 6-cell snake eating Shrink(2): head added (7), two tail cells dropped.
        val longSnake = listOf(
            Position(5, 5), Position(4, 5), Position(3, 5),
            Position(2, 5), Position(1, 5), Position(1, 6),
        )
        val state = runningState(Direction.Right, foods = listOf(shrinkFood(2)))
            .copy(snake = longSnake)
        val next = engine.tick(state)
        assertEquals(5, next.snake.size)
        val shrunk = next.lastEvents.filterIsInstance<GameEvent.Shrunk>().single()
        assertEquals(2, shrunk.removed)

        // At the floor, a big shrink can only ever drop the freshly-added head cell.
        val atFloor = runningState(Direction.Right, foods = listOf(shrinkFood(9)))
        val floored = engine.tick(atFloor)
        assertEquals(GameEngine.MIN_SNAKE_LENGTH, floored.snake.size)
    }

    @Test
    fun shrinkAwardsReducedSymbolicPoints() {
        val std = engine.tick(runningState(Direction.Right, foods = listOf(shrinkFood(2))))
        assertEquals(GameEngine.SHRINK_POINTS, std.score)
        val maxi = engine.tick(
            runningState(Direction.Right, foods = listOf(shrinkFood(2, FoodSize.Maxi))),
        )
        assertEquals(GameEngine.SHRINK_POINTS_MAXI, maxi.score)
    }

    @Test
    fun elapsedTicksAdvancesOnlyWhileRunning() {
        val running = runningState()
        assertEquals(1, engine.tick(running).elapsedTicks)
        val ready = engine.setup(Level.Beginner, BoardDimensions(18, 26))
        assertEquals(0, engine.tick(ready).elapsedTicks)
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
    fun earlyGameOnlySpawnsStandardGrowFood() {
        // At the very start nothing but small/medium growing food is unlocked.
        repeat(2000) { seed ->
            val spec = FoodTable.roll(Random(seed.toLong()), elapsedTicks = 0, level = Level.Beginner)
            assertEquals(FoodCategory.Grow, spec.category)
            assertEquals(FoodSize.Standard, spec.size)
            assertFalse(spec.tier == FoodTier.Mystery)
            assertTrue(spec.effect is FoodEffect.Grow)
        }
    }

    @Test
    fun lateGameUnlocksShrinkMaxiAndMystery() {
        // Far into a session every kind should appear across many samples.
        val specs = (0 until 4000).map {
            FoodTable.roll(Random(it.toLong()), elapsedTicks = 2000, level = Level.Beginner)
        }
        assertTrue("shrink unlocked", specs.any { it.category == FoodCategory.Shrink })
        assertTrue("maxi unlocked", specs.any { it.size == FoodSize.Maxi })
        assertTrue("mystery unlocked", specs.any { it.tier == FoodTier.Mystery })
    }

    @Test
    fun mysteryAmountsStayWithinRangeAndAreDeterministic() {
        val specs = (0 until 4000).map {
            FoodTable.roll(Random(it.toLong()), elapsedTicks = 2000, level = Level.Beginner)
        }
        specs.filter { it.tier == FoodTier.Mystery }.forEach { spec ->
            when (val e = spec.effect) {
                is FoodEffect.Grow -> assertTrue(e.segments in 2..24)
                is FoodEffect.Shrink -> assertTrue(e.segments in 2..14)
                else -> Unit // mystery foods are only ever Grow/Shrink
            }
        }
        // Same seed + inputs → identical spec (resolved deterministically at spawn).
        val a = FoodTable.roll(Random(7), elapsedTicks = 2000, level = Level.Beginner)
        val b = FoodTable.roll(Random(7), elapsedTicks = 2000, level = Level.Beginner)
        assertEquals(a, b)
    }

    // --- Auto-vanishing food ---------------------------------------------

    /** Beginner pace: 7000 ms / 175 ms ≈ 40 ticks before a regular food times out. */
    private val vanishTicks = (GameEngine.VANISH_FOOD_MS / Level.Beginner.tickMillis).toInt()

    @Test
    fun ignoredRegularFoodVanishesAndIsReplaced() {
        // A regular food well clear of the snake's path, stamped at tick 0.
        val stale = Food(Position(10, 10), FoodCategory.Grow, FoodTier.Small, FoodSize.Standard, FoodEffect.Grow(2), spawnTick = 0)
        val state = runningState(foods = listOf(stale)).copy(elapsedTicks = vanishTicks - 1)

        val next = engine.tick(state)

        val vanished = next.lastEvents.filterIsInstance<GameEvent.FoodVanished>().singleOrNull()
        assertTrue("the stale food was the one that vanished", vanished?.food == stale)
        // The fresh roll is stamped this tick, so it can't immediately vanish again.
        assertTrue("replacement is freshly stamped", next.foods.all { it.spawnTick == next.elapsedTicks })
        assertEquals("board topped back up", GameEngine.FOOD_COUNT, next.foods.size)
    }

    @Test
    fun freshRegularFoodDoesNotVanish() {
        val fresh = Food(Position(10, 10), FoodCategory.Grow, FoodTier.Small, FoodSize.Standard, FoodEffect.Grow(2), spawnTick = 0)
        val state = runningState(foods = listOf(fresh)).copy(elapsedTicks = vanishTicks - 5)

        val next = engine.tick(state)

        assertFalse("no premature vanish", next.lastEvents.any { it is GameEvent.FoodVanished })
        assertTrue("the food is still there", next.foods.any { it.position == Position(10, 10) })
    }

    /** Beginner pace: 14000 ms / 175 ms = 80 ticks before a special times out. */
    private val specialVanishTicks = (GameEngine.VANISH_SPECIAL_MS / Level.Beginner.tickMillis).toInt()

    @Test
    fun specialsOutlastTheRegularTimeout() {
        // Past the regular timeout but short of the (much longer) special one.
        val special = Food(Position(10, 10), FoodCategory.Special, FoodTier.Huge, FoodSize.Maxi, FoodEffect.Haste(6_000), spawnTick = 0)
        val state = runningState(foods = listOf(special)).copy(elapsedTicks = vanishTicks + 5)

        val next = engine.tick(state)

        assertFalse("special survives the regular timeout", next.lastEvents.any { it is GameEvent.FoodVanished })
        assertTrue("the special is still on the board", next.foods.any { it.position == Position(10, 10) && it.category == FoodCategory.Special })
    }

    @Test
    fun specialsVanishAfterTheirLongTimeout() {
        val special = Food(Position(10, 10), FoodCategory.Special, FoodTier.Huge, FoodSize.Maxi, FoodEffect.Haste(6_000), spawnTick = 0)
        val state = runningState(foods = listOf(special)).copy(elapsedTicks = specialVanishTicks)

        val next = engine.tick(state)

        val vanished = next.lastEvents.filterIsInstance<GameEvent.FoodVanished>().singleOrNull()
        assertTrue("the special eventually times out", vanished?.food == special)
    }
}

package com.brioni.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Unit tests for the Phase 6.2 special foods (power-ups / hazards). States are
 * built directly and a food is placed one step ahead so the head eats it on the
 * next tick. Spawn behaviour is checked with a seeded [Random] for determinism.
 */
class SpecialFoodTest {

    private val engine = GameEngine(Random(7))
    private val board = BoardDimensions(18, 26)

    /** Head at (5,5); the cell at (6,5) is one step Right (where foods are placed). */
    private fun runningState(
        snake: List<Position> = listOf(Position(5, 5), Position(4, 5), Position(3, 5)),
        direction: Direction = Direction.Right,
        foods: List<Food> = emptyList(),
        obstacles: Set<Position> = emptySet(),
        debris: List<Debris> = emptyList(),
        effectTimers: List<ActiveEffect> = emptyList(),
    ) = GameState(
        board = board,
        level = Level.Beginner,
        snake = snake,
        direction = direction,
        pendingDirection = direction,
        foods = foods,
        obstacles = obstacles,
        score = 0,
        pendingGrowth = 0,
        status = GameStatus.Running,
        debris = debris,
        effectTimers = effectTimers,
    )

    private fun specialAt(cell: Position, effect: FoodEffect) = Food(
        position = cell,
        category = FoodCategory.Special,
        tier = FoodTier.Huge,
        size = FoodSize.Maxi,
        effect = effect,
    )

    private val longSnake = listOf(
        Position(5, 5), Position(4, 5), Position(3, 5), Position(2, 5),
        Position(1, 5), Position(1, 6), Position(1, 7), Position(1, 8),
    )

    // --- Earthquake -------------------------------------------------------

    @Test
    fun quakeBitesTailAndEmitsEvent() {
        val state = runningState(snake = longSnake, foods = listOf(specialAt(Position(6, 5), FoodEffect.Quake(3))))
        val next = engine.tick(state)
        // 8 cells + head (9) − 3 bitten = 6.
        assertEquals(6, next.snake.size)
        val quake = next.lastEvents.filterIsInstance<GameEvent.Quaked>().single()
        assertEquals(3, quake.removed)
    }

    @Test
    fun quakeNeverDropsBelowFloor() {
        val short = listOf(Position(5, 5), Position(4, 5), Position(3, 5))
        val next = engine.tick(runningState(snake = short, foods = listOf(specialAt(Position(6, 5), FoodEffect.Quake(9)))))
        assertEquals(GameEngine.MIN_SNAKE_LENGTH, next.snake.size)
    }

    // --- Explosion --------------------------------------------------------

    @Test
    fun burstSplitsSnakeAndLeavesLethalDebris() {
        val state = runningState(snake = longSnake, foods = listOf(specialAt(Position(6, 5), FoodEffect.Burst(4_000))))
        val next = engine.tick(state)
        // Body after head add = 9; split at 9/2 = 4 → front keeps 4, 5 become debris.
        assertEquals(4, next.snake.size)
        assertEquals(5, next.debris.size)
        val exploded = next.lastEvents.filterIsInstance<GameEvent.Exploded>().single()
        assertEquals(5, exploded.debris.size)
    }

    @Test
    fun debrisIsLethal() {
        val state = runningState(debris = listOf(Debris(Position(6, 5), 4_000, 4_000)))
        assertEquals(GameStatus.GameOver, engine.tick(state).status)
    }

    @Test
    fun debrisAgesAndAutoClears() {
        // Beginner tick = 175ms. Debris with 175ms left clears after one tick.
        val cleared = engine.tick(runningState(debris = listOf(Debris(Position(12, 12), 175, 4_000))))
        assertTrue(cleared.debris.isEmpty())
        // With more time left it survives, aged by one interval.
        val survived = engine.tick(runningState(debris = listOf(Debris(Position(12, 12), 400, 4_000))))
        assertEquals(1, survived.debris.size)
        assertEquals(225, survived.debris.single().remainingMs)
    }

    // --- Speed: Lightning / Snail / Freeze --------------------------------

    @Test
    fun speedEffectsScaleTheTickInterval() {
        val base = Level.Beginner.tickMillis
        assertEquals(base, runningState().tickIntervalMillis)
        val haste = runningState(effectTimers = listOf(ActiveEffect(EffectKind.Haste, 6_000, 6_000)))
        assertTrue(haste.tickIntervalMillis < base)
        val slow = runningState(effectTimers = listOf(ActiveEffect(EffectKind.Slow, 6_000, 6_000)))
        assertTrue(slow.tickIntervalMillis > base)
    }

    @Test
    fun eatingHasteStartsEffectAndKeepsLength() {
        val state = runningState(foods = listOf(specialAt(Position(6, 5), FoodEffect.Haste(6_000))))
        val next = engine.tick(state)
        assertTrue(next.hasEffect(EffectKind.Haste))
        assertEquals(3, next.snake.size) // pure effect: no growth, no shrink
        assertTrue(next.lastEvents.any { it is GameEvent.EffectStarted })
    }

    @Test
    fun effectAgesDownAndExpiresWithEvent() {
        // 50ms remaining < one Beginner interval → expires this tick.
        val state = runningState(effectTimers = listOf(ActiveEffect(EffectKind.Slow, 50, 6_000)))
        val next = engine.tick(state)
        assertFalse(next.hasEffect(EffectKind.Slow))
        assertTrue(next.lastEvents.any { it is GameEvent.EffectExpired })
    }

    // --- Star (Ghost) -----------------------------------------------------

    @Test
    fun ghostPassesThroughWallsByWrapping() {
        val state = runningState(
            snake = listOf(Position(0, 5), Position(1, 5), Position(2, 5)),
            direction = Direction.Left,
            effectTimers = listOf(ActiveEffect(EffectKind.Ghost, 5_000, 5_000)),
        )
        val next = engine.tick(state)
        assertEquals(GameStatus.Running, next.status)
        assertEquals(Position(board.width - 1, 5), next.head)
    }

    @Test
    fun ghostIgnoresObstaclesAndDebris() {
        val ghost = listOf(ActiveEffect(EffectKind.Ghost, 5_000, 5_000))
        assertEquals(
            GameStatus.Running,
            engine.tick(runningState(obstacles = setOf(Position(6, 5)), effectTimers = ghost)).status,
        )
        assertEquals(
            GameStatus.Running,
            engine.tick(runningState(debris = listOf(Debris(Position(6, 5), 4_000, 4_000)), effectTimers = ghost)).status,
        )
    }

    // --- Jackpot ----------------------------------------------------------

    @Test
    fun jackpotAwardsBonusAndGrowth() {
        val state = runningState(foods = listOf(specialAt(Position(6, 5), FoodEffect.Jackpot(bonus = 250, growth = 4))))
        val next = engine.tick(state)
        assertEquals(250, next.score)
        assertEquals(4, next.pendingGrowth)
        assertTrue(next.lastEvents.any { it is GameEvent.JackpotHit })
    }

    // --- Spawn gating -----------------------------------------------------

    @Test
    fun specialsAreTimeGatedAfterMystery() {
        // Before the special gate, no special is ever produced.
        repeat(2000) { seed ->
            val spec = FoodTable.roll(Random(seed.toLong()), elapsedTicks = 0, level = Level.Beginner)
            assertFalse(spec.category == FoodCategory.Special)
        }
        // Far into a session, specials do appear.
        val late = (0 until 6000).map {
            FoodTable.roll(Random(it.toLong()), elapsedTicks = 2000, level = Level.Beginner)
        }
        assertTrue(late.any { it.category == FoodCategory.Special })
    }

    @Test
    fun hazardsToggleSuppressesHarmfulSpecials() {
        val withHazards = (0 until 6000).map {
            FoodTable.roll(Random(it.toLong()), 2000, Level.Beginner, hazardsEnabled = true)
        }.filter { it.category == FoodCategory.Special }
        val noHazards = (0 until 6000).map {
            FoodTable.roll(Random(it.toLong()), 2000, Level.Beginner, hazardsEnabled = false)
        }.filter { it.category == FoodCategory.Special }

        assertTrue("hazards appear when enabled", withHazards.any { it.effect.isHazard })
        assertTrue("specials still appear when disabled", noHazards.isNotEmpty())
        assertTrue("no hazards when disabled", noHazards.none { it.effect.isHazard })
    }

    @Test
    fun specialSuppressedWhenNotAllowed() {
        repeat(4000) {
            val spec = FoodTable.roll(Random(it.toLong()), 2000, Level.Beginner, specialAllowed = false)
            assertFalse(spec.category == FoodCategory.Special)
        }
    }

    @Test
    fun onlyOneSpecialIsKeptOnTheBoard() {
        // Deep into a session the refill must never seat a second special.
        var state = engine.newGame(Level.Legend, board)
            .copy(elapsedTicks = 5000)
        repeat(300) {
            state = engine.tick(state)
            if (state.status != GameStatus.Running) state = state.copy(status = GameStatus.Running)
            assertTrue(state.foods.count { it.category == FoodCategory.Special } <= 1)
        }
    }

    @Test
    fun rollIsDeterministicForSpecials() {
        val a = FoodTable.roll(Random(99), 2000, Level.Beginner)
        val b = FoodTable.roll(Random(99), 2000, Level.Beginner)
        assertEquals(a, b)
    }

    @Test
    fun freshStateHasNoSpecialsActive() {
        val state = engine.newGame(Level.Beginner, board)
        assertTrue(state.debris.isEmpty())
        assertTrue(state.effectTimers.isEmpty())
        assertNull(state.foods.firstOrNull { it.category == FoodCategory.Special })
    }
}

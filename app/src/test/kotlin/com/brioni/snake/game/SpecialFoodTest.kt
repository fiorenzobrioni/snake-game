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
        mode: GameMode = GameMode.Classic,
        elapsedTicks: Int = 0,
        playedMs: Long = 0,
        timeAdjustMs: Long = 0,
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
        mode = mode,
        elapsedTicks = elapsedTicks,
        playedMs = playedMs,
        debris = debris,
        effectTimers = effectTimers,
        timeAdjustMs = timeAdjustMs,
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

    @Test
    fun quakeScattersBittenTailAsLethalDebris() {
        val state = runningState(snake = longSnake, foods = listOf(specialAt(Position(6, 5), FoodEffect.Quake(3))))
        val next = engine.tick(state)
        // The 3 bitten cells reappear as the same number of debris blocks.
        assertEquals(3, next.debris.size)
        val quake = next.lastEvents.filterIsInstance<GameEvent.Quaked>().single()
        assertEquals(3, quake.removed)
        assertEquals(3, quake.debris.size)
        // Every scattered cell lands on a free square (no snake / obstacle / food).
        val blocked = next.snake.toSet() + next.obstacles + next.foods.flatMap { it.cells() }
        assertTrue(next.debris.none { it.cell in blocked })
        // And they are unique cells.
        assertEquals(next.debris.size, next.debris.map { it.cell }.toSet().size)
    }

    @Test
    fun quakeScatterCountMatchesActuallyRemoved() {
        // A short snake can only shed one cell before the floor; scatter matches.
        val short = listOf(Position(5, 5), Position(4, 5), Position(3, 5))
        val next = engine.tick(runningState(snake = short, foods = listOf(specialAt(Position(6, 5), FoodEffect.Quake(9)))))
        val quake = next.lastEvents.filterIsInstance<GameEvent.Quaked>().single()
        assertEquals(quake.removed, next.debris.size)
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
        val base = SnakeSpeed.Relaxed.tickMillis
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

    // --- 3D -------------------------------------------------------------

    @Test
    fun eatingThreeDStartsEffectAndKeepsLength() {
        val state = runningState(foods = listOf(specialAt(Position(6, 5), FoodEffect.ThreeD(11_000))))
        val next = engine.tick(state)
        assertTrue(next.hasEffect(EffectKind.ThreeD))
        assertEquals(3, next.snake.size) // pure effect: no growth, no shrink
        assertTrue(
            next.lastEvents.filterIsInstance<GameEvent.EffectStarted>()
                .any { it.kind == EffectKind.ThreeD },
        )
    }

    @Test
    fun threeDAgesDownAndExpiresWithEvent() {
        // 50ms remaining < one Beginner interval → expires this tick.
        val state = runningState(effectTimers = listOf(ActiveEffect(EffectKind.ThreeD, 50, 11_000)))
        val next = engine.tick(state)
        assertFalse(next.hasEffect(EffectKind.ThreeD))
        assertTrue(
            next.lastEvents.filterIsInstance<GameEvent.EffectExpired>()
                .any { it.kind == EffectKind.ThreeD },
        )
    }

    @Test
    fun threeDSlowsTheTickIntervalProportionally() {
        // The 3D view eases the pace by THREED_FACTOR (proportional to the base).
        val base = SnakeSpeed.Relaxed.tickMillis
        val state = runningState(effectTimers = listOf(ActiveEffect(EffectKind.ThreeD, 6_000, 11_000)))
        assertTrue(state.tickIntervalMillis > base)
        assertEquals((base * GameState.THREED_FACTOR).toLong(), state.tickIntervalMillis)
    }

    @Test
    fun threeDWorldSlowsAndUsesLevelPace() {
        // The 3D World flag eases the level base pace by the same factor.
        val base = SnakeSpeed.Relaxed.tickMillis
        val state = runningState().copy(threeDWorld = true)
        assertEquals((base * GameState.THREED_FACTOR).toLong(), state.tickIntervalMillis)
    }

    @Test
    fun threeDWorldDoesNotSpawnTheThreeDFood() {
        val rolls = (0 until 6000).map {
            FoodTable.roll(Random(it.toLong()), 2000, Level.Beginner, threeDWorld = true)
        }
        assertTrue("no 3D food in 3D World", rolls.none { it.effect is FoodEffect.ThreeD })
        // Other specials still appear (it is otherwise Classic-like).
        assertTrue("other specials still spawn", rolls.any { it.category == FoodCategory.Special })
    }

    @Test
    fun threeDIsAHazardGatedByTheToggle() {
        assertTrue(FoodEffect.ThreeD(11_000).isHazard)
        val withHazards = (0 until 6000).map {
            FoodTable.roll(Random(it.toLong()), 2000, Level.Beginner, hazardsEnabled = true)
        }
        assertTrue("3D appears when hazards enabled", withHazards.any { it.effect is FoodEffect.ThreeD })
        val noHazards = (0 until 6000).map {
            FoodTable.roll(Random(it.toLong()), 2000, Level.Beginner, hazardsEnabled = false)
        }
        assertTrue("no 3D when hazards disabled", noHazards.none { it.effect is FoodEffect.ThreeD })
    }

    @Test
    fun threeDSpawnsAsSpecialMaxi() {
        val spec = (0 until 6000).map {
            FoodTable.roll(Random(it.toLong()), 2000, Level.Beginner)
        }.first { it.effect is FoodEffect.ThreeD }
        assertEquals(FoodCategory.Special, spec.category)
        assertEquals(FoodSize.Maxi, spec.size)
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

    // --- Time Attack clock blocks -----------------------------------------

    @Test
    fun timeBonusExtendsTheClock() {
        val state = runningState(
            mode = GameMode.TimeAttack,
            foods = listOf(specialAt(Position(6, 5), FoodEffect.TimeBonus(5))),
        )
        val next = engine.tick(state)
        assertEquals(5_000L, next.timeAdjustMs)
        assertTrue(next.timeRemainingMs > state.timeRemainingMs)
        assertEquals(3, next.snake.size) // pure effect: no growth, no shrink
        assertTrue(next.lastEvents.filterIsInstance<GameEvent.TimeGained>().single().seconds == 5)
    }

    @Test
    fun timePenaltyShortensTheClock() {
        val state = runningState(
            mode = GameMode.TimeAttack,
            foods = listOf(specialAt(Position(6, 5), FoodEffect.TimePenalty(3))),
        )
        val next = engine.tick(state)
        assertEquals(-3_000L, next.timeAdjustMs)
        assertTrue(next.timeRemainingMs < state.timeRemainingMs)
        assertEquals(3, next.snake.size)
        assertTrue(next.lastEvents.filterIsInstance<GameEvent.TimeLost>().single().seconds == 3)
    }

    @Test
    fun timePenaltyCanEndTheRun() {
        // Almost out of time; the penalty drains what is left and ends the game.
        val state = runningState(
            mode = GameMode.TimeAttack,
            playedMs = GameState.TIME_ATTACK_MS - 100,
            foods = listOf(specialAt(Position(6, 5), FoodEffect.TimePenalty(3))),
        )
        val next = engine.tick(state)
        assertEquals(GameStatus.GameOver, next.status)
        assertTrue(next.lastEvents.contains(GameEvent.Died))
    }

    @Test
    fun timeBlocksOnlyRollInTimeAttack() {
        fun rolls(mode: GameMode) = (0 until 6000).map {
            FoodTable.roll(Random(it.toLong()), elapsedTicks = 2000, level = Level.Beginner, mode = mode)
        }
        val classic = rolls(GameMode.Classic)
        assertTrue(classic.none { it.effect is FoodEffect.TimeBonus || it.effect is FoodEffect.TimePenalty })
        val timeAttack = rolls(GameMode.TimeAttack)
        assertTrue(timeAttack.any { it.effect is FoodEffect.TimeBonus })
        assertTrue(timeAttack.any { it.effect is FoodEffect.TimePenalty })
    }

    // --- Special timeout --------------------------------------------------

    @Test
    fun specialsVanishAfterTheirLongTimeout() {
        // Beginner tick = 175ms; special timeout = 14_000ms ≈ 80 ticks.
        val state = runningState(
            snake = listOf(Position(5, 5), Position(4, 5), Position(3, 5)),
            direction = Direction.Up, // steer away from the special so it isn't eaten
            foods = listOf(specialAt(Position(10, 10), FoodEffect.Haste(6_000))),
            elapsedTicks = 80,
        )
        val next = engine.tick(state)
        assertTrue(next.lastEvents.filterIsInstance<GameEvent.FoodVanished>().any {
            it.food.category == FoodCategory.Special
        })
    }

    @Test
    fun specialsOutlastRegularFoodTimeout() {
        // Aged past the regular 7s timeout but below the 14s special one: it stays.
        val state = runningState(
            snake = listOf(Position(5, 5), Position(4, 5), Position(3, 5)),
            direction = Direction.Up,
            foods = listOf(specialAt(Position(10, 10), FoodEffect.Haste(6_000))),
            elapsedTicks = 50, // ~8.75s: past regular (40 ticks), short of special (80)
        )
        val next = engine.tick(state)
        assertTrue(next.foods.any { it.category == FoodCategory.Special })
        assertTrue(next.lastEvents.none { it is GameEvent.FoodVanished })
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
    fun atMostTwoSpecialsOnTheBoard() {
        // Deep into a session the refill must never seat a third special.
        var state = engine.newGame(Level.Legend, board)
            .copy(elapsedTicks = 5000)
        repeat(300) {
            state = engine.tick(state, specialFrequency = SpecialFrequency.Frenzy)
            if (state.status != GameStatus.Running) state = state.copy(status = GameStatus.Running)
            assertTrue(state.foods.count { it.category == FoodCategory.Special } <= GameEngine.MAX_SPECIALS_ON_BOARD)
        }
    }

    @Test
    fun twoSpecialsCanCoexist() {
        // With the cap at two and a high spawn rate, a long run should at some
        // point show both special slots filled at once.
        var state = engine.newGame(Level.Legend, board)
            .copy(elapsedTicks = 5000)
        var sawTwo = false
        repeat(600) {
            state = engine.tick(state, specialFrequency = SpecialFrequency.Frenzy)
            if (state.status != GameStatus.Running) state = state.copy(status = GameStatus.Running)
            if (state.foods.count { it.category == FoodCategory.Special } == 2) sawTwo = true
        }
        assertTrue("two specials should be able to share the board", sawTwo)
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

    // --- Special frequency setting ---------------------------------------

    private fun specialCount(frequency: SpecialFrequency, elapsedTicks: Int): Int =
        (0 until 6000).count {
            FoodTable.roll(Random(it.toLong()), elapsedTicks, Level.Beginner, specialFrequency = frequency)
                .category == FoodCategory.Special
        }

    @Test
    fun higherFrequencyYieldsMoreSpecials() {
        // Past every gate, the weight bump must show through over many rolls.
        val standard = specialCount(SpecialFrequency.Standard, elapsedTicks = 2000)
        val frequent = specialCount(SpecialFrequency.Frequent, elapsedTicks = 2000)
        val frenzy = specialCount(SpecialFrequency.Frenzy, elapsedTicks = 2000)
        assertTrue("frequent > standard", frequent > standard)
        assertTrue("frenzy > frequent", frenzy > frequent)
    }

    @Test
    fun frenzyUnlocksSpecialsEarlierThanStandard() {
        // At a tick where the standard gate hasn't opened yet, Frenzy's lower
        // gate factor already lets specials through.
        // Relaxed tickMillis = 175; gate (Standard) = 60_000 ms ≈ 343 ticks.
        val earlyTicks = 150
        val standardEarly = specialCount(SpecialFrequency.Standard, earlyTicks)
        val frenzyEarly = specialCount(SpecialFrequency.Frenzy, earlyTicks)
        assertEquals("standard still gated this early", 0, standardEarly)
        assertTrue("frenzy already unlocked", frenzyEarly > 0)
    }
}

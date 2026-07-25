package com.callbackdev.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The two rules that turn length into a decision: the **risk bonus** (points
 * scale with how much of the board the body covers) and the **Shed** ability
 * (spend a charge to cut the tail loose and cash the risk in).
 */
class RiskAndAbilityTest {

    private val engine = GameEngine(Random(11))
    private val board = BoardDimensions(20, 30) // 600 playable cells, no obstacles

    private fun runningState(
        snake: List<Position> = listOf(Position(5, 5), Position(4, 5), Position(3, 5)),
        foods: List<Food> = emptyList(),
        obstacles: Set<Position> = emptySet(),
        abilityCharge: Int = 0,
        growthRate: GrowthRate = GrowthRate.Off,
    ) = GameState(
        board = board,
        level = Level.Beginner,
        snake = snake,
        direction = Direction.Right,
        pendingDirection = Direction.Right,
        foods = foods,
        obstacles = obstacles,
        score = 0,
        pendingGrowth = 0,
        status = GameStatus.Running,
        growthRate = growthRate,
        abilityCharge = abilityCharge,
        graceAvailable = true,
    )

    /**
     * A body of [length] cells: the head alone on its row with the rest coiled
     * well below it, so the cell ahead of the head is always free. The engine only
     * tests head-against-body, so the shape does not have to be contiguous.
     */
    private fun bodyOf(length: Int): List<Position> =
        listOf(Position(2, 3)) + (0 until length - 1).map { Position(2 + it % 16, 10 + it / 16) }

    private fun growFoodAt(cell: Position, segments: Int = 2) = Food(
        position = cell,
        category = FoodCategory.Grow,
        tier = FoodTier.Medium,
        size = FoodSize.Standard,
        effect = FoodEffect.Grow(segments),
    )

    // --- The risk bonus ------------------------------------------------------

    @Test
    fun `the risk multiplier climbs with the share of the board covered`() {
        val empty = GameState.riskFactorFor(1, 600)
        val quarterOfTheCap = GameState.riskFactorFor(30, 600) // 5% fill
        val atTheCap = GameState.riskFactorFor((600 * GameState.RISK_FULL_FILL).toInt(), 600)
        assertEquals(1f, empty, 0.05f)
        assertTrue(quarterOfTheCap > empty)
        assertEquals(GameState.MAX_RISK_FACTOR, atTheCap, 0.01f)
        // And it never runs past the cap, however full the board gets.
        assertEquals(GameState.MAX_RISK_FACTOR, GameState.riskFactorFor(600, 600), 0.001f)
    }

    @Test
    fun `the same length is worth more on a smaller arena`() {
        // The point of measuring fill instead of raw length: 40 segments choke a
        // small board and are nothing on a big one, so they must not pay the same.
        val small = GameState.riskFactorFor(40, 286) // Cozy
        val large = GameState.riskFactorFor(40, 2030) // Colossal
        assertTrue(small > large)
    }

    @Test
    fun `obstacles and walls count against the playable area`() {
        val open = runningState()
        val cluttered = open.copy(obstacles = (0 until 200).map { Position(it % 20, 25 + it / 20) }.toSet())
        assertTrue(cluttered.playableCells < open.playableCells)
        assertTrue(cluttered.riskFactor > open.riskFactor)
    }

    @Test
    fun `a bite pays more when the board is fuller`() {
        fun scoreFor(length: Int): Int {
            val body = bodyOf(length)
            val head = body.first()
            val state = runningState(snake = body, foods = listOf(growFoodAt(Position(head.x + 1, head.y))))
            return engine.tick(state).score
        }
        assertTrue(scoreFor(60) > scoreFor(10))
    }

    @Test
    fun `the risk zone flags only once the multiplier is worth shouting about`() {
        assertFalse(runningState(snake = bodyOf(4)).inRiskZone)
        assertTrue(runningState(snake = bodyOf(80)).inRiskZone)
    }

    // --- The Shed ability ----------------------------------------------------

    @Test
    fun `eating charges the ability, and a streak charges it twice as fast`() {
        val head = Position(5, 5)
        val plain = engine.tick(runningState(foods = listOf(growFoodAt(Position(head.x + 1, head.y)))))
        assertEquals(1, plain.abilityCharge)

        val onStreak = engine.tick(
            runningState(foods = listOf(growFoodAt(Position(head.x + 1, head.y))))
                .copy(combo = GameEngine.ABILITY_COMBO_BONUS_AT - 1, comboDeadlineTick = 100),
        )
        assertEquals(2, onStreak.abilityCharge)
    }

    @Test
    fun `the charge tops out and announces itself once`() {
        val head = Position(5, 5)
        val nearlyFull = runningState(
            foods = listOf(growFoodAt(Position(head.x + 1, head.y))),
            abilityCharge = GameEngine.ABILITY_CHARGE_FULL - 1,
        )
        val charged = engine.tick(nearlyFull)
        assertEquals(GameEngine.ABILITY_CHARGE_FULL, charged.abilityCharge)
        assertTrue(charged.abilityReady)
        assertTrue(charged.lastEvents.contains(GameEvent.AbilityCharged))

        // Already full: it neither overflows nor re-announces.
        val again = engine.tick(
            charged.copy(foods = listOf(growFoodAt(Position(charged.head.x + 1, charged.head.y)))),
        )
        assertEquals(GameEngine.ABILITY_CHARGE_FULL, again.abilityCharge)
        assertFalse(again.lastEvents.contains(GameEvent.AbilityCharged))
    }

    @Test
    fun `shedding cuts a share of the tail and pays for the risk carried`() {
        val state = runningState(snake = bodyOf(40), abilityCharge = GameEngine.ABILITY_CHARGE_FULL)
        val after = engine.useAbility(state)
        val used = after.lastEvents.filterIsInstance<GameEvent.AbilityUsed>().single()

        // 35% of 40 = 14 cells, taken from the tail; the head never moves.
        assertEquals(14, used.segments)
        assertEquals(14, used.cells.size)
        assertEquals(26, after.snake.size)
        assertEquals(state.head, after.head)
        assertEquals(state.snake.last(), used.cells.last())
        // Paid out, charge spent.
        assertTrue(used.points > 0)
        assertEquals(state.score + used.points, after.score)
        assertEquals(0, after.abilityCharge)
    }

    @Test
    fun `the payout scales with the risk being cashed in`() {
        fun payoutPerSegment(length: Int): Float {
            val state = runningState(snake = bodyOf(length), abilityCharge = GameEngine.ABILITY_CHARGE_FULL)
            val used = engine.useAbility(state).lastEvents
                .filterIsInstance<GameEvent.AbilityUsed>().single()
            return used.points.toFloat() / used.segments
        }
        assertTrue(payoutPerSegment(90) > payoutPerSegment(20))
    }

    @Test
    fun `the charge is never spent for nothing`() {
        // Not charged: a tap does nothing at all.
        val uncharged = runningState(snake = bodyOf(40), abilityCharge = 3)
        assertSame(uncharged, engine.useAbility(uncharged))

        // Charged but already at the floor: the charge is kept for later.
        val tiny = runningState(
            snake = bodyOf(GameEngine.MIN_SNAKE_LENGTH),
            abilityCharge = GameEngine.ABILITY_CHARGE_FULL,
        )
        val after = engine.useAbility(tiny)
        assertSame(tiny, after)
        assertTrue(after.abilityReady)

        // And it is a no-op outside a running game.
        val paused = runningState(snake = bodyOf(40), abilityCharge = GameEngine.ABILITY_CHARGE_FULL)
            .copy(status = GameStatus.Paused)
        assertSame(paused, engine.useAbility(paused))
    }

    @Test
    fun `shedding drops owed growth so the escape is not undone`() {
        val state = runningState(snake = bodyOf(40), abilityCharge = GameEngine.ABILITY_CHARGE_FULL)
            .copy(pendingGrowth = 6)
        assertEquals(0, engine.useAbility(state).pendingGrowth)
    }

    @Test
    fun `the growth multiplier is applied to the payout too`() {
        fun payout(rate: GrowthRate): Int {
            val state = runningState(
                snake = bodyOf(40),
                abilityCharge = GameEngine.ABILITY_CHARGE_FULL,
                growthRate = rate,
            )
            return engine.useAbility(state).lastEvents
                .filterIsInstance<GameEvent.AbilityUsed>().single().points
        }
        assertTrue(payout(GrowthRate.Relentless) > payout(GrowthRate.Off))
    }

    @Test
    fun `the charge survives a campaign level change`() {
        val state = engine
            .setup(LevelsMode.SCORE_LEVEL, board, GameMode.Levels)
            .copy(
                status = GameStatus.Running,
                abilityCharge = 7,
                levelFoodsEaten = LevelsMode.LEVEL_FOOD_GOAL - 1,
                foods = listOf(growFoodAt(Position(board.width / 2, board.height / 2 - 1), segments = 1)),
            )
        val next = engine.tick(state)
        assertEquals(GameStatus.LevelIntro, next.status)
        // Earned like the score and the lives, so it carries across the staging
        // (+1 for the bite that completed the level).
        assertEquals(8, next.abilityCharge)
        assertNotNull(next.lastEvents.filterIsInstance<GameEvent.LevelAdvanced>().firstOrNull())
    }
}

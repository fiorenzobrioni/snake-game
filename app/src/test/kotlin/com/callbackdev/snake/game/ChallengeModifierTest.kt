package com.callbackdev.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The challenge twists' rule knobs, as consumed by the pure engine: Maxi Feast
 * (all food 2x2), Old School (no specials), Combo Rush (halved combo window)
 * and Overdrive (hotter Endless ramp, Turbo pace in Time Attack).
 */
class ChallengeModifierTest {

    private val board = BoardDimensions(18, 26)

    @Test
    fun `maxi feast rolls only maxi grow and shrink food`() {
        val random = Random(5)
        repeat(200) {
            val spec = FoodTable.roll(
                random, elapsedTicks = 0, level = Level.Beginner,
                specialAllowed = false, forceMaxi = true,
            )
            assertEquals(FoodSize.Maxi, spec.size)
        }
    }

    @Test
    fun `old school never spawns specials`() {
        // Deep into a run (well past the special unlock gate) with the Frenzy
        // frequency, an Old School board must still hold zero specials.
        val engine = GameEngine(Random(9))
        var state = engine.setup(
            Level.Beginner, board, GameMode.Endless,
            modifier = ChallengeModifier.OldSchool,
        )
        state = engine.start(state)
        // Fast-forward the unlock clock, then let food churn for a while.
        state = state.copy(elapsedTicks = 5_000)
        repeat(60) {
            // Keep the snake circling safely in the open centre.
            state = state.copy(
                snake = listOf(Position(5, 5), Position(4, 5), Position(3, 5)),
                direction = Direction.Right, pendingDirection = Direction.Right,
            )
            state = engine.tick(state, specialFrequency = SpecialFrequency.Frenzy)
            assertTrue(state.foods.none { it.category == FoodCategory.Special })
        }
    }

    @Test
    fun `combo rush halves the combo window`() {
        val engine = GameEngine(Random(13))
        fun eatAndReadDeadline(modifier: ChallengeModifier): Int {
            val food = Food(
                position = Position(6, 5), category = FoodCategory.Grow,
                tier = FoodTier.Small, size = FoodSize.Standard, effect = FoodEffect.Grow(2),
            )
            val state = GameState(
                board = board, level = Level.Beginner, modifier = modifier,
                snake = listOf(Position(5, 5), Position(4, 5), Position(3, 5)),
                direction = Direction.Right, pendingDirection = Direction.Right,
                foods = listOf(food), obstacles = emptySet(), score = 0, pendingGrowth = 0,
                status = GameStatus.Running, mode = GameMode.Endless,
            )
            return engine.tick(state).comboDeadlineTick
        }
        val standard = eatAndReadDeadline(ChallengeModifier.None)
        val rushed = eatAndReadDeadline(ChallengeModifier.ComboRush)
        assertEquals(1 + GameEngine.COMBO_WINDOW_TICKS, standard)
        assertEquals(1 + GameEngine.COMBO_WINDOW_TICKS / 2, rushed)
    }

    @Test
    fun `overdrive starts the endless ramp hotter`() {
        val plain = GameState(
            board = board, level = Level.Beginner,
            snake = listOf(Position(5, 5)), direction = Direction.Right,
            pendingDirection = Direction.Right, foods = emptyList(), obstacles = emptySet(),
            score = 0, pendingGrowth = 0, status = GameStatus.Running, mode = GameMode.Endless,
        )
        val boosted = plain.copy(modifier = ChallengeModifier.Overdrive)
        assertTrue(boosted.endlessSpeedTier > plain.endlessSpeedTier)
        assertTrue(boosted.tickIntervalMillis < plain.tickIntervalMillis)
        assertEquals(SnakeSpeed.Turbo, ChallengeModifier.Overdrive.speedOverride)
    }

    @Test
    fun `every modifier keeps a coherent knob set`() {
        for (m in ChallengeModifier.entries) {
            assertTrue(m.displayName.isNotBlank())
            assertTrue(m.description.isNotBlank())
            assertTrue(m.comboWindowFactor > 0f)
            assertTrue(m.endlessTierBoost >= 0)
        }
        // The pool is wide enough that a plain day is the exception, not the rule.
        assertTrue(ChallengeModifier.entries.size >= 8)
    }
}

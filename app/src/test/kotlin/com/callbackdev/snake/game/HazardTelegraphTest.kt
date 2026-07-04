package com.callbackdev.snake.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Step 6.9.1: the engine telegraphs a hazard one tick before the snake eats it,
 * by emitting [GameEvent.HazardImminent] when continuing straight would land on a
 * hazard food next tick. Predictive only - it never changes the rules.
 */
class HazardTelegraphTest {

    private val engine = GameEngine(Random(1))

    /** Head at (5,5) heading Right, three foods so no refill perturbs the test. */
    private fun stateWith(foods: List<Food>) = GameState(
        board = BoardDimensions(18, 26),
        level = Level.Beginner,
        snake = listOf(Position(5, 5), Position(4, 5), Position(3, 5)),
        direction = Direction.Right,
        pendingDirection = Direction.Right,
        foods = foods,
        obstacles = emptySet(),
        score = 0,
        pendingGrowth = 0,
        status = GameStatus.Running,
    )

    private fun grow(x: Int, y: Int) =
        Food(Position(x, y), FoodCategory.Grow, FoodTier.Small, FoodSize.Standard, FoodEffect.Grow(2))

    private fun quake(x: Int, y: Int) =
        Food(Position(x, y), FoodCategory.Special, FoodTier.Huge, FoodSize.Standard, FoodEffect.Quake(3_500L))

    @Test
    fun warnsOneTickBeforeEatingAHazardAhead() {
        // Hazard two cells ahead: after this tick the head is at (6,5) and the
        // cell it is about to enter, (7,5), holds the hazard.
        val state = stateWith(listOf(quake(7, 5), grow(2, 10), grow(15, 20)))
        val next = engine.tick(state)
        assertTrue(
            "expected a HazardImminent telegraph",
            next.lastEvents.any { it is GameEvent.HazardImminent },
        )
        assertFalse("must not die from the telegraph", next.status == GameStatus.GameOver)
    }

    @Test
    fun telegraphIsFollowedByTheStrikeNextTick() {
        val first = engine.tick(stateWith(listOf(quake(7, 5), grow(2, 10), grow(15, 20))))
        val second = engine.tick(first)
        assertTrue(
            "the hazard should fire the tick after the telegraph",
            second.lastEvents.any { it is GameEvent.EffectStarted && it.kind == EffectKind.Quake },
        )
    }

    @Test
    fun noWarningWhenTheHazardIsNotInThePath() {
        // Hazard beside the path (below the head's next cell), never entered.
        val next = engine.tick(stateWith(listOf(quake(6, 6), grow(2, 10), grow(15, 20))))
        assertFalse(
            "a hazard off the heading must not telegraph",
            next.lastEvents.any { it is GameEvent.HazardImminent },
        )
    }
}

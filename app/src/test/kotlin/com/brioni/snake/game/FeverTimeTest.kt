package com.brioni.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Time Attack scoring rules: the Fever Time finale (last [GameState.FEVER_MS],
 * doubled points, [GameEvent.FeverStarted] on entry) and the declared per-pace
 * score multiplier ([SnakeSpeed.timeAttackScoreFactor]).
 */
class FeverTimeTest {

    private val engine = GameEngine(Random(11))
    private val board = BoardDimensions(18, 26)

    private fun taState(
        playedMs: Long = 0,
        speed: SnakeSpeed = SnakeSpeed.Relaxed,
        foods: List<Food> = emptyList(),
    ) = GameState(
        board = board, level = Level.Beginner, snakeSpeed = speed,
        snake = listOf(Position(5, 5), Position(4, 5), Position(3, 5)),
        direction = Direction.Right, pendingDirection = Direction.Right,
        foods = foods, obstacles = emptySet(), score = 0, pendingGrowth = 0,
        status = GameStatus.Running, mode = GameMode.TimeAttack, playedMs = playedMs,
    )

    /** A grow food directly on the head's next cell ((5,5) heading Right → (6,5)). */
    private fun growFood(segments: Int = 2) = Food(
        position = Position(6, 5),
        category = FoodCategory.Grow,
        tier = FoodTier.Small,
        size = FoodSize.Standard,
        effect = FoodEffect.Grow(segments),
    )

    @Test
    fun `fever window opens over the last FEVER_MS`() {
        assertFalse(taState(playedMs = 0).inFeverTime)
        val feverStart = GameState.TIME_ATTACK_MS - GameState.FEVER_MS
        assertTrue(taState(playedMs = feverStart).inFeverTime)
        assertTrue(taState(playedMs = GameState.TIME_ATTACK_MS - 1_000).inFeverTime)
        // Expired clock: no longer fever (the run is over anyway).
        assertFalse(taState(playedMs = GameState.TIME_ATTACK_MS).inFeverTime)
        // Endless never enters fever.
        assertFalse(taState(playedMs = feverStart).copy(mode = GameMode.Endless).inFeverTime)
    }

    @Test
    fun `entering the window emits FeverStarted once`() {
        val justBefore = GameState.TIME_ATTACK_MS - GameState.FEVER_MS - 1
        val entering = engine.tick(taState(playedMs = justBefore))
        assertTrue(entering.lastEvents.contains(GameEvent.FeverStarted))
        // Already inside: no re-announcement.
        assertFalse(engine.tick(entering).lastEvents.contains(GameEvent.FeverStarted))
    }

    @Test
    fun `points double during fever`() {
        // Same eat, same pace — once outside and once inside the fever window.
        val calm = engine.tick(taState(playedMs = 0, foods = listOf(growFood())))
        val fever = engine.tick(
            taState(playedMs = GameState.TIME_ATTACK_MS - GameState.FEVER_MS, foods = listOf(growFood())),
        )
        assertTrue(calm.score > 0)
        assertEquals(calm.score * GameState.FEVER_SCORE_FACTOR, fever.score)
    }

    @Test
    fun `faster paces multiply the score`() {
        val relaxed = engine.tick(taState(speed = SnakeSpeed.Relaxed, foods = listOf(growFood())))
        val turbo = engine.tick(taState(speed = SnakeSpeed.Turbo, foods = listOf(growFood())))
        assertEquals(
            (relaxed.score * SnakeSpeed.Turbo.timeAttackScoreFactor).toInt(),
            turbo.score,
        )
    }

    @Test
    fun `endless scoring is untouched by pace and fever`() {
        val slow = engine.tick(
            taState(speed = SnakeSpeed.Relaxed, foods = listOf(growFood())).copy(mode = GameMode.Endless),
        )
        val fast = engine.tick(
            taState(speed = SnakeSpeed.Turbo, foods = listOf(growFood())).copy(mode = GameMode.Endless),
        )
        assertEquals(slow.score, fast.score)
    }

    @Test
    fun `the clock still ends the run during fever`() {
        val next = engine.tick(taState(playedMs = GameState.TIME_ATTACK_MS - 10))
        assertEquals(GameStatus.GameOver, next.status)
    }
}

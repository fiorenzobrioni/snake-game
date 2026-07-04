package com.callbackdev.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.random.Random

/** Tests for the relative two-button steering. */
class RelativeTurnTest {

    private val engine = GameEngine(Random(1))

    private fun running(direction: Direction) = GameState(
        board = BoardDimensions(18, 26),
        level = Level.Beginner,
        snake = listOf(Position(9, 13), Position(9, 14), Position(9, 15)),
        direction = direction,
        pendingDirection = direction,
        foods = emptyList(),
        obstacles = emptySet(),
        score = 0,
        pendingGrowth = 0,
        status = GameStatus.Running,
    )

    @Test
    fun turnedLeftCyclesCounterClockwise() {
        assertEquals(Direction.Left, Direction.Up.turnedLeft)
        assertEquals(Direction.Down, Direction.Left.turnedLeft)
        assertEquals(Direction.Right, Direction.Down.turnedLeft)
        assertEquals(Direction.Up, Direction.Right.turnedLeft)
    }

    @Test
    fun turnedRightCyclesClockwise() {
        assertEquals(Direction.Right, Direction.Up.turnedRight)
        assertEquals(Direction.Down, Direction.Right.turnedRight)
        assertEquals(Direction.Left, Direction.Down.turnedRight)
        assertEquals(Direction.Up, Direction.Left.turnedRight)
    }

    @Test
    fun relativeTurnsNeverProduceAReversal() {
        Direction.entries.forEach { d ->
            assertFalse(d.turnedLeft.isOpposite(d))
            assertFalse(d.turnedRight.isOpposite(d))
        }
    }

    @Test
    fun engineTurnsRelativeToCommittedHeading() {
        assertEquals(Direction.Left, engine.turnLeft(running(Direction.Up)).pendingDirection)
        assertEquals(Direction.Right, engine.turnRight(running(Direction.Up)).pendingDirection)
        assertEquals(Direction.Up, engine.turnLeft(running(Direction.Right)).pendingDirection)
        assertEquals(Direction.Down, engine.turnRight(running(Direction.Right)).pendingDirection)
    }

    @Test
    fun twoSameSideTurnsInOneTickCannotReverse() {
        // Heading Up, tap left twice before the tick commits. The second turn is
        // validated against the still-committed Up, so it can't fold into Down.
        var state = running(Direction.Up)
        state = engine.turnLeft(state) // pending → Left
        assertEquals(Direction.Left, state.pendingDirection)
        state = engine.turnLeft(state) // would be Down (a reversal) → rejected
        assertEquals(Direction.Left, state.pendingDirection)
    }
}

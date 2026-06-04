package com.brioni.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** Invariants for the 4-fold symmetric obstacle layout. */
class ObstacleSymmetryTest {

    private val boards = listOf(
        BoardDimensions(12, 20),
        BoardDimensions(18, 30),
        BoardDimensions(26, 39),
        BoardDimensions(15, 25), // odd dimensions exercise the centre axes
    )

    private fun setup(board: BoardDimensions, level: Level, seed: Long = 7L) =
        GameEngine(Random(seed)).setup(level, board)

    @Test
    fun obstaclesAreMirroredAcrossBothAxes() {
        boards.forEach { board ->
            val obstacles = setup(board, Level.Champion).obstacles
            obstacles.forEach { p ->
                assertTrue("horizontal mirror missing", Position(board.width - 1 - p.x, p.y) in obstacles)
                assertTrue("vertical mirror missing", Position(p.x, board.height - 1 - p.y) in obstacles)
            }
        }
    }

    @Test
    fun obstaclesKeepTwoCellMarginFromEveryBorder() {
        boards.forEach { board ->
            setup(board, Level.Legend).obstacles.forEach { p ->
                assertTrue("x too close to border: $p", p.x in 2..(board.width - 3))
                assertTrue("y too close to border: $p", p.y in 2..(board.height - 3))
            }
        }
    }

    @Test
    fun centreSpawnZoneStaysClear() {
        boards.forEach { board ->
            val state = setup(board, Level.Legend)
            val cx = (board.width - 1) / 2f
            val cy = (board.height - 1) / 2f
            val rx = (board.width * 0.18f).coerceAtLeast(2f)
            val ry = (board.height * 0.18f).coerceAtLeast(3f)
            state.obstacles.forEach { p ->
                val inZone = abs(p.x - cx) <= rx && abs(p.y - cy) <= ry
                assertFalse("obstacle inside spawn zone: $p", inZone)
            }
            // And the snake itself never overlaps an obstacle.
            state.snake.forEach { assertFalse(it in state.obstacles) }
        }
    }

    @Test
    fun layoutIsDeterministicForAFixedSeed() {
        val a = setup(BoardDimensions(18, 30), Level.Warrior, seed = 99L).obstacles
        val b = setup(BoardDimensions(18, 30), Level.Warrior, seed = 99L).obstacles
        assertEquals(a, b)
    }

    @Test
    fun beginnerHasNoObstacles() {
        boards.forEach { board ->
            assertTrue(setup(board, Level.Beginner).obstacles.isEmpty())
        }
    }
}

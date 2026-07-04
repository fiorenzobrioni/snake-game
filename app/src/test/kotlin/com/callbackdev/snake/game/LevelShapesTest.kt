package com.callbackdev.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

/**
 * Safety properties of the ten designed Levels-mode board shapes, checked over
 * representative responsive boards: the Cozy/Classic/Epic portrait presets plus
 * a landscape layout. Shapes are procedural, so these guards must hold for any
 * board the responsive sizing can produce.
 */
class LevelShapesTest {

    private val boards = listOf(
        BoardDimensions(13, 22), // Cozy portrait (odd columns)
        BoardDimensions(19, 32), // Classic portrait (odd columns)
        BoardDimensions(27, 45), // Epic portrait (odd columns)
        BoardDimensions(30, 19), // landscape (even columns must keep working)
    )

    private fun spawn(board: BoardDimensions): List<Position> {
        val cx = board.width / 2
        val cy = board.height / 2
        return listOf(Position(cx, cy), Position(cx, cy + 1), Position(cx, cy + 2))
    }

    @Test
    fun `level 1 is the plain rectangle`() {
        boards.forEach { board ->
            assertTrue(LevelsMode.shapeFor(1, board).isEmpty())
        }
    }

    @Test
    fun `every other level reshapes the board`() {
        boards.forEach { board ->
            for (level in 2..LevelsMode.LEVEL_COUNT) {
                assertTrue(
                    "level $level on ${board.width}x${board.height} should have walls",
                    LevelsMode.shapeFor(level, board).isNotEmpty(),
                )
            }
        }
    }

    @Test
    fun `shapes are deterministic`() {
        boards.forEach { board ->
            for (level in 1..LevelsMode.LEVEL_COUNT) {
                assertEquals(LevelsMode.shapeFor(level, board), LevelsMode.shapeFor(level, board))
            }
        }
    }

    @Test
    fun `shapes stay inside the board`() {
        boards.forEach { board ->
            for (level in 1..LevelsMode.LEVEL_COUNT) {
                LevelsMode.shapeFor(level, board).forEach { cell ->
                    assertTrue(cell.x in 0 until board.width && cell.y in 0 until board.height)
                }
            }
        }
    }

    @Test
    fun `the spawn and its protected zone are always clear`() {
        boards.forEach { board ->
            val clearZone = LevelsMode.protectedCenter(board)
            assertTrue(spawn(board).all { it in clearZone })
            for (level in 1..LevelsMode.LEVEL_COUNT) {
                val walls = LevelsMode.shapeFor(level, board)
                assertTrue(
                    "level $level on ${board.width}x${board.height} blocks the protected zone",
                    clearZone.none { it in walls },
                )
            }
        }
    }

    @Test
    fun `every playable cell is reachable from the spawn`() {
        boards.forEach { board ->
            for (level in 1..LevelsMode.LEVEL_COUNT) {
                val walls = LevelsMode.shapeFor(level, board)
                val playable = (board.width * board.height) - walls.size
                val reached = floodFill(spawn(board).first(), walls, board)
                assertEquals(
                    "level $level on ${board.width}x${board.height} has unreachable pockets",
                    playable, reached,
                )
            }
        }
    }

    @Test
    fun `shapes keep at least 60 percent of the board playable`() {
        boards.forEach { board ->
            val total = board.width * board.height
            for (level in 1..LevelsMode.LEVEL_COUNT) {
                val walls = LevelsMode.shapeFor(level, board)
                assertTrue(
                    "level $level on ${board.width}x${board.height} cuts too much",
                    total - walls.size >= total * 0.6,
                )
            }
        }
    }

    @Test
    fun `the speed curve ramps down to its floor`() {
        var previous = Long.MAX_VALUE
        for (cycle in 1..20) {
            val ms = LevelsMode.tickMillisFor(cycle)
            assertTrue("cycle $cycle should not be slower than cycle ${cycle - 1}", ms <= previous)
            assertTrue(ms >= LevelsMode.FLOOR_TICK_MS)
            previous = ms
        }
        assertEquals(LevelsMode.BASE_TICK_MS, LevelsMode.tickMillisFor(1))
        assertEquals(LevelsMode.FLOOR_TICK_MS, LevelsMode.tickMillisFor(100))
    }

    /** Counts the cells reachable from [start] with 4-way moves avoiding [walls]. */
    private fun floodFill(start: Position, walls: Set<Position>, board: BoardDimensions): Int {
        val seen = HashSet<Position>()
        val queue = ArrayDeque<Position>()
        seen.add(start)
        queue.add(start)
        while (queue.isNotEmpty()) {
            val cell = queue.poll()
            for (direction in Direction.entries) {
                val next = cell.step(direction)
                if (next.x !in 0 until board.width || next.y !in 0 until board.height) continue
                if (next in walls || !seen.add(next)) continue
                queue.add(next)
            }
        }
        return seen.size
    }
}

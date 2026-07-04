package com.callbackdev.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque
import kotlin.random.Random

/**
 * Safety and behaviour of the Campaign environmental hazards (Step 6.9.7):
 * moving-wall [Gate]s and [TeleportPair] portals. The shape-level invariants are
 * checked over the same representative responsive boards as [LevelShapesTest]
 * (Cozy/Classic/Epic portrait plus a landscape), and the engine integration is
 * exercised through [GameEngine.tick].
 */
class LevelHazardsTest {

    private val boards = listOf(
        BoardDimensions(13, 22), // Cozy portrait (odd columns)
        BoardDimensions(19, 32), // Classic portrait (odd columns)
        BoardDimensions(27, 45), // Epic portrait (odd columns)
        BoardDimensions(30, 19), // landscape (even columns must keep working)
    )

    private fun spawn(board: BoardDimensions) = Position(board.width / 2, board.height / 2)

    // --- Gate phase logic ---------------------------------------------------

    @Test
    fun `a gate cycles open then closed on its schedule`() {
        val gate = Gate(setOf(Position(3, 3)), period = 10, openTicks = 6, offsetTicks = 0)
        for (t in 0..5) assertTrue("tick $t should be open", gate.isOpenAt(t))
        for (t in 6..9) assertTrue("tick $t should be closed", gate.isClosedAt(t))
        assertTrue(gate.isOpenAt(10)) // wraps
        assertTrue(gate.isClosedAt(16))
    }

    @Test
    fun `a gate offset shifts the whole cycle`() {
        val base = Gate(setOf(Position(3, 3)), period = 10, openTicks = 6, offsetTicks = 0)
        val shifted = Gate(setOf(Position(3, 3)), period = 10, openTicks = 6, offsetTicks = 5)
        assertEquals(base.isOpenAt(5), shifted.isOpenAt(0))
        assertEquals(base.isOpenAt(0), shifted.isOpenAt(5))
    }

    @Test
    fun `closing and opening warnings fire only in the final ticks`() {
        val gate = Gate(setOf(Position(3, 3)), period = 10, openTicks = 6, offsetTicks = 0)
        // Open span is ticks 0..5; the last two (4,5) warn of the imminent close.
        assertFalse(gate.isClosingSoonAt(3, warnTicks = 2))
        assertTrue(gate.isClosingSoonAt(4, warnTicks = 2))
        assertTrue(gate.isClosingSoonAt(5, warnTicks = 2))
        // Closed span is 6..9; the last two (8,9) warn of the imminent open.
        assertFalse(gate.isOpeningSoonAt(7, warnTicks = 2))
        assertTrue(gate.isOpeningSoonAt(8, warnTicks = 2))
        assertTrue(gate.isOpeningSoonAt(9, warnTicks = 2))
    }

    @Test
    fun `teleport pair maps each pad to its partner`() {
        val pair = TeleportPair(Position(1, 1), Position(8, 8))
        assertEquals(Position(8, 8), pair.exitFor(Position(1, 1)))
        assertEquals(Position(1, 1), pair.exitFor(Position(8, 8)))
        assertNull(pair.exitFor(Position(4, 4)))
    }

    // --- Shape-level invariants ---------------------------------------------

    @Test
    fun `hazards are deterministic`() {
        boards.forEach { board ->
            for (level in 1..LevelsMode.LEVEL_COUNT) {
                assertEquals(LevelsMode.hazardsFor(level, board), LevelsMode.hazardsFor(level, board))
            }
        }
    }

    @Test
    fun `hazards never sit on walls or the protected spawn zone`() {
        boards.forEach { board ->
            val clearZone = LevelsMode.protectedCenter(board)
            for (level in 1..LevelsMode.LEVEL_COUNT) {
                val walls = LevelsMode.shapeFor(level, board)
                val hazards = LevelsMode.hazardsFor(level, board)
                val cells = hazards.gates.flatMap { it.cells } + hazards.teleports.flatMap { it.cells }
                cells.forEach { c ->
                    assertTrue("hazard at $c out of bounds (L$level ${board.width}x${board.height})",
                        c.x in 0 until board.width && c.y in 0 until board.height)
                    assertFalse("hazard at $c overlaps a wall (L$level)", c in walls)
                    assertFalse("hazard at $c is in the spawn zone (L$level)", c in clearZone)
                }
            }
        }
    }

    @Test
    fun `teleport pads are distinct and do not share cells with gates`() {
        boards.forEach { board ->
            for (level in 1..LevelsMode.LEVEL_COUNT) {
                val hazards = LevelsMode.hazardsFor(level, board)
                val gateCells = hazards.gates.flatMap { it.cells }.toSet()
                hazards.teleports.forEach { pair ->
                    assertTrue("a teleport pair collapsed to one cell (L$level)", pair.a != pair.b)
                    assertFalse("a pad overlaps a gate (L$level)", pair.a in gateCells || pair.b in gateCells)
                }
            }
        }
    }

    @Test
    fun `closing every gate never traps the snake`() {
        boards.forEach { board ->
            for (level in 1..LevelsMode.LEVEL_COUNT) {
                val walls = LevelsMode.shapeFor(level, board)
                val hazards = LevelsMode.hazardsFor(level, board)
                val closed = hazards.gates.flatMap { it.cells }.toSet()
                val blocked = walls + closed
                val playable = board.width * board.height - blocked.size
                val reached = floodFill(spawn(board), blocked, board)
                assertEquals(
                    "L$level on ${board.width}x${board.height}: a fully-closed gate set traps cells",
                    playable, reached,
                )
            }
        }
    }

    @Test
    fun `teleport pads are reachable while gates are open`() {
        boards.forEach { board ->
            for (level in 1..LevelsMode.LEVEL_COUNT) {
                val walls = LevelsMode.shapeFor(level, board)
                val reachable = reachableSet(spawn(board), walls, board)
                LevelsMode.hazardsFor(level, board).teleports.forEach { pair ->
                    assertTrue("pad ${pair.a} unreachable (L$level)", pair.a in reachable)
                    assertTrue("pad ${pair.b} unreachable (L$level)", pair.b in reachable)
                }
            }
        }
    }

    @Test
    fun `the designed levels actually ship gates and portals`() {
        val board = BoardDimensions(19, 32)
        assertTrue("Open Field should have teleports", LevelsMode.hazardsFor(1, board).teleports.isNotEmpty())
        assertTrue("Twin Pillars should have gates", LevelsMode.hazardsFor(3, board).gates.isNotEmpty())
        assertTrue("Three Chambers should have gates", LevelsMode.hazardsFor(9, board).gates.isNotEmpty())
        // A plain level keeps no hazards.
        assertTrue(LevelsMode.hazardsFor(2, board).isEmpty)
    }

    // --- Engine integration -------------------------------------------------

    private val board = BoardDimensions(18, 26)

    private fun levelsState(
        snake: List<Position> = listOf(Position(5, 5), Position(4, 5), Position(3, 5)),
        direction: Direction = Direction.Right,
        gates: List<Gate> = emptyList(),
        teleports: List<TeleportPair> = emptyList(),
        lives: Int = LevelsMode.START_LIVES,
        graceAvailable: Boolean = false,
    ) = GameState(
        board = board,
        level = LevelsMode.SCORE_LEVEL,
        snake = snake,
        direction = direction,
        pendingDirection = direction,
        foods = emptyList(),
        obstacles = emptySet(),
        score = 0,
        pendingGrowth = 0,
        status = GameStatus.Running,
        mode = GameMode.Levels,
        lives = lives,
        gates = gates,
        teleports = teleports,
        graceAvailable = graceAvailable,
    )

    @Test
    fun `stepping onto a pad emerges at its partner`() {
        val pad = Position(6, 5) // one step right of the head
        val exit = Position(12, 20)
        val next = GameEngine(Random(1)).tick(
            levelsState(teleports = listOf(TeleportPair(pad, exit))),
        )
        assertEquals(GameStatus.Running, next.status)
        assertEquals(exit, next.head)
        val event = next.lastEvents.filterIsInstance<GameEvent.Teleported>().single()
        assertEquals(pad, event.from)
        assertEquals(exit, event.to)
    }

    @Test
    fun `a closed gate is lethal like a wall`() {
        // openTicks = 0 → the gate is closed on every tick.
        val gate = Gate(setOf(Position(6, 5)), period = 4, openTicks = 0)
        val next = GameEngine(Random(1)).tick(levelsState(gates = listOf(gate), lives = 1))
        assertEquals(GameStatus.GameOver, next.status)
        assertTrue(next.lastEvents.contains(GameEvent.Died))
    }

    @Test
    fun `an open gate is passable`() {
        // openTicks = period → the gate is open on every tick.
        val gate = Gate(setOf(Position(6, 5)), period = 4, openTicks = 4)
        val next = GameEngine(Random(1)).tick(levelsState(gates = listOf(gate)))
        assertEquals(GameStatus.Running, next.status)
        assertEquals(Position(6, 5), next.head)
    }

    @Test
    fun `ghost passes through a closed gate`() {
        val gate = Gate(setOf(Position(6, 5)), period = 4, openTicks = 0)
        val state = levelsState(gates = listOf(gate)).copy(
            effectTimers = listOf(ActiveEffect(EffectKind.Ghost, 5_000, 5_000)),
        )
        val next = GameEngine(Random(1)).tick(state)
        assertEquals(GameStatus.Running, next.status)
        assertEquals(Position(6, 5), next.head)
    }

    @Test
    fun `food never spawns on gate or teleport cells`() {
        // Level 9 ships gates, level 1 ships teleports - seed both from many engines.
        listOf(1, 9).forEach { level ->
            val hazards = LevelsMode.hazardsFor(level, board)
            val reserved = hazards.gates.flatMap { it.cells }.toSet() + hazards.teleports.flatMap { it.cells }
            repeat(40) { seed ->
                val head = Position(board.width / 2, board.height / 2)
                val intro = GameState(
                    board = board,
                    level = LevelsMode.SCORE_LEVEL,
                    snake = listOf(head, Position(head.x, head.y + 1), Position(head.x, head.y + 2)),
                    direction = Direction.Up,
                    pendingDirection = Direction.Up,
                    foods = emptyList(),
                    obstacles = emptySet(),
                    score = 0,
                    pendingGrowth = 0,
                    status = GameStatus.LevelIntro,
                    mode = GameMode.Levels,
                    elapsedTicks = 2_000,
                    levelIndex = level,
                    walls = LevelsMode.shapeFor(level, board),
                    gates = hazards.gates,
                    teleports = hazards.teleports,
                )
                val running = GameEngine(Random(seed.toLong())).beginLevel(intro)
                running.foods.forEach { food ->
                    food.cells().forEach { c ->
                        assertFalse("food at $c overlaps a hazard (L$level)", c in reserved)
                    }
                }
            }
        }
    }

    // --- Flood-fill helpers -------------------------------------------------

    private fun floodFill(start: Position, blocked: Set<Position>, board: BoardDimensions): Int =
        reachableSet(start, blocked, board).size

    private fun reachableSet(start: Position, blocked: Set<Position>, board: BoardDimensions): Set<Position> {
        val seen = HashSet<Position>()
        if (start in blocked) return seen
        val queue = ArrayDeque<Position>()
        seen.add(start)
        queue.add(start)
        while (queue.isNotEmpty()) {
            val cell = queue.poll()
            for (direction in Direction.entries) {
                val next = cell.step(direction)
                if (next.x !in 0 until board.width || next.y !in 0 until board.height) continue
                if (next in blocked || !seen.add(next)) continue
                queue.add(next)
            }
        }
        return seen
    }
}

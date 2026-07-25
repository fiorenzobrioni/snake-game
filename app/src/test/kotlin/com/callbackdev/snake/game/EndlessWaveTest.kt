package com.callbackdev.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** The Endless wave events: their schedule, their effects and their fairness. */
class EndlessWaveTest {

    private val engine = GameEngine(Random(5))
    private val board = BoardDimensions(20, 30)

    private fun endlessState(
        playedMs: Long,
        snake: List<Position> = listOf(Position(5, 15), Position(4, 15), Position(3, 15)),
        elapsedTicks: Int = 0,
        foods: List<Food> = emptyList(),
        debris: List<Debris> = emptyList(),
        mode: GameMode = GameMode.Endless,
    ) = GameState(
        board = board,
        level = Level.Beginner,
        snake = snake,
        direction = Direction.Right,
        pendingDirection = Direction.Right,
        foods = foods,
        obstacles = emptySet(),
        score = 0,
        pendingGrowth = 0,
        status = GameStatus.Running,
        mode = mode,
        playedMs = playedMs,
        elapsedTicks = elapsedTicks,
        debris = debris,
        graceAvailable = true,
    )

    // --- The schedule --------------------------------------------------------

    @Test
    fun `the first stretch of a run is undisturbed`() {
        assertNull(EndlessWaves.activeAt(0))
        assertNull(EndlessWaves.activeAt(EndlessWaves.FIRST_START_MS - 1))
        assertNotNull(EndlessWaves.activeAt(EndlessWaves.FIRST_START_MS))
    }

    @Test
    fun `waves rotate in a fixed order with gaps between them`() {
        fun waveAtCycle(cycle: Int) =
            EndlessWaves.activeAt(EndlessWaves.FIRST_START_MS + cycle * EndlessWaves.PERIOD_MS)
        assertEquals(EndlessWave.Feast, waveAtCycle(0))
        assertEquals(EndlessWave.Drought, waveAtCycle(1))
        assertEquals(EndlessWave.Hailstorm, waveAtCycle(2))
        assertEquals(EndlessWave.Feast, waveAtCycle(3)) // the rotation repeats

        // Each wave ends, and the board is left alone until the next one.
        val justAfter = EndlessWaves.FIRST_START_MS + EndlessWaves.DURATION_MS
        assertNull(EndlessWaves.activeAt(justAfter))
        assertNull(EndlessWaves.activeAt(EndlessWaves.FIRST_START_MS + EndlessWaves.PERIOD_MS - 1))
    }

    @Test
    fun `the countdown drains across the wave`() {
        val start = EndlessWaves.FIRST_START_MS
        assertEquals(EndlessWaves.DURATION_MS, EndlessWaves.remainingMsAt(start))
        assertEquals(0f, EndlessWaves.fractionAt(start), 0.001f)
        val halfway = start + EndlessWaves.DURATION_MS / 2
        assertEquals(0.5f, EndlessWaves.fractionAt(halfway), 0.01f)
        assertEquals(0L, EndlessWaves.remainingMsAt(start + EndlessWaves.DURATION_MS))
    }

    @Test
    fun `only Endless has waves`() {
        val deepIntoARun = EndlessWaves.FIRST_START_MS + 1_000
        assertNotNull(endlessState(deepIntoARun).activeWave)
        GameMode.entries.filter { it != GameMode.Endless }.forEach { mode ->
            assertNull("$mode must stay wave-free", endlessState(deepIntoARun, mode = mode).activeWave)
        }
    }

    // --- Both edges are announced -------------------------------------------

    @Test
    fun `a wave announces its start and its end exactly once`() {
        // One tick before the first wave: crossing into it announces the start.
        val entering = endlessState(playedMs = EndlessWaves.FIRST_START_MS - 1)
        val started = engine.tick(entering)
        assertEquals(
            EndlessWave.Feast,
            started.lastEvents.filterIsInstance<GameEvent.WaveStarted>().single().wave,
        )
        // A tick inside the wave says nothing more.
        val inside = engine.tick(endlessState(playedMs = EndlessWaves.FIRST_START_MS + 2_000))
        assertTrue(inside.lastEvents.none { it is GameEvent.WaveStarted })

        // Crossing out announces the end.
        val leaving = endlessState(
            playedMs = EndlessWaves.FIRST_START_MS + EndlessWaves.DURATION_MS - 1,
        )
        val ended = engine.tick(leaving)
        assertEquals(
            EndlessWave.Feast,
            ended.lastEvents.filterIsInstance<GameEvent.WaveEnded>().single().wave,
        )
    }

    // --- What each wave does -------------------------------------------------

    @Test
    fun `a Feast floods the board and a Drought starves it`() {
        val feast = engine.tick(endlessState(playedMs = EndlessWaves.FIRST_START_MS - 1))
        assertEquals(EndlessWaves.FEAST_FOOD_COUNT, feast.foods.size)

        // Into the Drought with a full board: the count is not topped up, and the
        // stale pieces time out until only the famine ration is left.
        val droughtStart = EndlessWaves.FIRST_START_MS + EndlessWaves.PERIOD_MS
        var state = engine.tick(endlessState(playedMs = droughtStart - 1))
        assertEquals(EndlessWave.Drought, state.activeWave)
        assertTrue(state.foods.size <= EndlessWaves.FEAST_FOOD_COUNT)

        // A board that has nothing on it gets exactly the ration.
        state = engine.tick(endlessState(playedMs = droughtStart, elapsedTicks = 500))
        assertEquals(EndlessWaves.DROUGHT_FOOD_COUNT, state.foods.size)
    }

    @Test
    fun `hail lands clear of the head, off the food, and never floods the board`() {
        val hailStart = EndlessWaves.FIRST_START_MS + 2 * EndlessWaves.PERIOD_MS
        var state = endlessState(playedMs = hailStart, elapsedTicks = 0)
        assertEquals(EndlessWave.Hailstorm, state.activeWave)

        // Run the wave out and watch every block that lands.
        val landed = ArrayList<Position>()
        repeat(EndlessWaves.HAIL_INTERVAL_TICKS * (EndlessWaves.HAIL_MAX_BLOCKS + 4)) {
            state = engine.tick(state)
            if (state.status != GameStatus.Running) return@repeat
            state.lastEvents.filterIsInstance<GameEvent.HailLanded>().forEach { event ->
                landed.add(event.cell)
                val head = state.head
                assertTrue(
                    "hail must not land on the snake's nose",
                    abs(event.cell.x - head.x) + abs(event.cell.y - head.y) >= EndlessWaves.HAIL_HEAD_CLEARANCE,
                )
                assertTrue("hail must not land on the snake", event.cell !in state.snake)
                assertTrue("hail must not bury food", state.foods.none { it.occupies(event.cell) })
            }
            assertTrue(
                "the board never holds more hail than the cap",
                state.debris.size <= EndlessWaves.HAIL_MAX_BLOCKS,
            )
        }
        assertTrue("the storm actually rained", landed.isNotEmpty())
    }

    @Test
    fun `no hail falls outside its wave`() {
        var state = endlessState(playedMs = 0, elapsedTicks = 0)
        repeat(60) {
            state = engine.tick(state)
            assertTrue(state.lastEvents.none { it is GameEvent.HailLanded })
        }
    }
}

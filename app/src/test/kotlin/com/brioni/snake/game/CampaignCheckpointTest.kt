package com.brioni.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Campaign checkpoint starts: [GameEngine.setup] can stage any designed level as
 * the run's first board (the ViewModel gates which are offered and keeps such
 * practice runs out of the records).
 */
class CampaignCheckpointTest {

    private val engine = GameEngine(Random(3))
    private val board = BoardDimensions(18, 26)

    @Test
    fun `setup stages the requested starting level`() {
        val state = engine.setup(Level.Beginner, board, GameMode.Levels, startLevelIndex = 7)
        assertEquals(7, state.levelIndex)
        assertEquals(1, state.speedCycle)
        assertEquals(LevelsMode.shapeFor(7, board), state.walls)
        val hazards = LevelsMode.hazardsFor(7, board)
        assertEquals(hazards.gates, state.gates)
        assertEquals(hazards.teleports, state.teleports)
        assertEquals(LevelsMode.START_LIVES, state.lives)
    }

    @Test
    fun `start index is clamped to the designed range`() {
        assertEquals(1, engine.setup(Level.Beginner, board, GameMode.Levels, startLevelIndex = 0).levelIndex)
        assertEquals(
            LevelsMode.LEVEL_COUNT,
            engine.setup(Level.Beginner, board, GameMode.Levels, startLevelIndex = 99).levelIndex,
        )
    }

    @Test
    fun `non-campaign modes ignore the start index`() {
        val state = engine.setup(Level.Beginner, board, GameMode.Endless, startLevelIndex = 9)
        assertEquals(1, state.levelIndex)
        assertTrue(state.walls.isEmpty())
    }

    @Test
    fun `a checkpoint run advances to the following level`() {
        var state = engine.setup(Level.Beginner, board, GameMode.Levels, startLevelIndex = 5)
        state = engine.start(state)
        assertEquals(GameStatus.LevelIntro, state.status)
        state = engine.beginLevel(state)
        assertEquals(GameStatus.Running, state.status)
        // Complete the level's food goal directly and take one tick.
        state = state.copy(levelFoodsEaten = LevelsMode.LEVEL_FOOD_GOAL)
        state = engine.tick(state)
        val advanced = state.lastEvents.filterIsInstance<GameEvent.LevelAdvanced>().single()
        assertEquals(6, advanced.levelIndex)
        assertEquals(1, advanced.speedCycle)
        assertEquals(6, state.levelIndex)
        assertEquals(LevelsMode.shapeFor(6, board), state.walls)
    }
}

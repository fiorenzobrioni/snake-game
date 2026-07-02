package com.brioni.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The terrain set is the model-side contract the UI shaders mirror; keep it pure
 * (no Compose) so the renderer can map every constant to a board-background style.
 */
class BoardTerrainTest {

    @Test
    fun `six terrains are offered, matching the skin roster size`() {
        assertEquals(6, BoardTerrain.entries.size)
    }

    @Test
    fun `display names are non-blank and unique`() {
        val names = BoardTerrain.entries.map { it.displayName }
        assertTrue(names.none { it.isBlank() })
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `meadow is the first, default-facing entry, the skin-following arcade second`() {
        assertEquals(BoardTerrain.Meadow, BoardTerrain.entries[0])
        assertEquals(BoardTerrain.Arcade, BoardTerrain.entries[1])
    }
}

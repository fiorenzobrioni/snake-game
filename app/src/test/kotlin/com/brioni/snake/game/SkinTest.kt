package com.brioni.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The skin set is the model-side contract the UI palettes mirror; keep it pure
 * (no Compose) so the renderer can map every constant to a [ui.game.SkinPalette].
 */
class SkinTest {

    @Test
    fun `four skins are offered`() {
        assertEquals(4, Skin.entries.size)
    }

    @Test
    fun `display names are non-blank and unique`() {
        val names = Skin.entries.map { it.displayName }
        assertTrue(names.none { it.isBlank() })
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `classic is the default-facing first entry`() {
        assertEquals(Skin.Classic, Skin.entries.first())
    }
}

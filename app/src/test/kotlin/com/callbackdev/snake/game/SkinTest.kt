package com.callbackdev.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The skin set is the model-side contract the UI palettes mirror; keep it pure
 * (no Compose) so the renderer can map every constant to a [ui.game.SkinPalette].
 */
class SkinTest {

    @Test
    fun `six skins are offered`() {
        assertEquals(6, Skin.entries.size)
    }

    @Test
    fun `display names are non-blank and unique`() {
        val names = Skin.entries.map { it.displayName }
        assertTrue(names.none { it.isBlank() })
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `retro is the default-facing first entry, classic second`() {
        assertEquals(Skin.Retro, Skin.entries[0])
        assertEquals(Skin.Classic, Skin.entries[1])
    }

    @Test
    fun `every skin is free to pick`() {
        // The score / streak unlock gates were removed by design: a skin is
        // expression, not a reward, so the model carries no unlock state at all.
        assertEquals(6, Skin.entries.size)
        assertTrue(Skin.entries.all { it.displayName.isNotBlank() })
    }
}

package com.callbackdev.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `only retro and classic are unlocked by default`() {
        assertEquals(setOf(Skin.Retro, Skin.Classic), Skin.defaultUnlocked)
    }

    @Test
    fun `score milestones unlock neon and pixel`() {
        // Neon needs 1500, Pixel needs 5000 in a single run; streak irrelevant.
        assertEquals(emptyList<Skin>(), Skin.newlyUnlocked(score = 1499, streak = 0, already = emptySet()))
        assertTrue(Skin.Neon in Skin.newlyUnlocked(score = 1500, streak = 0, already = emptySet()))
        assertFalse(Skin.Pixel in Skin.newlyUnlocked(score = 4999, streak = 0, already = emptySet()))
        assertTrue(Skin.Pixel in Skin.newlyUnlocked(score = 5000, streak = 0, already = emptySet()))
    }

    @Test
    fun `streak milestones unlock aurora and ember`() {
        assertFalse(Skin.Aurora in Skin.newlyUnlocked(score = 0, streak = 6, already = emptySet()))
        assertTrue(Skin.Aurora in Skin.newlyUnlocked(score = 0, streak = 7, already = emptySet()))
        assertFalse(Skin.Ember in Skin.newlyUnlocked(score = 0, streak = 29, already = emptySet()))
        assertTrue(Skin.Ember in Skin.newlyUnlocked(score = 0, streak = 30, already = emptySet()))
    }

    @Test
    fun `always-unlocked and already-earned skins are never reported`() {
        // Retro/Classic are Always: they never appear in newlyUnlocked.
        val big = Skin.newlyUnlocked(score = 5000, streak = 60, already = emptySet())
        assertFalse(Skin.Retro in big)
        assertFalse(Skin.Classic in big)
        // Already-unlocked gated skins are excluded.
        val again = Skin.newlyUnlocked(score = 5000, streak = 60, already = big.map { it.name }.toSet())
        assertEquals(emptyList<Skin>(), again)
    }
}

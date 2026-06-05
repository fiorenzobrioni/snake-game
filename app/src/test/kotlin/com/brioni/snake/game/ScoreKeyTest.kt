package com.brioni.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The highscore key codec is the persistence contract for the Records screen. */
class ScoreKeyTest {

    @Test
    fun `round-trips through its storage name`() {
        for (mode in GameMode.entries) {
            for (level in Level.entries) {
                for (scale in BoardScale.entries) {
                    val key = ScoreKey(mode, level, scale)
                    assertEquals(key, ScoreKey.parse(key.storageName()))
                }
            }
        }
    }

    @Test
    fun `storage name is stable and prefixed`() {
        val key = ScoreKey(GameMode.TimeAttack, Level.Legend, BoardScale.Epic)
        assertEquals("highscore_TimeAttack_Legend_Epic", key.storageName())
    }

    @Test
    fun `rejects unrelated or stale keys`() {
        assertNull(ScoreKey.parse("music_volume"))
        assertNull(ScoreKey.parse("highscore_Beginner_Cozy")) // old 2-part format
        assertNull(ScoreKey.parse("highscore_Bogus_Beginner_Cozy"))
    }
}

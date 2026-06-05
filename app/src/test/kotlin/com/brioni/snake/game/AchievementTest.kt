package com.brioni.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementTest {

    private fun stats(
        mode: GameMode = GameMode.Classic,
        score: Int = 0,
        maxCombo: Int = 0,
        durationMs: Long = 0,
        foodsEaten: Int = 0,
        usedExplosion: Boolean = false,
        usedStar: Boolean = false,
        usedJackpot: Boolean = false,
    ) = RunStats(mode, score, maxCombo, durationMs, foodsEaten, usedExplosion, usedStar, usedJackpot)

    @Test
    fun `first feast unlocks on the first food`() {
        val earned = Achievement.earnedBy(stats(foodsEaten = 1), already = emptySet())
        assertTrue(Achievement.FirstFeast in earned)
    }

    @Test
    fun `thresholds gate score and combo achievements`() {
        assertTrue(Achievement.Centurion.test(stats(score = 100)))
        assertFalse(Achievement.Centurion.test(stats(score = 99)))
        assertTrue(Achievement.ComboMaster.test(stats(maxCombo = 5)))
        assertFalse(Achievement.ComboMaster.test(stats(maxCombo = 4)))
    }

    @Test
    fun `already-unlocked achievements are not re-reported`() {
        val all = stats(score = 1000, foodsEaten = 5)
        val first = Achievement.earnedBy(all, already = emptySet())
        assertTrue(Achievement.HighRoller in first)
        val second = Achievement.earnedBy(all, already = first.map { it.name }.toSet())
        assertEquals(emptyList<Achievement>(), second)
    }

    @Test
    fun `speed runner requires time attack mode`() {
        assertFalse(Achievement.SpeedRunner.test(stats(mode = GameMode.Classic, score = 500)))
        assertTrue(Achievement.SpeedRunner.test(stats(mode = GameMode.TimeAttack, score = 300)))
    }
}

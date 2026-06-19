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
        maxLevelReached: Int = 0,
        maxSpeedCycle: Int = 1,
        maxSnakeLength: Int = 0,
    ) = RunStats(
        mode, score, maxCombo, durationMs, foodsEaten, usedExplosion, usedStar, usedJackpot,
        maxLevelReached = maxLevelReached, maxSpeedCycle = maxSpeedCycle, maxSnakeLength = maxSnakeLength,
    )

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
        val all = stats(score = 2500, foodsEaten = 5)
        val first = Achievement.earnedBy(all, already = emptySet())
        assertTrue(Achievement.HighRoller in first)
        val second = Achievement.earnedBy(all, already = first.map { it.name }.toSet())
        assertEquals(emptyList<Achievement>(), second)
    }

    @Test
    fun `speed runner requires time attack mode`() {
        assertFalse(Achievement.SpeedRunner.test(stats(mode = GameMode.Classic, score = 700)))
        assertTrue(Achievement.SpeedRunner.test(stats(mode = GameMode.TimeAttack, score = 600)))
    }

    @Test
    fun `gourmand needs fifty foods`() {
        assertTrue(Achievement.Gourmand.test(stats(foodsEaten = 50)))
        assertFalse(Achievement.Gourmand.test(stats(foodsEaten = 49)))
    }

    @Test
    fun `levels achievements gate on mode and depth`() {
        assertTrue(Achievement.Climber.test(stats(mode = GameMode.Levels, maxLevelReached = 5)))
        assertFalse(Achievement.Climber.test(stats(mode = GameMode.Levels, maxLevelReached = 4)))
        assertFalse(Achievement.Climber.test(stats(mode = GameMode.Classic, maxLevelReached = 5)))
        assertTrue(Achievement.TowerTopper.test(stats(mode = GameMode.Levels, maxLevelReached = 10)))
        assertFalse(Achievement.TowerTopper.test(stats(mode = GameMode.Levels, maxLevelReached = 9)))
        assertTrue(Achievement.FullCircle.test(stats(mode = GameMode.Levels, maxSpeedCycle = 2)))
        assertFalse(Achievement.FullCircle.test(stats(mode = GameMode.Levels, maxSpeedCycle = 1)))
    }

    @Test
    fun `length achievements gate on max snake length`() {
        assertTrue(Achievement.LongHaul.test(stats(maxSnakeLength = 25)))
        assertFalse(Achievement.LongHaul.test(stats(maxSnakeLength = 24)))
        assertTrue(Achievement.Anaconda.test(stats(maxSnakeLength = 50)))
        assertFalse(Achievement.Anaconda.test(stats(maxSnakeLength = 49)))
        assertTrue(Achievement.Titanoboa.test(stats(maxSnakeLength = 90)))
        assertFalse(Achievement.Titanoboa.test(stats(maxSnakeLength = 89)))
    }
}

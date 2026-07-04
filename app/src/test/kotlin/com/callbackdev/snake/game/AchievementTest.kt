package com.callbackdev.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementTest {

    private fun stats(
        mode: GameMode = GameMode.Endless,
        score: Int = 0,
        maxCombo: Int = 0,
        durationMs: Long = 0,
        foodsEaten: Int = 0,
        usedExplosion: Boolean = false,
        usedStar: Boolean = false,
        usedJackpot: Boolean = false,
        maxLevelReached: Int = 0,
        maxSpeedCycle: Int = 1,
        maxLevelDepth: Int = 0,
        flawlessLap: Boolean = false,
        maxSnakeLength: Int = 0,
        dailyStreak: Int = 0,
    ) = RunStats(
        mode, score, maxCombo, durationMs, foodsEaten, usedExplosion, usedStar, usedJackpot,
        maxLevelReached = maxLevelReached, maxSpeedCycle = maxSpeedCycle, maxLevelDepth = maxLevelDepth,
        flawlessLap = flawlessLap, maxSnakeLength = maxSnakeLength, dailyStreak = dailyStreak,
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
        assertFalse(Achievement.SpeedRunner.test(stats(mode = GameMode.Endless, score = 700)))
        assertTrue(Achievement.SpeedRunner.test(stats(mode = GameMode.TimeAttack, score = 600)))
    }

    @Test
    fun `food-count achievements gate on foods eaten`() {
        assertTrue(Achievement.Gourmand.test(stats(foodsEaten = 50)))
        assertFalse(Achievement.Gourmand.test(stats(foodsEaten = 49)))
        assertTrue(Achievement.Glutton.test(stats(foodsEaten = 200)))
        assertFalse(Achievement.Glutton.test(stats(foodsEaten = 199)))
        assertTrue(Achievement.Insatiable.test(stats(foodsEaten = 500)))
        assertFalse(Achievement.Insatiable.test(stats(foodsEaten = 499)))
    }

    @Test
    fun `levels achievements gate on mode and depth`() {
        assertTrue(Achievement.Climber.test(stats(mode = GameMode.Levels, maxLevelReached = 5)))
        assertFalse(Achievement.Climber.test(stats(mode = GameMode.Levels, maxLevelReached = 4)))
        assertFalse(Achievement.Climber.test(stats(mode = GameMode.Endless, maxLevelReached = 5)))
        assertTrue(Achievement.TowerTopper.test(stats(mode = GameMode.Levels, maxLevelReached = 10)))
        assertFalse(Achievement.TowerTopper.test(stats(mode = GameMode.Levels, maxLevelReached = 9)))
    }

    @Test
    fun `full circle needs a flawless first lap`() {
        // A full lap (reach Speed 2) cleared without losing a life.
        assertTrue(Achievement.FullCircle.test(stats(mode = GameMode.Levels, flawlessLap = true)))
        // Reaching Speed 2 but having lost a life does not count (flawlessLap stays false).
        assertFalse(Achievement.FullCircle.test(stats(mode = GameMode.Levels, maxSpeedCycle = 2, flawlessLap = false)))
        assertFalse(Achievement.FullCircle.test(stats(mode = GameMode.Endless, flawlessLap = true)))
    }

    @Test
    fun `tower achievements gate on the deepest level depth`() {
        // With 15 levels per lap, depth = (speedCycle - 1) * 15 + levelIndex.
        // Level 10 at Speed 2 is depth 25; Level 10 at Speed 3 is depth 40;
        // Level 15 at Speed 3 is depth 45.
        assertTrue(Achievement.TowerMaster.test(stats(mode = GameMode.Levels, maxLevelDepth = 25)))
        assertFalse(Achievement.TowerMaster.test(stats(mode = GameMode.Levels, maxLevelDepth = 24)))
        assertFalse(Achievement.TowerMaster.test(stats(mode = GameMode.Endless, maxLevelDepth = 25)))
        assertTrue(Achievement.TowerSovereign.test(stats(mode = GameMode.Levels, maxLevelDepth = 40)))
        assertFalse(Achievement.TowerSovereign.test(stats(mode = GameMode.Levels, maxLevelDepth = 39)))
        assertTrue(Achievement.TowerAscendant.test(stats(mode = GameMode.Levels, maxLevelDepth = 45)))
        assertFalse(Achievement.TowerAscendant.test(stats(mode = GameMode.Levels, maxLevelDepth = 44)))
        assertFalse(Achievement.TowerAscendant.test(stats(mode = GameMode.Endless, maxLevelDepth = 45)))
        // Reaching only Level 15 of Speed 1 (depth 15) earns none of the tower tiers.
        assertFalse(Achievement.TowerMaster.test(stats(mode = GameMode.Levels, maxLevelDepth = 15)))
        assertFalse(Achievement.TowerSovereign.test(stats(mode = GameMode.Levels, maxLevelDepth = 15)))
        assertFalse(Achievement.TowerAscendant.test(stats(mode = GameMode.Levels, maxLevelDepth = 15)))
    }

    @Test
    fun `top-tier score and length achievements gate on their thresholds`() {
        assertTrue(Achievement.Mythmaker.test(stats(score = 10_000)))
        assertFalse(Achievement.Mythmaker.test(stats(score = 9_999)))
        assertTrue(Achievement.Leviathan.test(stats(maxSnakeLength = 250)))
        assertFalse(Achievement.Leviathan.test(stats(maxSnakeLength = 249)))
    }

    @Test
    fun `daily streak achievements gate on the streak length`() {
        assertFalse(Achievement.WeekWarrior.test(stats(dailyStreak = 6)))
        assertTrue(Achievement.WeekWarrior.test(stats(dailyStreak = 7)))
        assertFalse(Achievement.MonthMaster.test(stats(dailyStreak = 29)))
        assertTrue(Achievement.MonthMaster.test(stats(dailyStreak = 30)))
        // A 30-day streak earns both at once; no streak earns neither.
        val both = Achievement.earnedBy(stats(dailyStreak = 30), already = emptySet())
        assertTrue(Achievement.WeekWarrior in both)
        assertTrue(Achievement.MonthMaster in both)
        val none = Achievement.earnedBy(stats(dailyStreak = 0), already = emptySet())
        assertFalse(Achievement.WeekWarrior in none)
        assertFalse(Achievement.MonthMaster in none)
    }

    @Test
    fun `length achievements gate on max snake length`() {
        assertTrue(Achievement.LongHaul.test(stats(maxSnakeLength = 50)))
        assertFalse(Achievement.LongHaul.test(stats(maxSnakeLength = 49)))
        assertTrue(Achievement.Anaconda.test(stats(maxSnakeLength = 100)))
        assertFalse(Achievement.Anaconda.test(stats(maxSnakeLength = 99)))
        assertTrue(Achievement.Titanoboa.test(stats(maxSnakeLength = 180)))
        assertFalse(Achievement.Titanoboa.test(stats(maxSnakeLength = 179)))
    }
}

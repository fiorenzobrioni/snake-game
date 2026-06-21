package com.brioni.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionTest {

    private fun stats(
        score: Int = 0,
        maxCombo: Int = 0,
        durationMs: Long = 0,
        foodsEaten: Int = 0,
        usedExplosion: Boolean = false,
        usedStar: Boolean = false,
        usedJackpot: Boolean = false,
        maxSnakeLength: Int = 0,
    ) = RunStats(
        mode = GameMode.Endless,
        score = score,
        maxCombo = maxCombo,
        durationMs = durationMs,
        foodsEaten = foodsEaten,
        usedExplosion = usedExplosion,
        usedStar = usedStar,
        usedJackpot = usedJackpot,
        maxSnakeLength = maxSnakeLength,
    )

    @Test
    fun `same day yields the same missions`() {
        assertEquals(Mission.forDay(20_000), Mission.forDay(20_000))
    }

    @Test
    fun `daily set has the expected size and distinct missions`() {
        val missions = Mission.forDay(12_345)
        assertEquals(Mission.DAILY_COUNT, missions.size)
        assertEquals(missions.size, missions.map { it.id }.toSet().size)
    }

    @Test
    fun `the set rotates across days`() {
        val sets = (0L until 60L).map { Mission.forDay(it).map { m -> m.id }.toSet() }.toSet()
        // The pool is small, but the daily picks should still vary meaningfully.
        assertTrue("missions should rotate day to day", sets.size >= 5)
    }

    @Test
    fun `completion gates on the target`() {
        val eat15 = Mission.byId("eat_15")!!
        assertTrue(eat15.completedBy(stats(foodsEaten = 15)))
        assertFalse(eat15.completedBy(stats(foodsEaten = 14)))
    }

    @Test
    fun `survive missions read seconds from the duration`() {
        val survive60 = Mission.byId("survive_60")!!
        assertTrue(survive60.completedBy(stats(durationMs = 60_000)))
        assertFalse(survive60.completedBy(stats(durationMs = 59_999)))
    }

    @Test
    fun `the power-up mission accepts any power-up`() {
        val powerup = Mission.byId("powerup")!!
        assertFalse(powerup.completedBy(stats()))
        assertTrue(powerup.completedBy(stats(usedStar = true)))
        assertTrue(powerup.completedBy(stats(usedJackpot = true)))
        assertTrue(powerup.completedBy(stats(usedExplosion = true)))
    }

    @Test
    fun `progress is clamped to the target`() {
        val eat15 = Mission.byId("eat_15")!!
        assertEquals(10, eat15.progress(stats(foodsEaten = 10)))
        assertEquals(15, eat15.progress(stats(foodsEaten = 99)))
    }
}

package com.brioni.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyChallengeTest {

    @Test
    fun sameDayIsDeterministic() {
        assertEquals(DailyChallenge.forDay(20_000), DailyChallenge.forDay(20_000))
    }

    @Test
    fun seedsAreWellDistributedAcrossDays() {
        val seeds = (0L until 100L).map { DailyChallenge.forDay(it).seed }.toSet()
        assertTrue("seeds should be distinct day to day", seeds.size >= 95)
    }

    @Test
    fun configStaysInRangeAndExcludesCampaign() {
        for (day in 0L until 60L) {
            val c = DailyChallenge.forDay(day)
            assertEquals(day, c.epochDay)
            assertNotEquals("Campaign is excluded from the daily rotation", GameMode.Levels, c.mode)
            assertTrue(c.level in Level.entries)
            assertEquals(BoardScale.Classic, c.scale)
        }
    }

    @Test
    fun modeAlternatesByDay() {
        assertNotEquals(DailyChallenge.forDay(10).mode, DailyChallenge.forDay(11).mode)
    }
}

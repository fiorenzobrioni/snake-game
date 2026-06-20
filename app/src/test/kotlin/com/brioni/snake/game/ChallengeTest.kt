package com.brioni.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChallengeTest {

    @Test
    fun sameDayIsDeterministic() {
        assertEquals(Challenge.forDay(20_000), Challenge.forDay(20_000))
    }

    @Test
    fun sameSeedIsDeterministic() {
        assertEquals(Challenge.random(42L), Challenge.random(42L))
    }

    @Test
    fun seedsAreWellDistributedAcrossDays() {
        val seeds = (0L until 100L).map { Challenge.forDay(it).seed }.toSet()
        assertTrue("seeds should be distinct day to day", seeds.size >= 95)
    }

    @Test
    fun configStaysInRangeAndExcludesCampaign() {
        val challenges = (0L until 60L).map { Challenge.forDay(it) } +
            (0L until 60L).map { Challenge.random(it) }
        for (c in challenges) {
            assertNotEquals("Campaign is excluded from challenges", GameMode.Levels, c.mode)
            assertTrue(c.level in Level.entries)
            assertTrue(c.modifier in ChallengeModifier.entries)
            // The board is the default, unless the modifier overrides it.
            assertEquals(c.modifier.scaleOverride ?: BoardScale.Classic, c.scale)
        }
    }

    @Test
    fun dailyModeAlternatesByDay() {
        assertNotEquals(Challenge.forDay(10).mode, Challenge.forDay(11).mode)
    }

    @Test
    fun randomVariesAcrossSeeds() {
        val rng = Random(1)
        val configs = (0 until 50).map { Challenge.random(rng) }.toSet()
        // The (mode, level, modifier) space is small, but seeds should still spread.
        assertTrue("random challenges should vary", configs.size >= 5)
    }
}

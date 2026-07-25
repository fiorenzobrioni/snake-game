package com.callbackdev.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The achievement career ladder: coverage, reveal gates and the rank it yields. */
class AchievementTierTest {

    @Test
    fun `every achievement belongs to exactly one tier`() {
        val listed = AchievementTier.entries.flatMap { it.achievements }
        assertEquals("no badge is listed twice", listed.size, listed.toSet().size)
        assertEquals(
            "every badge is on the ladder",
            Achievement.entries.toSet(),
            listed.toSet(),
        )
    }

    @Test
    fun `tiers reveal in order, starting open`() {
        val thresholds = AchievementTier.entries.map { it.revealAt }
        assertEquals(0, thresholds.first()) // the first rank is always available
        thresholds.zipWithNext().forEach { (a, b) -> assertTrue("thresholds climb", b > a) }
    }

    @Test
    fun `no tier can be walled off by the badges it gates`() {
        // A rank must be reachable from the badges already revealed below it,
        // otherwise a single stubborn entry (a 30-day streak, a flawless lap)
        // could lock a player out of the rest of the ladder for good.
        AchievementTier.entries.forEachIndexed { index, tier ->
            val availableBelow = AchievementTier.entries.take(index).sumOf { it.achievements.size }
            assertTrue(
                "${tier.displayName} needs ${tier.revealAt} badges but only $availableBelow are revealed below it",
                tier.revealAt <= availableBelow,
            )
        }
    }

    @Test
    fun `the rank is the deepest revealed tier`() {
        assertEquals(AchievementTier.Hatchling, AchievementTier.rankFor(0))
        assertEquals(AchievementTier.Hatchling, AchievementTier.rankFor(AchievementTier.Forager.revealAt - 1))
        assertEquals(AchievementTier.Forager, AchievementTier.rankFor(AchievementTier.Forager.revealAt))
        assertEquals(AchievementTier.Mythic, AchievementTier.rankFor(Achievement.entries.size))
    }

    @Test
    fun `the next rank runs out at the top of the ladder`() {
        assertEquals(AchievementTier.Forager, AchievementTier.nextAfter(0))
        assertNull(AchievementTier.nextAfter(Achievement.entries.size))
    }

    @Test
    fun `a tier knows whether it is revealed`() {
        assertFalse(AchievementTier.Mythic.isRevealedAt(AchievementTier.Mythic.revealAt - 1))
        assertTrue(AchievementTier.Mythic.isRevealedAt(AchievementTier.Mythic.revealAt))
    }

    @Test
    fun `lookup finds the tier of every badge`() {
        Achievement.entries.forEach { achievement ->
            val tier = AchievementTier.of(achievement)
            assertTrue("${achievement.name} maps back to its own tier", achievement in tier.achievements)
        }
    }

    @Test
    fun `the hardest badges sit at the top of the ladder`() {
        // A guard on the intent of the grouping, not on its exact contents.
        assertEquals(AchievementTier.Hatchling, AchievementTier.of(Achievement.FirstFeast))
        assertEquals(AchievementTier.Mythic, AchievementTier.of(Achievement.ApexPredator))
        assertEquals(AchievementTier.Mythic, AchievementTier.of(Achievement.Unbowed))
    }
}

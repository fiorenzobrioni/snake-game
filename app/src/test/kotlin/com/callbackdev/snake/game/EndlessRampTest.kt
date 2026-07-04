package com.callbackdev.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The reworked Endless speed ramp: stepped tiers that keep climbing for many
 * minutes, a per-difficulty head start, and a [GameEvent.SpeedTierUp] emitted
 * for every audible/visible step (silent once the pace has floored).
 */
class EndlessRampTest {

    private val engine = GameEngine(Random(7))
    private val board = BoardDimensions(18, 26)

    private fun endlessState(playedMs: Long = 0, level: Level = Level.Beginner) = GameState(
        board = board, level = level,
        snake = listOf(Position(5, 5), Position(4, 5), Position(3, 5)),
        direction = Direction.Right, pendingDirection = Direction.Right,
        foods = emptyList(), obstacles = emptySet(), score = 0, pendingGrowth = 0,
        status = GameStatus.Running, mode = GameMode.Endless, playedMs = playedMs,
    )

    @Test
    fun `tier steps up with play time`() {
        assertEquals(1, endlessState(playedMs = 0).endlessSpeedTier)
        assertEquals(1, endlessState(playedMs = GameState.ENDLESS_TIER_MS - 1).endlessSpeedTier)
        assertEquals(2, endlessState(playedMs = GameState.ENDLESS_TIER_MS).endlessSpeedTier)
        assertEquals(5, endlessState(playedMs = GameState.ENDLESS_TIER_MS * 4).endlessSpeedTier)
    }

    @Test
    fun `harder difficulties start on a hotter tier and pace`() {
        val beginner = endlessState(level = Level.Beginner)
        val legend = endlessState(level = Level.Legend)
        assertTrue(legend.endlessSpeedTier > beginner.endlessSpeedTier)
        assertTrue(legend.tickIntervalMillis < beginner.tickIntervalMillis)
        assertEquals(1 + Level.Legend.endlessTierHeadStart, legend.endlessSpeedTier)
    }

    @Test
    fun `the ramp stays alive for minutes and floors eventually`() {
        // The pace must still be changing well past the old 90-second flatline.
        val at3min = endlessState(playedMs = 180_000).tickIntervalMillis
        val at5min = endlessState(playedMs = 300_000).tickIntervalMillis
        assertTrue("ramp should still be moving after 3 minutes", at5min < at3min)
        // And it must bottom out exactly at the floor, never below.
        val floored = endlessState(playedMs = 3_600_000).tickIntervalMillis
        assertEquals(GameState.ENDLESS_FLOOR_MS.toLong(), floored)
    }

    @Test
    fun `crossing a tier boundary emits SpeedTierUp once`() {
        // One tick before the boundary: the tick's elapsed interval crosses it.
        val before = endlessState(playedMs = GameState.ENDLESS_TIER_MS - 1)
        val after = engine.tick(before)
        val event = after.lastEvents.filterIsInstance<GameEvent.SpeedTierUp>().single()
        assertEquals(2, event.tier)
        // The very next tick must not re-announce the same tier.
        assertTrue(engine.tick(after).lastEvents.none { it is GameEvent.SpeedTierUp })
    }

    @Test
    fun `no announcement once the pace has floored`() {
        // Deep enough that both tiers around the boundary are already at the floor.
        val playedMs = GameState.ENDLESS_TIER_MS * 80 - 1
        val before = endlessState(playedMs = playedMs)
        assertEquals(GameState.ENDLESS_FLOOR_MS.toLong(), before.tickIntervalMillis)
        val after = engine.tick(before)
        assertFalse(after.lastEvents.any { it is GameEvent.SpeedTierUp })
    }

    @Test
    fun `other modes never emit SpeedTierUp`() {
        val ta = endlessState(playedMs = GameState.ENDLESS_TIER_MS - 1).copy(mode = GameMode.TimeAttack)
        assertTrue(engine.tick(ta).lastEvents.none { it is GameEvent.SpeedTierUp })
    }
}

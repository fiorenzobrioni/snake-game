package com.callbackdev.snake.game

/**
 * The environmental hazards layered on top of a Campaign ([GameMode.Levels])
 * board shape (Step 6.9.7): **moving walls** that open and close on a cycle, and
 * **teleport pads** that whisk the head from one pad to its pair. Both are
 * deterministic functions of the run's monotonic tick count, so the model stays
 * pure and unit-testable (no [kotlin.random.Random] is consulted here).
 *
 * Built by [LevelsMode.hazardsFor] and carried on [GameState.gates] /
 * [GameState.teleports]; applied in [GameEngine.tick] and painted by the
 * renderer as energy barriers and portals.
 */
data class LevelHazards(
    val gates: List<Gate> = emptyList(),
    val teleports: List<TeleportPair> = emptyList(),
) {
    val isEmpty: Boolean get() = gates.isEmpty() && teleports.isEmpty()

    companion object {
        val EMPTY = LevelHazards()
    }
}

/**
 * A phased "moving wall": a group of [cells] that are solid (lethal, like a level
 * wall) while the gate is **closed** and passable while it is **open**, cycling
 * forever on a fixed tick schedule. The schedule is a pure function of the run's
 * [GameState.elapsedTicks], so collisions stay deterministic and the renderer can
 * derive a smooth open/close animation from the same clock.
 *
 * Within each [period]-tick cycle the gate is open for the first [openTicks] and
 * closed for the rest; [offsetTicks] shifts the cycle so several gates on one
 * board can fall into a weaving, out-of-phase rhythm. Gates never fully trap the
 * snake: [LevelsMode.hazardsFor] keeps only sets that leave every playable cell
 * reachable with **all** gates treated as closed.
 *
 * @param cells       the gate's footprint (contiguous, on otherwise-open cells).
 * @param period      full cycle length in ticks (open span + closed span).
 * @param openTicks   ticks the gate stays open at the start of each cycle.
 * @param offsetTicks phase shift, to desync gates that share a board.
 */
data class Gate(
    val cells: Set<Position>,
    val period: Int = DEFAULT_PERIOD,
    val openTicks: Int = DEFAULT_OPEN,
    val offsetTicks: Int = 0,
) {
    init {
        require(period > 0) { "period must be positive" }
        require(openTicks in 0..period) { "openTicks must be within 0..period" }
    }

    /** Position within the cycle (0 until [period]) for the monotonic [tick]. */
    private fun phase(tick: Int): Int = ((tick + offsetTicks) % period + period) % period

    /** True when the gate is passable (not lethal) at [tick]. */
    fun isOpenAt(tick: Int): Boolean = phase(tick) < openTicks

    /** True when the gate is a solid, lethal wall at [tick]. */
    fun isClosedAt(tick: Int): Boolean = !isOpenAt(tick)

    /**
     * True in the final [warnTicks] ticks before an open gate slams shut - the
     * window the renderer strobes a warning so the close is never a surprise.
     */
    fun isClosingSoonAt(tick: Int, warnTicks: Int = WARN_TICKS): Boolean {
        if (isClosedAt(tick)) return false
        return phase(tick) >= openTicks - warnTicks
    }

    /**
     * True in the final [warnTicks] ticks before a closed gate reopens, so the
     * renderer can charge the barrier down and hint that the passage is clearing.
     */
    fun isOpeningSoonAt(tick: Int, warnTicks: Int = WARN_TICKS): Boolean {
        if (isOpenAt(tick)) return false
        return phase(tick) >= period - warnTicks
    }

    companion object {
        /** Default gate cadence (ticks). Open a touch longer than closed for fairness. */
        const val DEFAULT_PERIOD = 18
        const val DEFAULT_OPEN = 11

        /** Ticks of warning strobe before an open gate closes / a closed gate opens. */
        const val WARN_TICKS = 2
    }
}

/**
 * A linked pair of teleport pads. Stepping the head onto [a] makes it emerge at
 * [b] on the same tick, and vice-versa - a portal jump. The two pads are distinct
 * playable cells, kept clear of walls, gates and the spawn zone by
 * [LevelsMode.hazardsFor].
 */
data class TeleportPair(val a: Position, val b: Position) {
    /** The exit pad for an entry on [cell], or null if [cell] is neither pad. */
    fun exitFor(cell: Position): Position? = when (cell) {
        a -> b
        b -> a
        else -> null
    }

    /** Both pad cells, for spawn-exclusion and rendering. */
    val cells: List<Position> get() = listOf(a, b)
}

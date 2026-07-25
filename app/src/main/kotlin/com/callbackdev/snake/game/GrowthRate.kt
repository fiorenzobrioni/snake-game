package com.callbackdev.snake.game

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * How fast the snake grows **on its own**, independently of what it eats.
 *
 * Without it a careful player can circle an empty board forever: nothing forces
 * a decision, so a run has no natural end. With it the body is a clock made
 * physical - every [baseIntervalTicks] steps the snake keeps its tail for one
 * tick and gains a segment - which flips the food's role: growing pieces are the
 * score, **shrinking pieces are the way to buy time**.
 *
 * The five settings run from [Off] (the classic rules: only food changes your
 * length) to [Relentless]. Because a faster growth is strictly harder, each step
 * carries a declared [scoreFactor] applied to every point earned in the run -
 * the same risk/reward contract [SnakeSpeed.timeAttackScoreFactor] uses - so the
 * choice stays outside [ScoreKey] and no existing highscore is orphaned.
 *
 * The interval is measured in **ticks (steps taken)**, not wall-clock: a step is
 * the unit the board is made of, so the pressure per cell travelled stays the
 * same at any pace, and speed effects (Lightning / Snail) or the Endless ramp
 * cannot dilute it.
 */
enum class GrowthRate(
    val displayName: String,
    /**
     * Steps between two free segments on the reference board ([REFERENCE_CELLS]),
     * before the board-size scaling of [intervalTicksFor]. `0` disables growth.
     */
    val baseIntervalTicks: Int,
    /** Declared score multiplier for playing at this growth (see the class doc). */
    val scoreFactor: Float,
) {
    Off("Off", 0, 1.0f),
    Gentle("Gentle", 45, 1.05f),
    Steady("Steady", 30, 1.15f),
    Brisk("Brisk", 20, 1.3f),
    Relentless("Relentless", 13, 1.5f);

    /** True for every setting but [Off]: the snake gains segments by itself. */
    val isOn: Boolean get() = baseIntervalTicks > 0

    /** 1-based number for display, e.g. "3. Steady". */
    val label: String get() = "${ordinal + 1}. $displayName"

    /** The multiplier as a short display tag, e.g. "x1.15" ("x1" for [Off]). */
    val scoreFactorLabel: String
        get() = "x" + scoreFactor.toString().trimEnd('0').trimEnd('.')

    /**
     * Steps between two free segments on [board], or 0 when [Off].
     *
     * A segment costs the same on every board, but a cell is not worth the same:
     * filling a Colossal arena takes several times the length that chokes a Cozy
     * one. The interval is therefore scaled by the board's cell count relative to
     * [REFERENCE_CELLS] with a [BOARD_EXPONENT] softening - it sits between the
     * linear short-side scaling used for the item vanish times and the full area
     * scaling used for the obstacle counts. Pure area would make big boards grow
     * absurdly fast and small ones almost static; this keeps a run's arc
     * comparable across scales while still respecting the room available.
     */
    fun intervalTicksFor(board: BoardDimensions): Int {
        if (!isOn) return 0
        val cells = (board.width * board.height).coerceAtLeast(1)
        val factor = (REFERENCE_CELLS.toDouble() / cells)
            .pow(BOARD_EXPONENT)
            .coerceIn(MIN_BOARD_FACTOR, MAX_BOARD_FACTOR)
        return (baseIntervalTicks * factor).roundToInt().coerceAtLeast(MIN_INTERVAL_TICKS)
    }

    companion object {
        /** What a fresh install plays on: present but forgiving. */
        val DEFAULT = Steady

        /**
         * The growth pinned in the seeded Daily / Random challenges. A challenge
         * is only worth comparing when everyone runs the same rules, so the
         * player's own setting is ignored there (as the pace and the hazard
         * toggles already are).
         */
        val CHALLENGE = Steady

        /**
         * Cells on the board the [baseIntervalTicks] are tuned for: the Explorer
         * scale (19 cells on the short side) on a tall phone - 19 x 32.
         */
        const val REFERENCE_CELLS = 608

        /** Softening applied to the board-size ratio (see [intervalTicksFor]). */
        const val BOARD_EXPONENT = 0.75

        /** Bounds for the board factor, so an extreme grid can't break the pacing. */
        const val MIN_BOARD_FACTOR = 0.30
        const val MAX_BOARD_FACTOR = 2.50

        /** However the scaling lands, never grow more often than this. */
        const val MIN_INTERVAL_TICKS = 4
    }
}

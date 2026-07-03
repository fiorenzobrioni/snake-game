package com.brioni.snake.game

/**
 * How fast the snake moves, split out from [Level] so the obstacle layout and
 * the pace can be chosen independently: a heavily-obstacled Legend board can be
 * played at a relaxed pace, or an open Beginner board at a frantic one. The five
 * settings carry the tick intervals ported from v1.0.0 (eased ~25% slower than
 * the desktop build for touch play); [tickMillis] is the delay between game
 * steps, so a lower value is faster.
 *
 * Only consulted in [GameMode.TimeAttack] and [GameMode.Zen] (where it stays
 * fixed for the whole run): Endless ramps its own pace over time and Levels
 * paces by its speed cycle.
 */
enum class SnakeSpeed(
    val displayName: String,
    val tickMillis: Long,
    /**
     * Time Attack only: the declared score multiplier for playing at this pace.
     * A faster snake covers more board in the fixed 120 s, so without this a
     * Turbo run would structurally out-score a Relaxed one on the same record
     * slot; the multiplier turns the pace choice into an open risk/reward dial
     * instead. Applied by [GameEngine] to every point earned in the mode.
     */
    val timeAttackScoreFactor: Float,
) {
    Relaxed("Relaxed", 175, 1.0f),
    Steady("Steady", 150, 1.1f),
    Brisk("Brisk", 125, 1.2f),
    Rapid("Rapid", 100, 1.35f),
    Turbo("Turbo", 75, 1.5f);

    /** 1-based number for display, e.g. "1. Relaxed". */
    val label: String get() = "${ordinal + 1}. $displayName"

    /** The multiplier as a short display tag, e.g. "x1.5" ("x1" for the base pace). */
    val timeAttackFactorLabel: String
        get() = "x" + timeAttackScoreFactor.toString().trimEnd('0').trimEnd('.')

    companion object {
        /** The pace a fresh install starts on (matches the old Beginner speed). */
        val DEFAULT = Relaxed
    }
}

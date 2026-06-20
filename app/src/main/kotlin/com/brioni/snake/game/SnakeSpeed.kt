package com.brioni.snake.game

/**
 * How fast the snake moves, split out from [Level] so the obstacle layout and
 * the pace can be chosen independently: a heavily-obstacled Legend board can be
 * played at a relaxed pace, or an open Beginner board at a frantic one. The five
 * settings carry the tick intervals ported from v1.0.0 (eased ~25% slower than
 * the desktop build for touch play); [tickMillis] is the delay between game
 * steps, so a lower value is faster.
 *
 * Only consulted in the [GameMode.TimeAttack] mode: Endless ramps its own pace
 * over time and Levels paces by its speed cycle.
 */
enum class SnakeSpeed(val displayName: String, val tickMillis: Long) {
    Relaxed("Relaxed", 175),
    Steady("Steady", 150),
    Brisk("Brisk", 125),
    Rapid("Rapid", 100),
    Turbo("Turbo", 75);

    /** 1-based number for display, e.g. "1. Relaxed". */
    val label: String get() = "${ordinal + 1}. $displayName"

    companion object {
        /** The pace a fresh install starts on (matches the old Beginner speed). */
        val DEFAULT = Relaxed
    }
}

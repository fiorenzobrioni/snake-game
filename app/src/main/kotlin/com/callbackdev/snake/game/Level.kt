package com.callbackdev.snake.game

/**
 * The five board layouts ported from v1.0.0. A level selects how many obstacles
 * are placed when a game starts ([obstacleCount]) and, in Endless, how hot the
 * speed ramp starts ([endlessTierHeadStart]); the snake's base pace for Time
 * Attack is chosen separately via [SnakeSpeed].
 */
enum class Level(
    val displayName: String,
    val obstacleCount: Int,
    /**
     * Endless mode: how many speed tiers the ramp skips at the start, so a
     * harder difficulty is faster from the first tick (not just denser). See
     * [GameState.endlessTierFor].
     */
    val endlessTierHeadStart: Int,
) {
    Beginner("Beginner", 0, 0),
    Adventurer("Adventurer", 8, 1),
    Warrior("Warrior", 15, 2),
    Champion("Champion", 25, 3),
    Legend("Legend", 40, 4);

    /** 1-based level number for display, e.g. "1. Beginner". */
    val label: String get() = "${ordinal + 1}. $displayName"
}

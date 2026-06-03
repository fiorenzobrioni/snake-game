package com.brioni.snake.game

/**
 * The five difficulty levels ported from v1.0.0. [tickMillis] is the delay
 * between game steps (lower is faster); [obstacleCount] is how many obstacle
 * placements are attempted when a game starts.
 */
enum class Level(
    val displayName: String,
    val tickMillis: Long,
    val obstacleCount: Int,
) {
    Beginner("Beginner", 140, 0),
    Adventurer("Adventurer", 120, 8),
    Warrior("Warrior", 100, 15),
    Champion("Champion", 80, 25),
    Legend("Legend", 60, 40);

    /** 1-based level number for display, e.g. "1. Beginner". */
    val label: String get() = "${ordinal + 1}. $displayName"
}

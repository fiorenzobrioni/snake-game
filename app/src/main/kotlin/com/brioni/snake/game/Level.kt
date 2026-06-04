package com.brioni.snake.game

/**
 * The five difficulty levels ported from v1.0.0, with the tick interval eased
 * ~25% slower than the desktop build for more comfortable touch play.
 * [tickMillis] is the delay between game steps (lower is faster);
 * [obstacleCount] is the target number of obstacles placed when a game starts.
 */
enum class Level(
    val displayName: String,
    val tickMillis: Long,
    val obstacleCount: Int,
) {
    Beginner("Beginner", 175, 0),
    Adventurer("Adventurer", 150, 8),
    Warrior("Warrior", 125, 15),
    Champion("Champion", 100, 25),
    Legend("Legend", 75, 40);

    /** 1-based level number for display, e.g. "1. Beginner". */
    val label: String get() = "${ordinal + 1}. $displayName"
}

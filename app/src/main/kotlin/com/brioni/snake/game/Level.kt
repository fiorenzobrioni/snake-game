package com.brioni.snake.game

/**
 * The five board layouts ported from v1.0.0. A level now selects only how many
 * obstacles are placed when a game starts ([obstacleCount]); the snake's pace is
 * chosen separately via [SnakeSpeed], so the two can be mixed freely (e.g. the
 * dense Legend field at a gentle pace).
 */
enum class Level(
    val displayName: String,
    val obstacleCount: Int,
    /** Fixed 3-letter, upper-case tag for the compact in-game HUD. */
    val abbreviation: String,
) {
    Beginner("Beginner", 0, "BEG"),
    Adventurer("Adventurer", 8, "ADV"),
    Warrior("Warrior", 15, "WAR"),
    Champion("Champion", 25, "CHA"),
    Legend("Legend", 40, "LEG");

    /** 1-based level number for display, e.g. "1. Beginner". */
    val label: String get() = "${ordinal + 1}. $displayName"
}

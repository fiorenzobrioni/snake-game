package com.brioni.snake.game

/**
 * A play mode. Orthogonal to difficulty [Level] and [BoardScale], so highscores
 * are tracked per (mode × level × scale). The gameplay rules for Endless and
 * Time Attack live in [GameEngine] (Phase 6.5); [Classic] is the original game.
 */
enum class GameMode(val displayName: String) {
    /** The standard game at the selected level. */
    Classic("Classic"),

    /** Speed ramps up the longer you survive; runs until you die. */
    Endless("Endless"),

    /** Score as much as possible before a fixed time runs out. */
    TimeAttack("Time Attack"),
}

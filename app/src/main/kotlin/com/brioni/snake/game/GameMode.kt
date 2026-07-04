package com.brioni.snake.game

/**
 * A play mode. Orthogonal to difficulty [Level] and [BoardScale], so highscores
 * are tracked per (mode × level × scale). The gameplay rules for Endless and
 * Time Attack live in [GameEngine]; [Endless] is the default open-ended game.
 * [Levels] ignores the difficulty [Level] entirely — it has its own speed curve
 * and shaped boards (see [LevelsMode]) and its scores are keyed on a pinned level.
 *
 * Constant names double as DataStore keys (the saved mode preference and
 * [ScoreKey.storageName]), so they must stay stable even when a mode's
 * user-facing [displayName] changes — hence [Levels] is shown as "Campaign".
 */
enum class GameMode(val displayName: String) {
    /** Speed ramps up the longer you survive; runs until you die. */
    Endless("Endless"),

    /** Score as much as possible before a fixed time runs out. */
    TimeAttack("Time Attack"),

    /** Fifteen shaped boards cycling forever, faster each cycle, with lives. */
    Levels("Campaign"),

    /**
     * The calm mode: a borderless (wrap-around) arena with no obstacles and no
     * specials, at a fixed pace of the player's choice. Only the snake's own
     * body can end the run (see [ZenMode]).
     */
    Zen("Zen"),
}

package com.brioni.snake.game

/**
 * A play mode. Orthogonal to difficulty [Level] and [BoardScale], so highscores
 * are tracked per (mode × level × scale). The gameplay rules for Endless and
 * Time Attack live in [GameEngine] (Phase 6.5); [Classic] is the original game.
 * [Levels] ignores the difficulty [Level] entirely — it has its own speed curve
 * and shaped boards (see [LevelsMode]) and its scores are keyed on a pinned level.
 *
 * Constant names double as DataStore keys (the saved mode preference and
 * [ScoreKey.storageName]), so they must stay stable even when a mode's
 * user-facing [displayName] changes — hence [Levels] is shown as "Campaign".
 *
 * [abbreviation] is a fixed 3-letter, upper-case tag used by the compact in-game
 * HUD so the mode/level/scale row never overflows or ellipsizes.
 */
enum class GameMode(val displayName: String, val abbreviation: String) {
    /** The standard game at the selected level. */
    Classic("Classic", "CLS"),

    /** Speed ramps up the longer you survive; runs until you die. */
    Endless("Endless", "END"),

    /** Score as much as possible before a fixed time runs out. */
    TimeAttack("Time Attack", "TME"),

    /** Ten shaped boards cycling forever, faster each cycle, with lives. */
    Levels("Campaign", "CMP"),
}

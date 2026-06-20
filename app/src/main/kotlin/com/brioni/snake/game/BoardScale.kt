package com.brioni.snake.game

/**
 * User-facing board granularity. Rather than a fixed width×height, each preset
 * carries a target cell count for the **short side** of the play area: larger
 * cells / fewer cells (Cozy) through to smaller cells / more cells (Colossal).
 * The long side is derived from the device's play-area aspect ratio by
 * [boardFor], so the board fills the screen with square cells in any orientation.
 *
 * The counts are deliberately **odd** so the portrait board has a true middle
 * column: the snake spawns at `width / 2`, which on an odd grid is the exact
 * centre — visibly aligned with centred overlays like the Levels countdown.
 */
enum class BoardScale(
    val displayName: String,
    val cellsOnShortSide: Int,
    /** Fixed 3-letter, upper-case tag for the compact in-game HUD. */
    val abbreviation: String,
) {
    Cozy("Cozy", 13, "COZ"),
    Classic("Standard", 19, "STD"),
    Epic("Epic", 27, "EPC"),
    Colossal("Colossal", 35, "COL");

    /** Human-readable label for menus. */
    val label: String get() = displayName
}

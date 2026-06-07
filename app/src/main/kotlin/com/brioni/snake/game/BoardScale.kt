package com.brioni.snake.game

/**
 * User-facing board granularity. Rather than a fixed width×height, each preset
 * carries a target cell count for the **short side** of the play area: larger
 * cells / fewer cells (Cozy) through to smaller cells / more cells (Epic). The
 * long side is derived from the device's play-area aspect ratio by [boardFor],
 * so the board fills the screen with square cells in any orientation.
 */
enum class BoardScale(val displayName: String, val cellsOnShortSide: Int) {
    Cozy("Cozy", 12),
    Classic("Standard", 18),
    Epic("Epic", 26);

    /** Human-readable label for menus. */
    val label: String get() = displayName
}

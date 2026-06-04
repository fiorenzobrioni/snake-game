package com.brioni.snake.game

/**
 * User-facing board granularity. Rather than a fixed width×height, each preset
 * carries a target column count (cell granularity): larger cells / fewer
 * columns (Cozy) through to smaller cells / more columns (Epic). The matching
 * row count is derived from the device's play-area aspect ratio by [boardFor],
 * so the board always fills the screen with square cells.
 */
enum class BoardScale(val displayName: String, val targetColumns: Int) {
    Cozy("Cozy", 12),
    Classic("Classic", 18),
    Epic("Epic", 26);

    /** Human-readable label for menus. */
    val label: String get() = displayName
}

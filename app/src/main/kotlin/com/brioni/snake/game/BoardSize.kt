package com.brioni.snake.game

/**
 * The five board-size presets ported from the frozen v1.0.0 desktop build.
 * Dimensions are in grid cells; every preset keeps the original 3:2 ratio.
 */
enum class BoardSize(
    val displayName: String,
    val width: Int,
    val height: Int,
) {
    Pocket("Pocket", 30, 20),
    Classic("Classic", 45, 30),
    Grand("Grand", 60, 40),
    Colossal("Colossal", 75, 50),
    Infinite("Infinite", 120, 80);

    /** Total number of cells on the board. */
    val cellCount: Int get() = width * height

    /** Human-readable label, e.g. "Classic (45×30)". */
    val label: String get() = "$displayName ($width×$height)"
}

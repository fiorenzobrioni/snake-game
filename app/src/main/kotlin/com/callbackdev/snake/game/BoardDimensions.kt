package com.callbackdev.snake.game

/**
 * The concrete integer grid the engine plays on. Unlike the old fixed presets,
 * dimensions are derived at runtime from the chosen [BoardScale] and the
 * device's play-area aspect ratio (see [boardFor]) so the board fills the
 * screen with square cells. Kept a plain value type with no Android imports so
 * the model stays unit-testable.
 */
data class BoardDimensions(val width: Int, val height: Int) {

    /** Total number of cells on the board. */
    val cellCount: Int get() = width * height
}

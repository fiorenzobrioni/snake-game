package com.brioni.snake.game

/**
 * The four cardinal directions the snake can travel, each carrying the unit
 * delta it applies to a [Position] per step.
 *
 * Pure Kotlin — no Android/Compose imports — so the model stays unit-testable.
 */
enum class Direction(val dx: Int, val dy: Int) {
    Up(0, -1),
    Down(0, 1),
    Left(-1, 0),
    Right(1, 0);

    /** The 180° reversal of this direction. */
    val opposite: Direction
        get() = when (this) {
            Up -> Down
            Down -> Up
            Left -> Right
            Right -> Left
        }

    /** True when [other] is the direct reversal of this direction. */
    fun isOpposite(other: Direction): Boolean = other == opposite

    /**
     * A 90° counter-clockwise turn on screen (y grows downward):
     * Up → Left → Down → Right → Up. Used by the relative two-button controls;
     * being a quarter-turn it can never produce a reversal.
     */
    val turnedLeft: Direction
        get() = when (this) {
            Up -> Left
            Left -> Down
            Down -> Right
            Right -> Up
        }

    /**
     * A 90° clockwise turn on screen: Up → Right → Down → Left → Up. The mirror
     * of [turnedLeft]; likewise never a reversal.
     */
    val turnedRight: Direction
        get() = when (this) {
            Up -> Right
            Right -> Down
            Down -> Left
            Left -> Up
        }
}

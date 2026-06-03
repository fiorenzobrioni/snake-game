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
}

package com.callbackdev.snake.game

/**
 * An immutable cell coordinate on the board grid. The origin (0, 0) is the
 * top-left cell; x grows right, y grows down — matching the renderer.
 */
data class Position(val x: Int, val y: Int) {
    /** The neighbouring cell one step along [direction]. */
    fun step(direction: Direction): Position =
        Position(x + direction.dx, y + direction.dy)
}

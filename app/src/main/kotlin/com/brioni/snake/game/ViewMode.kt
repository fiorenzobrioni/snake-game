package com.brioni.snake.game

/**
 * How the board is presented. Orthogonal to the [GameMode]: any mode can be
 * played flat (2D) or in one of the two perspective views.
 *
 *  - [TwoD]        the classic flat top-down board.
 *  - [ThreeD]      a chase-cam behind and above the head that rotates to follow
 *                  the snake's heading.
 *  - [ThreeDFixed] a north-locked, panoramic perspective: the camera frames the
 *                  whole board from a fixed angle and never rotates, so the view
 *                  stays readable whatever direction the snake travels.
 */
enum class ViewMode(val displayName: String) {
    TwoD("2D"),
    ThreeD("3D"),
    ThreeDFixed("3D Fixed"),
    ;

    /** True for both perspective views (the model only needs the on/off bit). */
    val is3D: Boolean get() = this != TwoD

    /** True when the camera is north-locked (does not rotate with the snake). */
    val fixedNorth: Boolean get() = this == ThreeDFixed
}

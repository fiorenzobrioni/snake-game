package com.brioni.snake.game

/**
 * How the player steers the snake. [TwoButton] is the default: two half-width
 * buttons turn the snake left/right relative to its heading. [Swipe] and [DPad]
 * keep the classic schemes available. Kept in the model package (no Android
 * imports) so it can be persisted by name via DataStore.
 */
enum class ControlScheme(val displayName: String) {
    TwoButton("Two buttons"),
    Swipe("Swipe"),
    DPad("D-pad"),
}

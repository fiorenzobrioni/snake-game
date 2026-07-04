package com.callbackdev.snake.game

/**
 * How the player steers the snake. [Swipe] is the default (swipe anywhere on the
 * board). [DPad] is the classic four-way cross; [TapTurn] turns relative to the
 * heading by tapping the left or right half of the screen, for comfortable
 * one-handed play. Kept in the model package (no Android imports) so it can be
 * persisted by name via DataStore.
 */
enum class ControlScheme(val displayName: String) {
    Swipe("Swipe"),
    DPad("D-pad"),
    TapTurn("Tap to turn"),
}

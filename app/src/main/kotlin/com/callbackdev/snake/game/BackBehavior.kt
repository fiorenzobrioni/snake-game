package com.callbackdev.snake.game

/**
 * What the system **Back** gesture/button does while a game is actively running.
 * Outside of active play (paused, game over, the pre-game menu) Back always
 * returns to the menu regardless of this setting.
 *
 * Kept in the model package (no Android imports) so it can be persisted by name
 * via DataStore.
 */
enum class BackBehavior(val displayName: String) {
    /** Back pauses the running game (the original behaviour). */
    Pause("Pause"),

    /**
     * Back is ignored and play continues. With **Swipe** controls, a back gesture
     * is a horizontal edge swipe, so it is still fed to the snake as a left/right
     * direction change instead of being lost. This is the default a fresh install
     * starts on - an accidental Back never ends a run.
     */
    KeepPlaying("Keep playing");

    companion object {
        /** The default a fresh install starts on. */
        val DEFAULT = KeepPlaying
    }
}

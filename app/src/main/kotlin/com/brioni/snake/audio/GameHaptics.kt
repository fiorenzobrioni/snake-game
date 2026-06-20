package com.brioni.snake.audio

/**
 * Haptic-feedback sink the gameplay loop emits to, mirroring [GameSfx]: an
 * abstraction so [com.brioni.snake.ui.game.GameViewModel] stays free of Android
 * types and is trivially constructible in tests via [None]. [HapticController]
 * is the production implementation, backed by the device vibrator.
 *
 * Intensities run light → heavy: [nearMiss] (a faint tick) < [eat] < [special] /
 * [lifeLost] / [levelUp] < [death] (the strongest).
 */
interface GameHaptics {
    /** A food was eaten (grow / shrink / mystery): a light tap. */
    fun eat()

    /** A special power-up / hazard / time block was eaten: a firmer click. */
    fun special()

    /** The head grazed a static hazard without crashing: a faint tick. */
    fun nearMiss()

    /** The snake died: the strongest cue. */
    fun death()

    /** Levels mode: a crash consumed a life (the run continues). */
    fun lifeLost()

    /** Levels mode: the food goal was met and the next level is staging. */
    fun levelUp()

    /** No-op sink — the ViewModel's default, so it runs and tests without a vibrator. */
    object None : GameHaptics {
        override fun eat() {}
        override fun special() {}
        override fun nearMiss() {}
        override fun death() {}
        override fun lifeLost() {}
        override fun levelUp() {}
    }
}

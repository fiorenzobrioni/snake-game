package com.callbackdev.snake.audio

import com.callbackdev.snake.game.Food

/**
 * Sound-effect sink the gameplay loop emits to. Declared as an abstraction so
 * [com.callbackdev.snake.ui.game.GameViewModel] can stay free of Android audio types
 * (and trivially constructible in unit tests via [None]). [GameAudio] is the
 * production implementation.
 */
interface GameSfx {
    /** A food was eaten (grow / mystery), with the current combo multiplier. */
    fun ate(food: Food, combo: Int)

    /** A shrinking food was eaten. */
    fun shrunk(food: Food)

    /** The snake died. */
    fun died()

    /** A special power-up / hazard was eaten; the clip is chosen from its effect. */
    fun special(food: Food)

    /** Levels mode: an extra life was banked. */
    fun lifeGained()

    /** Levels mode: a crash consumed a life (the run continues). */
    fun lifeLost()

    /** Levels mode: the food goal was met and the next level is staging. */
    fun levelUp()

    /** Levels mode: the head jumped through a teleport pad. */
    fun teleport()

    /** Time Attack: the clock entered the Fever Time finale. */
    fun feverStarted()

    /** Endless: the speed ramp stepped up a tier. */
    fun speedTierUp()

    /** The live score just passed the stored best mid-run. */
    fun recordBroken()

    /** No-op sink — the ViewModel's default, so it runs and tests without audio. */
    object None : GameSfx {
        override fun ate(food: Food, combo: Int) {}
        override fun shrunk(food: Food) {}
        override fun died() {}
        override fun special(food: Food) {}
        override fun lifeGained() {}
        override fun lifeLost() {}
        override fun levelUp() {}
        override fun teleport() {}
        override fun feverStarted() {}
        override fun speedTierUp() {}
        override fun recordBroken() {}
    }
}

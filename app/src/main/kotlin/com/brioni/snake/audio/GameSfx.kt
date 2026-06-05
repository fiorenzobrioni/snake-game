package com.brioni.snake.audio

import com.brioni.snake.game.Food

/**
 * Sound-effect sink the gameplay loop emits to. Declared as an abstraction so
 * [com.brioni.snake.ui.game.GameViewModel] can stay free of Android audio types
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

    /** No-op sink — the ViewModel's default, so it runs and tests without audio. */
    object None : GameSfx {
        override fun ate(food: Food, combo: Int) {}
        override fun shrunk(food: Food) {}
        override fun died() {}
        override fun special(food: Food) {}
    }
}

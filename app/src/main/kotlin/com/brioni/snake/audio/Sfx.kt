package com.brioni.snake.audio

import com.brioni.snake.R

/** The discrete sound effects, each backed by an `res/raw` clip. */
enum class Sfx(val resId: Int) {
    Eat(R.raw.sfx_eat),
    Shrink(R.raw.sfx_shrink),
    Mystery(R.raw.sfx_mystery),
    GameOver(R.raw.sfx_game_over),
    Click(R.raw.sfx_click),
    Pause(R.raw.sfx_pause),
}

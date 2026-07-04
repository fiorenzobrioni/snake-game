package com.callbackdev.snake.audio

import com.callbackdev.snake.R

/** The discrete sound effects, each backed by an `res/raw` clip. */
enum class Sfx(val resId: Int) {
    Eat(R.raw.sfx_eat),
    Shrink(R.raw.sfx_shrink),
    Mystery(R.raw.sfx_mystery),
    GameOver(R.raw.sfx_game_over),
    Pause(R.raw.sfx_pause),

    // Phase 6.2 specials.
    Lightning(R.raw.sfx_lightning),
    Snail(R.raw.sfx_snail),
    Star(R.raw.sfx_star),
    Freeze(R.raw.sfx_freeze),
    Jackpot(R.raw.sfx_jackpot),
    Quake(R.raw.sfx_quake),
    Explosion(R.raw.sfx_explosion),
}

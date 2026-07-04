package com.callbackdev.snake.game

/**
 * Rules for [GameMode.Zen], the calm mode:
 *
 *  - The arena is a **torus**: the board has no lethal edges — the snake slides
 *    off one side and re-enters from the opposite one (the same wrap the Ghost
 *    power-up grants, here permanent).
 *  - **No obstacles, no specials.** Only the regular grow/shrink/mystery food
 *    progression spawns; power-ups and hazards never appear, so nothing ever
 *    startles. The only way a run ends is the snake biting its own body.
 *  - **Fixed pace, chosen by the player.** The [SnakeSpeed] selector applies
 *    directly and the pace never ramps: Zen runs at your rhythm.
 *  - **Flow scoring.** The combo window is stretched ([COMBO_WINDOW_FACTOR])
 *    so an unhurried, continuous eating rhythm can hold a streak — the mode
 *    rewards flow, not frenzy.
 *
 * Kept pure (no Android imports) alongside the other mode rule objects.
 */
object ZenMode {

    /**
     * The difficulty [Level] this mode's scores are keyed on. The selector is
     * hidden and ignored — Zen has no obstacles and no ramp, so difficulty is
     * meaningless — but [ScoreKey] is a (mode × level × scale) triple, so one
     * level is pinned. Beginner also carries zero obstacles, matching the rules.
     */
    val SCORE_LEVEL = Level.Beginner

    /**
     * How much wider the grow-combo window is in Zen than elsewhere: streaks are
     * about maintaining flow at a calm pace, not racing a tight timer.
     */
    const val COMBO_WINDOW_FACTOR = 2f
}

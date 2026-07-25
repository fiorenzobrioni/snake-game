package com.callbackdev.snake.game

/** What left a lethal block on the board; the renderer draws each kind its own way. */
enum class DebrisKind {
    /** The Explosion power-up's severed tail: drawn as snake body in the skin's material. */
    Tail,

    /**
     * An Endless **Hailstorm** stone. Hail lands as a [EndlessWaves.HAIL_SPAN]-square
     * cluster of these, so it reads as one chunky block of ice rather than a lone
     * pellet - a hazard the player has to see from across the board.
     */
    Hail,
}

/**
 * A lethal, time-limited block on the board — from the Explosion power-up's
 * detached tail, or from an Endless Hailstorm. Crashing the head into a debris
 * cell kills (unless a Ghost effect is active); the engine ages [remainingMs]
 * down each tick and removes the debris when it reaches zero.
 *
 * @param cell        the board cell this debris occupies.
 * @param remainingMs time left before it auto-clears.
 * @param totalMs     its lifetime when spawned, for the renderer's fade.
 * @param kind        what produced it, which selects how it is drawn.
 */
data class Debris(
    val cell: Position,
    val remainingMs: Long,
    val totalMs: Long,
    val kind: DebrisKind = DebrisKind.Tail,
) {
    /** 1.0 when fresh → 0.0 as it is about to clear; drives the fade-out. */
    val life: Float get() = if (totalMs <= 0) 0f else (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f)
}

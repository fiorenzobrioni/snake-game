package com.callbackdev.snake.game

/**
 * A lethal, time-limited block left on the board — currently produced by the
 * Explosion power-up from the snake's detached tail. Crashing the head into a
 * debris cell kills (unless a Ghost effect is active); the engine ages
 * [remainingMs] down each tick and removes the debris when it reaches zero.
 *
 * @param cell        the board cell this debris occupies.
 * @param remainingMs time left before it auto-clears.
 * @param totalMs     its lifetime when spawned, for the renderer's fade.
 */
data class Debris(
    val cell: Position,
    val remainingMs: Long,
    val totalMs: Long,
) {
    /** 1.0 when fresh → 0.0 as it is about to clear; drives the fade-out. */
    val life: Float get() = if (totalMs <= 0) 0f else (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f)
}

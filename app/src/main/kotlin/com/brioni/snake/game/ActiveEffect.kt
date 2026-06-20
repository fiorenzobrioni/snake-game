package com.brioni.snake.game

/**
 * The timed power-up effects that can be running on the snake. Instantaneous
 * specials (Explosion, Jackpot) are *not* here — they apply once and are done;
 * only effects with a duration become an [ActiveEffect].
 */
enum class EffectKind {
    /** Lightning: faster ticks. */
    Haste,

    /** Snail: slower ticks. */
    Slow,

    /** Star: invincible — the head passes through walls, obstacles, debris and self. */
    Ghost,

    /** Freeze: slows time and suspends new special spawns — a strategic breather. */
    Freeze,

    /** Earthquake (hazard): a sustained screen shake; no rule or speed change. */
    Quake,
}

/**
 * A running timed effect. [remainingMs] is aged down each tick by the elapsed
 * (wall-clock) interval, so a power-up lasts the same real time regardless of the
 * speed effects in play. [totalMs] is kept for the HUD countdown bar.
 */
data class ActiveEffect(
    val kind: EffectKind,
    val remainingMs: Long,
    val totalMs: Long,
) {
    /** 1.0 when just started → 0.0 at expiry; drives the HUD timer bar. */
    val fraction: Float get() = if (totalMs <= 0) 0f else (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f)
}

package com.brioni.snake.game

/** Lifecycle of a single game session. */
enum class GameStatus {
    /** Configured but not yet started — the menu is shown. */
    Ready,

    /** The loop is ticking and input is accepted. */
    Running,

    /** Frozen by the player; resumable. */
    Paused,

    /** The snake died; the loop is stopped. */
    GameOver,
}

/**
 * The complete, immutable state of a game at one instant. The [GameEngine]
 * produces new instances from old ones; nothing here mutates in place.
 *
 * @param snake            body cells, head first.
 * @param direction        the committed travel direction (last applied tick).
 * @param pendingDirection the next direction to commit, buffered from input
 *                         and already validated against 180° reversals.
 * @param pendingGrowth    segments still owed from eaten food, paid one per tick.
 * @param elapsedTicks     monotonic count of ticks since the game started;
 *                         drives the time-gated food progression.
 * @param combo            length of the current consecutive-eat streak (the
 *                         score multiplier), reset when a streak lapses.
 * @param comboDeadlineTick the streak survives only if the next food is eaten
 *                         on or before this tick.
 * @param debris           lethal, time-limited blocks (from Explosion); crashing
 *                         into one kills unless a Ghost effect is active.
 * @param effectTimers     the timed power-ups currently running (Haste/Slow/
 *                         Ghost/Freeze), each aged down per tick.
 * @param lastEvents       what happened on the most recent tick, for the UI to
 *                         react to (particle bursts, future effects). Not part
 *                         of the logical state — cleared/replaced every tick.
 */
data class GameState(
    val board: BoardDimensions,
    val level: Level,
    val snake: List<Position>,
    val direction: Direction,
    val pendingDirection: Direction,
    val foods: List<Food>,
    val obstacles: Set<Position>,
    val score: Int,
    val pendingGrowth: Int,
    val status: GameStatus,
    val mode: GameMode = GameMode.Classic,
    val elapsedTicks: Int = 0,
    val playedMs: Long = 0,
    val combo: Int = 0,
    val comboDeadlineTick: Int = 0,
    val debris: List<Debris> = emptyList(),
    val effectTimers: List<ActiveEffect> = emptyList(),
    val lastEvents: List<GameEvent> = emptyList(),
) {
    val head: Position get() = snake.first()

    val isPlayable: Boolean get() = status == GameStatus.Running

    /** True while a timed [kind] effect is running. */
    fun hasEffect(kind: EffectKind): Boolean = effectTimers.any { it.kind == kind }

    /**
     * The wall-clock delay until the next tick, after applying speed effects.
     * The game loop reads this instead of [Level.tickMillis] so Lightning/Snail/
     * Freeze actually change the pace; effect timers are aged by the same value,
     * keeping every power-up's real duration stable.
     */
    val tickIntervalMillis: Long
        get() {
            // Endless overrides the level pace with a curve that quickens over time.
            var ms = if (mode == GameMode.Endless) endlessBaseMs(elapsedTicks) else level.tickMillis.toDouble()
            if (hasEffect(EffectKind.Haste)) ms *= HASTE_FACTOR
            if (hasEffect(EffectKind.Slow)) ms *= SLOW_FACTOR
            if (hasEffect(EffectKind.Freeze)) ms *= FREEZE_FACTOR
            return ms.toLong().coerceIn(MIN_TICK_MS, MAX_TICK_MS)
        }

    /** Time Attack only: milliseconds left in the run (0 once expired). */
    val timeRemainingMs: Long get() = (TIME_ATTACK_MS - playedMs).coerceAtLeast(0)

    companion object {
        /** Tick-interval multipliers per speed effect (compounding if stacked). */
        const val HASTE_FACTOR = 0.6
        const val SLOW_FACTOR = 1.6
        const val FREEZE_FACTOR = 1.4

        /** Clamp so stacked effects can't make the game unplayably fast/slow. */
        const val MIN_TICK_MS = 40L
        const val MAX_TICK_MS = 400L

        /** Endless ramp: starts gentle and quickens to a floor as ticks accrue. */
        const val ENDLESS_BASE_MS = 190.0
        const val ENDLESS_FLOOR_MS = 70.0
        private const val ENDLESS_RAMP_PER_TICK = 0.22

        /** Time Attack run length. */
        const val TIME_ATTACK_MS = 120_000L

        private fun endlessBaseMs(elapsedTicks: Int): Double =
            (ENDLESS_BASE_MS - elapsedTicks * ENDLESS_RAMP_PER_TICK).coerceAtLeast(ENDLESS_FLOOR_MS)
    }
}

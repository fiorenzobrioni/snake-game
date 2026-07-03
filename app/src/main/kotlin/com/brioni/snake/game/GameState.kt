package com.brioni.snake.game

/** Lifecycle of a single game session. */
enum class GameStatus {
    /** Configured but not yet started — the menu is shown. */
    Ready,

    /** The loop is ticking and input is accepted. */
    Running,

    /** Frozen by the player; resumable. */
    Paused,

    /**
     * Levels mode only: the board is staged (walls swapped, snake at spawn,
     * no food) and the intro countdown is showing — at game start, on every
     * level advance and after a life loss. [GameEngine.beginLevel] moves on
     * to [Running]; the countdown timing itself lives in the ViewModel.
     */
    LevelIntro,

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
 * @param timeAdjustMs     Time Attack only: a signed shift of the run's budget
 *                         from time-bonus / time-penalty blocks (positive adds
 *                         time, negative removes it). Kept separate from
 *                         [playedMs] so the elapsed clock stays truthful.
 * @param levelIndex       Levels mode only: the 1-based designed level
 *                         (1..[LevelsMode.LEVEL_COUNT]).
 * @param speedCycle       Levels mode only: the 1-based cycle ("Speed x"); it
 *                         increments each time the ten levels wrap and drives
 *                         the pace via [LevelsMode.tickMillisFor].
 * @param lives            Levels mode only: remaining snakes (0 elsewhere).
 * @param levelFoodsEaten  Levels mode only: foods eaten in the current level;
 *                         the level advances at [LevelsMode.LEVEL_FOOD_GOAL].
 * @param walls            Levels mode only: cells outside the level's playable
 *                         shape. Lethal like out-of-bounds, excluded from every
 *                         spawn, and painted as "outside the board".
 * @param gates            Levels mode only: time-phased moving walls (Step 6.9.7);
 *                         each [Gate]'s cells are lethal while it is closed (a pure
 *                         function of [elapsedTicks]) and passable while open.
 * @param teleports        Levels mode only: portal pad pairs (Step 6.9.7); the head
 *                         stepping onto one pad emerges at its partner on the same
 *                         tick. Pad cells are excluded from every spawn.
 * @param lastEvents       what happened on the most recent tick, for the UI to
 *                         react to (particle bursts, future effects). Not part
 *                         of the logical state — cleared/replaced every tick.
 */
data class GameState(
    val board: BoardDimensions,
    val level: Level,
    val snakeSpeed: SnakeSpeed = SnakeSpeed.DEFAULT,
    /**
     * The challenge twist this run was configured with ([ChallengeModifier.None]
     * outside the Daily / Random challenges). Kept on the state so the engine's
     * pure rules (spawn table, combo window, pace) can consult it per tick.
     */
    val modifier: ChallengeModifier = ChallengeModifier.None,
    val snake: List<Position>,
    val direction: Direction,
    val pendingDirection: Direction,
    val foods: List<Food>,
    val obstacles: Set<Position>,
    val score: Int,
    val pendingGrowth: Int,
    val status: GameStatus,
    val mode: GameMode = GameMode.Endless,
    val elapsedTicks: Int = 0,
    val playedMs: Long = 0,
    val combo: Int = 0,
    val comboDeadlineTick: Int = 0,
    val debris: List<Debris> = emptyList(),
    val effectTimers: List<ActiveEffect> = emptyList(),
    val timeAdjustMs: Long = 0,
    val levelIndex: Int = 1,
    val speedCycle: Int = 1,
    val lives: Int = 0,
    val levelFoodsEaten: Int = 0,
    val walls: Set<Position> = emptySet(),
    val gates: List<Gate> = emptyList(),
    val teleports: List<TeleportPair> = emptyList(),
    /**
     * A "coyote" dodge banked by staying alive: the first lethal step spends it on
     * a one-tick freeze (the head hesitates against the hazard) instead of dying,
     * buying a beat to turn away. Re-banked by the next safe move. Granted by
     * [GameEngine.setup] / level staging; defaults false so a bare state still dies
     * on contact (keeps the engine's collision tests immediate).
     */
    val graceAvailable: Boolean = false,
    val lastEvents: List<GameEvent> = emptyList(),
) {
    val head: Position get() = snake.first()

    val isPlayable: Boolean get() = status == GameStatus.Running

    /** True while a timed [kind] effect is running. */
    fun hasEffect(kind: EffectKind): Boolean = effectTimers.any { it.kind == kind }

    /** The lethal (closed) gate cells at [tick] - solid walls just for that moment. */
    fun closedGateCells(tick: Int): Set<Position> {
        if (gates.isEmpty()) return emptySet()
        return gates.filter { it.isClosedAt(tick) }.flatMapTo(HashSet()) { it.cells }
    }

    /** Every gate and teleport cell (all phases) - excluded from food spawns. */
    val hazardSpawnCells: Set<Position>
        get() {
            if (gates.isEmpty() && teleports.isEmpty()) return emptySet()
            val cells = HashSet<Position>()
            gates.forEach { cells.addAll(it.cells) }
            teleports.forEach { cells.addAll(it.cells) }
            return cells
        }

    /**
     * The wall-clock delay until the next tick, after applying speed effects.
     * The game loop reads this instead of [SnakeSpeed.tickMillis] so Lightning/
     * Snail/Freeze actually change the pace; effect timers are aged by the same
     * value, keeping every power-up's real duration stable.
     */
    val tickIntervalMillis: Long
        get() {
            // Endless overrides the level pace with a stepped tier curve that
            // quickens over play time; Levels paces by its speed cycle instead
            // of the difficulty. Time Attack and Zen run at the selected
            // [SnakeSpeed], fixed for the whole run (Zen never ramps by design).
            var ms = when (mode) {
                GameMode.Endless -> endlessTickMs(endlessSpeedTier)
                GameMode.Levels -> LevelsMode.tickMillisFor(speedCycle).toDouble()
                else -> snakeSpeed.tickMillis.toDouble()
            }
            if (hasEffect(EffectKind.Haste)) ms *= HASTE_FACTOR
            if (hasEffect(EffectKind.Slow)) ms *= SLOW_FACTOR
            if (hasEffect(EffectKind.Freeze)) ms *= FREEZE_FACTOR
            return ms.toLong().coerceIn(MIN_TICK_MS, MAX_TICK_MS)
        }

    /**
     * Endless mode: the current 1-based speed tier ("Speed x" in the HUD). It
     * steps up every [ENDLESS_TIER_MS] of play, starts higher on harder
     * difficulty levels ([Level.endlessTierHeadStart]) and under the Overdrive
     * challenge twist ([ChallengeModifier.endlessTierBoost]), and keeps climbing
     * visibly for many minutes before the pace bottoms out at [ENDLESS_FLOOR_MS].
     */
    val endlessSpeedTier: Int
        get() = endlessTierFor(playedMs, level, modifier)

    /** Time Attack only: milliseconds left in the run (0 once expired). */
    val timeRemainingMs: Long get() = (TIME_ATTACK_MS + timeAdjustMs - playedMs).coerceAtLeast(0)

    /**
     * Time Attack only: true while the run is inside its Fever Time finale —
     * the last [FEVER_MS] on the clock, during which every point is doubled
     * ([FEVER_SCORE_FACTOR]) and the UI turns up the heat. A time-bonus block
     * can push the clock back out of the window (fever pauses until it drains
     * back in), so the window is a pure function of the remaining time.
     */
    val inFeverTime: Boolean
        get() = mode == GameMode.TimeAttack && timeRemainingMs in 1..FEVER_MS

    companion object {
        /** Tick-interval multipliers per speed effect (compounding if stacked). */
        const val HASTE_FACTOR = 0.6
        const val SLOW_FACTOR = 1.6
        const val FREEZE_FACTOR = 1.4

        /** Clamp so stacked effects can't make the game unplayably fast/slow. */
        const val MIN_TICK_MS = 40L
        const val MAX_TICK_MS = 400L

        /**
         * Endless ramp (stepped tiers): the pace starts at [ENDLESS_BASE_MS]
         * and each tier multiplies it by [ENDLESS_TIER_FACTOR], flooring at
         * [ENDLESS_FLOOR_MS]. A tier lasts [ENDLESS_TIER_MS] of play, so the
         * ramp stays alive for ~6-7 minutes (the old linear ramp flatlined
         * after ~90 seconds) and every step is announced to the player.
         */
        const val ENDLESS_BASE_MS = 190.0
        const val ENDLESS_FLOOR_MS = 60.0
        const val ENDLESS_TIER_FACTOR = 0.94
        const val ENDLESS_TIER_MS = 20_000L

        /** Time Attack run length. */
        const val TIME_ATTACK_MS = 120_000L

        /** Time Attack: the Fever Time window at the end of the clock. */
        const val FEVER_MS = 20_000L

        /** Time Attack: score multiplier while Fever Time runs. */
        const val FEVER_SCORE_FACTOR = 2

        /** The Endless speed tier for a given play time, difficulty and twist. */
        fun endlessTierFor(playedMs: Long, level: Level, modifier: ChallengeModifier): Int =
            1 + (playedMs / ENDLESS_TIER_MS).toInt() +
                level.endlessTierHeadStart + modifier.endlessTierBoost

        /** The Endless tick interval (ms) for a 1-based speed [tier], floored. */
        fun endlessTickMs(tier: Int): Double {
            var ms = ENDLESS_BASE_MS
            repeat((tier - 1).coerceAtLeast(0)) {
                ms *= ENDLESS_TIER_FACTOR
                if (ms <= ENDLESS_FLOOR_MS) return ENDLESS_FLOOR_MS
            }
            return ms.coerceAtLeast(ENDLESS_FLOOR_MS)
        }
    }
}

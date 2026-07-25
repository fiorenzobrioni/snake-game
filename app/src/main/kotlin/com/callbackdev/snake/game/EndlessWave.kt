package com.callbackdev.snake.game

/**
 * A timed **event** that sweeps the board in [GameMode.Endless].
 *
 * Endless escalates its pace, but nothing ever *happens* in it: minute five plays
 * exactly like minute two, only faster. The waves give a long run a rhythm - a
 * feeding frenzy, a famine, a bombardment - so it has movements instead of one
 * long crescendo, and so the player has something to remember about a run.
 *
 * They rotate in a fixed order rather than at random: a rhythm you can *learn* is
 * a rhythm you can plan around, and it keeps a seeded run reproducible.
 */
enum class EndlessWave(val displayName: String) {
    /**
     * Feast: the board floods with food. The moment to gorge - and the moment the
     * risk multiplier can run away with the score, if you dare keep the length.
     */
    Feast("Feast"),

    /**
     * Drought: food all but dries up. With the snake still growing on its own,
     * the brake is gone: pure survival for a few seconds.
     */
    Drought("Drought"),

    /**
     * Hailstorm: lethal blocks rain onto the board, then melt away. They never
     * land next to the head, so it is a puzzle to route around, not an ambush.
     */
    Hailstorm("Hailstorm"),
}

/**
 * When the waves fire and what each one changes. Pure functions of the run's
 * played time, so no extra state has to be carried or persisted, and a seeded
 * run stays reproducible.
 */
object EndlessWaves {

    /** How long a run plays undisturbed before the first wave. */
    const val FIRST_START_MS = 45_000L

    /** Time between the start of one wave and the start of the next. */
    const val PERIOD_MS = 45_000L

    /** How long a wave lasts. */
    const val DURATION_MS = 12_000L

    /** Simultaneous foods during [EndlessWave.Feast]. */
    const val FEAST_FOOD_COUNT = 9

    /** Simultaneous foods during [EndlessWave.Drought]. */
    const val DROUGHT_FOOD_COUNT = 1

    /** A hail block lands every this many ticks while [EndlessWave.Hailstorm] runs. */
    const val HAIL_INTERVAL_TICKS = 14

    /** At most this many hail blocks may be on the board at once. */
    const val HAIL_MAX_BLOCKS = 8

    /** Cells around the head kept clear of hail, so a block never lands on the snake's nose. */
    const val HAIL_HEAD_CLEARANCE = 4

    /** How long a hail block stays lethal before melting (outlasting the wave a little). */
    const val HAIL_LIFETIME_MS = 15_000L

    /** The wave running at [playedMs], or null between waves (and before the first). */
    fun activeAt(playedMs: Long): EndlessWave? {
        if (playedMs < FIRST_START_MS) return null
        val since = playedMs - FIRST_START_MS
        return if (since % PERIOD_MS < DURATION_MS) {
            EndlessWave.entries[((since / PERIOD_MS) % EndlessWave.entries.size).toInt()]
        } else {
            null
        }
    }

    /** Milliseconds left of the wave running at [playedMs] (0 when none is). */
    fun remainingMsAt(playedMs: Long): Long {
        if (activeAt(playedMs) == null) return 0
        val since = playedMs - FIRST_START_MS
        return DURATION_MS - (since % PERIOD_MS)
    }

    /** Progress through the running wave (0 at its start, 1 at its end). */
    fun fractionAt(playedMs: Long): Float {
        if (activeAt(playedMs) == null) return 0f
        return 1f - remainingMsAt(playedMs).toFloat() / DURATION_MS
    }

    /** Simultaneous foods to keep on the board during [wave] (null = the mode's normal count). */
    fun foodCountFor(wave: EndlessWave?): Int? = when (wave) {
        EndlessWave.Feast -> FEAST_FOOD_COUNT
        EndlessWave.Drought -> DROUGHT_FOOD_COUNT
        else -> null
    }
}

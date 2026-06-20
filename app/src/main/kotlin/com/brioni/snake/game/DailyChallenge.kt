package com.brioni.snake.game

/**
 * A deterministic, date-seeded challenge: everyone who plays on the same day
 * gets the same configuration and the same RNG [seed], so scores compare
 * like-for-like (modulo each device's board aspect, which stays responsive).
 *
 * Derived purely from the [epochDay] (days since 1970-01-01) with no clock
 * access here, so it is reproducible and unit-testable. Campaign is excluded
 * from the rotation - it has its own structure, lives and scoring.
 */
data class DailyChallenge(
    val epochDay: Long,
    val seed: Long,
    val mode: GameMode,
    val level: Level,
    val scale: BoardScale,
) {
    companion object {
        /** Modes the daily rotates through (Campaign excluded). */
        private val MODES = listOf(GameMode.Endless, GameMode.TimeAttack)

        /** A fixed board granularity, so the daily is the same size for everyone. */
        private val SCALE = BoardScale.Classic

        /** The challenge for a given [epochDay]. Stable: same day → same result. */
        fun forDay(epochDay: Long): DailyChallenge = DailyChallenge(
            epochDay = epochDay,
            seed = mix(epochDay),
            // Alternate the mode by day; pick the level from a decorrelated hash.
            mode = MODES[epochDay.mod(MODES.size.toLong()).toInt()],
            level = Level.entries[mix(epochDay + 1).mod(Level.entries.size.toLong()).toInt()],
            scale = SCALE,
        )

        /** SplitMix64 finalizer: a cheap, well-mixed hash so adjacent days differ a lot. */
        private fun mix(value: Long): Long {
            var z = value + 0x9E3779B97F4A7C15uL.toLong()
            z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
            z = (z xor (z ushr 27)) * 0x94D049BB133111EBuL.toLong()
            return z xor (z ushr 31)
        }
    }
}

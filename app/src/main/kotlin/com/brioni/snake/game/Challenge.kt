package com.brioni.snake.game

import kotlin.random.Random

/**
 * A self-contained run configuration shared by the **Daily** and **Random**
 * challenges: a fixed [seed] (driving the obstacle layout and food sequence) plus
 * a mode/level/board and a [modifier] twist. Pure and clock-free, so it is
 * reproducible and unit-testable. Campaign is excluded (it has its own structure).
 *
 * The two factories differ only in where the numbers come from: [forDay] is the
 * deterministic daily (same for everyone on a date); [random] is a one-off for
 * variety. Neither stores a date here - the daily's epoch day is tracked by the
 * caller, so this type stays a plain config.
 */
data class Challenge(
    val seed: Long,
    val mode: GameMode,
    val level: Level,
    val scale: BoardScale,
    val modifier: ChallengeModifier,
) {
    companion object {
        /** Modes the challenges rotate through (Campaign excluded). */
        private val MODES = listOf(GameMode.Endless, GameMode.TimeAttack)

        /** The default board granularity, unless the modifier overrides it. */
        private val DEFAULT_SCALE = BoardScale.Classic

        /** The deterministic daily challenge for [epochDay]: same day → same result. */
        fun forDay(epochDay: Long): Challenge = build(
            seed = mix(epochDay),
            // Alternate the mode by day; level and modifier from decorrelated hashes.
            modeIndex = epochDay.mod(MODES.size.toLong()).toInt(),
            levelHash = mix(epochDay + 1),
            modifierHash = mix(epochDay + 2),
        )

        /** A one-off random challenge derived entirely from [seed]. */
        fun random(seed: Long): Challenge = build(
            seed = seed,
            modeIndex = mix(seed + 3).mod(MODES.size.toLong()).toInt(),
            levelHash = mix(seed + 1),
            modifierHash = mix(seed + 2),
        )

        /** A random challenge seeded from [rng] (fresh each call). */
        fun random(rng: Random = Random): Challenge = random(rng.nextLong())

        private fun build(seed: Long, modeIndex: Int, levelHash: Long, modifierHash: Long): Challenge {
            val modifier = ChallengeModifier.entries[modifierHash.mod(ChallengeModifier.entries.size.toLong()).toInt()]
            return Challenge(
                seed = seed,
                mode = MODES[modeIndex],
                level = Level.entries[levelHash.mod(Level.entries.size.toLong()).toInt()],
                scale = modifier.scaleOverride ?: DEFAULT_SCALE,
                modifier = modifier,
            )
        }

        /** SplitMix64 finalizer: a cheap, well-mixed hash so nearby inputs differ a lot. */
        private fun mix(value: Long): Long {
            var z = value + 0x9E3779B97F4A7C15uL.toLong()
            z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
            z = (z xor (z ushr 27)) * 0x94D049BB133111EBuL.toLong()
            return z xor (z ushr 31)
        }
    }
}

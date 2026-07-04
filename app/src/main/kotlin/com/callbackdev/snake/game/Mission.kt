package com.callbackdev.snake.game

/**
 * A single per-run objective ("eat 20 foods", "reach a x5 combo", "survive 90s")
 * evaluated against a finished run's [RunStats]. Pure and Android-free so the
 * rotation and completion logic stays unit-testable.
 *
 * [id] is the stable key persisted on completion, so wording can be reworded
 * without orphaning progress. [target] plus [progressOf] let the UI show how far
 * a run got (clamped via [progress]) and whether it was [completedBy].
 */
data class Mission(
    val id: String,
    val description: String,
    val target: Int,
    /** The run's raw progress toward [target], extracted from finished-run stats. */
    val progressOf: (RunStats) -> Int,
) {
    /** Progress toward [target], clamped to it for display. */
    fun progress(stats: RunStats): Int = progressOf(stats).coerceIn(0, target)

    /** True when the run satisfied this mission. */
    fun completedBy(stats: RunStats): Boolean = progressOf(stats) >= target

    companion object {
        /** How many missions are active at once (the "2-3 rotating goals"). */
        const val DAILY_COUNT = 3

        /**
         * The pool the daily set is drawn from. Every mission is expressed purely
         * over the existing [RunStats] fields and is achievable in any mode, so the
         * rotation never offers an impossible goal for the current run.
         */
        val POOL: List<Mission> = listOf(
            Mission("eat_15", "Eat 15 foods in one run", 15) { it.foodsEaten },
            Mission("eat_30", "Eat 30 foods in one run", 30) { it.foodsEaten },
            Mission("eat_60", "Eat 60 foods in one run", 60) { it.foodsEaten },
            Mission("combo_4", "Reach a x4 combo", 4) { it.maxCombo },
            Mission("combo_6", "Reach a x6 combo", 6) { it.maxCombo },
            Mission("survive_60", "Survive 60 seconds", 60) { (it.durationMs / 1000L).toInt() },
            Mission("survive_120", "Survive 2 minutes", 120) { (it.durationMs / 1000L).toInt() },
            Mission("score_300", "Score 300 in one run", 300) { it.score },
            Mission("score_800", "Score 800 in one run", 800) { it.score },
            Mission("length_20", "Grow to 20 segments", 20) { it.maxSnakeLength },
            Mission("length_40", "Grow to 40 segments", 40) { it.maxSnakeLength },
            Mission("powerup", "Grab a power-up (Star, Jackpot or Explosion)", 1) {
                if (it.usedStar || it.usedJackpot || it.usedExplosion) 1 else 0
            },
        )

        /** Quick lookup by [Mission.id], for decoding persisted completion keys. */
        private val BY_ID: Map<String, Mission> = POOL.associateBy { it.id }

        /** The mission with [id], or null if the pool no longer contains it. */
        fun byId(id: String): Mission? = BY_ID[id]

        /**
         * The deterministic set of [count] distinct missions for [epochDay]: the
         * same day always yields the same goals (so the menu and game-over agree),
         * and the set rotates as the day advances. Pure - no clock access.
         */
        fun forDay(epochDay: Long, count: Int = DAILY_COUNT): List<Mission> {
            val take = count.coerceIn(0, POOL.size)
            return POOL.indices
                .sortedBy { mix(epochDay * 1_000_003L + it) }
                .take(take)
                .map { POOL[it] }
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

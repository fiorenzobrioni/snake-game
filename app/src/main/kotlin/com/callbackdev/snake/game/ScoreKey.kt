package com.callbackdev.snake.game

/**
 * Identifies one highscore slot: a (mode × level × scale) triple. The codec is
 * kept pure (no Android) so the persistence key format is unit-testable and
 * shared between writing and the Records screen's bulk read.
 */
data class ScoreKey(
    val mode: GameMode,
    val level: Level,
    val scale: BoardScale,
) {
    /** The DataStore preference name for this slot, e.g. `highscore_Endless_Beginner_Cozy`. */
    fun storageName(): String = "$PREFIX${mode.name}_${level.name}_${scale.name}"

    companion object {
        const val PREFIX = "highscore_"

        /** Parses a [storageName] back into a key, or null if it isn't one (or is stale). */
        fun parse(name: String): ScoreKey? {
            if (!name.startsWith(PREFIX)) return null
            val parts = name.removePrefix(PREFIX).split("_")
            if (parts.size != 3) return null
            val mode = runCatching { GameMode.valueOf(parts[0]) }.getOrNull() ?: return null
            val level = runCatching { Level.valueOf(parts[1]) }.getOrNull() ?: return null
            val scale = runCatching { BoardScale.valueOf(parts[2]) }.getOrNull() ?: return null
            return ScoreKey(mode, level, scale)
        }
    }
}

package com.callbackdev.snake.game

/**
 * How often special blocks (power-ups / hazards) appear, independent of the
 * level and board size. [Standard] is the original tuning. The higher tiers
 * both raise the spawn weight of the special branch in [FoodTable.roll] and
 * pull the special unlock gate ([FoodTable.GATE_SPECIAL_MS]) earlier via
 * [gateFactor], so they actually feel different from the very start of a run.
 *
 * Kept in the model package (no Android imports) so it can be persisted by name
 * via DataStore.
 */
enum class SpecialFrequency(
    val displayName: String,
    /** Weight of the special branch relative to the fixed grow weight of 40. */
    val spawnWeight: Int,
    /** Multiplies the special unlock gate; < 1 unlocks specials sooner. */
    val gateFactor: Double,
) {
    Standard("Standard", 12, 1.0),
    Frequent("Frequent", 32, 0.5),
    Frenzy("Frenzy", 62, 0.25),
}

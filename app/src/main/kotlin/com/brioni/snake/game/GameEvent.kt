package com.brioni.snake.game

/**
 * Something that happened during a single [GameEngine.tick]. The engine emits
 * these so the UI can react (particle bursts, screen shake, …) without
 * re-deriving what occurred from before/after snapshots. Sealed so the Phase 6
 * specials can add their own cases (earthquake, explosion, speed change, …).
 */
sealed interface GameEvent {

    /** A growing food was eaten, awarding [points] at the current [combo]. */
    data class Ate(val food: Food, val points: Int, val combo: Int) : GameEvent

    /**
     * A shrinking food was eaten. [removed] is how many tail cells were actually
     * dropped after applying the minimum-length floor; [points] is the small
     * symbolic bonus awarded.
     */
    data class Shrunk(val food: Food, val removed: Int, val points: Int) : GameEvent

    /** The snake died on this tick. */
    data object Died : GameEvent

    // --- Phase 6.2 specials. ---

    /**
     * Earthquake eaten: [removed] tail cells were bitten off (drives the shake)
     * and scattered onto the board as the lethal [debris] cells listed here.
     */
    data class Quaked(val food: Food, val removed: Int, val debris: List<Position>) : GameEvent

    /** Explosion eaten: the snake split, leaving [debris] lethal blocks behind. */
    data class Exploded(val food: Food, val debris: List<Position>) : GameEvent

    /** A timed effect ([kind]) started (or refreshed) from eating [food]. */
    data class EffectStarted(val kind: EffectKind, val food: Food) : GameEvent

    /** A timed effect ([kind]) ran out this tick. */
    data class EffectExpired(val kind: EffectKind) : GameEvent

    /** Jackpot eaten: awarded [points] and grew the snake by [growth]. */
    data class JackpotHit(val food: Food, val points: Int, val growth: Int) : GameEvent

    /** Time Attack: a time-bonus block added [seconds] to the clock. */
    data class TimeGained(val food: Food, val seconds: Int) : GameEvent

    /** Time Attack: a time-penalty block removed [seconds] from the clock. */
    data class TimeLost(val food: Food, val seconds: Int) : GameEvent

    /**
     * An uneaten regular food timed out and was removed (a fresh one is spawned
     * elsewhere in the same tick). Specials never vanish. Drives the "vanish"
     * particle burst.
     */
    data class FoodVanished(val food: Food) : GameEvent
}

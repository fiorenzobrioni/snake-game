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

    /** Earthquake eaten: [removed] tail cells were bitten off (drives the shake). */
    data class Quaked(val food: Food, val removed: Int) : GameEvent

    /** Explosion eaten: the snake split, leaving [debris] lethal blocks behind. */
    data class Exploded(val food: Food, val debris: List<Position>) : GameEvent

    /** A timed effect ([kind]) started (or refreshed) from eating [food]. */
    data class EffectStarted(val kind: EffectKind, val food: Food) : GameEvent

    /** A timed effect ([kind]) ran out this tick. */
    data class EffectExpired(val kind: EffectKind) : GameEvent

    /** Jackpot eaten: awarded [points] and grew the snake by [growth]. */
    data class JackpotHit(val food: Food, val points: Int, val growth: Int) : GameEvent
}

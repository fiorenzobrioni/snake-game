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
}

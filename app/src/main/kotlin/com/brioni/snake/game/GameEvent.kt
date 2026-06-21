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

    /**
     * The head survived this tick but is orthogonally adjacent to a static hazard
     * (a wall, an obstacle, the board edge, or lingering debris) - a near miss.
     * Self-body adjacency is excluded (coiling next to yourself is not a graze).
     * Drives a light haptic "tick"; emitted at most once per tick, throttled by
     * the UI so riding an edge does not buzz continuously.
     */
    data object NearMiss : GameEvent

    /**
     * A lethal step was cancelled by a banked grace/coyote dodge: the snake froze
     * for one tick instead of dying, leaving a beat to turn away. Drives a
     * "close call" cue (a firm haptic / small jolt).
     */
    data object GraceDodge : GameEvent

    /**
     * The snake is **one tick from eating** the hazard [food] (Earthquake /
     * Explosion / Snail / time penalty): the cell it is about to enter, on its
     * current heading, holds the hazard. Drives a short "tell" - a warning flash
     * on the piece plus a pre-haptic - so the strike never feels arbitrary. It is
     * predictive (the player can still turn away), so it is purely advisory.
     */
    data class HazardImminent(val food: Food) : GameEvent

    // --- Phase 6.2 specials. ---

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

    // --- Levels mode. ---

    /** Levels: the food goal was met; the board staged level [levelIndex] at [speedCycle]. */
    data class LevelAdvanced(val levelIndex: Int, val speedCycle: Int) : GameEvent

    /** Levels: a crash consumed a life; [remaining] are left and the level restages. */
    data class LifeLost(val remaining: Int) : GameEvent

    /**
     * Levels: an extra-life food was eaten. [lives] is the resulting stock;
     * [capped] is true when the stock was already full and points were paid
     * instead ([LevelsMode.LIFE_CAP_BONUS]).
     */
    data class LifeGained(val food: Food, val lives: Int, val capped: Boolean) : GameEvent
}

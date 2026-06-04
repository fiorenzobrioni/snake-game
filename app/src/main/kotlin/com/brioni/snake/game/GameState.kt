package com.brioni.snake.game

/** Lifecycle of a single game session. */
enum class GameStatus {
    /** Configured but not yet started — the menu is shown. */
    Ready,

    /** The loop is ticking and input is accepted. */
    Running,

    /** Frozen by the player; resumable. */
    Paused,

    /** The snake died; the loop is stopped. */
    GameOver,
}

/**
 * The complete, immutable state of a game at one instant. The [GameEngine]
 * produces new instances from old ones; nothing here mutates in place.
 *
 * @param snake            body cells, head first.
 * @param direction        the committed travel direction (last applied tick).
 * @param pendingDirection the next direction to commit, buffered from input
 *                         and already validated against 180° reversals.
 * @param pendingGrowth    segments still owed from eaten food, paid one per tick.
 * @param elapsedTicks     monotonic count of ticks since the game started;
 *                         drives the time-gated food progression.
 * @param combo            length of the current consecutive-eat streak (the
 *                         score multiplier), reset when a streak lapses.
 * @param comboDeadlineTick the streak survives only if the next food is eaten
 *                         on or before this tick.
 * @param lastEvents       what happened on the most recent tick, for the UI to
 *                         react to (particle bursts, future effects). Not part
 *                         of the logical state — cleared/replaced every tick.
 */
data class GameState(
    val board: BoardDimensions,
    val level: Level,
    val snake: List<Position>,
    val direction: Direction,
    val pendingDirection: Direction,
    val foods: List<Food>,
    val obstacles: Set<Position>,
    val score: Int,
    val pendingGrowth: Int,
    val status: GameStatus,
    val elapsedTicks: Int = 0,
    val combo: Int = 0,
    val comboDeadlineTick: Int = 0,
    val lastEvents: List<GameEvent> = emptyList(),
) {
    val head: Position get() = snake.first()

    val isPlayable: Boolean get() = status == GameStatus.Running
}

package com.brioni.snake.game

import kotlin.random.Random

/**
 * The pure-Kotlin rules engine. Every method takes a [GameState] and returns a
 * new one — no field is mutated in place — so the model is deterministic given
 * a seeded [random] and trivially unit-testable.
 *
 * Behaviour mirrors the frozen v1.0.0 desktop build (`SnakeForm.cs`): three-
 * segment snake starting at centre heading up, two foods kept on the board,
 * growth queued from food, and death on walls / body / obstacles.
 */
class GameEngine(private val random: Random = Random.Default) {

    /**
     * Builds a [GameStatus.Ready] state: snake centred, obstacles generated for
     * [level], no food yet. The menu is shown over this until [start].
     */
    fun setup(level: Level, board: BoardSize): GameState {
        val cx = board.width / 2
        val cy = board.height / 2
        val snake = listOf(
            Position(cx, cy),
            Position(cx, cy + 1),
            Position(cx, cy + 2),
        )
        return GameState(
            board = board,
            level = level,
            snake = snake,
            direction = Direction.Up,
            pendingDirection = Direction.Up,
            foods = emptyList(),
            obstacles = generateObstacles(level, board, snake),
            score = 0,
            pendingGrowth = 0,
            status = GameStatus.Ready,
        )
    }

    /** Transitions a [GameStatus.Ready] state into a running game with food. */
    fun start(state: GameState): GameState {
        if (state.status != GameStatus.Ready) return state
        return state.copy(
            foods = refill(state.board, state.snake, state.obstacles, emptyList(), elapsedTicks = 0, level = state.level),
            status = GameStatus.Running,
        )
    }

    /** Convenience: a fresh, already-running game. */
    fun newGame(level: Level, board: BoardSize): GameState = start(setup(level, board))

    /**
     * Buffers a direction change for the next tick. A 180° reversal of the
     * currently committed [GameState.direction] is rejected, which also makes
     * multiple inputs within one tick safe (each is validated against the same
     * committed direction, so two turns can never combine into a reversal).
     */
    fun changeDirection(state: GameState, direction: Direction): GameState {
        if (state.status != GameStatus.Running) return state
        if (direction.isOpposite(state.direction)) return state
        return state.copy(pendingDirection = direction)
    }

    /** Toggles between [GameStatus.Running] and [GameStatus.Paused]. */
    fun togglePause(state: GameState): GameState = when (state.status) {
        GameStatus.Running -> state.copy(status = GameStatus.Paused)
        GameStatus.Paused -> state.copy(status = GameStatus.Running)
        else -> state
    }

    /**
     * Advances the simulation by one step. No-op unless [GameStatus.Running].
     *
     * Order: bump the tick counter, commit the buffered direction, move the
     * head, apply the eaten food's [FoodEffect] (grow or shrink, with a length
     * floor) and combo-scored points, refill food, then test for death. Every
     * consequence is emitted as a [GameEvent] in [GameState.lastEvents].
     */
    fun tick(state: GameState): GameState {
        if (state.status != GameStatus.Running) return state

        val elapsedTicks = state.elapsedTicks + 1
        val direction = state.pendingDirection
        val newHead = state.head.step(direction)

        val body = ArrayList<Position>(state.snake.size + 1)
        body.add(newHead)
        body.addAll(state.snake)

        var foods = state.foods
        var score = state.score
        var pendingGrowth = state.pendingGrowth
        var combo = state.combo
        var comboDeadlineTick = state.comboDeadlineTick
        val events = ArrayList<GameEvent>(2)

        val eaten = foods.firstOrNull { it.occupies(newHead) }
        if (eaten != null) {
            foods = foods - eaten
            when (val effect = eaten.effect) {
                is FoodEffect.Grow -> {
                    // A fresh streak, or extend the running one if still in time.
                    combo = if (elapsedTicks <= comboDeadlineTick) combo + 1 else 1
                    comboDeadlineTick = elapsedTicks + COMBO_WINDOW_TICKS
                    val points = effect.segments * 10 * combo.coerceAtMost(MAX_COMBO)
                    score += points
                    pendingGrowth += effect.segments - 1 // head already added this tick
                    events.add(GameEvent.Ate(eaten, points, combo.coerceAtMost(MAX_COMBO)))
                }
                is FoodEffect.Shrink -> {
                    // Shrinking neither feeds nor breaks the grow combo; it just
                    // trims the tail down to the floor and pays a token score.
                    pendingGrowth = 0
                    val removable = (body.size - MIN_SNAKE_LENGTH).coerceAtLeast(0)
                    val removed = effect.segments.coerceAtMost(removable)
                    repeat(removed) { body.removeAt(body.lastIndex) }
                    val points = if (eaten.size == FoodSize.Maxi) SHRINK_POINTS_MAXI else SHRINK_POINTS
                    score += points
                    events.add(GameEvent.Shrunk(eaten, removed, points))
                }
            }
            foods = refill(state.board, body, state.obstacles, foods, elapsedTicks, state.level)
        } else if (pendingGrowth > 0) {
            pendingGrowth--
        } else {
            body.removeAt(body.lastIndex) // keep length: drop the tail
        }

        val dead = isOutOfBounds(newHead, state.board) ||
            newHead in state.obstacles ||
            collidesWithBody(newHead, body)
        if (dead) events.add(GameEvent.Died)

        return state.copy(
            snake = body,
            direction = direction,
            foods = foods,
            score = score,
            pendingGrowth = pendingGrowth,
            elapsedTicks = elapsedTicks,
            combo = combo,
            comboDeadlineTick = comboDeadlineTick,
            lastEvents = events,
            status = if (dead) GameStatus.GameOver else GameStatus.Running,
        )
    }

    private fun isOutOfBounds(cell: Position, board: BoardSize): Boolean =
        cell.x < 0 || cell.x >= board.width || cell.y < 0 || cell.y >= board.height

    /** The head (index 0) hitting any other body cell. */
    private fun collidesWithBody(head: Position, body: List<Position>): Boolean {
        for (i in 1 until body.size) {
            if (body[i] == head) return true
        }
        return false
    }

    private fun generateObstacles(
        level: Level,
        board: BoardSize,
        snake: List<Position>,
    ): Set<Position> {
        if (level.obstacleCount == 0) return emptySet()
        val snakeCells = snake.toHashSet()
        val obstacles = LinkedHashSet<Position>()
        repeat(level.obstacleCount) {
            val cell = Position(
                random.nextInt(1, board.width - 1),
                random.nextInt(1, board.height - 1),
            )
            if (cell !in snakeCells) obstacles.add(cell)
        }
        return obstacles
    }

    /** Tops the board up to [FOOD_COUNT] items, skipping if no cell is free. */
    private fun refill(
        board: BoardSize,
        snake: List<Position>,
        obstacles: Set<Position>,
        existing: List<Food>,
        elapsedTicks: Int,
        level: Level,
    ): List<Food> {
        var foods = existing
        while (foods.size < FOOD_COUNT) {
            val food = spawnFood(board, snake, obstacles, foods, elapsedTicks, level) ?: break
            foods = foods + food
        }
        return foods
    }

    /**
     * Rolls a food type and finds a free square for it, never on the border.
     * Tries random cells first (matching v1.0.0's feel), then falls back to a
     * deterministic scan so a near-full board can't loop forever.
     */
    private fun spawnFood(
        board: BoardSize,
        snake: List<Position>,
        obstacles: Set<Position>,
        existing: List<Food>,
        elapsedTicks: Int,
        level: Level,
    ): Food? {
        val spec = FoodTable.roll(random, elapsedTicks, level)
        val span = spec.size.cellSpan
        // Top-left cell range that keeps the whole square off the border.
        val maxX = board.width - span
        val maxY = board.height - span
        if (maxX <= 1 || maxY <= 1) return null

        val occupied = HashSet<Position>()
        occupied.addAll(snake)
        occupied.addAll(obstacles)
        existing.forEach { occupied.addAll(it.cells()) }

        fun candidateAt(x: Int, y: Int): Food? {
            val food = Food(Position(x, y), spec.category, spec.tier, spec.size, spec.effect)
            return if (food.cells().none { it in occupied }) food else null
        }

        repeat(MAX_SPAWN_ATTEMPTS) {
            candidateAt(random.nextInt(1, maxX), random.nextInt(1, maxY))?.let { return it }
        }
        for (y in 1 until maxY) {
            for (x in 1 until maxX) {
                candidateAt(x, y)?.let { return it }
            }
        }
        return null
    }

    companion object {
        /** Foods kept on the board at once — two, as in v1.0.0. */
        const val FOOD_COUNT = 2

        /** The snake never shrinks below this many segments. */
        const val MIN_SNAKE_LENGTH = 3

        /** A grow streak survives if the next grow happens within this many ticks. */
        const val COMBO_WINDOW_TICKS = 45

        /** The score multiplier is capped here. */
        const val MAX_COMBO = 5

        /** Symbolic points for eating a shrinking food (standard / maxi). */
        const val SHRINK_POINTS = 5
        const val SHRINK_POINTS_MAXI = 10

        private const val MAX_SPAWN_ATTEMPTS = 200
    }
}

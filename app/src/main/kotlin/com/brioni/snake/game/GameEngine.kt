package com.brioni.snake.game

import kotlin.math.abs
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
    fun setup(level: Level, board: BoardDimensions): GameState {
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
            foods = refill(state.board, state.snake, state.obstacles, emptyList()),
            status = GameStatus.Running,
        )
    }

    /** Convenience: a fresh, already-running game. */
    fun newGame(level: Level, board: BoardDimensions): GameState = start(setup(level, board))

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

    /**
     * Turns the snake 90° left/right relative to its committed heading. Used by
     * the two-button controls. Routed through [changeDirection] so the same
     * buffering and reversal-safety rules apply; turning relative to the
     * committed (not pending) direction also makes two quick taps within one
     * tick safe — the second is validated against the still-committed heading.
     */
    fun turnLeft(state: GameState): GameState =
        changeDirection(state, state.direction.turnedLeft)

    fun turnRight(state: GameState): GameState =
        changeDirection(state, state.direction.turnedRight)

    /** Toggles between [GameStatus.Running] and [GameStatus.Paused]. */
    fun togglePause(state: GameState): GameState = when (state.status) {
        GameStatus.Running -> state.copy(status = GameStatus.Paused)
        GameStatus.Paused -> state.copy(status = GameStatus.Running)
        else -> state
    }

    /**
     * Advances the simulation by one step. No-op unless [GameStatus.Running].
     *
     * Order mirrors v1.0.0: commit the buffered direction, move the head, settle
     * eating/growth (adjusting the tail), refill food, then test for death.
     */
    fun tick(state: GameState): GameState {
        if (state.status != GameStatus.Running) return state

        val direction = state.pendingDirection
        val newHead = state.head.step(direction)

        val body = ArrayList<Position>(state.snake.size + 1)
        body.add(newHead)
        body.addAll(state.snake)

        var foods = state.foods
        var score = state.score
        var pendingGrowth = state.pendingGrowth

        val eaten = foods.firstOrNull { it.occupies(newHead) }
        when {
            eaten != null -> {
                score += eaten.growth * 10
                pendingGrowth += eaten.growth - 1
                foods = foods - eaten
            }
            pendingGrowth > 0 -> pendingGrowth--
            else -> body.removeAt(body.lastIndex) // keep length: drop the tail
        }

        // Maintain the board's food count, avoiding the just-moved snake.
        if (eaten != null) {
            foods = refill(state.board, body, state.obstacles, foods)
        }

        val dead = isOutOfBounds(newHead, state.board) ||
            newHead in state.obstacles ||
            collidesWithBody(newHead, body)

        return state.copy(
            snake = body,
            direction = direction,
            foods = foods,
            score = score,
            pendingGrowth = pendingGrowth,
            status = if (dead) GameStatus.GameOver else GameStatus.Running,
        )
    }

    private fun isOutOfBounds(cell: Position, board: BoardDimensions): Boolean =
        cell.x < 0 || cell.x >= board.width || cell.y < 0 || cell.y >= board.height

    /** The head (index 0) hitting any other body cell. */
    private fun collidesWithBody(head: Position, body: List<Position>): Boolean {
        for (i in 1 until body.size) {
            if (body[i] == head) return true
        }
        return false
    }

    /**
     * Lays out obstacles with **4-fold symmetry**: cells are sampled in the
     * top-left quadrant and mirrored across the vertical and horizontal axes
     * (`x → w-1-x`, `y → h-1-y`) so the field looks deliberate rather than
     * random. Two rows/columns next to every border are kept clear, and a zone
     * around the centre (where the snake spawns) is excluded so a game never
     * ends on the first ticks. Deterministic given the injected [random].
     */
    private fun generateObstacles(
        level: Level,
        board: BoardDimensions,
        snake: List<Position>,
    ): Set<Position> {
        if (level.obstacleCount == 0) return emptySet()

        val w = board.width
        val h = board.height
        // Inclusive sampling bounds: keep two cells clear by each border.
        val minX = OBSTACLE_MARGIN
        val minY = OBSTACLE_MARGIN
        val maxXExclusive = (w + 1) / 2 // seed quadrant spans up to the centre
        val maxYExclusive = (h + 1) / 2
        if (maxXExclusive <= minX || maxYExclusive <= minY) return emptySet()

        val cx = (w - 1) / 2f
        val cy = (h - 1) / 2f
        val clearRadiusX = (w * CENTER_CLEAR_FRACTION).coerceAtLeast(2f)
        val clearRadiusY = (h * CENTER_CLEAR_FRACTION).coerceAtLeast(3f)

        val snakeCells = snake.toHashSet()
        val obstacles = LinkedHashSet<Position>()
        val perQuadrant = (level.obstacleCount + 3) / 4 // ceil — four mirrors each
        val targetCount = perQuadrant * 4
        var attempts = 0
        val maxAttempts = perQuadrant * 40

        while (obstacles.size < targetCount && attempts < maxAttempts) {
            attempts++
            val x = random.nextInt(minX, maxXExclusive)
            val y = random.nextInt(minY, maxYExclusive)
            // Keep the spawn area around the centre clear.
            if (abs(x - cx) <= clearRadiusX && abs(y - cy) <= clearRadiusY) continue
            val mirrored = listOf(
                Position(x, y),
                Position(w - 1 - x, y),
                Position(x, h - 1 - y),
                Position(w - 1 - x, h - 1 - y),
            )
            if (mirrored.any { it in snakeCells }) continue
            obstacles.addAll(mirrored)
        }
        return obstacles
    }

    /** Tops the board up to [FOOD_COUNT] items, skipping if no cell is free. */
    private fun refill(
        board: BoardDimensions,
        snake: List<Position>,
        obstacles: Set<Position>,
        existing: List<Food>,
    ): List<Food> {
        var foods = existing
        while (foods.size < FOOD_COUNT) {
            val food = spawnFood(board, snake, obstacles, foods) ?: break
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
        board: BoardDimensions,
        snake: List<Position>,
        obstacles: Set<Position>,
        existing: List<Food>,
    ): Food? {
        val spec = FoodTable.roll(random)
        val span = spec.type.cellSpan
        // Top-left cell range that keeps the whole square off the border.
        val maxX = board.width - span
        val maxY = board.height - span
        if (maxX <= 1 || maxY <= 1) return null

        val occupied = HashSet<Position>()
        occupied.addAll(snake)
        occupied.addAll(obstacles)
        existing.forEach { occupied.addAll(it.cells()) }

        fun candidateAt(x: Int, y: Int): Food? {
            val food = Food(Position(x, y), spec.type, spec.growth)
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
        private const val MAX_SPAWN_ATTEMPTS = 200

        /** Rows/columns kept clear next to every border for symmetric obstacles. */
        private const val OBSTACLE_MARGIN = 2

        /** Half-extent of the central spawn clear-zone, as a fraction of the board. */
        private const val CENTER_CLEAR_FRACTION = 0.18f
    }
}

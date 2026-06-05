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
            foods = refill(state.board, state.snake, state.obstacles, emptyList(), elapsedTicks = 0, level = state.level),
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
     * Order: age the timed effects and debris by the elapsed (effective) tick
     * interval; commit the buffered direction and move the head (wrapping while a
     * Ghost effect is active); apply the eaten food's [FoodEffect] — grow/shrink
     * or a Phase 6.2 special (earthquake, explosion, speed, ghost, freeze,
     * jackpot); refill food; then test for death (skipped under Ghost, lethal on
     * debris). Every consequence is emitted as a [GameEvent] in
     * [GameState.lastEvents].
     *
     * @param hazardsEnabled gates whether harmful specials can spawn (a setting).
     */
    fun tick(state: GameState, hazardsEnabled: Boolean = true): GameState {
        if (state.status != GameStatus.Running) return state

        val elapsedTicks = state.elapsedTicks + 1
        // The wall-clock that just elapsed = the interval the loop delayed by.
        val elapsedMs = state.tickIntervalMillis
        val direction = state.pendingDirection
        val events = ArrayList<GameEvent>(3)

        // Age timed effects; expired ones fire an event and drop out.
        var effectTimers = state.effectTimers.map { it.copy(remainingMs = it.remainingMs - elapsedMs) }
        effectTimers.filter { it.remainingMs <= 0 }.forEach { events.add(GameEvent.EffectExpired(it.kind)) }
        effectTimers = effectTimers.filter { it.remainingMs > 0 }

        // Age debris; expired blocks auto-clear.
        var debris = state.debris.mapNotNull { d ->
            val remaining = d.remainingMs - elapsedMs
            if (remaining > 0) d.copy(remainingMs = remaining) else null
        }

        val ghost = effectTimers.any { it.kind == EffectKind.Ghost }
        val board = state.board
        val stepped = state.head.step(direction)
        val newHead = if (ghost) {
            Position((stepped.x + board.width) % board.width, (stepped.y + board.height) % board.height)
        } else {
            stepped
        }

        var body = ArrayList<Position>(state.snake.size + 1)
        body.add(newHead)
        body.addAll(state.snake)

        var foods = state.foods
        var score = state.score
        var pendingGrowth = state.pendingGrowth
        var combo = state.combo
        var comboDeadlineTick = state.comboDeadlineTick

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
                    val removed = trimTail(body, effect.segments)
                    val points = if (eaten.size == FoodSize.Maxi) SHRINK_POINTS_MAXI else SHRINK_POINTS
                    score += points
                    events.add(GameEvent.Shrunk(eaten, removed, points))
                }
                is FoodEffect.Quake -> {
                    // Earthquake: bite a chunk off the tail and shake the screen.
                    pendingGrowth = 0
                    val removed = trimTail(body, effect.segments)
                    events.add(GameEvent.Quaked(eaten, removed))
                }
                is FoodEffect.Burst -> {
                    // Explosion: split the snake; the detached tail becomes lethal
                    // debris that auto-clears after its timer.
                    pendingGrowth = 0
                    val splitIndex = (body.size / 2).coerceIn(MIN_SNAKE_LENGTH, body.size)
                    val detached = if (splitIndex < body.size) body.subList(splitIndex, body.size).toList() else emptyList()
                    body = ArrayList(body.subList(0, splitIndex))
                    debris = debris + detached.map { Debris(it, effect.debrisMs, effect.debrisMs) }
                    events.add(GameEvent.Exploded(eaten, detached))
                }
                is FoodEffect.Haste -> {
                    body.removeAt(body.lastIndex) // pure effect: keep length
                    effectTimers = addOrRefresh(effectTimers, EffectKind.Haste, effect.durationMs)
                    events.add(GameEvent.EffectStarted(EffectKind.Haste, eaten))
                }
                is FoodEffect.Slow -> {
                    body.removeAt(body.lastIndex)
                    effectTimers = addOrRefresh(effectTimers, EffectKind.Slow, effect.durationMs)
                    events.add(GameEvent.EffectStarted(EffectKind.Slow, eaten))
                }
                is FoodEffect.Ghost -> {
                    body.removeAt(body.lastIndex)
                    effectTimers = addOrRefresh(effectTimers, EffectKind.Ghost, effect.durationMs)
                    events.add(GameEvent.EffectStarted(EffectKind.Ghost, eaten))
                }
                is FoodEffect.Freeze -> {
                    body.removeAt(body.lastIndex)
                    effectTimers = addOrRefresh(effectTimers, EffectKind.Freeze, effect.durationMs)
                    events.add(GameEvent.EffectStarted(EffectKind.Freeze, eaten))
                }
                is FoodEffect.Jackpot -> {
                    score += effect.bonus
                    pendingGrowth += effect.growth
                    events.add(GameEvent.JackpotHit(eaten, effect.bonus, effect.growth))
                }
            }
            val freezeActive = effectTimers.any { it.kind == EffectKind.Freeze }
            val specialOnBoard = foods.any { it.category == FoodCategory.Special }
            foods = refill(
                board, body, state.obstacles, foods, elapsedTicks, state.level,
                hazardsEnabled = hazardsEnabled,
                specialAllowed = !specialOnBoard && !freezeActive,
            )
        } else if (pendingGrowth > 0) {
            pendingGrowth--
        } else {
            body.removeAt(body.lastIndex) // keep length: drop the tail
        }

        val dead = !ghost && (
            isOutOfBounds(newHead, board) ||
                newHead in state.obstacles ||
                collidesWithBody(newHead, body) ||
                debris.any { it.cell == newHead }
            )
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
            debris = debris,
            effectTimers = effectTimers,
            lastEvents = events,
            status = if (dead) GameStatus.GameOver else GameStatus.Running,
        )
    }

    /** Drops up to [segments] tail cells, never below the length floor; returns how many went. */
    private fun trimTail(body: MutableList<Position>, segments: Int): Int {
        val removable = (body.size - MIN_SNAKE_LENGTH).coerceAtLeast(0)
        val removed = segments.coerceAtMost(removable)
        repeat(removed) { body.removeAt(body.lastIndex) }
        return removed
    }

    /** Restarts a [kind] timer at its full [durationMs] (one instance per kind). */
    private fun addOrRefresh(timers: List<ActiveEffect>, kind: EffectKind, durationMs: Long): List<ActiveEffect> =
        timers.filter { it.kind != kind } + ActiveEffect(kind, durationMs, durationMs)

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
        elapsedTicks: Int,
        level: Level,
        hazardsEnabled: Boolean = true,
        specialAllowed: Boolean = true,
    ): List<Food> {
        var foods = existing
        while (foods.size < FOOD_COUNT) {
            // A special is allowed only if one isn't already on the board.
            val allowSpecial = specialAllowed && foods.none { it.category == FoodCategory.Special }
            val food = spawnFood(board, snake, obstacles, foods, elapsedTicks, level, hazardsEnabled, allowSpecial) ?: break
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
        elapsedTicks: Int,
        level: Level,
        hazardsEnabled: Boolean,
        specialAllowed: Boolean,
    ): Food? {
        val spec = FoodTable.roll(random, elapsedTicks, level, hazardsEnabled, specialAllowed)
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

        /** Rows/columns kept clear next to every border for symmetric obstacles. */
        private const val OBSTACLE_MARGIN = 2

        /** Half-extent of the central spawn clear-zone, as a fraction of the board. */
        private const val CENTER_CLEAR_FRACTION = 0.18f
    }
}

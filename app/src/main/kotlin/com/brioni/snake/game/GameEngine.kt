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
     * [level], no food yet. The menu is shown over this until [start]. In
     * [GameMode.Levels] the random obstacles are replaced by the first level's
     * designed wall shape and the lives stock is filled.
     */
    fun setup(
        level: Level,
        board: BoardDimensions,
        mode: GameMode = GameMode.Endless,
        snakeSpeed: SnakeSpeed = SnakeSpeed.DEFAULT,
    ): GameState {
        val snake = startingSnake(board)
        val isLevels = mode == GameMode.Levels
        return GameState(
            board = board,
            level = level,
            snakeSpeed = snakeSpeed,
            snake = snake,
            direction = Direction.Up,
            pendingDirection = Direction.Up,
            foods = emptyList(),
            obstacles = if (isLevels) emptySet() else generateObstacles(level, board, snake),
            score = 0,
            pendingGrowth = 0,
            status = GameStatus.Ready,
            mode = mode,
            lives = if (isLevels) LevelsMode.START_LIVES else 0,
            walls = if (isLevels) LevelsMode.shapeFor(1, board) else emptySet(),
        )
    }

    /**
     * Transitions a [GameStatus.Ready] state into a running game with food.
     * In [GameMode.Levels] it lands on [GameStatus.LevelIntro] instead (still
     * without food) so the intro countdown can play; [beginLevel] follows.
     */
    fun start(state: GameState): GameState {
        if (state.status != GameStatus.Ready) return state
        if (state.mode == GameMode.Levels) return state.copy(status = GameStatus.LevelIntro)
        return state.copy(
            foods = refill(
                state.board, state.snake, state.obstacles, state.walls, emptyList(),
                elapsedTicks = 0, level = state.level, baseTickMillis = state.snakeSpeed.tickMillis,
                mode = state.mode,
            ),
            status = GameStatus.Running,
        )
    }

    /**
     * Levels mode: leaves [GameStatus.LevelIntro] (after the countdown ran in
     * the ViewModel), seeding food onto the staged board and starting the loop.
     */
    fun beginLevel(state: GameState): GameState {
        if (state.status != GameStatus.LevelIntro) return state
        return state.copy(
            foods = refill(
                state.board, state.snake, state.obstacles, state.walls, emptyList(),
                elapsedTicks = state.elapsedTicks, level = state.level,
                baseTickMillis = state.snakeSpeed.tickMillis, mode = state.mode,
            ),
            status = GameStatus.Running,
        )
    }

    /** The centred three-cell spawn (heading Up) shared by setup and Levels resets. */
    private fun startingSnake(board: BoardDimensions): List<Position> {
        val cx = board.width / 2
        val cy = board.height / 2
        return listOf(
            Position(cx, cy),
            Position(cx, cy + 1),
            Position(cx, cy + 2),
        )
    }

    /** Convenience: a fresh, already-running game. */
    fun newGame(
        level: Level,
        board: BoardDimensions,
        mode: GameMode = GameMode.Endless,
        snakeSpeed: SnakeSpeed = SnakeSpeed.DEFAULT,
    ): GameState =
        start(setup(level, board, mode, snakeSpeed))

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
     * @param specialFrequency how often specials appear (a setting).
     */
    fun tick(
        state: GameState,
        hazardsEnabled: Boolean = true,
        specialFrequency: SpecialFrequency = SpecialFrequency.Standard,
    ): GameState {
        if (state.status != GameStatus.Running) return state

        val elapsedTicks = state.elapsedTicks + 1
        // The wall-clock that just elapsed = the interval the loop delayed by.
        val elapsedMs = state.tickIntervalMillis
        val playedMs = state.playedMs + elapsedMs
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
        var timeAdjustMs = state.timeAdjustMs
        var lives = state.lives
        var levelFoodsEaten = state.levelFoodsEaten

        val eaten = foods.firstOrNull { it.occupies(newHead) }
        if (eaten != null) {
            foods = foods - eaten
            // Levels: every eaten food counts toward the level's food goal —
            // except the extra-life bonus, which is a pure gift: eating it must
            // never be the bite that triggers a level transition.
            if (state.mode == GameMode.Levels && eaten.effect !is FoodEffect.ExtraLife) levelFoodsEaten++
            when (val effect = eaten.effect) {
                is FoodEffect.Grow -> {
                    // A fresh streak, or extend the running one if still in time.
                    combo = if (elapsedTicks <= comboDeadlineTick) combo + 1 else 1
                    comboDeadlineTick = elapsedTicks + COMBO_WINDOW_TICKS
                    // Longer snakes earn proportionally more per bite (up to a cap),
                    // so the same food is worth far more late in a run than early on.
                    val points = (effect.segments * 10 * combo.coerceAtMost(MAX_COMBO) * lengthScoreFactor(body.size)).toInt()
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
                    // Earthquake: a pure-disruption malus. No tail bite and no
                    // debris - just a sustained screen shake (a timed effect the
                    // UI reads) that makes the board hard to read while it runs.
                    body.removeAt(body.lastIndex) // pure effect: keep length
                    effectTimers = addOrRefresh(effectTimers, EffectKind.Quake, effect.durationMs)
                    events.add(GameEvent.EffectStarted(EffectKind.Quake, eaten))
                }
                is FoodEffect.Burst -> {
                    // Explosion: split the snake; the detached tail becomes lethal
                    // debris that auto-clears after its timer.
                    pendingGrowth = 0
                    // Sever only the last third: the snake keeps two-thirds, and
                    // the detached tail lingers as lethal debris (long timer).
                    val splitIndex = (body.size * 2 / 3).coerceIn(MIN_SNAKE_LENGTH, body.size)
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
                is FoodEffect.TimeBonus -> {
                    // Time Attack: extend the clock. Pure effect — keep length.
                    body.removeAt(body.lastIndex)
                    timeAdjustMs += effect.seconds * 1000L
                    events.add(GameEvent.TimeGained(eaten, effect.seconds))
                }
                is FoodEffect.TimePenalty -> {
                    // Time Attack: shorten the clock. Pure effect — keep length.
                    body.removeAt(body.lastIndex)
                    timeAdjustMs -= effect.seconds * 1000L
                    events.add(GameEvent.TimeLost(eaten, effect.seconds))
                }
                is FoodEffect.ExtraLife -> {
                    // Levels: bank a life, or pay points once the stock is full.
                    // Pure effect — keep length.
                    body.removeAt(body.lastIndex)
                    val capped = lives >= LevelsMode.MAX_LIVES
                    if (capped) score += LevelsMode.LIFE_CAP_BONUS else lives++
                    events.add(GameEvent.LifeGained(eaten, lives, capped))
                }
            }
        } else if (pendingGrowth > 0) {
            pendingGrowth--
        } else {
            body.removeAt(body.lastIndex) // keep length: drop the tail
        }

        // Vanish the single oldest food that has sat uneaten too long, so looping
        // without eating keeps the board fresh. Specials linger much longer than
        // regular food (they are rare events worth waiting for) but no longer stay
        // forever. One per tick keeps the bursts staggered and avoids both foods
        // popping at once when they were seeded on the same tick.
        //
        // Bigger boards give items proportionally more time before they vanish, so
        // the snake can cross the extra distance to reach them: the base lifetimes
        // are scaled by the board's short side relative to the reference board.
        val vanishScale = minOf(board.width, board.height).toDouble() / VANISH_REFERENCE_SHORT_SIDE
        val baseTickMs = state.snakeSpeed.tickMillis
        val regularVanishTicks = (VANISH_FOOD_MS * vanishScale / baseTickMs).toInt().coerceAtLeast(1)
        val specialVanishTicks = (VANISH_SPECIAL_MS * vanishScale / baseTickMs).toInt().coerceAtLeast(1)
        fun vanishTicksFor(f: Food): Int =
            if (f.category == FoodCategory.Special) specialVanishTicks else regularVanishTicks
        val stale = foods
            .filter { elapsedTicks - it.spawnTick >= vanishTicksFor(it) }
            .maxByOrNull { elapsedTicks - it.spawnTick }
        if (stale != null) {
            foods = foods - stale
            events.add(GameEvent.FoodVanished(stale))
        }

        // Top the board back up — covers both an eaten food and a vanished one.
        if (foods.size < FOOD_COUNT) {
            val freezeActive = effectTimers.any { it.kind == EffectKind.Freeze }
            val specialsOnBoard = foods.count { it.category == FoodCategory.Special }
            foods = refill(
                board, body, state.obstacles, state.walls, foods, elapsedTicks, state.level,
                baseTickMillis = baseTickMs,
                hazardsEnabled = hazardsEnabled,
                specialAllowed = specialsOnBoard < MAX_SPECIALS_ON_BOARD && !freezeActive,
                specialFrequency = specialFrequency,
                mode = state.mode,
            )
        }

        // Levels: the food goal advances to the next staged level. Checked
        // before the death test — the completing eat wins over a same-tick
        // crash, since the snake is reset to the spawn anyway.
        if (state.mode == GameMode.Levels && levelFoodsEaten >= LevelsMode.LEVEL_FOOD_GOAL) {
            val nextIndex = state.levelIndex % LevelsMode.LEVEL_COUNT + 1
            val nextCycle = if (state.levelIndex == LevelsMode.LEVEL_COUNT) state.speedCycle + 1 else state.speedCycle
            events.add(GameEvent.LevelAdvanced(nextIndex, nextCycle))
            return stageLevel(
                state, events,
                levelIndex = nextIndex, speedCycle = nextCycle, lives = lives,
                score = score, elapsedTicks = elapsedTicks, playedMs = playedMs,
            )
        }

        // Time Attack ends when the clock runs out, regardless of collisions.
        // The budget shifts with time-bonus / time-penalty blocks (timeAdjustMs).
        val timeUp = state.mode == GameMode.TimeAttack &&
            (playedMs - timeAdjustMs) >= GameState.TIME_ATTACK_MS
        val crashed = !ghost && (
            isOutOfBounds(newHead, board) ||
                newHead in state.obstacles ||
                newHead in state.walls ||
                collidesWithBody(newHead, body) ||
                debris.any { it.cell == newHead }
            )

        // Levels: a crash with lives to spare consumes one and restages the
        // same level (score and food progress kept) instead of ending the run.
        if (crashed && state.mode == GameMode.Levels && lives > 1) {
            events.add(GameEvent.LifeLost(lives - 1))
            return stageLevel(
                state, events,
                levelIndex = state.levelIndex, speedCycle = state.speedCycle, lives = lives - 1,
                score = score, elapsedTicks = elapsedTicks, playedMs = playedMs,
                levelFoodsEaten = levelFoodsEaten,
            )
        }

        val dead = crashed || timeUp
        if (dead) events.add(GameEvent.Died)

        // Near-miss: the head survived but is grazing a static hazard. Skipped
        // while invincible (Ghost passes through everything anyway).
        if (!dead && !ghost && isNearMiss(newHead, board, state.obstacles, state.walls, debris)) {
            events.add(GameEvent.NearMiss)
        }

        return state.copy(
            snake = body,
            direction = direction,
            foods = foods,
            score = score,
            pendingGrowth = pendingGrowth,
            elapsedTicks = elapsedTicks,
            playedMs = playedMs,
            combo = combo,
            comboDeadlineTick = comboDeadlineTick,
            debris = debris,
            effectTimers = effectTimers,
            timeAdjustMs = timeAdjustMs,
            lives = if (dead && crashed && state.mode == GameMode.Levels) 0 else lives,
            levelFoodsEaten = levelFoodsEaten,
            lastEvents = events,
            status = if (dead) GameStatus.GameOver else GameStatus.Running,
        )
    }

    /**
     * Levels mode: stages a fresh board for [levelIndex] — new wall shape,
     * snake back at the spawn, everything transient cleared — landing on
     * [GameStatus.LevelIntro] for the countdown. Score, [elapsedTicks] (which
     * drives the food-progression gates across the whole run) and the clock
     * are carried over; [levelFoodsEaten] restarts unless a life loss restages
     * the same level mid-goal.
     */
    private fun stageLevel(
        state: GameState,
        events: List<GameEvent>,
        levelIndex: Int,
        speedCycle: Int,
        lives: Int,
        score: Int,
        elapsedTicks: Int,
        playedMs: Long,
        levelFoodsEaten: Int = 0,
    ): GameState = state.copy(
        snake = startingSnake(state.board),
        direction = Direction.Up,
        pendingDirection = Direction.Up,
        foods = emptyList(),
        score = score,
        pendingGrowth = 0,
        elapsedTicks = elapsedTicks,
        playedMs = playedMs,
        combo = 0,
        comboDeadlineTick = 0,
        debris = emptyList(),
        effectTimers = emptyList(),
        levelIndex = levelIndex,
        speedCycle = speedCycle,
        lives = lives,
        levelFoodsEaten = levelFoodsEaten,
        walls = LevelsMode.shapeFor(levelIndex, state.board),
        lastEvents = events,
        status = GameStatus.LevelIntro,
    )

    /** Drops up to [segments] tail cells, never below the length floor; returns how many went. */
    private fun trimTail(body: MutableList<Position>, segments: Int): Int {
        val removable = (body.size - MIN_SNAKE_LENGTH).coerceAtLeast(0)
        val removed = segments.coerceAtMost(removable)
        repeat(removed) { body.removeAt(body.lastIndex) }
        return removed
    }

    /**
     * Grow-score multiplier from the current snake [length]. Ramps from 1x for a
     * short snake up to [MAX_LENGTH_FACTOR] for a very long one, so a bite is
     * worth a lot more late in a run than at the start.
     */
    private fun lengthScoreFactor(length: Int): Float =
        (1f + (length - LENGTH_FACTOR_START) / LENGTH_FACTOR_STEP).coerceIn(1f, MAX_LENGTH_FACTOR)

    /** Restarts a [kind] timer at its full [durationMs] (one instance per kind). */
    private fun addOrRefresh(timers: List<ActiveEffect>, kind: EffectKind, durationMs: Long): List<ActiveEffect> =
        timers.filter { it.kind != kind } + ActiveEffect(kind, durationMs, durationMs)

    private fun isOutOfBounds(cell: Position, board: BoardDimensions): Boolean =
        cell.x < 0 || cell.x >= board.width || cell.y < 0 || cell.y >= board.height

    /**
     * True when any orthogonal neighbour of [head] is a static lethal cell - the
     * board edge, an obstacle, a level wall or lingering debris. The snake's own
     * body is intentionally not considered (coiling beside yourself is normal).
     */
    private fun isNearMiss(
        head: Position,
        board: BoardDimensions,
        obstacles: Set<Position>,
        walls: Set<Position>,
        debris: List<Debris>,
    ): Boolean = Direction.entries.any { dir ->
        val n = head.step(dir)
        isOutOfBounds(n, board) || n in obstacles || n in walls || debris.any { it.cell == n }
    }

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
     * random. After the first cell, each new cell grows orthogonally out of an
     * already-placed one with probability [OBSTACLE_CLUSTER_BIAS], so blocks
     * tend to clump into larger shapes instead of scattering as singletons.
     * Two rows/columns next to every border are kept clear, and a zone
     * around the centre (where the snake spawns) is excluded so a game never
     * ends on the first ticks. Deterministic given the injected [random].
     */
    private fun generateObstacles(
        level: Level,
        board: BoardDimensions,
        snake: List<Position>,
    ): Set<Position> {
        // Scaled with the board's area so density stays constant across scales.
        val obstacleCount = obstacleCountFor(level, board)
        if (obstacleCount == 0) return emptySet()

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
        val seedCells = ArrayList<Position>() // quadrant cells placed so far
        val perQuadrant = (obstacleCount + 3) / 4 // ceil — four mirrors each
        val targetCount = perQuadrant * 4
        var attempts = 0
        val maxAttempts = perQuadrant * 40

        while (obstacles.size < targetCount && attempts < maxAttempts) {
            attempts++
            val x: Int
            val y: Int
            if (seedCells.isNotEmpty() && random.nextFloat() < OBSTACLE_CLUSTER_BIAS) {
                // Grow a cluster: step one cell out of an existing seed.
                val base = seedCells[random.nextInt(seedCells.size)]
                val side = random.nextInt(4)
                x = base.x + if (side == 0) -1 else if (side == 1) 1 else 0
                y = base.y + if (side == 2) -1 else if (side == 3) 1 else 0
                if (x < minX || x >= maxXExclusive || y < minY || y >= maxYExclusive) continue
            } else {
                x = random.nextInt(minX, maxXExclusive)
                y = random.nextInt(minY, maxYExclusive)
            }
            // Keep the spawn area around the centre clear.
            if (abs(x - cx) <= clearRadiusX && abs(y - cy) <= clearRadiusY) continue
            val cell = Position(x, y)
            if (cell in obstacles) continue
            val mirrored = listOf(
                cell,
                Position(w - 1 - x, y),
                Position(x, h - 1 - y),
                Position(w - 1 - x, h - 1 - y),
            )
            if (mirrored.any { it in snakeCells }) continue
            obstacles.addAll(mirrored)
            seedCells.add(cell)
        }
        return obstacles
    }

    /** Tops the board up to [FOOD_COUNT] items, skipping if no cell is free. */
    private fun refill(
        board: BoardDimensions,
        snake: List<Position>,
        obstacles: Set<Position>,
        walls: Set<Position>,
        existing: List<Food>,
        elapsedTicks: Int,
        level: Level,
        baseTickMillis: Long = SnakeSpeed.DEFAULT.tickMillis,
        hazardsEnabled: Boolean = true,
        specialAllowed: Boolean = true,
        specialFrequency: SpecialFrequency = SpecialFrequency.Standard,
        mode: GameMode = GameMode.Endless,
    ): List<Food> {
        var foods = existing
        while (foods.size < FOOD_COUNT) {
            // A special is allowed only while fewer than the cap are on the board.
            val allowSpecial = specialAllowed &&
                foods.count { it.category == FoodCategory.Special } < MAX_SPECIALS_ON_BOARD
            val food = spawnFood(board, snake, obstacles, walls, foods, elapsedTicks, level, baseTickMillis, hazardsEnabled, allowSpecial, specialFrequency, mode) ?: break
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
        walls: Set<Position>,
        existing: List<Food>,
        elapsedTicks: Int,
        level: Level,
        baseTickMillis: Long,
        hazardsEnabled: Boolean,
        specialAllowed: Boolean,
        specialFrequency: SpecialFrequency,
        mode: GameMode,
    ): Food? {
        val spec = FoodTable.roll(random, elapsedTicks, level, baseTickMillis, hazardsEnabled, specialAllowed, specialFrequency, mode)
        val span = spec.size.cellSpan
        // Top-left cell range that keeps the whole square off the border.
        val maxX = board.width - span
        val maxY = board.height - span
        if (maxX <= 1 || maxY <= 1) return null

        val occupied = HashSet<Position>()
        occupied.addAll(snake)
        occupied.addAll(obstacles)
        occupied.addAll(walls)
        existing.forEach { occupied.addAll(it.cells()) }

        fun candidateAt(x: Int, y: Int): Food? {
            val food = Food(Position(x, y), spec.category, spec.tier, spec.size, spec.effect, spawnTick = elapsedTicks)
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
        /** Foods kept on the board at once. */
        const val FOOD_COUNT = 3

        /** At most this many specials (power-ups / hazards) may share the board. */
        const val MAX_SPECIALS_ON_BOARD = 2

        /** The snake never shrinks below this many segments. */
        const val MIN_SNAKE_LENGTH = 3

        /** A grow streak survives if the next grow happens within this many ticks. */
        const val COMBO_WINDOW_TICKS = 45

        /** The score multiplier is capped here. */
        const val MAX_COMBO = 5

        /** Symbolic points for eating a shrinking food (standard / maxi). */
        const val SHRINK_POINTS = 5
        const val SHRINK_POINTS_MAXI = 10

        /**
         * Grow-score length scaling: the multiplier is 1x at [LENGTH_FACTOR_START]
         * segments and climbs by 1 per [LENGTH_FACTOR_STEP] extra segments, capped
         * at [MAX_LENGTH_FACTOR] (reached around length 81 with these values).
         */
        const val LENGTH_FACTOR_START = 5f
        const val LENGTH_FACTOR_STEP = 19f
        const val MAX_LENGTH_FACTOR = 5f

        /**
         * How long an uneaten *regular* food survives before it vanishes and is
         * replaced elsewhere. Measured at the level's base pace (converted to
         * ticks per level), so it stays ~7 s regardless of board size.
         */
        const val VANISH_FOOD_MS = 7_000L

        /**
         * How long an uneaten *special* (power-up / hazard) survives before it
         * vanishes. Much longer than [VANISH_FOOD_MS] — specials are rare events
         * the player should have a fair chance to reach — but no longer infinite.
         */
        const val VANISH_SPECIAL_MS = 14_000L

        /**
         * The board short-side (in cells) the base vanish times are tuned for
         * (the Standard scale). Larger boards scale the lifetimes up in
         * proportion to their short side so items stay reachable.
         */
        const val VANISH_REFERENCE_SHORT_SIDE = 19

        private const val MAX_SPAWN_ATTEMPTS = 200

        /** Rows/columns kept clear next to every border for symmetric obstacles. */
        private const val OBSTACLE_MARGIN = 2

        /**
         * Probability that the next obstacle cell grows adjacent to one already
         * placed (instead of being sampled uniformly), clumping blocks together.
         */
        private const val OBSTACLE_CLUSTER_BIAS = 0.6f

        /** Half-extent of the central spawn clear-zone, as a fraction of the board. */
        private const val CENTER_CLEAR_FRACTION = 0.18f
    }
}

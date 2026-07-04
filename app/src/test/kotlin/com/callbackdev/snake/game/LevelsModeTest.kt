package com.callbackdev.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Rules of [GameMode.Levels]: food-goal level advancement, the lives stock with
 * same-level respawns, lethal wall shapes and the extra-life special. States
 * are built directly (one food a step ahead of the head) so each rule is
 * exercised deterministically.
 */
class LevelsModeTest {

    private val engine = GameEngine(Random(7))
    private val board = BoardDimensions(18, 26)

    /** Head at (5,5) heading Right; foods are placed at (6,5) to be eaten next tick. */
    private fun levelsState(
        snake: List<Position> = listOf(Position(5, 5), Position(4, 5), Position(3, 5)),
        direction: Direction = Direction.Right,
        foods: List<Food> = emptyList(),
        walls: Set<Position> = emptySet(),
        effectTimers: List<ActiveEffect> = emptyList(),
        lives: Int = LevelsMode.START_LIVES,
        levelIndex: Int = 1,
        speedCycle: Int = 1,
        levelFoodsEaten: Int = 0,
        score: Int = 0,
        elapsedTicks: Int = 0,
        status: GameStatus = GameStatus.Running,
    ) = GameState(
        board = board,
        level = LevelsMode.SCORE_LEVEL,
        snake = snake,
        direction = direction,
        pendingDirection = direction,
        foods = foods,
        obstacles = emptySet(),
        score = score,
        pendingGrowth = 0,
        status = status,
        mode = GameMode.Levels,
        elapsedTicks = elapsedTicks,
        effectTimers = effectTimers,
        levelIndex = levelIndex,
        speedCycle = speedCycle,
        lives = lives,
        levelFoodsEaten = levelFoodsEaten,
        walls = walls,
    )

    private fun growFoodAt(cell: Position) = Food(
        position = cell,
        category = FoodCategory.Grow,
        tier = FoodTier.Small,
        size = FoodSize.Standard,
        effect = FoodEffect.Grow(2),
    )

    private fun specialAt(cell: Position, effect: FoodEffect) = Food(
        position = cell,
        category = FoodCategory.Special,
        tier = FoodTier.Huge,
        size = FoodSize.Maxi,
        effect = effect,
    )

    private val spawnHead = Position(board.width / 2, board.height / 2)

    // --- Setup / start / beginLevel ----------------------------------------

    @Test
    fun `setup stocks lives and stages level 1 without random obstacles`() {
        val state = engine.setup(Level.Legend, board, GameMode.Levels)
        assertEquals(LevelsMode.START_LIVES, state.lives)
        assertEquals(1, state.levelIndex)
        assertEquals(1, state.speedCycle)
        assertTrue(state.obstacles.isEmpty()) // Legend would otherwise scatter 40
        assertEquals(LevelsMode.shapeFor(1, board), state.walls)
    }

    @Test
    fun `start lands on the intro and beginLevel seeds food and runs`() {
        val ready = engine.setup(LevelsMode.SCORE_LEVEL, board, GameMode.Levels)
        val intro = engine.start(ready)
        assertEquals(GameStatus.LevelIntro, intro.status)
        assertTrue(intro.foods.isEmpty())
        val running = engine.beginLevel(intro)
        assertEquals(GameStatus.Running, running.status)
        assertEquals(GameEngine.FOOD_COUNT, running.foods.size)
    }

    @Test
    fun `beginLevel only acts on the intro state`() {
        val running = levelsState()
        assertEquals(running, engine.beginLevel(running))
    }

    // --- Food goal & level advancement --------------------------------------

    @Test
    fun `every eaten food counts toward the level goal`() {
        val next = engine.tick(levelsState(foods = listOf(growFoodAt(Position(6, 5)))))
        assertEquals(1, next.levelFoodsEaten)
        // A tick without an eat leaves the count alone.
        assertEquals(1, engine.tick(next).levelFoodsEaten)
        // Specials count too (the extra-life is the one exception).
        val special = engine.tick(levelsState(foods = listOf(specialAt(Position(6, 5), FoodEffect.Haste(6_000)))))
        assertEquals(1, special.levelFoodsEaten)
    }

    @Test
    fun `meeting the food goal stages the next level`() {
        val state = levelsState(
            foods = listOf(growFoodAt(Position(6, 5))),
            levelFoodsEaten = LevelsMode.LEVEL_FOOD_GOAL - 1,
            score = 100,
            elapsedTicks = 500,
        )
        val next = engine.tick(state)
        assertEquals(GameStatus.LevelIntro, next.status)
        assertEquals(2, next.levelIndex)
        assertEquals(1, next.speedCycle)
        assertEquals(0, next.levelFoodsEaten)
        assertEquals(120, next.score) // the completing eat still pays its 20 points
        assertEquals(501, next.elapsedTicks) // food-gate progression carries over
        assertEquals(spawnHead, next.head) // snake back at the spawn
        assertEquals(Direction.Up, next.direction)
        assertTrue(next.foods.isEmpty())
        assertEquals(0, next.combo)
        assertEquals(LevelsMode.shapeFor(2, board), next.walls)
        val advanced = next.lastEvents.filterIsInstance<GameEvent.LevelAdvanced>().single()
        assertEquals(2, advanced.levelIndex)
        assertEquals(1, advanced.speedCycle)
    }

    @Test
    fun `clearing the last level wraps to level 1 at the next speed cycle`() {
        val state = levelsState(
            foods = listOf(growFoodAt(Position(6, 5))),
            walls = LevelsMode.shapeFor(LevelsMode.LEVEL_COUNT, board),
            levelIndex = LevelsMode.LEVEL_COUNT,
            levelFoodsEaten = LevelsMode.LEVEL_FOOD_GOAL - 1,
        )
        val next = engine.tick(state)
        assertEquals(1, next.levelIndex)
        assertEquals(2, next.speedCycle)
        assertEquals(LevelsMode.shapeFor(1, board), next.walls)
        assertTrue(next.lastEvents.any { it is GameEvent.LevelAdvanced })
    }

    @Test
    fun `clearing a mid-lap level advances within the same speed cycle`() {
        val state = levelsState(
            foods = listOf(growFoodAt(Position(6, 5))),
            walls = LevelsMode.shapeFor(10, board),
            levelIndex = 10,
            levelFoodsEaten = LevelsMode.LEVEL_FOOD_GOAL - 1,
        )
        val next = engine.tick(state)
        assertEquals(11, next.levelIndex)
        assertEquals(1, next.speedCycle)
        assertEquals(LevelsMode.shapeFor(11, board), next.walls)
        assertTrue(next.lastEvents.any { it is GameEvent.LevelAdvanced })
    }

    @Test
    fun `the levels pace follows the speed cycle not the difficulty`() {
        assertEquals(LevelsMode.BASE_TICK_MS, levelsState().tickIntervalMillis)
        val cycle3 = levelsState(speedCycle = 3)
        assertEquals(LevelsMode.tickMillisFor(3), cycle3.tickIntervalMillis)
        // The snake-speed setting's own pace (175 ms) is never consulted here.
        assertFalse(cycle3.tickIntervalMillis == SnakeSpeed.DEFAULT.tickMillis)
    }

    // --- Walls --------------------------------------------------------------

    @Test
    fun `wall cells are lethal like out-of-bounds`() {
        val state = levelsState(walls = setOf(Position(6, 5)), lives = 1)
        val next = engine.tick(state)
        assertEquals(GameStatus.GameOver, next.status)
        assertTrue(next.lastEvents.contains(GameEvent.Died))
    }

    @Test
    fun `ghost passes through walls`() {
        val state = levelsState(
            walls = setOf(Position(6, 5)),
            effectTimers = listOf(ActiveEffect(EffectKind.Ghost, 5_000, 5_000)),
        )
        val next = engine.tick(state)
        assertEquals(GameStatus.Running, next.status)
        assertEquals(Position(6, 5), next.head)
    }

    @Test
    fun `food never spawns on wall cells`() {
        // The Vault (level 10) has the densest wall set; seed food onto it from
        // many engines and late in a run so maxi (2x2) pieces are in the pool.
        val walls = LevelsMode.shapeFor(10, board)
        repeat(50) { seed ->
            val intro = levelsState(
                snake = listOf(spawnHead, Position(spawnHead.x, spawnHead.y + 1), Position(spawnHead.x, spawnHead.y + 2)),
                direction = Direction.Up,
                walls = walls,
                levelIndex = 10,
                elapsedTicks = 2_000,
                status = GameStatus.LevelIntro,
            )
            val running = GameEngine(Random(seed.toLong())).beginLevel(intro)
            assertEquals(GameEngine.FOOD_COUNT, running.foods.size)
            running.foods.forEach { food ->
                assertTrue("food at ${food.position} overlaps a wall", food.cells().none { it in walls })
            }
        }
    }

    // --- Lives --------------------------------------------------------------

    @Test
    fun `a crash with lives left respawns the same level keeping progress`() {
        val state = levelsState(
            walls = LevelsMode.shapeFor(5, board) + Position(6, 5),
            levelIndex = 5,
            lives = 3,
            levelFoodsEaten = 7,
            score = 240,
        )
        val next = engine.tick(state)
        assertEquals(GameStatus.LevelIntro, next.status)
        assertEquals(2, next.lives)
        assertEquals(5, next.levelIndex) // same level
        assertEquals(7, next.levelFoodsEaten) // goal progress kept
        assertEquals(240, next.score) // score kept
        assertEquals(spawnHead, next.head)
        assertTrue(next.foods.isEmpty())
        assertEquals(2, next.lastEvents.filterIsInstance<GameEvent.LifeLost>().single().remaining)
        assertFalse(next.lastEvents.contains(GameEvent.Died))
    }

    @Test
    fun `a crash on the last life is game over`() {
        val state = levelsState(walls = setOf(Position(6, 5)), lives = 1)
        val next = engine.tick(state)
        assertEquals(GameStatus.GameOver, next.status)
        assertEquals(0, next.lives)
        assertTrue(next.lastEvents.contains(GameEvent.Died))
        assertTrue(next.lastEvents.none { it is GameEvent.LifeLost })
    }

    @Test
    fun `body and debris crashes also consume lives`() {
        // Self-collision: the head turns back into its own body ring.
        val loop = listOf(
            Position(5, 5), Position(5, 6), Position(6, 6), Position(6, 5),
            Position(7, 5), Position(7, 6),
        )
        val selfHit = engine.tick(levelsState(snake = loop, direction = Direction.Right, lives = 2))
        assertEquals(GameStatus.LevelIntro, selfHit.status)
        assertEquals(1, selfHit.lives)
    }

    // --- Extra life ----------------------------------------------------------

    @Test
    fun `extra life banks a life and keeps the snake length`() {
        val state = levelsState(foods = listOf(specialAt(Position(6, 5), FoodEffect.ExtraLife)), lives = 3)
        val next = engine.tick(state)
        assertEquals(GameStatus.Running, next.status) // the run just continues
        assertEquals(4, next.lives)
        assertEquals(3, next.snake.size) // pure effect: no growth
        assertEquals(0, next.score)
        val gained = next.lastEvents.filterIsInstance<GameEvent.LifeGained>().single()
        assertEquals(4, gained.lives)
        assertFalse(gained.capped)
    }

    @Test
    fun `extra life never triggers a level transition`() {
        // Even with the food goal one bite away, the extra-life is a pure gift:
        // it does not count toward the goal and the game keeps running.
        val state = levelsState(
            foods = listOf(specialAt(Position(6, 5), FoodEffect.ExtraLife)),
            levelFoodsEaten = LevelsMode.LEVEL_FOOD_GOAL - 1,
            lives = 3,
        )
        val next = engine.tick(state)
        assertEquals(GameStatus.Running, next.status)
        assertEquals(1, next.levelIndex)
        assertEquals(LevelsMode.LEVEL_FOOD_GOAL - 1, next.levelFoodsEaten)
        assertEquals(4, next.lives)
        assertTrue(next.lastEvents.none { it is GameEvent.LevelAdvanced })
    }

    @Test
    fun `extra life past the cap pays points instead`() {
        val state = levelsState(
            foods = listOf(specialAt(Position(6, 5), FoodEffect.ExtraLife)),
            lives = LevelsMode.MAX_LIVES,
        )
        val next = engine.tick(state)
        assertEquals(LevelsMode.MAX_LIVES, next.lives)
        assertEquals(LevelsMode.LIFE_CAP_BONUS, next.score)
        assertTrue(next.lastEvents.filterIsInstance<GameEvent.LifeGained>().single().capped)
    }

    @Test
    fun `extra life rolls only in levels mode and time blocks never do`() {
        fun rolls(mode: GameMode) = (0 until 6000).map {
            FoodTable.roll(Random(it.toLong()), elapsedTicks = 2000, level = Level.Beginner, mode = mode)
        }
        val levels = rolls(GameMode.Levels)
        assertTrue(levels.any { it.effect is FoodEffect.ExtraLife })
        assertTrue(levels.none { it.effect is FoodEffect.TimeBonus || it.effect is FoodEffect.TimePenalty })
        val endless = rolls(GameMode.Endless)
        assertTrue(endless.none { it.effect is FoodEffect.ExtraLife })
        val timeAttack = rolls(GameMode.TimeAttack)
        assertTrue(timeAttack.none { it.effect is FoodEffect.ExtraLife })
    }
}

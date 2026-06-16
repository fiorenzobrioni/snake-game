package com.brioni.snake.game

import kotlin.random.Random

/**
 * Which way a food changes the snake. [Special] is reserved for the Phase 6
 * power-ups / hazards (earthquake, explosion, …) and is not produced yet.
 */
enum class FoodCategory { Grow, Shrink, Special }

/**
 * On-board footprint of a food. [Standard] occupies one cell; [Maxi] a 2×2
 * square and amplifies the effect of its standard counterpart.
 */
enum class FoodSize(val cellSpan: Int) { Standard(1), Maxi(2) }

/**
 * Magnitude/visual tier within a category. [Mystery] hides a random amount
 * behind a "?" and is resolved at spawn time (see [FoodTable]).
 */
enum class FoodTier { Small, Medium, Large, Huge, Mystery }

/**
 * What eating a food does. Sealed so the Phase 6 specials (earthquake,
 * explosion, speed, invincibility, …) can be added as new cases without
 * touching call sites that already handle [Grow]/[Shrink].
 */
sealed interface FoodEffect {
    /** Adds [segments] to the snake (also drives the score). */
    data class Grow(val segments: Int) : FoodEffect

    /** Removes up to [segments] tail cells, never below the length floor. */
    data class Shrink(val segments: Int) : FoodEffect

    // --- Phase 6.2 specials. Beneficial unless noted as a hazard. ---

    /** Earthquake (hazard): bites [segments] off the tail and shakes the screen. */
    data class Quake(val segments: Int) : FoodEffect

    /**
     * Explosion (hazard): splits the snake, leaving the detached tail on the
     * board as lethal debris for [debrisMs] before it auto-clears.
     */
    data class Burst(val debrisMs: Long) : FoodEffect

    /** Lightning: speeds the snake up for [durationMs]. */
    data class Haste(val durationMs: Long) : FoodEffect

    /** Snail (hazard): slows the snake down for [durationMs]. */
    data class Slow(val durationMs: Long) : FoodEffect

    /** Star: invincible pass-through for [durationMs]. */
    data class Ghost(val durationMs: Long) : FoodEffect

    /** Freeze: slows time and suspends special spawns for [durationMs]. */
    data class Freeze(val durationMs: Long) : FoodEffect

    /**
     * 3D (hazard): tilts the board into a chase-cam view from behind the snake's
     * head for [durationMs]. A pure render/controls effect — it does not change
     * any gameplay rule or the tick speed.
     */
    data class ThreeD(val durationMs: Long) : FoodEffect

    /** Jackpot: a large score [bonus] plus a [growth] of segments. */
    data class Jackpot(val bonus: Int, val growth: Int) : FoodEffect

    /** Time Attack only: adds [seconds] to the remaining clock. */
    data class TimeBonus(val seconds: Int) : FoodEffect

    /** Time Attack only (hazard): subtracts [seconds] from the remaining clock. */
    data class TimePenalty(val seconds: Int) : FoodEffect

    /** Levels mode only: grants one extra life (points past [LevelsMode.MAX_LIVES]). */
    data object ExtraLife : FoodEffect
}

/** True for the [FoodEffect]s that make the game harder (gated by the hazards toggle). */
val FoodEffect.isHazard: Boolean
    get() = this is FoodEffect.Quake || this is FoodEffect.Burst ||
        this is FoodEffect.Slow || this is FoodEffect.TimePenalty ||
        this is FoodEffect.ThreeD

/**
 * A food item on the board.
 *
 * @param position top-left cell of the occupied square.
 * @param category grow vs shrink (drives the colour family + score rule).
 * @param tier     magnitude/visual tier (drives the shade and the "?" glyph).
 * @param size     standard (1×1) or maxi (2×2); maxi amplifies the effect.
 * @param effect   the already-resolved consequence (mystery amounts are rolled
 *                 at spawn, so the model stays deterministic at eat time).
 * @param spawnTick the tick this food was placed on the board; drives the
 *                 auto-vanish of ignored regular food (see [GameEngine.tick]).
 */
data class Food(
    val position: Position,
    val category: FoodCategory,
    val tier: FoodTier,
    val size: FoodSize,
    val effect: FoodEffect,
    val spawnTick: Int = 0,
) {
    val span: Int get() = size.cellSpan

    /** True for the concealed "?" foods of either category. */
    val isMystery: Boolean get() = tier == FoodTier.Mystery

    /** Every cell this food covers. */
    fun cells(): List<Position> = buildList {
        for (i in 0 until span) {
            for (j in 0 until span) {
                add(Position(position.x + i, position.y + j))
            }
        }
    }

    /** True when [cell] lies inside this food's occupied square. */
    fun occupies(cell: Position): Boolean =
        cell.x >= position.x && cell.x < position.x + span &&
            cell.y >= position.y && cell.y < position.y + span
}

/** A resolved spawn template produced by [FoodTable], before a cell is chosen. */
data class FoodSpec(
    val category: FoodCategory,
    val tier: FoodTier,
    val size: FoodSize,
    val effect: FoodEffect,
)

/**
 * Time- and level-aware spawn table. Replaces the flat v1.0.0 probabilities
 * with a progression that ramps the *purpose* of a session:
 *
 *  - From the start: only growing food (small/medium most common).
 *  - After [GATE_SHRINK_MS] of play: shrinking food unlocks.
 *  - After [GATE_MAXI_MS]: maxi (2×2, amplified) variants unlock.
 *  - After [GATE_MYSTERY_MS]: the concealed "?" foods unlock.
 *
 * Higher difficulty levels reach every gate sooner (see [levelGateFactor]).
 * Mystery amounts are rolled here, so the resolved [FoodEffect] is fixed by the
 * time the food reaches the board — keeping [GameEngine.tick] deterministic.
 */
object FoodTable {

    // Elapsed in-game time (ms) before each kind of food unlocks, at the
    // easiest level. Compared against elapsedTicks * level.tickMillis.
    const val GATE_SHRINK_MS = 15_000L
    const val GATE_MAXI_MS = 30_000L
    const val GATE_MYSTERY_MS = 45_000L

    /** Specials (power-ups / hazards) unlock last; they are rare, maxi "events". */
    const val GATE_SPECIAL_MS = 60_000L

    // Durations / magnitudes for the specials, resolved here so the engine stays
    // deterministic at eat time.
    // Timed effects last long enough for the player to actually feel and play
    // them out (a few seconds felt too fleeting to enjoy).
    private const val HASTE_MS = 9_000L
    private const val SLOW_MS = 8_000L
    // Star is the "save me" power: long enough to actually escape a tight spot.
    private const val GHOST_MS = 9_000L
    private const val FREEZE_MS = 8_000L
    private const val BURST_DEBRIS_MS = 4_000L

    /** 3D lasts long enough to read the perspective and play a few turns in it. */
    private const val THREE_D_MS = 11_000L

    /** Seconds the Time Attack clock blocks add / remove (tuned for a 120 s run). */
    const val TIME_BONUS_SECONDS = 5
    const val TIME_PENALTY_SECONDS = 3

    /** Harder levels shrink the gates so hazards arrive earlier. */
    private fun levelGateFactor(level: Level): Double = 1.0 - level.ordinal * 0.1

    /**
     * Rolls the next food to spawn.
     *
     * @param hazardsEnabled when false, harmful specials (Earthquake / Explosion /
     *        Snail) are never produced — only beneficial power-ups can appear.
     * @param specialAllowed when false (a special is already on the board, or a
     *        Freeze is active), the special branch is suppressed entirely.
     * @param specialFrequency scales the special spawn weight and unlock gate so
     *        the player can dial how often power-ups / hazards appear.
     * @param mode the active [GameMode]; the time-bonus / time-penalty specials
     *        are produced only in [GameMode.TimeAttack] and the extra-life
     *        special only in [GameMode.Levels].
     */
    fun roll(
        random: Random,
        elapsedTicks: Int,
        level: Level,
        hazardsEnabled: Boolean = true,
        specialAllowed: Boolean = true,
        specialFrequency: SpecialFrequency = SpecialFrequency.Standard,
        mode: GameMode = GameMode.Classic,
    ): FoodSpec {
        val elapsedMs = elapsedTicks.toLong() * level.tickMillis
        val factor = levelGateFactor(level)
        val shrinkUnlocked = elapsedMs >= (GATE_SHRINK_MS * factor)
        val maxiUnlocked = elapsedMs >= (GATE_MAXI_MS * factor)
        val mysteryUnlocked = elapsedMs >= (GATE_MYSTERY_MS * factor)
        val specialUnlocked = elapsedMs >= (GATE_SPECIAL_MS * factor * specialFrequency.gateFactor)

        val entries = buildList {
            add(Weighted(40) { growSpec(random, maxiUnlocked) })
            if (shrinkUnlocked) add(Weighted(24) { shrinkSpec(random, maxiUnlocked) })
            if (mysteryUnlocked) {
                add(Weighted(9) { mysterySpec(random, FoodCategory.Grow, maxiUnlocked) })
                add(Weighted(6) { mysterySpec(random, FoodCategory.Shrink, maxiUnlocked) })
            }
            if (specialUnlocked && specialAllowed) {
                add(Weighted(specialFrequency.spawnWeight) { specialSpec(random, hazardsEnabled, mode) })
            }
        }
        return pick(entries, random)
    }

    /** Builds a maxi special, choosing a kind weighted by benefit, the toggle and the mode. */
    private fun specialSpec(random: Random, hazardsEnabled: Boolean, mode: GameMode): FoodSpec {
        val choices = buildList<Weighted<FoodEffect>> {
            // Beneficial — always available.
            add(Weighted(20) { FoodEffect.Haste(HASTE_MS) })
            add(Weighted(14) { FoodEffect.Ghost(GHOST_MS) })
            add(Weighted(14) { FoodEffect.Freeze(FREEZE_MS) })
            add(Weighted(10) { FoodEffect.Jackpot(bonus = random.nextInt(150, 401), growth = random.nextInt(2, 6)) })
            // Harmful — only when hazards are enabled.
            if (hazardsEnabled) {
                add(Weighted(16) { FoodEffect.Quake(random.nextInt(3, 7)) })
                add(Weighted(12) { FoodEffect.Burst(BURST_DEBRIS_MS) })
                add(Weighted(14) { FoodEffect.Slow(SLOW_MS) })
                // Pointless inside the always-on 3D World mode.
                if (mode != GameMode.ThreeDWorld) add(Weighted(12) { FoodEffect.ThreeD(THREE_D_MS) })
            }
            // Time Attack only: the clock blocks. The penalty is a hazard.
            if (mode == GameMode.TimeAttack) {
                add(Weighted(14) { FoodEffect.TimeBonus(TIME_BONUS_SECONDS) })
                if (hazardsEnabled) add(Weighted(12) { FoodEffect.TimePenalty(TIME_PENALTY_SECONDS) })
            }
            // Levels only: a rare extra life.
            if (mode == GameMode.Levels) {
                add(Weighted(8) { FoodEffect.ExtraLife })
            }
        }
        val effect = pick(choices, random)
        return FoodSpec(FoodCategory.Special, FoodTier.Huge, FoodSize.Maxi, effect)
    }

    private fun growSpec(random: Random, maxiUnlocked: Boolean): FoodSpec {
        val tier = weightedTier(random, GROW_TIER_WEIGHTS)
        val size = rollSize(random, maxiUnlocked)
        val amount = growBase(tier) * size.cellSpan
        return FoodSpec(FoodCategory.Grow, tier, size, FoodEffect.Grow(amount))
    }

    private fun shrinkSpec(random: Random, maxiUnlocked: Boolean): FoodSpec {
        val tier = weightedTier(random, SHRINK_TIER_WEIGHTS)
        val size = rollSize(random, maxiUnlocked)
        val amount = shrinkBase(tier) * size.cellSpan
        return FoodSpec(FoodCategory.Shrink, tier, size, FoodEffect.Shrink(amount))
    }

    private fun mysterySpec(
        random: Random,
        category: FoodCategory,
        maxiUnlocked: Boolean,
    ): FoodSpec {
        val size = rollSize(random, maxiUnlocked)
        val effect = if (category == FoodCategory.Grow) {
            // Standard 2..12, maxi 4..24.
            FoodEffect.Grow(random.nextInt(2, 13) * size.cellSpan)
        } else {
            // Standard 2..8, maxi 4..14 (clamped by the engine's length floor).
            val base = random.nextInt(2, 9)
            FoodEffect.Shrink(if (size == FoodSize.Maxi) (base + 5).coerceAtMost(14) else base)
        }
        return FoodSpec(category, FoodTier.Mystery, size, effect)
    }

    /** 25% maxi once unlocked, otherwise always standard. */
    private fun rollSize(random: Random, maxiUnlocked: Boolean): FoodSize =
        if (maxiUnlocked && random.nextInt(100) < 25) FoodSize.Maxi else FoodSize.Standard

    private fun growBase(tier: FoodTier): Int = when (tier) {
        FoodTier.Small -> 2
        FoodTier.Medium -> 4
        FoodTier.Large -> 6
        FoodTier.Huge -> 8
        FoodTier.Mystery -> 4
    }

    private fun shrinkBase(tier: FoodTier): Int = when (tier) {
        FoodTier.Small -> 2
        FoodTier.Medium -> 3
        FoodTier.Large -> 5
        FoodTier.Huge -> 7
        FoodTier.Mystery -> 3
    }

    private val GROW_TIER_WEIGHTS = listOf(
        FoodTier.Small to 35,
        FoodTier.Medium to 30,
        FoodTier.Large to 22,
        FoodTier.Huge to 13,
    )

    private val SHRINK_TIER_WEIGHTS = listOf(
        FoodTier.Small to 45,
        FoodTier.Medium to 35,
        FoodTier.Large to 20,
    )

    private fun weightedTier(random: Random, weights: List<Pair<FoodTier, Int>>): FoodTier {
        val total = weights.sumOf { it.second }
        var r = random.nextInt(total)
        for ((tier, weight) in weights) {
            if (r < weight) return tier
            r -= weight
        }
        return weights.last().first
    }

    private class Weighted<T>(val weight: Int, val build: () -> T)

    private fun <T> pick(entries: List<Weighted<T>>, random: Random): T {
        val total = entries.sumOf { it.weight }
        var r = random.nextInt(total)
        for (e in entries) {
            if (r < e.weight) return e.build()
            r -= e.weight
        }
        return entries.last().build()
    }
}

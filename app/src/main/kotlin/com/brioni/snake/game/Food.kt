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
}

/**
 * A food item on the board.
 *
 * @param position top-left cell of the occupied square.
 * @param category grow vs shrink (drives the colour family + score rule).
 * @param tier     magnitude/visual tier (drives the shade and the "?" glyph).
 * @param size     standard (1×1) or maxi (2×2); maxi amplifies the effect.
 * @param effect   the already-resolved consequence (mystery amounts are rolled
 *                 at spawn, so the model stays deterministic at eat time).
 */
data class Food(
    val position: Position,
    val category: FoodCategory,
    val tier: FoodTier,
    val size: FoodSize,
    val effect: FoodEffect,
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

    /** Harder levels shrink the gates so hazards arrive earlier. */
    private fun levelGateFactor(level: Level): Double = 1.0 - level.ordinal * 0.1

    fun roll(random: Random, elapsedTicks: Int, level: Level): FoodSpec {
        val elapsedMs = elapsedTicks.toLong() * level.tickMillis
        val factor = levelGateFactor(level)
        val shrinkUnlocked = elapsedMs >= (GATE_SHRINK_MS * factor)
        val maxiUnlocked = elapsedMs >= (GATE_MAXI_MS * factor)
        val mysteryUnlocked = elapsedMs >= (GATE_MYSTERY_MS * factor)

        val entries = buildList {
            add(Weighted(40) { growSpec(random, maxiUnlocked) })
            if (shrinkUnlocked) add(Weighted(24) { shrinkSpec(random, maxiUnlocked) })
            if (mysteryUnlocked) {
                add(Weighted(9) { mysterySpec(random, FoodCategory.Grow, maxiUnlocked) })
                add(Weighted(6) { mysterySpec(random, FoodCategory.Shrink, maxiUnlocked) })
            }
        }
        return pick(entries, random)
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

    private class Weighted(val weight: Int, val build: () -> FoodSpec)

    private fun pick(entries: List<Weighted>, random: Random): FoodSpec {
        val total = entries.sumOf { it.weight }
        var r = random.nextInt(total)
        for (e in entries) {
            if (r < e.weight) return e.build()
            r -= e.weight
        }
        return entries.last().build()
    }
}

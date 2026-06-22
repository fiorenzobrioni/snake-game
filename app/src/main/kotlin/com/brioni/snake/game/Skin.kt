package com.brioni.snake.game

/**
 * How a [Skin] is earned. Kept primitive (no Android) so the unlock logic stays
 * pure and testable. [requirementText] is the short human-readable hint shown on
 * a locked skin card in Settings.
 */
sealed interface SkinUnlock {
    val requirementText: String

    /** Available from the start. */
    data object Always : SkinUnlock {
        override val requirementText: String = ""
    }

    /** Unlocked by reaching [min] points in a single run. */
    data class Score(val min: Int) : SkinUnlock {
        override val requirementText: String = "Score $min in one run"
    }

    /** Unlocked by reaching a Daily streak of [days] days. */
    data class Streak(val days: Int) : SkinUnlock {
        override val requirementText: String = "Reach a $days-day Daily streak"
    }
}

/**
 * A visual theme for the board and snake. A skin is purely cosmetic — it changes
 * the palette and render style (rounded vs square cells, glow on/off, continuous
 * vs segmented body) but never the rules — so it lives in the pure model only as
 * an identifier + label + [unlock] rule; the concrete colours are mapped in the
 * UI layer (`ui/game/SkinPalette`).
 *
 * The enum order is the order shown in the Settings picker. [Retro] (the default)
 * and [Classic] are always unlocked and listed first; the rest are gated behind a
 * score milestone or a Daily streak (see [unlock]).
 */
enum class Skin(val displayName: String, val unlock: SkinUnlock) {
    /** Warm, limited arcade palette that pairs with the CRT filter. Default skin. */
    Retro("Retro", SkinUnlock.Always),

    /** The original look: lime snake, green/warm foods on a dark gradient. */
    Classic("Classic", SkinUnlock.Always),

    /** High-contrast saturated neon on near-black, with boosted glow. */
    Neon("Neon", SkinUnlock.Score(1500)),

    /** Flat, square, glow-free pixel-art styling. */
    Pixel("Pixel", SkinUnlock.Score(5000)),

    /** Cool aurora gradient with glow and a segmented body. */
    Aurora("Aurora", SkinUnlock.Streak(7)),

    /** Hot lava palette with intense glow and a segmented body. */
    Ember("Ember", SkinUnlock.Streak(30)),
    ;

    companion object {
        /** Skins available without unlocking (their [unlock] is [SkinUnlock.Always]). */
        val defaultUnlocked: Set<Skin> = entries.filterTo(mutableSetOf()) { it.unlock is SkinUnlock.Always }

        /**
         * The gated skins newly satisfied by a finished run that aren't in
         * [already] unlocked: score milestones against this run's [score] and
         * streak milestones against the post-run Daily [streak].
         */
        fun newlyUnlocked(score: Int, streak: Int, already: Set<String>): List<Skin> =
            entries.filter { skin ->
                skin.name !in already && when (val u = skin.unlock) {
                    is SkinUnlock.Always -> false
                    is SkinUnlock.Score -> score >= u.min
                    is SkinUnlock.Streak -> streak >= u.days
                }
            }
    }
}

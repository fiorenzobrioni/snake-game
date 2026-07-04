package com.callbackdev.snake.game

/**
 * A challenge twist layered on top of the base [Challenge] config, so a run has a
 * recognisable flavour. Each modifier overrides one or two rule knobs; [None]
 * leaves the standard rules. Picked deterministically from the challenge
 * seed/day, so a Daily is the same for everyone and a Random is stable for its
 * seed. The pool is deliberately wide so a plain "Standard" day is rare.
 *
 * The spawn/pace knobs ([forceMaxiFood], [suppressSpecials], [comboWindowFactor],
 * [endlessTierBoost]) travel with the run inside [GameState.modifier], so the
 * pure engine applies them per tick; the config knobs ([hazardsOverride],
 * [specialFrequencyOverride], [scaleOverride], [speedOverride]) are applied by
 * the ViewModel when the challenge run is staged.
 */
enum class ChallengeModifier(val displayName: String, val description: String) {
    /** No twist: standard rules. */
    None("Standard", "The classic rules, untouched."),

    /** Bonus Rush: hazards off, only beneficial power-ups appear. */
    BonusRush("Bonus Rush", "No hazards today - only helpful power-ups spawn."),

    /** Frenzy: specials appear far more often. */
    Frenzy("Frenzy", "Power-ups and hazards rain down far more often."),

    /** Compact Arena: a smaller, tighter board. */
    Compact("Compact Arena", "A tight little board - no room for mistakes."),

    /** Grand Arena: the biggest board, a marathon of open space. */
    GrandArena("Grand Arena", "A colossal board - long hauls between bites."),

    /** Maxi Feast: every grow/shrink food spawns as a giant 2x2 piece. */
    MaxiFeast("Maxi Feast", "Every food is a giant 2x2 piece. Big bites, big body."),

    /** Combo Rush: the combo window is halved, so streaks demand real pace. */
    ComboRush("Combo Rush", "The combo timer is halved - keep the streak alive."),

    /** Overdrive: everything is faster (and Time Attack pays its speed bonus). */
    Overdrive("Overdrive", "Everything runs hotter and faster from the first tick."),

    /** Old School: no specials at all - pure classic snake. */
    OldSchool("Old School", "No power-ups, no hazards - pure classic snake."),
    ;

    /** Forced hazards toggle, or null to keep the default. */
    val hazardsOverride: Boolean?
        get() = when (this) {
            BonusRush, OldSchool -> false
            else -> null
        }

    /** Forced special-spawn frequency, or null to keep the default. */
    val specialFrequencyOverride: SpecialFrequency? get() = if (this == Frenzy) SpecialFrequency.Frenzy else null

    /** Forced board granularity, or null to keep the challenge's default. */
    val scaleOverride: BoardScale?
        get() = when (this) {
            Compact -> BoardScale.Cozy
            GrandArena -> BoardScale.Colossal
            else -> null
        }

    /** Forced snake pace (Time Attack only), or null to keep the default. */
    val speedOverride: SnakeSpeed? get() = if (this == Overdrive) SnakeSpeed.Turbo else null

    /** When true, every grow/shrink food spawns maxi (2x2) from the first tick. */
    val forceMaxiFood: Boolean get() = this == MaxiFeast

    /** When true, the special branch of the spawn table never fires. */
    val suppressSpecials: Boolean get() = this == OldSchool

    /** Scales the combo window ([GameEngine.COMBO_WINDOW_TICKS]); 0.5 halves it. */
    val comboWindowFactor: Float get() = if (this == ComboRush) 0.5f else 1.0f

    /** Extra Endless speed tiers stacked on the ramp's starting point. */
    val endlessTierBoost: Int get() = if (this == Overdrive) 4 else 0
}

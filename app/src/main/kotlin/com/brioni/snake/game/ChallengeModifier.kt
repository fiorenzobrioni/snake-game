package com.brioni.snake.game

/**
 * A challenge twist layered on top of the base [Challenge] config, so a run has a
 * recognisable flavour. Each modifier optionally overrides one spawn-affecting
 * knob; [None] leaves the standard rules. Picked deterministically from the
 * challenge seed/day, so a Daily is the same for everyone and a Random is stable
 * for its seed.
 */
enum class ChallengeModifier(val displayName: String) {
    /** No twist: standard rules. */
    None("Standard"),

    /** Bonus Rush: hazards off, only beneficial power-ups appear. */
    BonusRush("Bonus Rush"),

    /** Frenzy: specials appear far more often. */
    Frenzy("Frenzy"),

    /** Compact Arena: a smaller, tighter board. */
    Compact("Compact Arena"),
    ;

    /** Forced hazards toggle, or null to keep the default. */
    val hazardsOverride: Boolean? get() = if (this == BonusRush) false else null

    /** Forced special-spawn frequency, or null to keep the default. */
    val specialFrequencyOverride: SpecialFrequency? get() = if (this == Frenzy) SpecialFrequency.Frenzy else null

    /** Forced board granularity, or null to keep the challenge's default. */
    val scaleOverride: BoardScale? get() = if (this == Compact) BoardScale.Cozy else null
}

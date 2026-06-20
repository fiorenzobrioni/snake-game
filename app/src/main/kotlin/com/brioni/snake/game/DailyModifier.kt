package com.brioni.snake.game

/**
 * A Daily Challenge twist layered on top of the base config, so a given day's
 * run has a recognisable flavour. Each modifier optionally overrides one of the
 * spawn-affecting knobs; [None] leaves the standard rules. Picked deterministically
 * from the day's seed in [DailyChallenge.forDay], so it is the same for everyone.
 */
enum class DailyModifier(val displayName: String) {
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

    /** Forced board granularity, or null to keep the daily's default. */
    val scaleOverride: BoardScale? get() = if (this == Compact) BoardScale.Cozy else null
}

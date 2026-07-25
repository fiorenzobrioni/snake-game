package com.callbackdev.snake.game

/**
 * The **career ladder** the achievements are grouped into.
 *
 * Thirty-eight badges laid out as one flat list is a checklist, not a journey:
 * everything is visible from the first launch, nothing is ever *revealed*, and the
 * hardest entries sit next to "eat your first food". The tiers give the same set an
 * arc - each rank shows only when the player has earned [revealAt] badges in
 * total, so the list opens up as they play and the late-game goals arrive as a
 * discovery rather than as noise on day one.
 *
 * The gate is deliberately a **total count**, not "clear the previous tier": no
 * single stubborn badge (a 30-day streak, a Campaign lap) can ever wall a player
 * out of the rest of the game. Every tier holds far more badges than the next
 * one's threshold needs, so the ladder is always climbable from what is in reach.
 *
 * The player's current rank is simply the deepest revealed tier - that is the
 * reward, and it is what the Achievements screen leads with.
 */
enum class AchievementTier(
    val displayName: String,
    /** Badges the player must have earned in total before this rank is revealed. */
    val revealAt: Int,
    /** The badges that belong to this rank. Coverage is guarded by a unit test. */
    val achievements: List<Achievement>,
) {
    Hatchling(
        "Hatchling",
        0,
        listOf(
            Achievement.FirstFeast,
            Achievement.Centurion,
            Achievement.ComboMaster,
            Achievement.Gourmand,
            Achievement.Demolition,
            Achievement.Untouchable,
            Achievement.Lucky,
            Achievement.LongHaul,
        ),
    ),
    Forager(
        "Forager",
        5,
        listOf(
            Achievement.Survivor,
            Achievement.BigEater,
            Achievement.SpeedRunner,
            Achievement.Stylist,
            Achievement.HighRoller,
            Achievement.Climber,
            Achievement.Anaconda,
            Achievement.InnerPeace,
            Achievement.Ouroboros,
        ),
    ),
    Stalker(
        "Stalker",
        12,
        listOf(
            Achievement.Glutton,
            Achievement.Marathoner,
            Achievement.Trifecta,
            Achievement.Grandmaster,
            Achievement.TowerTopper,
            Achievement.Titanoboa,
            Achievement.WeekWarrior,
            Achievement.EternalFlow,
            Achievement.Sculptor,
        ),
    ),
    Constrictor(
        "Constrictor",
        20,
        listOf(
            Achievement.Insatiable,
            Achievement.FullCircle,
            Achievement.TowerMaster,
            Achievement.Mythmaker,
            Achievement.Leviathan,
            Achievement.Featherweight,
            Achievement.Purist,
        ),
    ),
    Mythic(
        "Mythic",
        26,
        listOf(
            Achievement.TowerSovereign,
            Achievement.TowerAscendant,
            Achievement.MonthMaster,
            Achievement.Unbowed,
            Achievement.ApexPredator,
        ),
    ),
    ;

    /** True once [unlockedCount] badges have been earned in total. */
    fun isRevealedAt(unlockedCount: Int): Boolean = unlockedCount >= revealAt

    companion object {
        /**
         * The rank a player with [unlockedCount] badges holds: the deepest revealed
         * tier. Always defined - [Hatchling] is revealed from zero.
         */
        fun rankFor(unlockedCount: Int): AchievementTier =
            entries.last { it.isRevealedAt(unlockedCount) }

        /** The next rank to reveal, or null once every tier is open. */
        fun nextAfter(unlockedCount: Int): AchievementTier? =
            entries.firstOrNull { !it.isRevealedAt(unlockedCount) }

        /**
         * The tier [achievement] belongs to. Falls back to the always-revealed
         * [Hatchling] if an entry was ever left out of the lists above, so a
         * mistake makes a badge *visible* rather than hiding it; `AchievementTierTest`
         * makes sure that never happens.
         */
        fun of(achievement: Achievement): AchievementTier =
            entries.firstOrNull { achievement in it.achievements } ?: Hatchling
    }
}

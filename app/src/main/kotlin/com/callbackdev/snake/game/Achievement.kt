package com.callbackdev.snake.game

/**
 * What one finished run achieved — the inputs every [Achievement] is judged
 * against. Kept primitive (no Android) so unlock logic is pure and testable.
 */
data class RunStats(
    val mode: GameMode,
    val score: Int,
    val maxCombo: Int,
    val durationMs: Long,
    val foodsEaten: Int,
    val usedExplosion: Boolean,
    val usedStar: Boolean,
    val usedJackpot: Boolean,
    /** Levels mode: the highest 1-based level entered during the run. */
    val maxLevelReached: Int = 0,
    /** Levels mode: the highest 1-based speed cycle entered during the run. */
    val maxSpeedCycle: Int = 1,
    /**
     * Levels mode: the deepest linear level progress reached during the run,
     * `(speedCycle - 1) * LevelsMode.LEVEL_COUNT + levelIndex`. Unlike
     * [maxLevelReached] / [maxSpeedCycle] (independent maxima) this is a single
     * monotone position, so "reach Level 10 at Speed 2" is exactly `>= 20`.
     */
    val maxLevelDepth: Int = 0,
    /**
     * Levels mode: true when the player completed a full first lap - cleared all
     * ten levels and reached Speed 2 - without ever losing a life along the way.
     */
    val flawlessLap: Boolean = false,
    /** Levels mode: extra lives banked during the run. */
    val extraLivesGained: Int = 0,
    /**
     * The greatest snake length reached at any point during the run - the run's
     * truthful peak, surfaced in the game-over recap.
     *
     * **Not** what the length goals are judged on: since Step 6.15.1 the snake
     * also grows on its own ([GrowthRate]), so peak length is largely a function
     * of how long the run lasted and which growth setting was picked - a
     * duplicate of the survival goals, and worth a different amount on every
     * setting. [segmentsFromFood] is the auto-growth-proof measure of the same
     * ambition, and it means exactly the same thing at every setting including
     * [GrowthRate.Off].
     */
    val maxSnakeLength: Int = 0,
    /**
     * Segments the player *earned*: the total added by eating over the run (grow
     * food plus a Jackpot's growth). Cumulative, so trimming the tail never takes
     * it back - it measures how much snake you built, not how much you kept.
     */
    val segmentsFromFood: Int = 0,
    /** Segments cut away by eating shrinking food over the run (cumulative). */
    val segmentsTrimmed: Int = 0,
    /**
     * The auto-growth setting the run was played on. The game's difficulty dial,
     * so the top-tier badges can ask for the top of it - the same way the
     * mode-specific ones ask for a mode.
     */
    val growthRate: GrowthRate = GrowthRate.Off,
    /** Daily challenge: the consecutive-day streak after this run (0 otherwise). */
    val dailyStreak: Int = 0,
)

/**
 * The local achievements. Each carries its user-facing [title]/[description] and
 * a pure [test] over a finished run's [RunStats]. The stable enum [name] is what
 * gets persisted, so titles can be reworded without orphaning unlocks.
 */
enum class Achievement(
    val title: String,
    val description: String,
    val test: (RunStats) -> Boolean,
) {
    FirstFeast("First Feast", "Eat your first food", { it.foodsEaten >= 1 }),
    Centurion("Centurion", "Score 100 in a single run", { it.score >= 100 }),
    ComboMaster("Combo Master", "Reach a x5 combo", { it.maxCombo >= 5 }),
    Gourmand("Gourmand", "Eat 50 foods in one run", { it.foodsEaten >= 50 }),
    Glutton("Glutton", "Eat 200 foods in one run", { it.foodsEaten >= 200 }),
    Insatiable("Insatiable", "Eat 500 foods in one run", { it.foodsEaten >= 500 }),
    Survivor("Survivor", "Last three minutes in one run", { it.durationMs >= 180_000 }),
    Demolition("Demolition", "Set off an explosion", { it.usedExplosion }),
    Untouchable("Untouchable", "Use a Star power-up", { it.usedStar }),
    Lucky("Lucky", "Hit a Jackpot", { it.usedJackpot }),
    SpeedRunner("Speed Runner", "Score 600 in Time Attack", { it.mode == GameMode.TimeAttack && it.score >= 600 }),
    Stylist("Stylist", "Score 1500 with a x5 combo", { it.maxCombo >= 5 && it.score >= 1500 }),
    HighRoller("High Roller", "Score 2500 in a single run", { it.score >= 2500 }),
    Marathoner("Marathoner", "Last five minutes in one run", { it.durationMs >= 300_000 }),
    BigEater("Big Eater", "Eat 100 foods in one run", { it.foodsEaten >= 100 }),
    Trifecta("Trifecta", "Explosion, Star and Jackpot in one run", { it.usedExplosion && it.usedStar && it.usedJackpot }),
    Grandmaster("Grandmaster", "Score 5000 in a single run", { it.score >= 5000 }),
    Climber("Climber", "Reach Level 5 in Campaign mode", { it.mode == GameMode.Levels && it.maxLevelReached >= 5 }),
    TowerTopper("Tower Topper", "Reach Level 10 in Campaign mode", { it.mode == GameMode.Levels && it.maxLevelReached >= 10 }),
    FullCircle("Full Circle", "Clear all fifteen levels and reach Speed 2 without losing a life", { it.mode == GameMode.Levels && it.flawlessLap }),
    TowerMaster("Tower Master", "Reach Level 10 at Speed 2 in Campaign", { it.mode == GameMode.Levels && it.maxLevelDepth >= 25 }),
    TowerSovereign("Tower Sovereign", "Reach Level 10 at Speed 3 in Campaign", { it.mode == GameMode.Levels && it.maxLevelDepth >= 40 }),
    // The "big snake" family. Judged on segments *earned by eating*
    // ([RunStats.segmentsFromFood]), not on peak length, so auto-growth can never
    // hand them out for merely staying alive. The thresholds are halved against
    // the pre-6.15.1 peak-length ones to match the halved grow table, keeping the
    // number of pieces a player has to eat about the same as it always was.
    LongHaul("Long Haul", "Grow 25 segments from food in one run", { it.segmentsFromFood >= 25 }),
    Anaconda("Anaconda", "Grow 50 segments from food in one run", { it.segmentsFromFood >= 50 }),
    Titanoboa("Titanoboa", "Grow 90 segments from food in one run", { it.segmentsFromFood >= 90 }),
    WeekWarrior("Week Warrior", "Reach a 7-day Daily streak", { it.dailyStreak >= 7 }),
    MonthMaster("Monthly Master", "Reach a 30-day Daily streak", { it.dailyStreak >= 30 }),
    Mythmaker("Mythmaker", "Score 10,000 in a single run", { it.score >= 10_000 }),
    Leviathan("Leviathan", "Grow 125 segments from food in one run", { it.segmentsFromFood >= 125 }),
    TowerAscendant("Tower Ascendant", "Reach Level 15 at Speed 3 in Campaign", { it.mode == GameMode.Levels && it.maxLevelDepth >= 45 }),
    InnerPeace("Inner Peace", "Flow for five minutes in one Zen run", { it.mode == GameMode.Zen && it.durationMs >= 300_000 }),
    Ouroboros("Ouroboros", "Grow 30 segments from food in one Zen run", { it.mode == GameMode.Zen && it.segmentsFromFood >= 30 }),
    EternalFlow("Eternal Flow", "Score 3000 in a Zen run", { it.mode == GameMode.Zen && it.score >= 3000 }),
    // The counterweight the auto-growth rebalance created: keeping a snake short
    // is now an active skill, so the trimming itself is worth a badge.
    Sculptor("Sculptor", "Trim 50 segments with shrink food in one run", { it.segmentsTrimmed >= 50 }),
    // Two badges for playing *against* the length rather than with it: scoring
    // big while staying small, and running with the brake untouched.
    Featherweight(
        "Featherweight",
        "Score 3000 with a snake never longer than 20 segments",
        { it.score >= 3000 && it.maxSnakeLength in 1..20 },
    ),
    Purist(
        "Purist",
        "Grow 60 segments from food without trimming once",
        { it.segmentsFromFood >= 60 && it.segmentsTrimmed == 0 },
    ),
    // The top of the difficulty dial. Relentless adds a segment about every
    // second, so both of these are meant to stay rare.
    Unbowed(
        "Unbowed",
        "Survive three minutes at Relentless growth",
        { it.growthRate == GrowthRate.Relentless && it.durationMs >= 180_000 },
    ),
    ApexPredator(
        "Apex Predator",
        "Score 5000 in a run at Relentless growth",
        { it.growthRate == GrowthRate.Relentless && it.score >= 5000 },
    ),
    ;

    companion object {
        /** Achievements satisfied by [stats] that aren't in [already] unlocked. */
        fun earnedBy(stats: RunStats, already: Set<String>): List<Achievement> =
            entries.filter { it.name !in already && it.test(stats) }
    }
}

package com.brioni.snake.game

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
    /** The greatest snake length reached at any point during the run. */
    val maxSnakeLength: Int = 0,
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
    LongHaul("Long Haul", "Grow the snake to 50 segments", { it.maxSnakeLength >= 50 }),
    Anaconda("Anaconda", "Grow the snake to 100 segments", { it.maxSnakeLength >= 100 }),
    Titanoboa("Titanoboa", "Grow the snake to 180 segments", { it.maxSnakeLength >= 180 }),
    WeekWarrior("Week Warrior", "Reach a 7-day Daily streak", { it.dailyStreak >= 7 }),
    MonthMaster("Monthly Master", "Reach a 30-day Daily streak", { it.dailyStreak >= 30 }),
    Mythmaker("Mythmaker", "Score 10,000 in a single run", { it.score >= 10_000 }),
    Leviathan("Leviathan", "Grow the snake to 250 segments", { it.maxSnakeLength >= 250 }),
    TowerAscendant("Tower Ascendant", "Reach Level 15 at Speed 3 in Campaign", { it.mode == GameMode.Levels && it.maxLevelDepth >= 45 }),
    ;

    companion object {
        /** Achievements satisfied by [stats] that aren't in [already] unlocked. */
        fun earnedBy(stats: RunStats, already: Set<String>): List<Achievement> =
            entries.filter { it.name !in already && it.test(stats) }
    }
}

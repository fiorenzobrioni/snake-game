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
    ComboMaster("Combo Master", "Reach a x5 combo", { it.maxCombo >= 5 }),
    Centurion("Centurion", "Score 100 in a single run", { it.score >= 100 }),
    Gourmand("Gourmand", "Eat 50 foods in one run", { it.foodsEaten >= 50 }),
    Survivor("Survivor", "Last three minutes in one run", { it.durationMs >= 180_000 }),
    Demolition("Demolition", "Set off an explosion", { it.usedExplosion }),
    Untouchable("Untouchable", "Use a Star power-up", { it.usedStar }),
    Lucky("Lucky", "Hit a Jackpot", { it.usedJackpot }),
    SpeedRunner("Speed Runner", "Score 600 in Time Attack", { it.mode == GameMode.TimeAttack && it.score >= 600 }),
    HighRoller("High Roller", "Score 2500 in a single run", { it.score >= 2500 }),
    ;

    companion object {
        /** Achievements satisfied by [stats] that aren't in [already] unlocked. */
        fun earnedBy(stats: RunStats, already: Set<String>): List<Achievement> =
            entries.filter { it.name !in already && it.test(stats) }
    }
}

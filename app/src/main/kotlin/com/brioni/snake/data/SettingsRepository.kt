package com.brioni.snake.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.brioni.snake.game.BoardScale
import com.brioni.snake.game.BoardTerrain
import com.brioni.snake.game.BackBehavior
import com.brioni.snake.game.ControlScheme
import com.brioni.snake.game.GameMode
import com.brioni.snake.game.Level
import com.brioni.snake.game.ScoreKey
import com.brioni.snake.game.Skin
import com.brioni.snake.game.SnakeSpeed
import com.brioni.snake.game.SpecialFrequency
import com.brioni.snake.game.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.max

/** Persisted player preferences, decoded into model types. */
data class Settings(
    val level: Level,
    val scale: BoardScale,
    val controlScheme: ControlScheme,
    /** Swipe steering sensitivity (0..1); 0.5 keeps the tuned default distance. */
    val swipeSensitivity: Float = DEFAULT_SWIPE_SENSITIVITY,
    val backBehavior: BackBehavior = BackBehavior.DEFAULT,
    val snakeSpeed: SnakeSpeed = SnakeSpeed.DEFAULT,
    val masterVolume: Float = DEFAULT_MASTER_VOLUME,
    val musicVolume: Float = DEFAULT_MUSIC_VOLUME,
    val sfxVolume: Float = DEFAULT_SFX_VOLUME,
    val crtEnabled: Boolean = false,
    /** Vibration feedback for gameplay events and near-misses (default on). */
    val hapticsEnabled: Boolean = true,
    /** Accessibility: damp screen shake, particle bursts and near-miss flashes (default off). */
    val reduceMotion: Boolean = false,
    val skin: Skin = Skin.Retro,
    /** The board's animated backdrop, independent of the skin (Arcade follows it). */
    val terrain: BoardTerrain = BoardTerrain.Meadow,
    val hazardsEnabled: Boolean = true,
    val specialFrequency: SpecialFrequency = SpecialFrequency.Standard,
    val mode: GameMode = GameMode.Endless,
    val themeMode: ThemeMode = ThemeMode.Dark,
    /** First-run flag: true once the player has seen (or skipped) the tutorial. */
    val onboardingCompleted: Boolean = false,
)

/**
 * Default audio levels (also used as the in-memory fallback before load).
 * Tuned quiet by design: the music should sit as a light backdrop under the
 * gameplay and the SFX should punctuate without startling — players who want
 * more can raise the sliders in Settings.
 */
const val DEFAULT_MASTER_VOLUME = 1f
const val DEFAULT_MUSIC_VOLUME = 0.3f
const val DEFAULT_SFX_VOLUME = 0.6f

/**
 * Default swipe sensitivity. 0.5 is the midpoint and maps to the carefully tuned
 * swipe distance the game already shipped with, so the default feel is unchanged.
 */
const val DEFAULT_SWIPE_SENSITIVITY = 0.5f

/** Process-wide DataStore, created once for the app's [Context]. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "snake_prefs")

/**
 * Reads and writes player settings and per-(level, scale) highscores via
 * Preferences DataStore. Enum values are stored by name and decoded with
 * `runCatching` so a renamed/removed constant can never crash on stale data.
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            level = prefs[LEVEL].toEnum(Level::valueOf) ?: Level.Beginner,
            scale = prefs[SCALE].toEnum(BoardScale::valueOf) ?: BoardScale.Classic,
            controlScheme = prefs[CONTROL].toEnum(ControlScheme::valueOf) ?: ControlScheme.Swipe,
            swipeSensitivity = prefs[SWIPE_SENSITIVITY] ?: DEFAULT_SWIPE_SENSITIVITY,
            backBehavior = prefs[BACK_BEHAVIOR].toEnum(BackBehavior::valueOf) ?: BackBehavior.DEFAULT,
            snakeSpeed = prefs[SNAKE_SPEED].toEnum(SnakeSpeed::valueOf) ?: SnakeSpeed.DEFAULT,
            masterVolume = prefs[MASTER_VOLUME] ?: DEFAULT_MASTER_VOLUME,
            musicVolume = prefs[MUSIC_VOLUME] ?: DEFAULT_MUSIC_VOLUME,
            sfxVolume = prefs[SFX_VOLUME] ?: DEFAULT_SFX_VOLUME,
            crtEnabled = prefs[CRT_ENABLED] ?: false,
            hapticsEnabled = prefs[HAPTICS_ENABLED] ?: true,
            reduceMotion = prefs[REDUCE_MOTION] ?: false,
            skin = prefs[SKIN].toEnum(Skin::valueOf) ?: Skin.Retro,
            terrain = prefs[TERRAIN].toEnum(BoardTerrain::valueOf) ?: BoardTerrain.Meadow,
            hazardsEnabled = prefs[HAZARDS_ENABLED] ?: true,
            specialFrequency = prefs[SPECIAL_FREQUENCY].toEnum(SpecialFrequency::valueOf) ?: SpecialFrequency.Standard,
            mode = prefs[MODE].toEnum(GameMode::valueOf) ?: GameMode.Endless,
            themeMode = prefs[THEME_MODE].toEnum(ThemeMode::valueOf) ?: ThemeMode.Dark,
            onboardingCompleted = prefs[ONBOARDING_COMPLETED] ?: false,
        )
    }

    suspend fun setLevel(level: Level) =
        edit { it[LEVEL] = level.name }

    suspend fun setSnakeSpeed(speed: SnakeSpeed) =
        edit { it[SNAKE_SPEED] = speed.name }

    suspend fun setScale(scale: BoardScale) =
        edit { it[SCALE] = scale.name }

    suspend fun setControlScheme(scheme: ControlScheme) =
        edit { it[CONTROL] = scheme.name }

    suspend fun setSwipeSensitivity(value: Float) =
        edit { it[SWIPE_SENSITIVITY] = value.coerceIn(0f, 1f) }

    suspend fun setBackBehavior(behavior: BackBehavior) =
        edit { it[BACK_BEHAVIOR] = behavior.name }

    suspend fun setMasterVolume(volume: Float) =
        edit { it[MASTER_VOLUME] = volume.coerceIn(0f, 1f) }

    suspend fun setMusicVolume(volume: Float) =
        edit { it[MUSIC_VOLUME] = volume.coerceIn(0f, 1f) }

    suspend fun setSfxVolume(volume: Float) =
        edit { it[SFX_VOLUME] = volume.coerceIn(0f, 1f) }

    suspend fun setCrtEnabled(enabled: Boolean) =
        edit { it[CRT_ENABLED] = enabled }

    suspend fun setHapticsEnabled(enabled: Boolean) =
        edit { it[HAPTICS_ENABLED] = enabled }

    suspend fun setReduceMotion(enabled: Boolean) =
        edit { it[REDUCE_MOTION] = enabled }

    suspend fun setSkin(skin: Skin) =
        edit { it[SKIN] = skin.name }

    suspend fun setTerrain(terrain: BoardTerrain) =
        edit { it[TERRAIN] = terrain.name }

    suspend fun setHazardsEnabled(enabled: Boolean) =
        edit { it[HAZARDS_ENABLED] = enabled }

    suspend fun setSpecialFrequency(value: SpecialFrequency) =
        edit { it[SPECIAL_FREQUENCY] = value.name }

    suspend fun setGameMode(mode: GameMode) =
        edit { it[MODE] = mode.name }

    suspend fun setThemeMode(themeMode: ThemeMode) =
        edit { it[THEME_MODE] = themeMode.name }

    /** Whether the first-run tutorial has been seen or skipped. */
    fun onboardingCompleted(): Flow<Boolean> =
        context.dataStore.data.map { it[ONBOARDING_COMPLETED] ?: false }

    /** Marks the first-run tutorial as seen so it never auto-shows again. */
    suspend fun setOnboardingCompleted(completed: Boolean) =
        edit { it[ONBOARDING_COMPLETED] = completed }

    /** The stored best for a (mode × level × scale) slot (0 if none yet). */
    fun highScore(mode: GameMode, level: Level, scale: BoardScale): Flow<Int> =
        context.dataStore.data.map { it[highScoreKey(mode, level, scale)] ?: 0 }

    /**
     * Records [score] for the slot iff it beats the stored best; returns the
     * resulting best either way.
     */
    suspend fun submitScore(mode: GameMode, level: Level, scale: BoardScale, score: Int): Int {
        val key = highScoreKey(mode, level, scale)
        var best = score
        context.dataStore.edit { prefs ->
            best = max(prefs[key] ?: 0, score)
            prefs[key] = best
        }
        return best
    }

    /** Every recorded highscore, decoded into its [ScoreKey], for the Records screen. */
    fun allHighScores(): Flow<Map<ScoreKey, Int>> =
        context.dataStore.data.map { prefs ->
            buildMap {
                prefs.asMap().forEach { (key, value) ->
                    if (value is Int) ScoreKey.parse(key.name)?.let { put(it, value) }
                }
            }
        }

    /**
     * Levels mode: the deepest run per board scale, stored as the total count
     * of completed levels (cycles × 10 + levels), for the Records screen.
     */
    fun allLevelsProgress(): Flow<Map<BoardScale, Int>> =
        context.dataStore.data.map { prefs ->
            BoardScale.entries.associateWith { prefs[levelsProgressKey(it)] ?: 0 }
        }

    /** Records [levelsCompleted] for the scale iff it beats the stored best. */
    suspend fun submitLevelsProgress(scale: BoardScale, levelsCompleted: Int): Int {
        val key = levelsProgressKey(scale)
        var best = levelsCompleted
        context.dataStore.edit { prefs ->
            best = max(prefs[key] ?: 0, levelsCompleted)
            prefs[key] = best
        }
        return best
    }

    /**
     * Campaign: the furthest 1-based level ever reached (the checkpoint), from
     * which practice starts may begin. 1 until the player first advances.
     */
    fun campaignCheckpoint(): Flow<Int> =
        context.dataStore.data.map { (it[CAMPAIGN_CHECKPOINT] ?: 1).coerceAtLeast(1) }

    /** Raises the stored checkpoint to [levelIndex] iff it is further (monotonic). */
    suspend fun submitCampaignCheckpoint(levelIndex: Int) =
        edit { prefs ->
            prefs[CAMPAIGN_CHECKPOINT] = max(prefs[CAMPAIGN_CHECKPOINT] ?: 1, levelIndex)
        }

    /** Campaign: the player's chosen starting level for the next run (default 1). */
    fun campaignStartLevel(): Flow<Int> =
        context.dataStore.data.map { (it[CAMPAIGN_START_LEVEL] ?: 1).coerceAtLeast(1) }

    /** Persists the chosen Campaign starting level. */
    suspend fun setCampaignStartLevel(levelIndex: Int) =
        edit { it[CAMPAIGN_START_LEVEL] = levelIndex.coerceAtLeast(1) }

    /** The stored Daily Challenge best for [epochDay] (0 if not played yet). */
    fun dailyBest(epochDay: Long): Flow<Int> =
        context.dataStore.data.map { it[dailyBestKey(epochDay)] ?: 0 }

    /** The current Daily Challenge streak (consecutive days played). */
    fun dailyStreak(): Flow<Int> =
        context.dataStore.data.map { it[DAILY_STREAK] ?: 0 }

    /** The last epoch day on which a Daily Challenge was played (null if never). */
    fun dailyLastPlayedDay(): Flow<Long?> =
        context.dataStore.data.map { it[DAILY_LAST_DAY] }

    /**
     * Records [score] for [epochDay]'s daily iff it beats the stored best, and
     * advances the streak on the first play of a new day (consecutive day → +1,
     * otherwise reset to 1). Returns the resulting best.
     */
    suspend fun submitDailyScore(epochDay: Long, score: Int): Int {
        var best = score
        context.dataStore.edit { prefs ->
            val key = dailyBestKey(epochDay)
            best = max(prefs[key] ?: 0, score)
            prefs[key] = best
            val last = prefs[DAILY_LAST_DAY]
            if (last != epochDay) {
                val streak = prefs[DAILY_STREAK] ?: 0
                prefs[DAILY_STREAK] = if (last != null && epochDay == last + 1) streak + 1 else 1
                prefs[DAILY_LAST_DAY] = epochDay
            }
        }
        return best
    }

    /**
     * The stored Daily bests for the [days]-day window ending on [endEpochDay],
     * keyed by epoch day (only days actually played are present). Used by the
     * Daily history / weekly screen (Step 6.9.10).
     */
    fun dailyBests(endEpochDay: Long, days: Int): Flow<Map<Long, Int>> =
        context.dataStore.data.map { prefs ->
            buildMap {
                for (d in (endEpochDay - days + 1)..endEpochDay) {
                    prefs[dailyBestKey(d)]?.let { put(d, it) }
                }
            }
        }

    /** The set of unlocked achievement ids (enum names). */
    fun unlockedAchievements(): Flow<Set<String>> =
        context.dataStore.data.map { it[UNLOCKED_ACHIEVEMENTS] ?: emptySet() }

    /** Adds [ids] to the unlocked set (idempotent). */
    suspend fun addUnlockedAchievements(ids: Collection<String>) =
        edit { it[UNLOCKED_ACHIEVEMENTS] = (it[UNLOCKED_ACHIEVEMENTS] ?: emptySet()) + ids }

    /**
     * The set of unlocked skin ids (enum names) beyond the always-available ones.
     * [Skin.defaultUnlocked] are not stored here; combine with this set in the UI.
     */
    fun unlockedSkins(): Flow<Set<String>> =
        context.dataStore.data.map { it[UNLOCKED_SKINS] ?: emptySet() }

    /** Adds [ids] to the unlocked skin set (idempotent). */
    suspend fun addUnlockedSkins(ids: Collection<String>) =
        edit { it[UNLOCKED_SKINS] = (it[UNLOCKED_SKINS] ?: emptySet()) + ids }

    /**
     * The mission ids completed on [epochDay] (Step 6.9.5). Completions are stored
     * tagged with their day ("epochDay/id") so the daily rotation resets naturally:
     * yesterday's completions never satisfy today's goals.
     */
    fun completedMissionsForDay(epochDay: Long): Flow<Set<String>> =
        context.dataStore.data.map { prefs ->
            val prefix = "$epochDay/"
            (prefs[COMPLETED_MISSIONS] ?: emptySet())
                .asSequence()
                .filter { it.startsWith(prefix) }
                .map { it.removePrefix(prefix) }
                .toSet()
        }

    /** The lifetime count of missions ever completed (across all days). */
    fun completedMissionsTotal(): Flow<Int> =
        context.dataStore.data.map { (it[COMPLETED_MISSIONS] ?: emptySet()).size }

    /** Marks [ids] complete for [epochDay] (idempotent). */
    suspend fun addCompletedMissions(epochDay: Long, ids: Collection<String>) =
        edit { prefs ->
            val tags = ids.map { "$epochDay/$it" }
            prefs[COMPLETED_MISSIONS] = (prefs[COMPLETED_MISSIONS] ?: emptySet()) + tags
        }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private inline fun <T> String?.toEnum(parse: (String) -> T): T? =
        this?.let { runCatching { parse(it) }.getOrNull() }

    private fun highScoreKey(mode: GameMode, level: Level, scale: BoardScale) =
        intPreferencesKey(ScoreKey(mode, level, scale).storageName())

    private fun levelsProgressKey(scale: BoardScale) =
        intPreferencesKey("levels_progress_${scale.name}")

    private fun dailyBestKey(epochDay: Long) =
        intPreferencesKey("daily_best_$epochDay")

    private companion object {
        val LEVEL = stringPreferencesKey("level")
        val SNAKE_SPEED = stringPreferencesKey("snake_speed")
        val SCALE = stringPreferencesKey("board_scale")
        val CONTROL = stringPreferencesKey("control_scheme")
        val SWIPE_SENSITIVITY = floatPreferencesKey("swipe_sensitivity")
        val BACK_BEHAVIOR = stringPreferencesKey("back_behavior")
        val MASTER_VOLUME = floatPreferencesKey("master_volume")
        val MUSIC_VOLUME = floatPreferencesKey("music_volume")
        val SFX_VOLUME = floatPreferencesKey("sfx_volume")
        val CRT_ENABLED = booleanPreferencesKey("crt_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val SKIN = stringPreferencesKey("skin")
        val TERRAIN = stringPreferencesKey("board_terrain")
        val HAZARDS_ENABLED = booleanPreferencesKey("hazards_enabled")
        val SPECIAL_FREQUENCY = stringPreferencesKey("special_frequency")
        val MODE = stringPreferencesKey("game_mode")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val UNLOCKED_ACHIEVEMENTS = stringSetPreferencesKey("unlocked_achievements")
        val UNLOCKED_SKINS = stringSetPreferencesKey("unlocked_skins")
        val COMPLETED_MISSIONS = stringSetPreferencesKey("completed_missions")
        val DAILY_STREAK = intPreferencesKey("daily_streak")
        val DAILY_LAST_DAY = longPreferencesKey("daily_last_day")
        val CAMPAIGN_CHECKPOINT = intPreferencesKey("campaign_checkpoint")
        val CAMPAIGN_START_LEVEL = intPreferencesKey("campaign_start_level")
    }
}

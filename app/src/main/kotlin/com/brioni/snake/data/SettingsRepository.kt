package com.brioni.snake.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.brioni.snake.game.BoardScale
import com.brioni.snake.game.BackBehavior
import com.brioni.snake.game.ControlScheme
import com.brioni.snake.game.GameMode
import com.brioni.snake.game.Level
import com.brioni.snake.game.ScoreKey
import com.brioni.snake.game.Skin
import com.brioni.snake.game.SnakeSpeed
import com.brioni.snake.game.SpecialFrequency
import com.brioni.snake.game.ThemeMode
import com.brioni.snake.game.ViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.max

/** Persisted player preferences, decoded into model types. */
data class Settings(
    val level: Level,
    val scale: BoardScale,
    val controlScheme: ControlScheme,
    val backBehavior: BackBehavior = BackBehavior.DEFAULT,
    val snakeSpeed: SnakeSpeed = SnakeSpeed.DEFAULT,
    val masterVolume: Float = DEFAULT_MASTER_VOLUME,
    val musicVolume: Float = DEFAULT_MUSIC_VOLUME,
    val sfxVolume: Float = DEFAULT_SFX_VOLUME,
    val crtEnabled: Boolean = false,
    /** Animated electric/plasma flow on the 3D boundary barrier (default on). */
    val electricWallsEnabled: Boolean = true,
    val skin: Skin = Skin.Classic,
    val hazardsEnabled: Boolean = true,
    val specialFrequency: SpecialFrequency = SpecialFrequency.Standard,
    val mode: GameMode = GameMode.Classic,
    val themeMode: ThemeMode = ThemeMode.Dark,
    /** The board presentation: flat 2D, follow chase-cam 3D, or fixed-north 3D. */
    val viewMode: ViewMode = ViewMode.TwoD,
)

/** Default audio levels (also used as the in-memory fallback before load). */
const val DEFAULT_MASTER_VOLUME = 1f
const val DEFAULT_MUSIC_VOLUME = 0.5f
const val DEFAULT_SFX_VOLUME = 0.8f

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
            backBehavior = prefs[BACK_BEHAVIOR].toEnum(BackBehavior::valueOf) ?: BackBehavior.DEFAULT,
            snakeSpeed = prefs[SNAKE_SPEED].toEnum(SnakeSpeed::valueOf) ?: SnakeSpeed.DEFAULT,
            masterVolume = prefs[MASTER_VOLUME] ?: DEFAULT_MASTER_VOLUME,
            musicVolume = prefs[MUSIC_VOLUME] ?: DEFAULT_MUSIC_VOLUME,
            sfxVolume = prefs[SFX_VOLUME] ?: DEFAULT_SFX_VOLUME,
            crtEnabled = prefs[CRT_ENABLED] ?: false,
            electricWallsEnabled = prefs[ELECTRIC_WALLS] ?: true,
            skin = prefs[SKIN].toEnum(Skin::valueOf) ?: Skin.Classic,
            hazardsEnabled = prefs[HAZARDS_ENABLED] ?: true,
            specialFrequency = prefs[SPECIAL_FREQUENCY].toEnum(SpecialFrequency::valueOf) ?: SpecialFrequency.Standard,
            mode = prefs[MODE].toEnum(GameMode::valueOf) ?: GameMode.Classic,
            themeMode = prefs[THEME_MODE].toEnum(ThemeMode::valueOf) ?: ThemeMode.Dark,
            viewMode = prefs[VIEW_MODE].toEnum(ViewMode::valueOf)
                // Fall back from the legacy boolean: a stored "true" maps to the
                // follow chase-cam, anything else (or unset) to flat 2D.
                ?: if (prefs[THREE_D_WORLD] == true) ViewMode.ThreeD else ViewMode.TwoD,
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

    suspend fun setElectricWallsEnabled(enabled: Boolean) =
        edit { it[ELECTRIC_WALLS] = enabled }

    suspend fun setSkin(skin: Skin) =
        edit { it[SKIN] = skin.name }

    suspend fun setHazardsEnabled(enabled: Boolean) =
        edit { it[HAZARDS_ENABLED] = enabled }

    suspend fun setSpecialFrequency(value: SpecialFrequency) =
        edit { it[SPECIAL_FREQUENCY] = value.name }

    suspend fun setGameMode(mode: GameMode) =
        edit { it[MODE] = mode.name }

    suspend fun setThemeMode(themeMode: ThemeMode) =
        edit { it[THEME_MODE] = themeMode.name }

    suspend fun setViewMode(mode: ViewMode) =
        edit { it[VIEW_MODE] = mode.name }

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

    /** The set of unlocked achievement ids (enum names). */
    fun unlockedAchievements(): Flow<Set<String>> =
        context.dataStore.data.map { it[UNLOCKED_ACHIEVEMENTS] ?: emptySet() }

    /** Adds [ids] to the unlocked set (idempotent). */
    suspend fun addUnlockedAchievements(ids: Collection<String>) =
        edit { it[UNLOCKED_ACHIEVEMENTS] = (it[UNLOCKED_ACHIEVEMENTS] ?: emptySet()) + ids }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private inline fun <T> String?.toEnum(parse: (String) -> T): T? =
        this?.let { runCatching { parse(it) }.getOrNull() }

    private fun highScoreKey(mode: GameMode, level: Level, scale: BoardScale) =
        intPreferencesKey(ScoreKey(mode, level, scale).storageName())

    private fun levelsProgressKey(scale: BoardScale) =
        intPreferencesKey("levels_progress_${scale.name}")

    private companion object {
        val LEVEL = stringPreferencesKey("level")
        val SNAKE_SPEED = stringPreferencesKey("snake_speed")
        val SCALE = stringPreferencesKey("board_scale")
        val CONTROL = stringPreferencesKey("control_scheme")
        val BACK_BEHAVIOR = stringPreferencesKey("back_behavior")
        val MASTER_VOLUME = floatPreferencesKey("master_volume")
        val MUSIC_VOLUME = floatPreferencesKey("music_volume")
        val SFX_VOLUME = floatPreferencesKey("sfx_volume")
        val CRT_ENABLED = booleanPreferencesKey("crt_enabled")
        val ELECTRIC_WALLS = booleanPreferencesKey("electric_walls")
        val SKIN = stringPreferencesKey("skin")
        val HAZARDS_ENABLED = booleanPreferencesKey("hazards_enabled")
        val SPECIAL_FREQUENCY = stringPreferencesKey("special_frequency")
        val MODE = stringPreferencesKey("game_mode")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THREE_D_WORLD = booleanPreferencesKey("three_d_world")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val UNLOCKED_ACHIEVEMENTS = stringSetPreferencesKey("unlocked_achievements")
    }
}

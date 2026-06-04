package com.brioni.snake.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.brioni.snake.game.BoardScale
import com.brioni.snake.game.ControlScheme
import com.brioni.snake.game.Level
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.max

/** Persisted player preferences, decoded into model types. */
data class Settings(
    val level: Level,
    val scale: BoardScale,
    val controlScheme: ControlScheme,
    val masterVolume: Float = DEFAULT_MASTER_VOLUME,
    val musicVolume: Float = DEFAULT_MUSIC_VOLUME,
    val sfxVolume: Float = DEFAULT_SFX_VOLUME,
)

/** Default audio levels (also used as the in-memory fallback before load). */
const val DEFAULT_MASTER_VOLUME = 1f
const val DEFAULT_MUSIC_VOLUME = 0f
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
            masterVolume = prefs[MASTER_VOLUME] ?: DEFAULT_MASTER_VOLUME,
            musicVolume = prefs[MUSIC_VOLUME] ?: DEFAULT_MUSIC_VOLUME,
            sfxVolume = prefs[SFX_VOLUME] ?: DEFAULT_SFX_VOLUME,
        )
    }

    suspend fun setLevel(level: Level) =
        edit { it[LEVEL] = level.name }

    suspend fun setScale(scale: BoardScale) =
        edit { it[SCALE] = scale.name }

    suspend fun setControlScheme(scheme: ControlScheme) =
        edit { it[CONTROL] = scheme.name }

    suspend fun setMasterVolume(volume: Float) =
        edit { it[MASTER_VOLUME] = volume.coerceIn(0f, 1f) }

    suspend fun setMusicVolume(volume: Float) =
        edit { it[MUSIC_VOLUME] = volume.coerceIn(0f, 1f) }

    suspend fun setSfxVolume(volume: Float) =
        edit { it[SFX_VOLUME] = volume.coerceIn(0f, 1f) }

    /** The stored best for a [level]×[scale] pairing (0 if none yet). */
    fun highScore(level: Level, scale: BoardScale): Flow<Int> =
        context.dataStore.data.map { it[highScoreKey(level, scale)] ?: 0 }

    /**
     * Records [score] for the pairing iff it beats the stored best; returns the
     * resulting best either way.
     */
    suspend fun submitScore(level: Level, scale: BoardScale, score: Int): Int {
        val key = highScoreKey(level, scale)
        var best = score
        context.dataStore.edit { prefs ->
            best = max(prefs[key] ?: 0, score)
            prefs[key] = best
        }
        return best
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private inline fun <T> String?.toEnum(parse: (String) -> T): T? =
        this?.let { runCatching { parse(it) }.getOrNull() }

    private fun highScoreKey(level: Level, scale: BoardScale) =
        intPreferencesKey("highscore_${level.name}_${scale.name}")

    private companion object {
        val LEVEL = stringPreferencesKey("level")
        val SCALE = stringPreferencesKey("board_scale")
        val CONTROL = stringPreferencesKey("control_scheme")
        val MASTER_VOLUME = floatPreferencesKey("master_volume")
        val MUSIC_VOLUME = floatPreferencesKey("music_volume")
        val SFX_VOLUME = floatPreferencesKey("sfx_volume")
    }
}

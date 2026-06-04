package com.brioni.snake.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
)

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
        )
    }

    suspend fun setLevel(level: Level) =
        edit { it[LEVEL] = level.name }

    suspend fun setScale(scale: BoardScale) =
        edit { it[SCALE] = scale.name }

    suspend fun setControlScheme(scheme: ControlScheme) =
        edit { it[CONTROL] = scheme.name }

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
    }
}

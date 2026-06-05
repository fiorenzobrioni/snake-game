package com.brioni.snake.audio

import android.content.Context
import com.brioni.snake.data.SettingsRepository
import com.brioni.snake.game.Food
import com.brioni.snake.game.FoodEffect
import com.brioni.snake.game.FoodTier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Single audio facade owned by the UI for the app's lifetime. Bundles a
 * [SoundManager] (SFX) and a [MusicManager] (background music), seeds their
 * volumes from persisted settings, and implements [GameSfx] so the gameplay
 * loop can emit effects through a clean abstraction.
 *
 * Create once with the application [Context]; call [release] when the host is
 * destroyed.
 */
class GameAudio(context: Context, repo: SettingsRepository) : GameSfx {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sound = SoundManager(context)
    private val music = MusicManager(context, scope)

    init {
        // Seed the initial volumes from persisted settings.
        scope.launch {
            val settings = repo.settings.first()
            applyVolumes(settings.masterVolume, settings.musicVolume, settings.sfxVolume)
        }
    }

    // --- GameSfx: emitted from the gameplay loop -------------------------

    override fun ate(food: Food, combo: Int) {
        val sfx = if (food.isMystery) Sfx.Mystery else Sfx.Eat
        sound.play(sfx, rate = eatRate(food.tier, combo))
    }

    override fun shrunk(food: Food) = sound.play(Sfx.Shrink)

    override fun died() = sound.play(Sfx.GameOver)

    override fun special(food: Food) = sound.play(
        when (food.effect) {
            is FoodEffect.Haste -> Sfx.Lightning
            is FoodEffect.Slow -> Sfx.Snail
            is FoodEffect.Ghost -> Sfx.Star
            is FoodEffect.Freeze -> Sfx.Freeze
            is FoodEffect.Jackpot -> Sfx.Jackpot
            is FoodEffect.Quake -> Sfx.Quake
            is FoodEffect.Burst -> Sfx.Explosion
            // Grow/Shrink never reach here (those route through ate/shrunk).
            else -> Sfx.Eat
        },
    )

    // --- UI sound effects ------------------------------------------------

    fun playUiClick() = sound.play(Sfx.Click)

    fun playPause() = sound.play(Sfx.Pause)

    // --- Music -----------------------------------------------------------

    fun setMusic(track: MusicTrack) = music.play(track)

    fun pauseMusic() = music.pause()

    fun resumeMusic() = music.resume()

    // --- Volumes ---------------------------------------------------------

    /** Applies the three volumes to both engines. */
    fun applyVolumes(master: Float, musicVolume: Float, sfxVolume: Float) {
        sound.setMasterVolume(master)
        sound.setSfxVolume(sfxVolume)
        music.setMasterVolume(master)
        music.setMusicVolume(musicVolume)
    }

    /** Live, un-persisted preview while a Settings slider is being dragged. */
    fun previewVolumes(master: Float, musicVolume: Float, sfxVolume: Float) =
        applyVolumes(master, musicVolume, sfxVolume)

    fun release() {
        music.release()
        sound.release()
        scope.cancel()
    }

    /** Bigger tiers and longer combos nudge the eat pitch up for reward feel. */
    private fun eatRate(tier: FoodTier, combo: Int): Float {
        val tierAdjust = when (tier) {
            FoodTier.Small -> 1.0f
            FoodTier.Medium -> 1.06f
            FoodTier.Large -> 1.12f
            FoodTier.Huge -> 1.18f
            FoodTier.Mystery -> 1.0f
        }
        val comboAdjust = 1f + (combo.coerceIn(1, 6) - 1) * 0.04f
        return (tierAdjust * comboAdjust).coerceIn(0.5f, 2f)
    }
}

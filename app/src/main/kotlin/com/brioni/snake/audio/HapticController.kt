package com.brioni.snake.audio

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.brioni.snake.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Device-vibrator implementation of [GameHaptics], owned by the UI for the app's
 * lifetime (created with the application [Context]; call [release] on host
 * destroy). Gameplay events map to short predefined effects, scaled by purpose.
 *
 * The `hapticsEnabled` setting is observed live so toggling it in Settings takes
 * effect immediately. If the device has no vibrator the controller is a silent
 * no-op. `minSdk 33` guarantees [VibratorManager] and the predefined effects.
 */
class HapticController(context: Context, repo: SettingsRepository) : GameHaptics {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val vibrator: Vibrator? = run {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator?.takeIf { it.hasVibrator() }
    }

    @Volatile
    private var enabled = true

    init {
        scope.launch { repo.settings.collectLatest { enabled = it.hapticsEnabled } }
    }

    override fun eat() = play(predefined(VibrationEffect.EFFECT_TICK))

    override fun special() = play(predefined(VibrationEffect.EFFECT_CLICK))

    // A deliberately faint, brief buzz so a graze is felt but never intrusive.
    override fun nearMiss() = play(VibrationEffect.createOneShot(10L, 40))

    // A crisp "tick-tick" alert, distinct from the faint near-miss, so an
    // about-to-strike hazard is felt a beat before contact.
    override fun hazardWarning() = play(
        VibrationEffect.createWaveform(longArrayOf(0, 18, 28, 18), intArrayOf(0, 150, 0, 150), -1),
    )

    // The strongest cue: a short double "thud".
    override fun death() = play(
        VibrationEffect.createWaveform(longArrayOf(0, 60, 50, 90), intArrayOf(0, 200, 0, 255), -1),
    )

    override fun lifeLost() = play(predefined(VibrationEffect.EFFECT_HEAVY_CLICK))

    override fun levelUp() = play(predefined(VibrationEffect.EFFECT_DOUBLE_CLICK))

    /** Cancels the settings observer; safe to call once on host destroy. */
    fun release() {
        scope.cancel()
    }

    private fun predefined(effectId: Int): VibrationEffect = VibrationEffect.createPredefined(effectId)

    private fun play(effect: VibrationEffect) {
        if (!enabled) return
        vibrator?.vibrate(effect)
    }
}

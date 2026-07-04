package com.callbackdev.snake.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Plays short [Sfx] clips through a single [SoundPool]. All clips are loaded
 * once on construction; [play] is cheap and fire-and-forget. Effective loudness
 * is `master * sfx`, both held here and updated from Settings.
 */
class SoundManager(context: Context) {

    private val appContext = context.applicationContext

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val sampleIds = HashMap<Sfx, Int>()
    private val loaded = HashSet<Int>()

    @Volatile private var masterVolume = 1f
    @Volatile private var sfxVolume = 1f

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded.add(sampleId)
        }
        Sfx.entries.forEach { sfx -> sampleIds[sfx] = pool.load(appContext, sfx.resId, 1) }
    }

    fun setMasterVolume(value: Float) { masterVolume = value.coerceIn(0f, 1f) }

    fun setSfxVolume(value: Float) { sfxVolume = value.coerceIn(0f, 1f) }

    /**
     * Plays [sfx] at the current effective volume. [rate] shifts pitch/tempo
     * (0.5–2.0) for playful per-tier / per-combo variation. No-op until the clip
     * has finished loading or when muted.
     */
    fun play(sfx: Sfx, rate: Float = 1f) {
        val id = sampleIds[sfx] ?: return
        if (id !in loaded) return
        val volume = (masterVolume * sfxVolume).coerceIn(0f, 1f)
        if (volume <= 0f) return
        pool.play(id, volume, volume, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    fun release() = pool.release()

    private companion object {
        const val MAX_STREAMS = 8
    }
}

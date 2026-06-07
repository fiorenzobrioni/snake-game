package com.brioni.snake.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import com.brioni.snake.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The two looping background tracks, each backed by an `res/raw` clip. */
enum class MusicTrack(val resId: Int) {
    Menu(R.raw.music_menu),
    Gameplay(R.raw.music_game),
}

/**
 * Looping background music with a smooth crossfade between [MusicTrack]s, built
 * on two [MediaPlayer]s. Requests audio focus (so other apps duck us cleanly)
 * and exposes lifecycle [pause]/[resume]. Effective loudness is `master * music`.
 *
 * All player mutation happens on [scope]; crossfades ramp volumes over
 * [FADE_MS]. Track preparation runs off the main thread.
 */
class MusicManager(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private var current: MediaPlayer? = null
    private var currentTrack: MusicTrack? = null
    private var fadeJob: Job? = null

    private var masterVolume = 1f
    private var musicVolume = 1f

    /** Whether the player should be sounding (vs. lifecycle-paused). */
    private var active = false

    private var hasFocus = false
    private var focusRequest: AudioFocusRequest? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasFocus = false
                current?.takeIf { it.isPlaying }?.pause()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                setVolume(current, effectiveVolume() * DUCK_FACTOR)

            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                setVolume(current, effectiveVolume())
                if (active) current?.takeIf { !it.isPlaying }?.start()
            }
        }
    }

    /** Starts (or crossfades to) [track]. No-op if already playing it. */
    fun play(track: MusicTrack) {
        active = true
        if (track == currentTrack && current?.isPlaying == true) return
        fadeJob?.cancel()
        fadeJob = scope.launch {
            ensureFocus()
            val incoming = withContext(Dispatchers.IO) { create(track) }
            val outgoing = current
            current = incoming
            currentTrack = track

            val target = effectiveVolume()
            incoming.setVolume(0f, 0f)
            incoming.start()
            for (i in 1..FADE_STEPS) {
                val f = i.toFloat() / FADE_STEPS
                setVolume(incoming, target * f)
                outgoing?.let { setVolume(it, target * (1f - f)) }
                delay(FADE_MS / FADE_STEPS)
            }
            outgoing?.let { runCatching { it.stop() }; it.release() }
        }
    }

    /** Lifecycle pause: silence without losing the current track/position. */
    fun pause() {
        active = false
        fadeJob?.cancel()
        current?.takeIf { it.isPlaying }?.pause()
    }

    /** Lifecycle resume: continue the current track. */
    fun resume() {
        active = true
        if (current == null) return
        ensureFocus()
        setVolume(current, effectiveVolume())
        current?.takeIf { !it.isPlaying }?.start()
    }

    fun setMasterVolume(value: Float) {
        masterVolume = value.coerceIn(0f, 1f)
        if (fadeJob?.isActive != true) setVolume(current, effectiveVolume())
    }

    fun setMusicVolume(value: Float) {
        musicVolume = value.coerceIn(0f, 1f)
        if (fadeJob?.isActive != true) setVolume(current, effectiveVolume())
    }

    fun release() {
        fadeJob?.cancel()
        current?.let { runCatching { it.stop() }; it.release() }
        current = null
        currentTrack = null
        abandonFocus()
    }

    private fun effectiveVolume() = (masterVolume * musicVolume).coerceIn(0f, 1f)

    private fun setVolume(player: MediaPlayer?, level: Float) {
        val v = level.coerceIn(0f, 1f)
        runCatching { player?.setVolume(v, v) }
    }

    private fun create(track: MusicTrack): MediaPlayer = MediaPlayer().apply {
        setAudioAttributes(attributes)
        appContext.resources.openRawResourceFd(track.resId).use { afd ->
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        }
        isLooping = true
        prepare()
    }

    private fun ensureFocus() {
        if (hasFocus) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = request
        val result = audioManager.requestAudioFocus(request)
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        hasFocus = false
    }

    private companion object {
        const val FADE_MS = 600L
        const val FADE_STEPS = 24
        const val DUCK_FACTOR = 0.3f
    }
}

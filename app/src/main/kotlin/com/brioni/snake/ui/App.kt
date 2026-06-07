package com.brioni.snake.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brioni.snake.audio.GameAudio
import com.brioni.snake.audio.MusicTrack
import com.brioni.snake.data.SettingsRepository
import com.brioni.snake.ui.game.GameScreen
import com.brioni.snake.ui.game.GameViewModel
import com.brioni.snake.ui.achievements.AchievementsScreen
import com.brioni.snake.ui.intro.BrandIntroScreen
import com.brioni.snake.ui.menu.MainMenuScreen
import com.brioni.snake.ui.records.RecordsScreen
import com.brioni.snake.ui.settings.SettingsScreen
import kotlin.coroutines.cancellation.CancellationException

/** The top-level destinations. [Intro] is the cold-launch brand splash. */
private enum class Screen { Intro, Menu, Game, Settings, Records, Achievements }

/**
 * Root of the UI. Hosts a lightweight, state-based navigation between the main
 * menu, gameplay and settings, cross-fading between them (also covering the
 * Phase 3.7 fade transitions). A single [SettingsRepository], [GameAudio] and
 * [GameViewModel] are shared across destinations.
 *
 * Audio is owned here: music crossfades to match the active screen (Phase 4.4),
 * the host lifecycle pauses/resumes it (Phase 4.3), and SFX fire on navigation
 * actions and (via the ViewModel) gameplay events.
 */
@Composable
fun App(repo: SettingsRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audio = remember(context) { GameAudio(context.applicationContext, repo) }
    val gameViewModel: GameViewModel = viewModel(factory = GameViewModel.factory(repo, audio))

    var ordinal by rememberSaveable { mutableIntStateOf(Screen.Intro.ordinal) }
    val screen = Screen.entries[ordinal]
    fun navigate(target: Screen) { ordinal = target.ordinal }

    // Crossfade music to match the screen (menu/settings share the menu track).
    LaunchedEffect(screen) {
        audio.setMusic(if (screen == Screen.Game) MusicTrack.Gameplay else MusicTrack.Menu)
    }

    // Lifecycle-aware music + resource cleanup.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> audio.pauseMusic()
                Lifecycle.Event.ON_START -> audio.resumeMusic()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            audio.release()
        }
    }

    // The Game screen owns its own back behaviour (pause vs. exit) and the Menu is
    // the root (system predictive-back exits the app). For the other screens, a
    // PredictiveBackHandler drives the system back gesture: as the user drags, the
    // current screen scales/fades back, then commits to the Menu on release.
    val backProgress = remember { Animatable(0f) }
    var backFromLeftEdge by remember { mutableStateOf(true) }
    PredictiveBackHandler(enabled = screen != Screen.Menu && screen != Screen.Game) { progress ->
        try {
            progress.collect { event ->
                backFromLeftEdge = event.swipeEdge == BackEventCompat.EDGE_LEFT
                backProgress.snapTo(event.progress)
            }
            navigate(Screen.Menu)
            backProgress.snapTo(0f)
        } catch (cancelled: CancellationException) {
            // Gesture aborted: ease the screen back to rest (do not re-throw —
            // this is the canonical PredictiveBackHandler cancel path).
            backProgress.animateTo(0f)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Shared animated AGSL backdrop behind the menu-family screens; it stays
        // put while screens dissolve over it, and is revealed as the predictive
        // back gesture scales the foreground down.
        if (screen != Screen.Game && screen != Screen.Intro) {
            AnimatedShaderBackground(modifier = Modifier.fillMaxSize())
        }

        AnimatedContent(
            targetState = screen,
            transitionSpec = { (fadeIn(tween(260)) togetherWith fadeOut(tween(260))) using null },
            modifier = Modifier.graphicsLayer {
                // Predictive back: the foreground shrinks, slides toward the swipe
                // edge, rounds its corners and dims — revealing the live backdrop.
                val p = backProgress.value
                val s = 1f - 0.18f * p
                scaleX = s
                scaleY = s
                val shift = size.width * 0.08f * p
                translationX = if (backFromLeftEdge) shift else -shift
                alpha = 1f - 0.25f * p
                clip = true
                shape = RoundedCornerShape((28f * p).dp)
            },
            label = "screen",
        ) { current ->
            // A "glass" blur dissolve: each entering screen sharpens into focus,
            // the leaving one blurs out (cheap RenderEffect, always on at minSdk 33).
            val blurRadius by transition.animateDp(
                transitionSpec = { tween(260) },
                label = "screenBlur",
            ) { state -> if (state == EnterExitState.Visible) 0.dp else 16.dp }

            Box(Modifier.fillMaxSize().blur(blurRadius)) {
                when (current) {
                    Screen.Intro -> BrandIntroScreen(
                        onFinished = { navigate(Screen.Menu) },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.Menu -> MainMenuScreen(
                        onPlay = { navigate(Screen.Game) },
                        onRecords = { navigate(Screen.Records) },
                        onAchievements = { navigate(Screen.Achievements) },
                        onSettings = { navigate(Screen.Settings) },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.Game -> GameScreen(
                        viewModel = gameViewModel,
                        audio = audio,
                        onExitToMenu = { navigate(Screen.Menu) },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.Settings -> SettingsScreen(
                        repo = repo,
                        audio = audio,
                        onBack = { navigate(Screen.Menu) },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.Records -> RecordsScreen(
                        repo = repo,
                        onBack = { navigate(Screen.Menu) },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.Achievements -> AchievementsScreen(
                        repo = repo,
                        onBack = { navigate(Screen.Menu) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

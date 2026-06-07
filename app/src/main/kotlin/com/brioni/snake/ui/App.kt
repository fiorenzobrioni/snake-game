package com.brioni.snake.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
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
    // the root (the system runs its predictive exit-to-home). For the other
    // screens a PredictiveBackHandler animates the gesture: the current screen
    // lifts into a card that shrinks and slides off toward the swipe edge,
    // revealing the destination (the Menu) previewed, softly blurred, behind it;
    // releasing commits the back, cancelling eases it back.
    val backProgress = remember { Animatable(0f) }
    var backFromLeftEdge by remember { mutableStateOf(true) }
    var backInProgress by remember { mutableStateOf(false) }
    var instantSwap by remember { mutableStateOf(false) }
    val cardColor = MaterialTheme.colorScheme.surface
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    PredictiveBackHandler(enabled = screen != Screen.Menu && screen != Screen.Game) { progress ->
        backInProgress = true
        try {
            progress.collect { event ->
                backFromLeftEdge = event.swipeEdge == BackEventCompat.EDGE_LEFT
                backProgress.snapTo(event.progress)
            }
            // Committed: swap straight to the destination already previewed behind.
            instantSwap = true
            navigate(Screen.Menu)
            backProgress.snapTo(0f)
            backInProgress = false
        } catch (cancelled: CancellationException) {
            // Aborted: ease the card back to rest, then drop the preview.
            backProgress.animateTo(0f)
            backInProgress = false
        }
    }
    // Re-enable animated transitions after a predictive-commit instant swap.
    LaunchedEffect(screen) { if (instantSwap) instantSwap = false }

    Box(modifier = modifier.fillMaxSize()) {
        // Shared animated AGSL backdrop behind the menu-family screens — only in the
        // dark (brand) theme; the light theme keeps its plain light surface.
        if (darkTheme && screen != Screen.Game && screen != Screen.Intro) {
            AnimatedShaderBackground(modifier = Modifier.fillMaxSize())
        }

        // Destination preview shown during a back gesture: the Menu, behind the
        // card, softly blurred and easing into focus as the gesture progresses.
        if (backInProgress) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = backProgress.value
                        val s = 0.92f + 0.08f * p
                        scaleX = s
                        scaleY = s
                        alpha = 0.6f + 0.4f * p
                        val r = (12f * (1f - p)).dp.toPx()
                        renderEffect = if (r > 0.5f) BlurEffect(r, r) else null
                    },
            ) {
                MainMenuScreen(
                    onPlay = { navigate(Screen.Game) },
                    onRecords = { navigate(Screen.Records) },
                    onAchievements = { navigate(Screen.Achievements) },
                    onSettings = { navigate(Screen.Settings) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (instantSwap) {
                    (EnterTransition.None togetherWith ExitTransition.None) using null
                } else {
                    (fadeIn(tween(260)) togetherWith fadeOut(tween(260))) using null
                }
            },
            modifier = Modifier
                .graphicsLayer {
                    // Predictive back: lift the current screen into a rounded,
                    // shadowed card that shrinks and slides off toward the swipe
                    // edge, fully revealing the destination previewed behind it.
                    val p = backProgress.value
                    val s = 1f - 0.35f * p
                    scaleX = s
                    scaleY = s
                    val dir = if (backFromLeftEdge) 1f else -1f
                    translationX = dir * size.width * 0.85f * p
                    clip = true
                    shape = RoundedCornerShape((34f * p).dp)
                    shadowElevation = 28.dp.toPx() * p
                }
                // Fill the (otherwise transparent) card so it reads as a solid
                // surface peeling away over the previewed destination.
                .drawBehind {
                    if (backInProgress) drawRect(cardColor)
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

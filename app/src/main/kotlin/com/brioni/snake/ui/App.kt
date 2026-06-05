package com.brioni.snake.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.brioni.snake.ui.menu.MainMenuScreen
import com.brioni.snake.ui.records.RecordsScreen
import com.brioni.snake.ui.settings.SettingsScreen

/** The top-level destinations. */
private enum class Screen { Menu, Game, Settings, Records, Achievements }

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

    var ordinal by rememberSaveable { mutableIntStateOf(Screen.Menu.ordinal) }
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

    // The Game screen owns its own back behaviour (pause vs. exit); the app-level
    // handler only covers the other secondary screens.
    BackHandler(enabled = screen != Screen.Menu && screen != Screen.Game) {
        audio.playUiClick(); navigate(Screen.Menu)
    }

    Crossfade(targetState = screen, animationSpec = tween(300), label = "screen") { current ->
        when (current) {
            Screen.Menu -> MainMenuScreen(
                onPlay = { audio.playUiClick(); navigate(Screen.Game) },
                onRecords = { audio.playUiClick(); navigate(Screen.Records) },
                onAchievements = { audio.playUiClick(); navigate(Screen.Achievements) },
                onSettings = { audio.playUiClick(); navigate(Screen.Settings) },
                modifier = modifier,
            )

            Screen.Game -> GameScreen(
                viewModel = gameViewModel,
                audio = audio,
                onExitToMenu = { navigate(Screen.Menu) },
                modifier = modifier,
            )

            Screen.Settings -> SettingsScreen(
                repo = repo,
                audio = audio,
                onBack = { audio.playUiClick(); navigate(Screen.Menu) },
                modifier = modifier,
            )

            Screen.Records -> RecordsScreen(
                repo = repo,
                onBack = { audio.playUiClick(); navigate(Screen.Menu) },
                modifier = modifier,
            )

            Screen.Achievements -> AchievementsScreen(
                repo = repo,
                onBack = { audio.playUiClick(); navigate(Screen.Menu) },
                modifier = modifier,
            )
        }
    }
}

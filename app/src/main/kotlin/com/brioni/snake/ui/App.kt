package com.brioni.snake.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brioni.snake.data.SettingsRepository
import com.brioni.snake.ui.game.GameScreen
import com.brioni.snake.ui.game.GameViewModel
import com.brioni.snake.ui.menu.MainMenuScreen
import com.brioni.snake.ui.settings.SettingsScreen

/** The three top-level destinations. */
private enum class Screen { Menu, Game, Settings }

/**
 * Root of the UI. Hosts a lightweight, state-based navigation between the main
 * menu, gameplay and settings, cross-fading between them (also covering the
 * Phase 3.7 fade transitions). A single [SettingsRepository] and a single
 * [GameViewModel] are shared across destinations.
 */
@Composable
fun App(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember(context) { SettingsRepository(context.applicationContext) }
    val gameViewModel: GameViewModel = viewModel(factory = GameViewModel.factory(repo))

    var ordinal by rememberSaveable { mutableIntStateOf(Screen.Menu.ordinal) }
    val screen = Screen.entries[ordinal]
    fun navigate(target: Screen) { ordinal = target.ordinal }

    BackHandler(enabled = screen != Screen.Menu) { navigate(Screen.Menu) }

    Crossfade(targetState = screen, animationSpec = tween(300), label = "screen") { current ->
        when (current) {
            Screen.Menu -> MainMenuScreen(
                onPlay = { navigate(Screen.Game) },
                onSettings = { navigate(Screen.Settings) },
                modifier = modifier,
            )

            Screen.Game -> GameScreen(
                viewModel = gameViewModel,
                onExitToMenu = { navigate(Screen.Menu) },
                modifier = modifier,
            )

            Screen.Settings -> SettingsScreen(
                repo = repo,
                onBack = { navigate(Screen.Menu) },
                modifier = modifier,
            )
        }
    }
}

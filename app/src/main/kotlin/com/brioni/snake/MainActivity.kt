package com.brioni.snake

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.brioni.snake.data.Settings
import com.brioni.snake.data.SettingsRepository
import com.brioni.snake.game.BoardScale
import com.brioni.snake.game.ControlScheme
import com.brioni.snake.game.Level
import com.brioni.snake.game.ThemeMode
import com.brioni.snake.ui.App
import com.brioni.snake.ui.theme.SnakeGameTheme

/**
 * Single entry point of the app. Hosts the Compose game surface: edge-to-edge,
 * portrait, full-screen, with content kept clear of system bars / cutouts.
 */
class MainActivity : ComponentActivity() {
    // Latest resolved *app* dark-theme flag, kept by the composition so the splash
    // exit can restore the right status-bar icon colour (see applyBarAppearance).
    private var appDarkTheme: Boolean = false

    /**
     * Set the system-bar icon colour to match the app theme. The splash screen owns
     * the bar appearance during a cold start, so our edge-to-edge calls run while it
     * is up and get reset to the (light-icon) default once it is removed. We re-apply
     * here after the splash is gone — and on every theme change — so a Light app theme
     * on a dark-mode device gets dark, readable icons from the first frame.
     */
    private fun applyBarAppearance() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !appDarkTheme
        controller.isAppearanceLightNavigationBars = !appDarkTheme
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Fade the system splash out so it hands off smoothly to the Compose
        // brand-intro screen instead of cutting abruptly.
        installSplashScreen().setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(250L)
                .withEndAction {
                    provider.remove()
                    // Restore the app-themed bar icons the splash left at its default.
                    applyBarAppearance()
                }
                .start()
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val repo = remember { SettingsRepository(applicationContext) }
            val settings by repo.settings.collectAsState(
                initial = Settings(Level.Beginner, BoardScale.Classic, ControlScheme.Swipe),
            )
            val darkTheme = when (settings.themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                ThemeMode.System -> isSystemInDarkTheme()
            }
            // Drive the system-bar icon colour from the *app* theme, not the system
            // one: edge-to-edge detects dark mode from the system config by default,
            // so a Light app theme on a dark-mode device would leave light (invisible)
            // status-bar icons on our light background. Re-applying enableEdgeToEdge
            // with a custom detectDarkMode that returns our own darkTheme is the
            // supported hook, and it re-runs whenever the theme changes.
            DisposableEffect(darkTheme) {
                appDarkTheme = darkTheme
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                    ) { darkTheme },
                )
                // Also set it imperatively so post-splash theme changes take effect
                // immediately (the splash is gone, so nothing resets it afterwards).
                applyBarAppearance()
                onDispose {}
            }
            // The selected terrain seeds the UI accent colours (see SnakeGameTheme),
            // so the whole interface recolours with the player's chosen world.
            SnakeGameTheme(terrain = settings.terrain, darkTheme = darkTheme) {
                SnakeApp(repo)
            }
        }
    }
}

@Composable
private fun SnakeApp(repo: SettingsRepository) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        // Apply safe-area insets here, OUTSIDE the App's Crossfade, so the padding
        // node is created once and stays stable. Padding inside the Crossfade was
        // recreated per screen, and the first GameScreen layout could see 0 insets
        // for a frame — locking the board to a too-tall area that then ran under
        // the navigation bar until the next game.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            App(repo = repo, modifier = Modifier.fillMaxSize())
        }
    }
}

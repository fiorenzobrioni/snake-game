package com.brioni.snake

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
    override fun onCreate(savedInstanceState: Bundle?) {
        // Fade the system splash out so it hands off smoothly to the Compose
        // brand-intro screen instead of cutting abruptly.
        installSplashScreen().setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(250L)
                .withEndAction { provider.remove() }
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
            SnakeGameTheme(darkTheme = darkTheme) {
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

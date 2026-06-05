package com.brioni.snake

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
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
        installSplashScreen()
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
        // safeDrawingPadding keeps content clear of system bars / cutouts while
        // the background still draws edge-to-edge behind them.
        App(
            repo = repo,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        )
    }
}

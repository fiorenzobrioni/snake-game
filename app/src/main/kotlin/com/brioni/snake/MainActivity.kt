package com.brioni.snake

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
            SnakeGameTheme {
                SnakeApp()
            }
        }
    }
}

@Composable
private fun SnakeApp() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        // safeDrawingPadding keeps content clear of system bars / cutouts while
        // the background still draws edge-to-edge behind them.
        App(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        )
    }
}

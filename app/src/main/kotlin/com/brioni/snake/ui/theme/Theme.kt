package com.brioni.snake.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = SnakeGreenBright,
    secondary = SnakeGreen,
    tertiary = FoodGold,
    background = BoardBackground,
    surface = BoardSurface,
    onBackground = OnDark,
    onSurface = OnDark,
)

private val LightColors = lightColorScheme(
    primary = SnakeGreen,
    secondary = SnakeGreenBright,
    tertiary = FoodGold,
)

/**
 * App-wide Material 3 theme. The game leans dark by design, so the dark scheme
 * is used unless the system explicitly requests light. Dynamic color is left
 * off on purpose to keep a consistent brand look across devices.
 */
@Composable
fun SnakeGameTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SnakeTypography,
        content = content,
    )
}

package com.callbackdev.snake.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.callbackdev.snake.game.BoardTerrain

/**
 * App-wide Material 3 theme. The game leans dark by design, so the dark scheme
 * is used unless the system explicitly requests light. System dynamic color is
 * left off on purpose; instead the accent pair is seeded by the player's
 * selected [BoardTerrain] (see [TerrainAccents]) — the in-app equivalent of
 * Material You, with the terrain as the seed. Accent changes cross-fade so
 * switching terrains in Settings recolours the whole interface smoothly.
 */
@Composable
fun SnakeGameTheme(
    terrain: BoardTerrain = BoardTerrain.Meadow,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val accents = if (darkTheme) darkTerrainAccents(terrain) else lightTerrainAccents(terrain)
    val primary by animateAccent(accents.primary, "primaryAccent")
    val secondary by animateAccent(accents.secondary, "secondaryAccent")

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = primary,
            secondary = secondary,
            tertiary = FoodGold,
            background = BoardBackground,
            surface = BoardSurface,
            onBackground = OnDark,
            onSurface = OnDark,
        )
    } else {
        lightColorScheme(
            primary = primary,
            secondary = secondary,
            tertiary = FoodGold,
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SnakeTypography,
        content = content,
    )
}

@Composable
private fun animateAccent(target: Color, label: String) =
    animateColorAsState(targetValue = target, animationSpec = tween(ACCENT_FADE_MILLIS), label = label)

/** How long an accent-recolour cross-fade takes when the terrain (or theme) changes. */
private const val ACCENT_FADE_MILLIS = 600

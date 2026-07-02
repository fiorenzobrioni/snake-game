package com.brioni.snake.ui.theme

import androidx.compose.ui.graphics.Color
import com.brioni.snake.game.BoardTerrain

// Snake palette — a dark, arcade-leaning base. Tuned further in Phase 3.
val SnakeGreen = Color(0xFF4CAF50)
val SnakeGreenBright = Color(0xFF7CFC00) // Chartreuse-ish head accent
val FoodRed = Color(0xFFE53935)
val FoodGold = Color(0xFFFFC107)
val BoardBackground = Color(0xFF101418)
val BoardSurface = Color(0xFF1B2127)
val OnDark = Color(0xFFECEFF1)

/**
 * The UI accent pair a [BoardTerrain] seeds into the Material colour scheme —
 * the app's take on Material You's dynamic colour: instead of the wallpaper,
 * the player's chosen terrain recolours the interface, so the menus live in the
 * same world as the board. [primary] carries the high-emphasis chrome (filled
 * buttons, selected states, headlines), [secondary] the supporting tints.
 *
 * These are *tuned* accents, not the raw in-game frame colours
 * (`terrainBoardBorder`): the dark variants are lifted bright enough to fill a
 * button behind dark ink, the light variants sunk dark enough to read on a pale
 * surface. Meadow — the out-of-the-box default — keeps the original brand
 * greens exactly, and Arcade (the skin-following floor) stays on brand too, so
 * a fresh install looks unchanged.
 */
data class TerrainAccents(val primary: Color, val secondary: Color)

/** Accents for [terrain] in the dark (brand) theme: bright fills over dark ink. */
fun darkTerrainAccents(terrain: BoardTerrain): TerrainAccents = when (terrain) {
    BoardTerrain.Meadow,
    BoardTerrain.Arcade -> TerrainAccents(SnakeGreenBright, SnakeGreen)
    BoardTerrain.Abyss -> TerrainAccents(Color(0xFF4DDCFF), Color(0xFF2FA8CC))
    BoardTerrain.Nebula -> TerrainAccents(Color(0xFFB79CFF), Color(0xFF8C7BFF))
    BoardTerrain.Dunes -> TerrainAccents(Color(0xFFFFC46B), Color(0xFFD99A50))
    BoardTerrain.Glacier -> TerrainAccents(Color(0xFFA9E2FF), Color(0xFF7FB8DE))
}

/** Accents for [terrain] in the light theme: deeper tones over pale surfaces. */
fun lightTerrainAccents(terrain: BoardTerrain): TerrainAccents = when (terrain) {
    BoardTerrain.Meadow,
    BoardTerrain.Arcade -> TerrainAccents(SnakeGreen, SnakeGreenBright)
    BoardTerrain.Abyss -> TerrainAccents(Color(0xFF0E7FA8), Color(0xFF2EB6D9))
    BoardTerrain.Nebula -> TerrainAccents(Color(0xFF6A55C9), Color(0xFF9C7BFF))
    BoardTerrain.Dunes -> TerrainAccents(Color(0xFFA06A2C), Color(0xFFC89052))
    BoardTerrain.Glacier -> TerrainAccents(Color(0xFF3E7FA8), Color(0xFF7CC0E8))
}

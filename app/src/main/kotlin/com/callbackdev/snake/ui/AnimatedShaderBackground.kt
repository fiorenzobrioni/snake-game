package com.callbackdev.snake.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.callbackdev.snake.game.BoardTerrain
import com.callbackdev.snake.ui.game.Shaders
import com.callbackdev.snake.ui.game.TerrainLayer

/**
 * A full-area, gently animated AGSL background for the menu-family screens: the
 * player's selected [terrain] floor, so the menus live in the same world as the
 * board ([BoardTerrain.Arcade] keeps the brand's dark-arcade drifting-glows
 * gradient). AGSL is always available (minSdk 33), so there is no fallback path.
 *
 * The terrain floors are tuned for gameplay, not for reading text, so a dark
 * vertical scrim is layered on top — lighter around the middle where the brand
 * hero sits, heavier towards the edges where controls and small labels live —
 * keeping the world visible as ambience without costing contrast.
 */
@Composable
fun AnimatedShaderBackground(
    modifier: Modifier = Modifier,
    terrain: BoardTerrain = BoardTerrain.Arcade,
) {
    val layer = remember(terrain) {
        TerrainLayer(Shaders.menuBackdropSource(terrain)).apply {
            if (terrain == BoardTerrain.Arcade) {
                // The arcade backdrop keeps the brand's dark gradient (the Classic
                // board colours); in-game the board feeds these from the active skin.
                shader.setColorUniform("topColor", MENU_TOP_COLOR)
                shader.setColorUniform("bottomColor", MENU_BOTTOM_COLOR)
            }
        }
    }
    var timeSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> timeSeconds = (now - start) / 1_000_000_000f }
        }
    }

    Canvas(modifier) {
        layer.shader.setFloatUniform("origin", 0f, 0f)
        layer.shader.setFloatUniform("resolution", size.width, size.height)
        layer.shader.setFloatUniform("time", timeSeconds)
        if (terrain != BoardTerrain.Arcade) {
            // A board-like cell pitch so cell-aligned features (e.g. the Meadow
            // checker) read at full-screen scale.
            layer.shader.setFloatUniform("cellPx", size.width / MENU_CELL_COLUMNS)
        }
        drawRect(layer.brush)
        if (terrain != BoardTerrain.Arcade) {
            // The dimming scrim (the arcade gradient is already menu-dark).
            drawRect(
                Brush.verticalGradient(
                    0f to MENU_SCRIM_EDGE,
                    0.38f to MENU_SCRIM_CENTER,
                    1f to MENU_SCRIM_EDGE,
                ),
            )
        }
    }
}

/** The arcade backdrop's fixed gradient endpoints (the Classic board colours). */
private val MENU_TOP_COLOR = 0xFF121A22.toInt()
private val MENU_BOTTOM_COLOR = 0xFF0A0E13.toInt()

/** Cell columns the menu backdrop pretends to have (a board-like pitch). */
private const val MENU_CELL_COLUMNS = 12f

/** Scrim over a terrain floor: heavy at the edges, lighter behind the brand hero. */
private val MENU_SCRIM_EDGE = Color(0xCC070A0D)
private val MENU_SCRIM_CENTER = Color(0x8C070A0D)

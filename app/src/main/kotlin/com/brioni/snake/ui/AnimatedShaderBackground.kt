package com.brioni.snake.ui

import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ShaderBrush
import com.brioni.snake.ui.game.Shaders

/**
 * A full-area, gently animated AGSL background — the same drifting-glows +
 * vignette shader the game board uses ([Shaders.BACKGROUND]) — so the menus feel
 * as alive as gameplay. AGSL is always available (minSdk 33), so there is no
 * fallback path. The `time` uniform is advanced every frame.
 */
@Composable
fun AnimatedShaderBackground(modifier: Modifier = Modifier) {
    val shader = remember {
        RuntimeShader(Shaders.BACKGROUND).apply {
            // The menus keep the brand's dark-arcade gradient (the Classic board
            // colours); in-game the board feeds these from the active skin.
            setColorUniform("topColor", MENU_TOP_COLOR)
            setColorUniform("bottomColor", MENU_BOTTOM_COLOR)
        }
    }
    val brush = remember { ShaderBrush(shader) }
    var timeSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> timeSeconds = (now - start) / 1_000_000_000f }
        }
    }

    Canvas(modifier) {
        shader.setFloatUniform("origin", 0f, 0f)
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("time", timeSeconds)
        drawRect(brush)
    }
}

/** The menu backdrop's fixed gradient endpoints (the Classic board colours). */
private val MENU_TOP_COLOR = 0xFF121A22.toInt()
private val MENU_BOTTOM_COLOR = 0xFF0A0E13.toInt()

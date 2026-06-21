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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import com.brioni.snake.ui.game.Shaders

// Brand backdrop palette for the menus: a deep blue-black gradient lit by a
// teal-green (the snake) and a cool blue accent. Run at full intensity so the
// menu feels more alive than the deliberately-subtle in-game board.
private val MenuTop = Color(0xFF101A24)
private val MenuBottom = Color(0xFF06080D)
private val MenuGlowA = Color(0xFF34E0A0)
private val MenuGlowB = Color(0xFF3A78F0)

/**
 * A full-area, richly animated AGSL background — the same shader the game board
 * uses ([Shaders.BACKGROUND]) — so the menus feel as alive as gameplay: a drifting
 * nebula, breathing glows, a slow light sweep and a vignette over a brand gradient.
 * AGSL is always available (minSdk 33), so there is no fallback path. The `time`
 * uniform is advanced every frame.
 */
@Composable
fun AnimatedShaderBackground(modifier: Modifier = Modifier) {
    val shader = remember { RuntimeShader(Shaders.BACKGROUND) }
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
        shader.setFloatUniform("intensity", 1f)
        shader.setColorUniform("topColor", MenuTop.toArgb())
        shader.setColorUniform("bottomColor", MenuBottom.toArgb())
        shader.setColorUniform("glowA", MenuGlowA.toArgb())
        shader.setColorUniform("glowB", MenuGlowB.toArgb())
        drawRect(brush)
    }
}

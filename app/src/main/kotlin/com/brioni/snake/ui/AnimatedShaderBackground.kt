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
        drawRect(brush)
    }
}

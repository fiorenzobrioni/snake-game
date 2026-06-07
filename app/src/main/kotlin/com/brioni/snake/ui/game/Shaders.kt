package com.brioni.snake.ui.game

import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.ShaderBrush

/**
 * AGSL ([android.graphics.RuntimeShader]) sources and a holder for the GPU
 * effects. AGSL requires Android 13+, which is the app's `minSdk`, so these are
 * always available.
 *
 * Skia evaluates shaders in **premultiplied** alpha, so the glow/halo shaders
 * return `rgb * a` with alpha `a`.
 */
object Shaders {

    /**
     * Animated board background: the Phase 2 vertical gradient brought to life
     * with two slow-drifting glows and a soft vignette.
     */
    const val BACKGROUND = """
        uniform float2 origin;
        uniform float2 resolution;
        uniform float time;
        half4 main(float2 fragCoord) {
            float2 uv = (fragCoord - origin) / resolution;
            float3 top = float3(0.071, 0.102, 0.133);
            float3 bottom = float3(0.039, 0.055, 0.075);
            float3 col = mix(top, bottom, uv.y);
            float2 p1 = float2(0.25 + 0.15 * sin(time * 0.25), 0.30 + 0.10 * cos(time * 0.20));
            float2 p2 = float2(0.75 + 0.12 * cos(time * 0.18), 0.70 + 0.12 * sin(time * 0.23));
            float g1 = 0.05 / (distance(uv, p1) + 0.08);
            float g2 = 0.05 / (distance(uv, p2) + 0.09);
            col += float3(0.10, 0.45, 0.30) * g1 * 0.10;
            col += float3(0.16, 0.30, 0.52) * g2 * 0.08;
            float vig = smoothstep(1.15, 0.35, distance(uv, float2(0.5, 0.5)));
            col *= mix(0.82, 1.0, vig);
            return half4(col, 1.0);
        }
    """

    /** Pulsing, gently rotating radial glow for the snake's head. */
    const val GLOW = """
        uniform float2 center;
        uniform float radius;
        uniform float time;
        layout(color) uniform half4 glowColor;
        half4 main(float2 fragCoord) {
            float d = distance(fragCoord, center) / radius;
            float pulse = 0.85 + 0.15 * sin(time * 6.0);
            float a = exp(-d * d * 3.5) * pulse;
            float ang = atan(fragCoord.y - center.y, fragCoord.x - center.x);
            a *= 0.82 + 0.18 * sin(ang * 6.0 + time * 4.0);
            a = clamp(a, 0.0, 1.0);
            return half4(glowColor.rgb * half(a), half(a));
        }
    """

    /** Pulsing outline + halo for rare foods (maxi / mystery / huge). */
    const val FOOD_HALO = """
        uniform float2 center;
        uniform float radius;
        uniform float time;
        layout(color) uniform half4 ringColor;
        half4 main(float2 fragCoord) {
            float d = distance(fragCoord, center) / radius;
            float r = 0.72 + 0.10 * sin(time * 3.0);
            float ring = smoothstep(0.10, 0.0, abs(d - r));
            float halo = exp(-d * d * 2.5) * 0.45;
            float a = clamp(ring * 0.8 + halo, 0.0, 1.0);
            return half4(ringColor.rgb * half(a), half(a));
        }
    """

    /** Optional CRT post-filter: scanlines + vignette over the board layer. */
    const val CRT = """
        uniform shader content;
        uniform float2 resolution;
        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            half4 c = content.eval(fragCoord);
            float scan = 0.90 + 0.10 * sin(uv.y * resolution.y * 1.5);
            c.rgb *= half(scan);
            float vig = smoothstep(1.25, 0.35, distance(uv, float2(0.5, 0.5)));
            c.rgb *= half(mix(0.75, 1.0, vig));
            return c;
        }
    """
}

/**
 * Live [RuntimeShader] instances (and their [ShaderBrush] wrappers) for the
 * in-board effects, created once and mutated per frame via `setFloatUniform` /
 * `setColorUniform`.
 */
class BoardShaders {
    val background = RuntimeShader(Shaders.BACKGROUND)
    val glow = RuntimeShader(Shaders.GLOW)
    val foodHalo = RuntimeShader(Shaders.FOOD_HALO)

    val backgroundBrush = ShaderBrush(background)
    val glowBrush = ShaderBrush(glow)
    val foodHaloBrush = ShaderBrush(foodHalo)
}

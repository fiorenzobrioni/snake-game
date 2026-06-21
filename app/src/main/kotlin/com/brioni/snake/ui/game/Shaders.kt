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
     * Animated board / menu background: a vertical gradient ([topColor] →
     * [bottomColor]) brought to life with a slow-drifting noise "nebula" tinted
     * between two accents ([glowA]/[glowB]), two breathing bloom glows, a faint
     * diagonal light sweep, a soft vignette and ordered dithering to kill banding
     * on the dark gradient. [intensity] scales every additive layer (0 = just the
     * gradient + vignette) so the board can stay subtle while the menu turns it up.
     * Colours are uniforms so each skin (and the menu) gets its own palette.
     */
    const val BACKGROUND = """
        uniform float2 origin;
        uniform float2 resolution;
        uniform float time;
        uniform float intensity;
        layout(color) uniform half4 topColor;
        layout(color) uniform half4 bottomColor;
        layout(color) uniform half4 glowA;
        layout(color) uniform half4 glowB;

        float hash(float2 p) { return fract(sin(dot(p, float2(41.3, 289.1))) * 43758.5453); }
        float vnoise(float2 p) {
            float2 i = floor(p);
            float2 f = fract(p);
            float2 u = f * f * (3.0 - 2.0 * f);
            float a = hash(i);
            float b = hash(i + float2(1.0, 0.0));
            float c = hash(i + float2(0.0, 1.0));
            float d = hash(i + float2(1.0, 1.0));
            return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
        }
        float fbm(float2 p) {
            float v = 0.0;
            float amp = 0.5;
            for (int k = 0; k < 4; k++) { v += amp * vnoise(p); p *= 2.0; amp *= 0.5; }
            return v;
        }

        half4 main(float2 fragCoord) {
            float2 uv = (fragCoord - origin) / resolution;
            float aspect = resolution.x / max(resolution.y, 1.0);

            // Base gradient (eased so the midtones are smooth).
            float gy = smoothstep(0.0, 1.0, uv.y);
            float3 col = mix(float3(topColor.rgb), float3(bottomColor.rgb), gy);

            // Atmosphere is calmest at the centre (where the action / menu title sit)
            // and blooms toward the edges, so it never flattens contrast on the play.
            float centerness = smoothstep(0.55, 0.0, distance(uv, float2(0.5, 0.5)));
            float atmo = intensity * (1.0 - 0.5 * centerness);

            // Drifting nebula: faint coloured clouds that crossfade between accents.
            float2 p = uv * float2(aspect, 1.0);
            float n = fbm(p * 2.3 + float2(time * 0.03, time * 0.05));
            float neb = smoothstep(0.38, 1.0, n);
            col += mix(float3(glowA.rgb), float3(glowB.rgb), n) * neb * 0.09 * atmo;

            // Two large breathing bloom glows.
            float2 c1 = float2(0.28 + 0.16 * sin(time * 0.23), 0.34 + 0.12 * cos(time * 0.19));
            float2 c2 = float2(0.74 + 0.13 * cos(time * 0.17), 0.68 + 0.13 * sin(time * 0.21));
            float b1 = 0.85 + 0.15 * sin(time * 0.70);
            float b2 = 0.85 + 0.15 * sin(time * 0.50 + 1.7);
            float d1 = distance(uv, c1);
            float d2 = distance(uv, c2);
            col += float3(glowA.rgb) * (exp(-d1 * d1 * 7.0) * 0.20 * b1 * atmo);
            col += float3(glowB.rgb) * (exp(-d2 * d2 * 8.0) * 0.16 * b2 * atmo);

            // A slow diagonal light sweep for a sense of motion.
            float sweep = sin((uv.x + uv.y) * 2.2 - time * 0.33);
            col += float3(glowA.rgb) * smoothstep(0.92, 1.0, sweep) * 0.05 * atmo;

            // Vignette.
            float vig = smoothstep(1.15, 0.32, distance(uv, float2(0.5, 0.5)));
            col *= mix(0.70, 1.0, vig);

            // Ordered dither breaks up banding on the dark gradient.
            float dither = (hash(fragCoord) - 0.5) / 255.0;
            col += dither;

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

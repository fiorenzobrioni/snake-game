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

    /**
     * Subtle electric/plasma flow for the 3D boundary barrier's translucent face.
     * Fed a screen->UV homography ([h0]/[h1]/[h2]) so the energy flows *along the
     * wall* in perspective (u runs along the wall, v up it) instead of being pinned
     * to the screen. Drifting fbm + thin travelling filaments, brightest near the
     * top/bottom edges; kept low-alpha for an elegant shimmer. Premultiplied output.
     */
    const val WALL_FIELD = """
        uniform float3 h0;
        uniform float3 h1;
        uniform float3 h2;
        uniform float time;
        uniform float intensity;
        layout(color) uniform half4 fieldColor;

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
            float3 p = float3(fragCoord, 1.0);
            float w = dot(h2, p);
            float2 uv = float2(dot(h0, p), dot(h1, p)) / w;
            // Travelling energy bands rising up the wall, warped by drifting noise.
            float warp = fbm(float2(uv.x * 4.0 - time * 0.15, uv.y * 3.0 + time * 0.1));
            float band = sin(uv.y * 16.0 - time * 2.2 + warp * 5.0 + uv.x * 3.0);
            band = pow(max(band, 0.0), 6.0);
            float shimmer = fbm(float2(uv.x * 6.0 + time * 0.2, uv.y * 5.0 - time * 0.25));
            // Brighter toward the top/bottom rails, softly faded at the side edges.
            float vEdge = smoothstep(0.0, 0.35, uv.y) * smoothstep(1.0, 0.65, uv.y);
            float vRails = 1.0 - 0.7 * vEdge;
            float uFade = smoothstep(0.0, 0.05, uv.x) * smoothstep(1.0, 0.95, uv.x);
            float a = (0.045 * shimmer + 0.11 * band * vRails) * uFade * intensity;
            a = clamp(a, 0.0, 0.20);
            // Hot near-white core on the filaments, the field colour elsewhere.
            float3 col = mix(float3(fieldColor.rgb), float3(1.0), band * 0.6);
            return half4(half3(col * a), half(a));
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
    // Guarded: if this AGSL ever fails to compile on a device, the caller falls back
    // to the plain translucent field rather than crashing the whole board renderer.
    val wallField: RuntimeShader? = runCatching { RuntimeShader(Shaders.WALL_FIELD) }.getOrNull()

    val backgroundBrush = ShaderBrush(background)
    val glowBrush = ShaderBrush(glow)
    val foodHaloBrush = ShaderBrush(foodHalo)
    val wallFieldBrush: ShaderBrush? = wallField?.let { ShaderBrush(it) }
}

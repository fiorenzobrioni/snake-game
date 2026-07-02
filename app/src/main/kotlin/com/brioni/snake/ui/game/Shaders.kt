package com.brioni.snake.ui.game

import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.ShaderBrush
import com.brioni.snake.game.BoardTerrain

/**
 * AGSL ([android.graphics.RuntimeShader]) sources and a holder for the GPU
 * effects. AGSL requires Android 13+, which is the app's `minSdk`, so these are
 * always available.
 *
 * Skia evaluates shaders in **premultiplied** alpha, so the glow/halo shaders
 * return `rgb * a` with alpha `a`.
 *
 * The board floor comes in two flavours: [BACKGROUND] (the skin's own gradient,
 * colours passed as uniforms) and the five standalone **terrains** (Meadow /
 * Abyss / Nebula / Dunes / Circuit) selectable in Settings. Terrains share the
 * same uniform interface — `origin`, `resolution`, `time`, `cellPx` — so the
 * renderer drives any of them identically. They are designed as *stages*: dark,
 * desaturated and slowly animated, so gameplay stays readable under every skin.
 */
object Shaders {

    /**
     * Animated board background: the Phase 2 vertical gradient brought to life
     * with two slow-drifting glows and a soft vignette. The gradient endpoints
     * arrive as uniforms (the active skin's `boardTop`/`boardBottom`), so every
     * skin — not just Classic — gets its own floor colours.
     */
    const val BACKGROUND = """
        uniform float2 origin;
        uniform float2 resolution;
        uniform float time;
        layout(color) uniform half4 topColor;
        layout(color) uniform half4 bottomColor;
        half4 main(float2 fragCoord) {
            float2 uv = (fragCoord - origin) / resolution;
            float3 col = mix(float3(topColor.rgb), float3(bottomColor.rgb), uv.y);
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

    /**
     * Meadow terrain: a mowed lawn. Two-tone grass stripes checkered on the play
     * grid (via `cellPx`), a fine vertically-combed blade texture, and soft cloud
     * shadows drifting slowly across the field.
     */
    const val MEADOW = """
        uniform float2 origin;
        uniform float2 resolution;
        uniform float time;
        uniform float cellPx;
        float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
        float vnoise(float2 p) {
            float2 i = floor(p);
            float2 f = fract(p);
            float2 u = f * f * (3.0 - 2.0 * f);
            return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
                       mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
        }
        half4 main(float2 fragCoord) {
            float2 p = fragCoord - origin;
            float2 uv = p / resolution;
            // Mowed-lawn checker aligned with the play grid.
            float2 cellId = floor(p / cellPx);
            float checker = mod(cellId.x + cellId.y, 2.0);
            float3 col = mix(float3(0.055, 0.135, 0.045), float3(0.072, 0.165, 0.058), checker);
            // Blade micro-texture: fine noise stretched vertically like combed grass.
            float blades = vnoise(float2(p.x * 0.85, p.y * 0.18));
            col *= 0.86 + 0.28 * blades;
            // Cloud shadows drifting slowly over the field.
            float2 cp = p / resolution.y;
            float cloud = 0.6 * vnoise(cp * 1.7 + float2(time * 0.030, time * 0.012))
                        + 0.4 * vnoise(cp * 3.4 - float2(time * 0.020, 0.0));
            col *= mix(0.78, 1.06, smoothstep(0.30, 0.78, cloud));
            float vig = smoothstep(1.15, 0.35, distance(uv, float2(0.5, 0.5)));
            col *= mix(0.80, 1.0, vig);
            return half4(col, 1.0);
        }
    """

    /**
     * Abyss terrain: the deep ocean floor. A near-black blue column of water lit
     * by an animated caustic web (two warped sine fields multiplied and
     * sharpened) and faint light shafts falling from the surface.
     */
    const val ABYSS = """
        uniform float2 origin;
        uniform float2 resolution;
        uniform float time;
        uniform float cellPx;
        half4 main(float2 fragCoord) {
            float2 p = fragCoord - origin;
            float2 uv = p / resolution;
            float2 q = (p / resolution.y) * 7.0;
            float3 col = mix(float3(0.016, 0.052, 0.088), float3(0.003, 0.012, 0.026), uv.y);
            // Caustics: two warped sine webs multiplied, then sharpened.
            float t = time * 0.45;
            float a = sin(q.x * 1.1 + t + 1.7 * sin(q.y * 0.8 + t * 0.60));
            float b = sin(q.y * 1.3 - t * 0.7 + 1.5 * sin(q.x * 0.9 - t * 0.50));
            float caust = pow(clamp(0.5 + 0.5 * a * b, 0.0, 1.0), 4.0);
            col += float3(0.06, 0.22, 0.26) * caust * mix(0.55, 1.0, 1.0 - uv.y);
            // Light shafts from the surface, strongest near the top edge.
            float shaft = pow(max(sin(uv.x * 12.0 - uv.y * 3.0 + time * 0.12), 0.0), 5.0);
            col += float3(0.05, 0.14, 0.18) * shaft * (1.0 - uv.y) * 0.55;
            float vig = smoothstep(1.15, 0.35, distance(uv, float2(0.5, 0.5)));
            col *= mix(0.78, 1.0, vig);
            return half4(col, 1.0);
        }
    """

    /**
     * Nebula terrain: deep space. Violet and teal nebula wisps (two noise
     * octaves) drift almost imperceptibly behind a two-layer star field — a
     * sparse bright layer that twinkles at per-star rates over a dense faint
     * static one.
     */
    const val NEBULA = """
        uniform float2 origin;
        uniform float2 resolution;
        uniform float time;
        uniform float cellPx;
        float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
        float vnoise(float2 p) {
            float2 i = floor(p);
            float2 f = fract(p);
            float2 u = f * f * (3.0 - 2.0 * f);
            return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
                       mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
        }
        half4 main(float2 fragCoord) {
            float2 p = fragCoord - origin;
            float2 uv = p / resolution;
            float3 col = mix(float3(0.016, 0.014, 0.040), float3(0.003, 0.003, 0.010), uv.y);
            // Nebula wisps in violet and teal, drifting very slowly.
            float2 np = (p / resolution.y) * 2.1 + float2(time * 0.008, -time * 0.005);
            float n1 = 0.6 * vnoise(np) + 0.4 * vnoise(np * 2.3 + 7.0);
            col += float3(0.085, 0.030, 0.140) * smoothstep(0.45, 0.85, n1) * 0.8;
            float n2 = vnoise(np * 1.4 + 31.0);
            col += float3(0.015, 0.065, 0.100) * smoothstep(0.55, 0.90, n2) * 0.7;
            // Star field: sparse bright twinkling layer.
            float2 g1 = p / 30.0;
            float2 id1 = floor(g1);
            float h1 = hash(id1);
            float2 sp1 = float2(hash(id1 + 3.1), hash(id1 + 7.7)) * 0.7 + 0.15;
            float d1 = length(fract(g1) - sp1);
            float tw = 0.55 + 0.45 * sin(time * (1.5 + 4.0 * hash(id1 + 11.3)) + h1 * 40.0);
            col += float3(0.90, 0.95, 1.00) * exp(-d1 * d1 * 90.0) * tw * step(0.80, h1);
            // Star field: dense faint static layer.
            float2 g2 = p / 13.0;
            float2 id2 = floor(g2);
            float h2 = hash(id2 + 51.0);
            float2 sp2 = float2(hash(id2 + 17.3), hash(id2 + 29.9)) * 0.7 + 0.15;
            float d2 = length(fract(g2) - sp2);
            col += float3(0.55, 0.60, 0.75) * exp(-d2 * d2 * 140.0) * 0.35 * step(0.75, h2);
            float vig = smoothstep(1.20, 0.35, distance(uv, float2(0.5, 0.5)));
            col *= mix(0.80, 1.0, vig);
            return half4(col, 1.0);
        }
    """

    /**
     * Dunes terrain: a desert at night. Warm dark sand with three stacked dune
     * ridges — each shades the sand below its crest and wears a moonlit glint
     * along the crest line — plus rare sand sparkles twinkling in and out.
     */
    const val DUNES = """
        uniform float2 origin;
        uniform float2 resolution;
        uniform float time;
        uniform float cellPx;
        float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
        half4 main(float2 fragCoord) {
            float2 p = fragCoord - origin;
            float2 uv = p / resolution;
            float3 col = mix(float3(0.105, 0.068, 0.038), float3(0.038, 0.022, 0.011), uv.y);
            // Three stacked dune ridges, drifting almost imperceptibly.
            for (int i = 0; i < 3; i++) {
                float fi = float(i);
                float ridge = 0.30 + fi * 0.22
                    + 0.055 * sin(uv.x * (2.2 + fi * 1.3) * 3.1416 + fi * 2.1 + time * (0.015 + fi * 0.008));
                float d = uv.y - ridge;
                col *= mix(1.0, 0.86, smoothstep(0.0, 0.05, d));
                col += float3(0.16, 0.10, 0.05) * exp(-abs(d) * 120.0) * step(0.0, d) * 0.8;
            }
            // Sand sparkle: rare glints twinkling in and out.
            float2 g = p / 9.0;
            float2 id = floor(g);
            float h = hash(id);
            float2 sp = float2(hash(id + 3.1), hash(id + 7.7)) * 0.6 + 0.2;
            float d2 = length(fract(g) - sp);
            float tw = pow(max(sin(time * (0.8 + 2.5 * hash(id + 11.3)) + h * 50.0), 0.0), 6.0);
            col += float3(1.00, 0.85, 0.60) * exp(-d2 * d2 * 120.0) * tw * 0.45 * step(0.965, h);
            float vig = smoothstep(1.15, 0.35, distance(uv, float2(0.5, 0.5)));
            col *= mix(0.80, 1.0, vig);
            return half4(col, 1.0);
        }
    """

    /**
     * Circuit terrain: a dark PCB. A soft solder pad glows at each cell centre,
     * and hash-picked grid columns/rows carry faint teal traces with a bright
     * pulse travelling along each at its own pace (grid-aligned via `cellPx`).
     */
    const val CIRCUIT = """
        uniform float2 origin;
        uniform float2 resolution;
        uniform float time;
        uniform float cellPx;
        float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
        half4 main(float2 fragCoord) {
            float2 p = fragCoord - origin;
            float2 uv = p / resolution;
            float3 col = mix(float3(0.010, 0.042, 0.036), float3(0.004, 0.018, 0.016), uv.y);
            // A soft solder pad at each cell centre.
            float2 f = fract(p / cellPx) - 0.5;
            col += float3(0.020, 0.070, 0.060) * exp(-dot(f, f) * 26.0) * 0.6;
            float2 cellId = floor(p / cellPx);
            // Vertical traces on hash-picked columns, a pulse running down each.
            float hx = hash(float2(cellId.x, 3.0));
            float dx = abs(p.x - (cellId.x + 0.5) * cellPx) / cellPx;
            float lineX = exp(-dx * dx * 60.0) * step(0.85, hx);
            float py = fract(time * (0.05 + 0.06 * hash(float2(cellId.x, 9.0))) + hx * 7.0) * resolution.y;
            float dpy = (p.y - py) / cellPx;
            col += float3(0.05, 0.42, 0.34) * lineX * (0.16 + 1.1 * exp(-dpy * dpy * 0.5));
            // Horizontal traces on hash-picked rows, a pulse running along each.
            float hy = hash(float2(cellId.y, 17.0));
            float dy = abs(p.y - (cellId.y + 0.5) * cellPx) / cellPx;
            float lineY = exp(-dy * dy * 60.0) * step(0.87, hy);
            float px = fract(time * (0.04 + 0.05 * hash(float2(cellId.y, 23.0))) + hy * 5.0) * resolution.x;
            float dpx = (p.x - px) / cellPx;
            col += float3(0.04, 0.30, 0.40) * lineY * (0.14 + 1.0 * exp(-dpx * dpx * 0.5));
            float vig = smoothstep(1.15, 0.35, distance(uv, float2(0.5, 0.5)));
            col *= mix(0.80, 1.0, vig);
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

    /**
     * Optional CRT post-filter: scanlines + an aperture-grille shimmer + vignette
     * over the board layer. Scanlines/grille use a fixed *pixel* period (rather
     * than the old resolution-scaled frequency that aliased into near-invisibility)
     * so the lines actually read on screen; the vignette is deeper for a tube feel.
     */
    const val CRT = """
        uniform shader content;
        uniform float2 resolution;
        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / resolution;
            half4 c = content.eval(fragCoord);
            // Horizontal scanlines: a visible dark line roughly every 3 pixels.
            float scan = 0.84 + 0.16 * sin(fragCoord.y * 2.0944);
            c.rgb *= half(scan);
            // Aperture grille: a faint vertical RGB-ish modulation across columns.
            float grille = 0.94 + 0.06 * sin(fragCoord.x * 2.0944);
            c.rgb *= half(grille);
            // Vignette: darker toward the edges for a curved-tube look.
            float vig = smoothstep(1.25, 0.35, distance(uv, float2(0.5, 0.5)));
            c.rgb *= half(mix(0.62, 1.0, vig));
            return c;
        }
    """
}

/**
 * A live terrain floor: the compiled [shader] and the [brush] that paints it.
 * All terrain shaders share the same uniforms (`origin`, `resolution`, `time`,
 * `cellPx`), so callers can drive any layer identically.
 */
class TerrainLayer(source: String) {
    val shader = RuntimeShader(source)
    val brush = ShaderBrush(shader)
}

/**
 * Live [RuntimeShader] instances (and their [ShaderBrush] wrappers) for the
 * in-board effects, created once and mutated per frame via `setFloatUniform` /
 * `setColorUniform`. The five terrain floors compile lazily so holders that
 * never draw a board background (e.g. the menu emblem) pay nothing for them.
 */
class BoardShaders {
    val background = RuntimeShader(Shaders.BACKGROUND)
    val glow = RuntimeShader(Shaders.GLOW)
    val foodHalo = RuntimeShader(Shaders.FOOD_HALO)

    val backgroundBrush = ShaderBrush(background)
    val glowBrush = ShaderBrush(glow)
    val foodHaloBrush = ShaderBrush(foodHalo)

    private val meadow by lazy { TerrainLayer(Shaders.MEADOW) }
    private val abyss by lazy { TerrainLayer(Shaders.ABYSS) }
    private val nebula by lazy { TerrainLayer(Shaders.NEBULA) }
    private val dunes by lazy { TerrainLayer(Shaders.DUNES) }
    private val circuit by lazy { TerrainLayer(Shaders.CIRCUIT) }

    /**
     * The floor layer for [terrain], compiled on first use — or null for
     * [BoardTerrain.Default], which paints the skin's own gradient via
     * [background] instead.
     */
    fun terrainLayer(terrain: BoardTerrain): TerrainLayer? = when (terrain) {
        BoardTerrain.Default -> null
        BoardTerrain.Meadow -> meadow
        BoardTerrain.Abyss -> abyss
        BoardTerrain.Nebula -> nebula
        BoardTerrain.Dunes -> dunes
        BoardTerrain.Circuit -> circuit
    }
}

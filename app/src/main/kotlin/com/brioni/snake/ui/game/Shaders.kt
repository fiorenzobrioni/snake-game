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
 * Abyss / Nebula / Dunes / Glacier) selectable in Settings. Terrains share the
 * same uniform interface — `origin`, `resolution`, `time`, `cellPx` — so the
 * renderer drives any of them identically. They are designed as *stages*:
 * calm, mid-to-dark and slowly animated, so gameplay stays readable under
 * every skin while each floor keeps a clear personality of its own.
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
        // Sinless hash ("Hash without Sine", Dave Hoskins, MIT): GPU sin() loses
        // precision for large arguments, which tears value-noise into visibly
        // misaligned rectangular patches along the lattice lines.
        float hash(float2 p) {
            float3 q = fract(float3(p.x, p.y, p.x) * float3(0.1031, 0.1030, 0.0973));
            q += dot(q, float3(q.y, q.z, q.x) + 33.33);
            return fract((q.x + q.y) * q.z);
        }
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
            float3 col = mix(float3(0.090, 0.205, 0.072), float3(0.118, 0.255, 0.095), checker);
            // Blade micro-texture: fine noise stretched vertically like combed grass.
            float blades = vnoise(float2(p.x * 0.85, p.y * 0.18));
            col *= 0.86 + 0.30 * blades;
            // Cloud shadows drifting slowly over the field.
            float2 cp = p / resolution.y;
            float cloud = 0.6 * vnoise(cp * 1.7 + float2(time * 0.030, time * 0.012))
                        + 0.4 * vnoise(cp * 3.4 - float2(time * 0.020, 0.0));
            col *= mix(0.80, 1.10, smoothstep(0.30, 0.78, cloud));
            float vig = smoothstep(1.15, 0.35, distance(uv, float2(0.5, 0.5)));
            col *= mix(0.84, 1.0, vig);
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
            float3 col = mix(float3(0.034, 0.100, 0.160), float3(0.010, 0.032, 0.062), uv.y);
            // Caustics: two warped sine webs multiplied, then sharpened. The wider
            // exponent + brighter tint keep the light web clearly readable.
            float t = time * 0.45;
            float a = sin(q.x * 1.1 + t + 1.7 * sin(q.y * 0.8 + t * 0.60));
            float b = sin(q.y * 1.3 - t * 0.7 + 1.5 * sin(q.x * 0.9 - t * 0.50));
            float caust = pow(clamp(0.5 + 0.5 * a * b, 0.0, 1.0), 3.5);
            col += float3(0.10, 0.32, 0.38) * caust * mix(0.60, 1.0, 1.0 - uv.y);
            // Light shafts from the surface, strongest near the top edge.
            float shaft = pow(max(sin(uv.x * 12.0 - uv.y * 3.0 + time * 0.12), 0.0), 5.0);
            col += float3(0.07, 0.18, 0.24) * shaft * (1.0 - uv.y) * 0.65;
            float vig = smoothstep(1.15, 0.35, distance(uv, float2(0.5, 0.5)));
            col *= mix(0.82, 1.0, vig);
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
        // Sinless hash ("Hash without Sine", Dave Hoskins, MIT): GPU sin() loses
        // precision for large arguments, which tears value-noise into visibly
        // misaligned rectangular patches along the lattice lines.
        float hash(float2 p) {
            float3 q = fract(float3(p.x, p.y, p.x) * float3(0.1031, 0.1030, 0.0973));
            q += dot(q, float3(q.y, q.z, q.x) + 33.33);
            return fract((q.x + q.y) * q.z);
        }
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
        // Sinless hash ("Hash without Sine", Dave Hoskins, MIT): GPU sin() loses
        // precision for large arguments, which tears value-noise into visibly
        // misaligned rectangular patches along the lattice lines.
        float hash(float2 p) {
            float3 q = fract(float3(p.x, p.y, p.x) * float3(0.1031, 0.1030, 0.0973));
            q += dot(q, float3(q.y, q.z, q.x) + 33.33);
            return fract((q.x + q.y) * q.z);
        }
        half4 main(float2 fragCoord) {
            float2 p = fragCoord - origin;
            float2 uv = p / resolution;
            float3 col = mix(float3(0.170, 0.112, 0.064), float3(0.072, 0.044, 0.024), uv.y);
            // Three stacked dune ridges, drifting almost imperceptibly. The crest
            // glints are warmer and the shading a touch deeper so the dunes read.
            for (int i = 0; i < 3; i++) {
                float fi = float(i);
                float ridge = 0.30 + fi * 0.22
                    + 0.055 * sin(uv.x * (2.2 + fi * 1.3) * 3.1416 + fi * 2.1 + time * (0.015 + fi * 0.008));
                float d = uv.y - ridge;
                col *= mix(1.0, 0.84, smoothstep(0.0, 0.05, d));
                col += float3(0.22, 0.14, 0.07) * exp(-abs(d) * 110.0) * step(0.0, d) * 0.9;
            }
            // Sand sparkle: rare glints twinkling in and out.
            float2 g = p / 9.0;
            float2 id = floor(g);
            float h = hash(id);
            float2 sp = float2(hash(id + 3.1), hash(id + 7.7)) * 0.6 + 0.2;
            float d2 = length(fract(g) - sp);
            float tw = pow(max(sin(time * (0.8 + 2.5 * hash(id + 11.3)) + h * 50.0), 0.0), 6.0);
            col += float3(1.00, 0.85, 0.60) * exp(-d2 * d2 * 120.0) * tw * 0.55 * step(0.965, h);
            float vig = smoothstep(1.15, 0.35, distance(uv, float2(0.5, 0.5)));
            col *= mix(0.84, 1.0, vig);
            return half4(col, 1.0);
        }
    """

    /**
     * Glacier terrain: a frozen lake. The brightest floor of the set - a pale
     * icy blue surface mottled by soft noise, veined with bright cracks (two
     * ridged-noise layers, coarse + fine), an internal sheen band drifting
     * diagonally like light through the ice, and cool sparkling glints.
     */
    const val GLACIER = """
        uniform float2 origin;
        uniform float2 resolution;
        uniform float time;
        uniform float cellPx;
        // Sinless hash ("Hash without Sine", Dave Hoskins, MIT): GPU sin() loses
        // precision for large arguments, which tears value-noise into visibly
        // misaligned rectangular patches along the lattice lines.
        float hash(float2 p) {
            float3 q = fract(float3(p.x, p.y, p.x) * float3(0.1031, 0.1030, 0.0973));
            q += dot(q, float3(q.y, q.z, q.x) + 33.33);
            return fract((q.x + q.y) * q.z);
        }
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
            float2 iso = p / resolution.y;
            float3 col = mix(float3(0.150, 0.230, 0.330), float3(0.075, 0.135, 0.215), uv.y);
            // Frozen-surface mottling: broad, soft tonal variation.
            float m = 0.6 * vnoise(iso * 2.4) + 0.4 * vnoise(iso * 5.0 + 13.0);
            col += float3(0.10, 0.13, 0.16) * (m - 0.5) * 0.7;
            // Crack veins: ridged noise sharpened into thin bright lines,
            // a coarse web plus a finer secondary layer. Static, like real ice.
            float r1 = 1.0 - abs(2.0 * vnoise(iso * 3.2 + 7.0) - 1.0);
            float crack1 = pow(smoothstep(0.82, 1.0, r1), 2.0);
            float r2 = 1.0 - abs(2.0 * vnoise(iso * 6.6 + 29.0) - 1.0);
            float crack2 = pow(smoothstep(0.88, 1.0, r2), 2.0);
            col += float3(0.55, 0.75, 0.92) * crack1 * 0.38;
            col += float3(0.45, 0.65, 0.85) * crack2 * 0.22;
            // An internal sheen band drifting diagonally through the ice.
            float sheen = pow(max(sin((uv.x + uv.y) * 3.7699 + time * 0.12), 0.0), 4.0);
            col += float3(0.10, 0.15, 0.21) * sheen * 0.55;
            // Ice glints twinkling in and out.
            float2 g = p / 10.0;
            float2 id = floor(g);
            float h = hash(id);
            float2 sp = float2(hash(id + 3.1), hash(id + 7.7)) * 0.6 + 0.2;
            float d = length(fract(g) - sp);
            float tw = pow(max(sin(time * (0.9 + 2.4 * hash(id + 11.3)) + h * 50.0), 0.0), 6.0);
            col += float3(0.95, 1.00, 1.00) * exp(-d * d * 130.0) * tw * 0.5 * step(0.965, h);
            float vig = smoothstep(1.15, 0.35, distance(uv, float2(0.5, 0.5)));
            col *= mix(0.85, 1.0, vig);
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
    private val glacier by lazy { TerrainLayer(Shaders.GLACIER) }

    /**
     * The floor layer for [terrain], compiled on first use — or null for
     * [BoardTerrain.Arcade], which paints the skin's own gradient via
     * [background] instead.
     */
    fun terrainLayer(terrain: BoardTerrain): TerrainLayer? = when (terrain) {
        BoardTerrain.Arcade -> null
        BoardTerrain.Meadow -> meadow
        BoardTerrain.Abyss -> abyss
        BoardTerrain.Nebula -> nebula
        BoardTerrain.Dunes -> dunes
        BoardTerrain.Glacier -> glacier
    }
}

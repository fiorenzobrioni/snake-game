package com.brioni.snake.ui.intro

import android.graphics.RuntimeShader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import com.brioni.snake.game.Skin
import com.brioni.snake.ui.game.Particle
import com.brioni.snake.ui.game.SkinPalette
import com.brioni.snake.ui.game.emitExplosionBurst
import com.brioni.snake.ui.game.paletteFor
import com.brioni.snake.ui.game.updateParticles
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// Total time on screen before handing off to the menu; the last EXIT_FADE_MS are
// a fade-out. It's also tap-to-skip.
private const val INTRO_DURATION_MS = 4100L
private const val EXIT_FADE_MS = 500L
private const val TRAVEL_START_MS = 300L
private const val TRAVEL_MS = 2700L

// Board grid: TARGET_COLS columns wide (square cells); the "SNAKE" pixel-art is
// WORD_COLS wide, centred with a margin either side.
private const val TARGET_COLS = 28
private const val SNAKE_LENGTH = 6
// How many columns the per-cell reveal takes to ramp from head → body behind the
// head, giving a soft "settling trail" edge.
private const val FADE_COLS = 1.6f

// 4×5 pixel font for the five letters of SNAKE ('#' = lit cell).
private val LETTER_WIDTH = 4
private val LETTER_GAP = 1
private val WORD = "SNAKE"
private val WORD_COLS = WORD.length * LETTER_WIDTH + (WORD.length - 1) * LETTER_GAP // 24
private val FONT: Map<Char, List<String>> = mapOf(
    'S' to listOf(
        "####",
        "#...",
        "####",
        "...#",
        "####",
    ),
    'N' to listOf(
        "#..#",
        "##.#",
        "#.##",
        "#..#",
        "#..#",
    ),
    'A' to listOf(
        ".##.",
        "#..#",
        "####",
        "#..#",
        "#..#",
    ),
    'K' to listOf(
        "#..#",
        "#.#.",
        "##..",
        "#.#.",
        "#..#",
    ),
    'E' to listOf(
        "####",
        "#...",
        "###.",
        "#...",
        "####",
    ),
)

private data class Cell(val row: Int, val col: Int)

// Two particle detonations fire in the snake's wake — one above the word, one below
// — to make the splash feel alive. They reuse the in-game explosion burst
// (emitExplosionBurst): fast hot sparks, falling embers and drifting smoke, so the
// splash shares the gameplay's "juice" rather than a bespoke shader effect. The
// accent tints the lead sparks; warm tones keep them in the Retro palette's family.
private val BlastAccentTop = Color(0xFFFFC107)    // gold
private val BlastAccentBottom = Color(0xFFFF8A3D) // warm orange

// Splash-only grid colour — a touch brighter than the in-game Retro palette so the
// board's squares read clearly here (the game's palette is left untouched).
private val SplashGridLine = Color(0x26FFB000)

// Bloom post-filter (AGSL, API 33+). Samples bright neighbours of each pixel and
// screen-adds them, so the snake, the glowing letters and the explosion sparks gain
// a soft halo. Dark board pixels stay below the luminance threshold and are
// untouched, so the grid keeps its crisp lines. Below API 33 this is skipped.
private const val BLOOM_AGSL = """
uniform shader content;
uniform float2 resolution;

half4 main(float2 coord) {
    half4 src = content.eval(coord);
    float r = resolution.y * 0.008;
    float3 glow = float3(0.0);
    const int DIRS = 12;
    for (int i = 0; i < DIRS; i++) {
        float a = (float(i) / float(DIRS)) * 6.2831853;
        float2 dir = float2(cos(a), sin(a));
        half4 s1 = content.eval(coord + dir * r);
        half4 s2 = content.eval(coord + dir * r * 2.0);
        float l1 = float(max(max(s1.r, s1.g), s1.b));
        float l2 = float(max(max(s2.r, s2.g), s2.b));
        glow += float3(s1.rgb) * max(0.0, l1 - 0.45);
        glow += float3(s2.rgb) * max(0.0, l2 - 0.45) * 0.6;
    }
    glow *= 0.18;
    float3 base = float3(src.rgb);
    float3 outc = base + glow * (float3(1.0) - base);
    return half4(half3(outc), src.a);
}
"""

/**
 * The first thing the player sees on a cold launch: the game board itself, drawn in
 * the game's **Retro skin** (warm gradient, square cells, grid, framed border). A
 * snake crawls in from the left, and the word **SNAKE** forms from retro snake-body
 * pieces in its wake — each column settling as the head passes. Two particle
 * detonations pop above and below the word (the in-game explosion burst), the snake
 * exits to the right, the word holds, then the whole splash fades to the menu.
 *
 * Auto-advances after [INTRO_DURATION_MS]; a tap skips it. Either way it calls
 * [onFinished] exactly once. The premium board (drifting warm glows + vignette) and
 * the bloom are GPU-cheap at the app's minSdk 33.
 */
@Composable
fun BrandIntroScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val palette = remember { paletteFor(Skin.Retro).copy(gridLine = SplashGridLine) }

    // A bloom RuntimeShader adds a soft glow around the bright snake / letters /
    // sparks. Built defensively: a compile failure falls back to null, not a crash.
    val bloomShader = remember { runCatching { RuntimeShader(BLOOM_AGSL) }.getOrNull() }

    // Fire onFinished once, whether by tap or by the auto-advance timer.
    var done by remember { mutableStateOf(false) }
    val latestOnFinished by rememberUpdatedState(onFinished)
    val finish = remember {
        {
            if (!done) {
                done = true
                latestOnFinished()
            }
        }
    }

    // entrance: the board fades in. travel: the snake crosses the board (0→1).
    // exitAlpha: the whole splash fades out before the menu.
    val entrance = remember { Animatable(0f) }
    val travel = remember { Animatable(0f) }
    val exitAlpha = remember { Animatable(1f) }

    IntroAnimations(entrance, travel, exitAlpha, finish)

    // Live particle bursts and a wall-clock used to animate the board and to force a
    // redraw every frame (so particles keep advancing after the crawl settles).
    val particles = remember { mutableStateListOf<Particle>() }
    var timeSec by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val cols = TARGET_COLS
        val cell = widthPx / cols
        val rows = ceil(heightPx / cell).toInt()
        val originX = 0f
        val originY = (heightPx - rows * cell) / 2f

        // Word layout: centred horizontally, on the board's mid band.
        val wordStartCol = (cols - WORD_COLS) / 2
        val bandTop = rows / 2 - 2
        val midRow = bandTop + 2
        val litCells = remember(rows) { wordCells(wordStartCol, bandTop) }

        // The float head column drives the snake and the reveal curtain. It ends past
        // the right edge by the snake's full length, so the tail clears the board.
        val startCol = -SNAKE_LENGTH - 1f
        val endCol = cols + SNAKE_LENGTH + 2f

        // Detonation cell-positions/triggers in the snake's wake (cell space, mapped
        // to pixels by the renderer just like the in-game particles).
        val topCenterX = wordStartCol + 6f
        val topCenterY = bandTop - 2.5f
        val topTrigger = wordStartCol + 4f
        val botCenterX = wordStartCol + 18f
        val botCenterY = bandTop + 6.5f
        val botTrigger = wordStartCol + 16f

        // Per-frame simulation: advance particles + clock, and fire each burst once as
        // the head sweeps past its column. Keyed on size so it tracks the live layout.
        LaunchedEffect(widthPx, heightPx) {
            var firedTop = false
            var firedBottom = false
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                timeSec += dt
                updateParticles(particles, dt)
                val headCol = startCol + (endCol - startCol) * travel.value
                if (!firedTop && headCol >= topTrigger) {
                    emitExplosionBurst(particles, topCenterX, topCenterY, BlastAccentTop, span = 2)
                    firedTop = true
                }
                if (!firedBottom && headCol >= botTrigger) {
                    emitExplosionBurst(particles, botCenterX, botCenterY, BlastAccentBottom, span = 2)
                    firedBottom = true
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = entrance.value * exitAlpha.value
                    // Apply the bloom as a render effect over the rasterised canvas.
                    if (bloomShader != null) {
                        bloomShader.setFloatUniform("resolution", size.width, size.height)
                        renderEffect = android.graphics.RenderEffect
                            .createRuntimeShaderEffect(bloomShader, "content")
                            .asComposeRenderEffect()
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                }
                .pointerInput(Unit) { detectTapGestures { finish() } },
        ) {
            drawBoard(originX, originY, cell, cols, rows, palette, timeSec)

            val headCol = startCol + (endCol - startCol) * travel.value

            // Letters: settle each lit cell as the head passes its column.
            for (c in litCells) {
                val revealT = ((headCol - (c.col + 0.5f)) / FADE_COLS).coerceIn(0f, 1f)
                if (revealT <= 0f) continue
                val fill = lerp(palette.snakeHead, palette.snakeBody, revealT)
                drawCellSquare(originX, originY, cell, c.col.toFloat(), c.row.toFloat(), fill, palette)
            }

            // Snake on top, crawling along the mid row toward the right.
            for (i in 0 until SNAKE_LENGTH) {
                val segCol = headCol - i
                if (segCol < -1.2f || segCol > cols + 1.2f) continue
                drawSegment(originX, originY, cell, segCol, midRow.toFloat(), isHead = i == 0, palette)
            }

            // The detonation particles, mapped from cell space and faded by life.
            drawParticles(particles, originX, originY, cell)
        }
    }
}

/** Draws the live particle bursts: an additive glow halo plus a solid core. */
private fun DrawScope.drawParticles(particles: List<Particle>, originX: Float, originY: Float, cell: Float) {
    particles.forEach { p ->
        val px = originX + p.x * cell
        val py = originY + p.y * cell
        val r = p.radiusCells * cell
        val a = p.fade
        if (p.glow) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(p.color.copy(alpha = a * 0.55f), Color.Transparent),
                    center = Offset(px, py),
                    radius = r * 2.4f,
                ),
                radius = r * 2.4f,
                center = Offset(px, py),
                blendMode = BlendMode.Plus,
            )
        }
        drawCircle(p.color.copy(alpha = a), radius = r, center = Offset(px, py))
    }
}

/** Lit cell coordinates of the whole WORD, expanded from the pixel font. */
private fun wordCells(wordStartCol: Int, bandTop: Int): List<Cell> {
    val cells = ArrayList<Cell>()
    var col0 = wordStartCol
    for (ch in WORD) {
        val glyph = FONT.getValue(ch)
        for (r in glyph.indices) {
            val rowPattern = glyph[r]
            for (cc in rowPattern.indices) {
                if (rowPattern[cc] == '#') cells.add(Cell(bandTop + r, col0 + cc))
            }
        }
        col0 += LETTER_WIDTH + LETTER_GAP
    }
    return cells
}

/**
 * Premium board background: the Retro vertical gradient lifted with two slow-drifting
 * warm glows, a soft radial vignette for depth, a 1px grid, and a framed border with
 * a soft outer halo — richer than a flat fill, while staying in the Retro family.
 */
private fun DrawScope.drawBoard(
    originX: Float,
    originY: Float,
    cell: Float,
    cols: Int,
    rows: Int,
    palette: SkinPalette,
    time: Float,
) {
    val boardW = cell * cols
    val boardH = cell * rows
    val topLeft = Offset(originX, originY)
    val boardSize = Size(boardW, boardH)
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(palette.boardTop, palette.boardBottom),
            startY = originY,
            endY = originY + boardH,
        ),
        topLeft = topLeft,
        size = boardSize,
    )

    // Two slow-drifting warm glows for life (additive, low alpha).
    val g1 = Offset(
        originX + boardW * (0.30f + 0.10f * sin(time * 0.45f)),
        originY + boardH * (0.32f + 0.08f * cos(time * 0.38f)),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFF9A3D).copy(alpha = 0.12f), Color.Transparent),
            center = g1,
            radius = boardW * 0.55f,
        ),
        radius = boardW * 0.55f,
        center = g1,
        blendMode = BlendMode.Plus,
    )
    val g2 = Offset(
        originX + boardW * (0.72f + 0.10f * cos(time * 0.33f)),
        originY + boardH * (0.68f + 0.08f * sin(time * 0.42f)),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFC24D).copy(alpha = 0.10f), Color.Transparent),
            center = g2,
            radius = boardW * 0.50f,
        ),
        radius = boardW * 0.50f,
        center = g2,
        blendMode = BlendMode.Plus,
    )

    if (cell > 10f) {
        val gridStroke = 1.5f
        for (x in 0..cols) {
            val lineX = originX + x * cell
            drawLine(palette.gridLine, Offset(lineX, originY), Offset(lineX, originY + boardH), gridStroke)
        }
        for (y in 0..rows) {
            val lineY = originY + y * cell
            drawLine(palette.gridLine, Offset(originX, lineY), Offset(originX + boardW, lineY), gridStroke)
        }
    }

    // Radial vignette: darken the corners for depth.
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.34f)),
            center = Offset(originX + boardW / 2f, originY + boardH / 2f),
            radius = max(boardW, boardH) * 0.62f,
        ),
        topLeft = topLeft,
        size = boardSize,
    )

    // Framed border: a soft outer halo under a crisp inner line.
    drawRect(
        color = palette.boardBorder.copy(alpha = 0.30f),
        topLeft = topLeft,
        size = boardSize,
        style = Stroke(width = cell * 0.34f),
    )
    drawRect(
        color = palette.boardBorder,
        topLeft = topLeft,
        size = boardSize,
        style = Stroke(width = (cell * 0.12f).coerceAtLeast(2f)),
    )
}

/**
 * A filled rounded cell (a snake-body piece), shaped by the skin's corner factor and
 * lit with a subtle top sheen so the letters read as premium body pieces.
 */
private fun DrawScope.drawCellSquare(
    originX: Float,
    originY: Float,
    cell: Float,
    col: Float,
    row: Float,
    fill: Color,
    palette: SkinPalette,
) {
    val inset = cell * 0.06f
    val topLeft = Offset(originX + col * cell + inset, originY + row * cell + inset)
    val side = cell - 2 * inset
    val radius = CornerRadius(cell * palette.cornerFactor, cell * palette.cornerFactor)
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(lerp(fill, Color.White, 0.16f), fill),
            startY = topLeft.y,
            endY = topLeft.y + side,
        ),
        topLeft = topLeft,
        size = Size(side, side),
        cornerRadius = radius,
    )
    drawRoundRect(
        palette.snakeOutline,
        topLeft,
        Size(side, side),
        radius,
        style = Stroke(width = cell * 0.06f),
    )
}

/** A snake segment (head or body), drawn like the in-game renderer. */
private fun DrawScope.drawSegment(
    originX: Float,
    originY: Float,
    cell: Float,
    col: Float,
    row: Float,
    isHead: Boolean,
    palette: SkinPalette,
) {
    val cx = originX + (col + 0.5f) * cell
    val cy = originY + (row + 0.5f) * cell
    if (isHead && palette.useGlow) {
        val glowRadius = cell * 1.1f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.headGlow.copy(alpha = 0.45f), Color.Transparent),
                center = Offset(cx, cy),
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = Offset(cx, cy),
        )
    }
    drawCellSquare(
        originX, originY, cell, col, row,
        fill = if (isHead) palette.snakeHead else palette.snakeBody,
        palette = palette,
    )
    if (isHead) drawEyes(cx, cy, cell, palette)
}

/** Two eyes looking right (travel direction). Mirrors GameBoard's drawEyes. */
private fun DrawScope.drawEyes(centerX: Float, centerY: Float, cell: Float, palette: SkinPalette) {
    val forward = cell * 0.16f
    val spread = cell * 0.2f
    val eyeRadius = cell * 0.11f
    val pupilRadius = cell * 0.055f
    for (sign in intArrayOf(-1, 1)) {
        val ex = centerX + forward
        val ey = centerY + spread * sign
        drawCircle(Color.White, eyeRadius, Offset(ex, ey))
        drawCircle(palette.snakeEye, pupilRadius, Offset(ex + cell * 0.03f, ey))
    }
}

/** Board fade-in, the snake's crawl, and the timed fade-out → hand-off. */
@Composable
private fun IntroAnimations(
    entrance: Animatable<Float, AnimationVector1D>,
    travel: Animatable<Float, AnimationVector1D>,
    exitAlpha: Animatable<Float, AnimationVector1D>,
    finish: () -> Unit,
) {
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(durationMillis = 500, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        delay(TRAVEL_START_MS)
        travel.animateTo(1f, tween(durationMillis = TRAVEL_MS.toInt(), easing = LinearEasing))
    }
    LaunchedEffect(Unit) {
        delay(INTRO_DURATION_MS - EXIT_FADE_MS)
        exitAlpha.animateTo(0f, tween(durationMillis = EXIT_FADE_MS.toInt(), easing = FastOutSlowInEasing))
        finish()
    }
}

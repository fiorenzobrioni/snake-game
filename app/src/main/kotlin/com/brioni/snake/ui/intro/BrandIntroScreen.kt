package com.brioni.snake.ui.intro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import com.brioni.snake.game.Skin
import com.brioni.snake.ui.game.SkinPalette
import com.brioni.snake.ui.game.paletteFor
import kotlinx.coroutines.delay
import kotlin.math.ceil

// Total time on screen before handing off to the menu; the last EXIT_FADE_MS are
// a fade-out. It's also tap-to-skip.
private const val INTRO_DURATION_MS = 3600L
private const val EXIT_FADE_MS = 500L
private const val TRAVEL_START_MS = 300L
private const val TRAVEL_MS = 2200L

// Board grid: TARGET_COLS columns wide (square cells); the "SNAKE" pixel-art is
// WORD_COLS wide, centred with a margin either side.
private const val TARGET_COLS = 28
private const val SNAKE_LENGTH = 6
// How many columns the per-cell reveal takes to ramp from lime → green behind
// the head, giving a soft "cooling trail" edge.
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

/**
 * The first thing the player sees on a cold launch: the game board itself, drawn
 * exactly as in-game (square cells, gradient, grid, frame). A snake crawls in
 * from the left, and the word **SNAKE** forms in pixel-art cells in its wake —
 * each column lighting up as the head passes, fresh cells glowing lime then
 * cooling to the snake's green. The snake exits to the right, the word holds,
 * then the whole splash fades to the menu.
 *
 * Auto-advances after [INTRO_DURATION_MS]; a tap skips it. Either way it calls
 * [onFinished] exactly once. Self-contained on a [Canvas] (no shaders), so it
 * renders identically on every supported API level.
 */
@Composable
fun BrandIntroScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val palette = remember { paletteFor(Skin.Classic) }

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

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = entrance.value * exitAlpha.value }
            .pointerInput(Unit) { detectTapGestures { finish() } },
    ) {
        val cols = TARGET_COLS
        val cell = size.width / cols
        val rows = ceil(size.height / cell).toInt()
        val originX = 0f
        val originY = (size.height - rows * cell) / 2f

        drawBoard(originX, originY, cell, cols, rows, palette)

        // Word layout: centred horizontally, vertically on the board's mid band.
        val wordStartCol = (cols - WORD_COLS) / 2
        val bandTop = rows / 2 - 2
        val midRow = bandTop + 2
        val litCells = wordCells(wordStartCol, bandTop)

        // Float head column drives both the snake and the reveal curtain.
        val startCol = -SNAKE_LENGTH - 1f
        val endCol = cols + 2f
        val headCol = startCol + (endCol - startCol) * travel.value

        // Letters: reveal each lit cell as the head passes its column.
        for (c in litCells) {
            val revealT = ((headCol - (c.col + 0.5f)) / FADE_COLS).coerceIn(0f, 1f)
            if (revealT <= 0f) continue
            val fill = lerp(palette.snakeHead, palette.snakeBody, revealT)
            val cx = originX + (c.col + 0.5f) * cell
            val cy = originY + (c.row + 0.5f) * cell
            val glow = (1f - revealT) * 0.5f
            if (glow > 0f && palette.useGlow) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(palette.headGlow.copy(alpha = glow), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = cell * 0.9f,
                    ),
                    radius = cell * 0.9f,
                    center = Offset(cx, cy),
                )
            }
            drawCellSquare(originX, originY, cell, c.col.toFloat(), c.row.toFloat(), fill, palette)
        }

        // Snake on top, crawling along the mid row toward the right.
        for (i in 0 until SNAKE_LENGTH) {
            val segCol = headCol - i
            if (segCol < -1.2f || segCol > cols + 1.2f) continue
            drawSegment(originX, originY, cell, segCol, midRow.toFloat(), isHead = i == 0, palette)
        }
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

/** Board background: vertical gradient, 1px grid, bordered frame — like the game. */
private fun DrawScope.drawBoard(
    originX: Float,
    originY: Float,
    cell: Float,
    cols: Int,
    rows: Int,
    palette: SkinPalette,
) {
    val boardW = cell * cols
    val boardH = cell * rows
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(palette.boardTop, palette.boardBottom),
            startY = originY,
            endY = originY + boardH,
        ),
        topLeft = Offset(originX, originY),
        size = Size(boardW, boardH),
    )
    if (cell > 10f) {
        for (x in 0..cols) {
            val lineX = originX + x * cell
            drawLine(palette.gridLine, Offset(lineX, originY), Offset(lineX, originY + boardH), 1f)
        }
        for (y in 0..rows) {
            val lineY = originY + y * cell
            drawLine(palette.gridLine, Offset(originX, lineY), Offset(originX + boardW, lineY), 1f)
        }
    }
    drawRect(
        color = palette.boardBorder,
        topLeft = Offset(originX, originY),
        size = Size(boardW, boardH),
        style = Stroke(width = (cell * 0.12f).coerceAtLeast(2f)),
    )
}

/** A filled rounded letter cell (same shape as a snake body segment). */
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
    drawRoundRect(fill, topLeft, Size(side, side), radius)
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

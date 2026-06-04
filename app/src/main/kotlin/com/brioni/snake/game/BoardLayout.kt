package com.brioni.snake.game

import kotlin.math.roundToInt

/** Smallest/largest grids we allow, to keep cells tappable and the board sane. */
private const val MIN_COLUMNS = 8
private const val MAX_COLUMNS = 40
private const val MIN_ROWS = 10
private const val MAX_ROWS = 60

/** Fallback aspect ratio (≈ a tall phone) used when none has been measured yet. */
const val DEFAULT_ASPECT = 0.6f

/**
 * Builds a concrete [BoardDimensions] for [scale] that fills a play area of the
 * given [aspectRatio] (`playAreaWidth / playAreaHeight`) with square cells.
 *
 * Columns come from the preset; rows are solved from the aspect ratio so each
 * cell is square (the renderer uses `min(w/cols, h/rows)`, so matching the grid
 * ratio to the area ratio fills it). Both axes are clamped so extreme aspect
 * ratios (foldables, tablets) still produce a playable, tappable board.
 */
fun boardFor(scale: BoardScale, aspectRatio: Float): BoardDimensions {
    val cols = scale.targetColumns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
    val ar = if (aspectRatio > 0f) aspectRatio else DEFAULT_ASPECT
    val rows = (cols / ar).roundToInt().coerceIn(MIN_ROWS, MAX_ROWS)
    return BoardDimensions(cols, rows)
}

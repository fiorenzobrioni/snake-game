package com.brioni.snake.game

import kotlin.math.roundToInt

/** Smallest/largest grids we allow, to keep cells tappable and the board sane. */
private const val MIN_COLUMNS = 8
private const val MAX_COLUMNS = 60
private const val MIN_ROWS = 8
private const val MAX_ROWS = 60

/** Fallback aspect ratio (≈ a tall phone) used when none has been measured yet. */
const val DEFAULT_ASPECT = 0.6f

/**
 * Builds a concrete [BoardDimensions] for [scale] that fills a play area of the
 * given [aspectRatio] (`playAreaWidth / playAreaHeight`) with square cells.
 *
 * The preset's cell count is applied to the **short side** of the play area, and
 * the long side is solved from the aspect ratio so each cell is square (the
 * renderer uses `min(w/cols, h/rows)`). Fixing the short side keeps the cell
 * size — and the feel — consistent across orientations: a phone in portrait and
 * a tablet in landscape get the same granularity, instead of a landscape board
 * collapsing to a few rows. Both axes are clamped so extreme aspect ratios still
 * produce a playable, tappable board.
 */
fun boardFor(scale: BoardScale, aspectRatio: Float): BoardDimensions {
    val ar = if (aspectRatio > 0f) aspectRatio else DEFAULT_ASPECT
    val shortSide = scale.cellsOnShortSide
    val cols: Int
    val rows: Int
    if (ar <= 1f) {
        // Portrait (or square): width is the short side.
        cols = shortSide
        rows = (shortSide / ar).roundToInt()
    } else {
        // Landscape: height is the short side.
        rows = shortSide
        cols = (shortSide * ar).roundToInt()
    }
    return BoardDimensions(
        cols.coerceIn(MIN_COLUMNS, MAX_COLUMNS),
        rows.coerceIn(MIN_ROWS, MAX_ROWS),
    )
}

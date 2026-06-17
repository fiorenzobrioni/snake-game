package com.brioni.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Tests for the responsive board sizing ([boardFor]). */
class BoardLayoutTest {

    @Test
    fun shortSideComesFromTheScalePreset() {
        // In portrait the short side is the width, fixed to the preset target.
        BoardScale.entries.forEach { scale ->
            val dims = boardFor(scale, aspectRatio = 0.55f)
            assertEquals(scale.cellsOnShortSide, dims.width)
        }
    }

    @Test
    fun landscapeFixesTheShortSideAndSolvesColumns() {
        // A tablet in landscape: the preset applies to the short side (rows) and
        // columns are solved from the aspect, so the board fills the width instead
        // of collapsing to a handful of rows.
        val dims = boardFor(BoardScale.Classic, aspectRatio = 1.6f)
        assertEquals(19, dims.height) // short side fixed to the preset
        assertEquals(30, dims.width) // 19 * 1.6 ≈ 30
        assertTrue(dims.width > dims.height)
    }

    @Test
    fun rowsAreDerivedFromAspectRatioForSquareCells() {
        val dims = boardFor(BoardScale.Classic, aspectRatio = 0.6f)
        // rows ≈ columns / aspectRatio → 19 / 0.6 ≈ 32.
        assertEquals(32, dims.height)
    }

    @Test
    fun portraitBoardsHaveAnOddCentreColumn() {
        // Odd column counts give the board a true middle column, so the snake's
        // spawn (width / 2) is the exact centre under centred overlays.
        BoardScale.entries.forEach { scale ->
            val dims = boardFor(scale, aspectRatio = 0.55f)
            assertEquals("${scale.displayName} columns must stay odd", 1, dims.width % 2)
        }
    }

    @Test
    fun tallerScreensGetMoreRows() {
        val wide = boardFor(BoardScale.Classic, aspectRatio = 0.75f)
        val tall = boardFor(BoardScale.Classic, aspectRatio = 0.45f)
        assertTrue(tall.height > wide.height)
        assertEquals(wide.width, tall.width) // columns unaffected by aspect
    }

    @Test
    fun extremeAspectRatiosStayWithinClamps() {
        val squished = boardFor(BoardScale.Epic, aspectRatio = 5f) // very wide
        assertTrue(squished.height >= 10)
        val skyscraper = boardFor(BoardScale.Cozy, aspectRatio = 0.05f) // absurdly tall
        assertTrue(skyscraper.height <= 80)
    }

    @Test
    fun nonPositiveAspectFallsBackToDefault() {
        val fallback = boardFor(BoardScale.Classic, aspectRatio = 0f)
        val expected = boardFor(BoardScale.Classic, aspectRatio = DEFAULT_ASPECT)
        assertEquals(expected, fallback)
    }

    @Test
    fun cellsStayRoughlySquare() {
        // With rows solved from the aspect ratio, the grid ratio should track the
        // area ratio closely (within one row of rounding).
        val aspect = 0.5f
        val dims = boardFor(BoardScale.Classic, aspect)
        val gridRatio = dims.width.toFloat() / dims.height.toFloat()
        assertTrue(abs(gridRatio - aspect) < 0.05f)
    }
}

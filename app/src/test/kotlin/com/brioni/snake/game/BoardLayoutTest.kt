package com.brioni.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Tests for the responsive board sizing ([boardFor]). */
class BoardLayoutTest {

    @Test
    fun columnsComeFromTheScalePreset() {
        // A typical phone aspect ratio leaves columns at the preset target.
        BoardScale.entries.forEach { scale ->
            val dims = boardFor(scale, aspectRatio = 0.55f)
            assertEquals(scale.targetColumns, dims.width)
        }
    }

    @Test
    fun rowsAreDerivedFromAspectRatioForSquareCells() {
        val dims = boardFor(BoardScale.Classic, aspectRatio = 0.6f)
        // rows ≈ columns / aspectRatio → 18 / 0.6 = 30.
        assertEquals(30, dims.height)
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
        assertTrue(skyscraper.height <= 60)
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

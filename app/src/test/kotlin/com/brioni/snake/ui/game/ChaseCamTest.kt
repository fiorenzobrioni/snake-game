package com.brioni.snake.ui.game

import com.brioni.snake.game.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * Sanity checks for the pure chase-cam projection ([ChaseCam.kt]). Full rendering
 * can't be unit-tested, but the camera math can: the head should sit near the
 * screen centre, and a cell straight ahead should project higher up the screen
 * and smaller than one nearer the camera.
 */
class ChaseCamTest {

    private val aspect = 0.7f // a typical portrait board (width / height)

    /** Chase-cam locked onto a head at (10, 10) heading Right (east). */
    private fun cam(): Cam =
        blendedCam(headX = 10.5f, headY = 10.5f, boardW = 18f, boardH = 26f, yawTarget = yawFor(Direction.Right), blend = 1f, aspect = aspect)

    @Test
    fun headProjectsNearScreenCentre() {
        // The head is just ahead of the camera; it should land near the centre,
        // a little below it (the camera looks down over the head).
        val p = cam().project(Vec3(10.5f, 10.5f, ZTOP))
        assertTrue("head visible", p.visible)
        assertTrue("head near centre x (|${p.sx}|<0.4)", kotlin.math.abs(p.sx) < 0.4f)
        assertTrue("head near centre y (|${p.sy}|<0.8)", kotlin.math.abs(p.sy) < 0.8f)
    }

    @Test
    fun fartherCellsProjectHigherAndSmaller() {
        val c = cam()
        // Two cells ahead (east) of the head, at floor level.
        val near = c.project(Vec3(12.5f, 10.5f, 0f))
        val far = c.project(Vec3(16.5f, 10.5f, 0f))
        assertTrue(near.visible && far.visible)
        // Farther = greater depth.
        assertTrue("far is deeper", far.depth > near.depth)
        // Farther projects higher up the screen (larger +y in our up-positive NDC).
        assertTrue("far is higher on screen", far.sy > near.sy)
    }

    @Test
    fun cellsBehindTheCameraAreCulled() {
        // The camera sits behind the head; a cell well behind it is not visible.
        val behind = cam().project(Vec3(2.5f, 10.5f, 0f))
        assertTrue("behind-camera cell culled", !behind.visible)
    }

    @Test
    fun yawForMatchesScreenConvention() {
        assertEquals(0f, yawFor(Direction.Right), 1e-4f)
        assertEquals((PI / 2).toFloat(), yawFor(Direction.Down), 1e-4f)
        assertEquals((-PI / 2).toFloat(), yawFor(Direction.Up), 1e-4f)
    }

    @Test
    fun shortestDeltaWrapsAcrossPi() {
        // From just under +π to just over -π should be a small positive step,
        // not nearly a full turn.
        val a = (PI - 0.1).toFloat()
        val b = (-PI + 0.1).toFloat()
        val d = shortestDelta(a, b)
        assertTrue("small wrap delta (|$d|<0.5)", kotlin.math.abs(d) < 0.5f)
    }
}

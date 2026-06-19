package com.brioni.snake.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewModeTest {

    @Test
    fun `is3D is true for both perspective modes only`() {
        assertFalse(ViewMode.TwoD.is3D)
        assertTrue(ViewMode.ThreeD.is3D)
        assertTrue(ViewMode.ThreeDFixed.is3D)
    }

    @Test
    fun `fixedNorth is true only for the fixed variant`() {
        assertFalse(ViewMode.TwoD.fixedNorth)
        assertFalse(ViewMode.ThreeD.fixedNorth)
        assertTrue(ViewMode.ThreeDFixed.fixedNorth)
    }
}

package com.callbackdev.snake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The ghost trajectory is the replay contract for Step 6.9.12: its body
 * reconstruction and its int encoding must both round-trip exactly, since a
 * corrupt or drifting ghost would misrepresent the player's best run.
 */
class GhostRunTest {

    @Test
    fun `body is the trail of the last length head positions`() {
        // A snake walking right along row 5, growing from length 1 to 3.
        val xs = listOf(0, 1, 2, 3, 4)
        val ys = listOf(5, 5, 5, 5, 5)
        val lens = listOf(1, 2, 3, 3, 3)
        val run = GhostRun.of(20, 20, xs, ys, lens)!!

        // At the start pose the body is just the head.
        assertEquals(listOf(Position(0, 5)), run.bodyAt(0))
        // Grown to 3: head first, then the two cells it just left.
        assertEquals(
            listOf(Position(4, 5), Position(3, 5), Position(2, 5)),
            run.bodyAt(4),
        )
    }

    @Test
    fun `bodyAt clamps out-of-range indices to the log`() {
        val run = GhostRun.of(20, 20, listOf(0, 1, 2, 3), listOf(0, 0, 0, 0), listOf(1, 1, 1, 1))!!
        assertEquals(run.bodyAt(0), run.bodyAt(-5))
        assertEquals(run.bodyAt(run.lastTick), run.bodyAt(999))
    }

    @Test
    fun `round-trips through its integer encoding`() {
        val xs = listOf(3, 4, 4, 4, 5, 6)
        val ys = listOf(7, 7, 8, 9, 9, 9)
        val lens = listOf(3, 3, 4, 4, 5, 5)
        val run = GhostRun.of(24, 32, xs, ys, lens)!!

        val restored = GhostRun.fromInts(run.toInts())
        assertNotNull(restored)
        assertEquals(run.boardWidth, restored!!.boardWidth)
        assertEquals(run.boardHeight, restored.boardHeight)
        assertEquals(run.tickCount, restored.tickCount)
        for (i in 0..run.lastTick) {
            assertEquals(run.bodyAt(i), restored.bodyAt(i))
        }
    }

    @Test
    fun `rejects too-short runs and malformed encodings`() {
        assertNull(GhostRun.of(20, 20, listOf(0, 1), listOf(0, 0), listOf(1, 1)))
        assertNull(GhostRun.fromInts(intArrayOf())) // empty
        assertNull(GhostRun.fromInts(intArrayOf(999, 20, 20, 0))) // wrong version
        assertNull(GhostRun.fromInts(intArrayOf(GhostRun.FORMAT_VERSION, 20, 20, 5))) // count/size mismatch
    }
}

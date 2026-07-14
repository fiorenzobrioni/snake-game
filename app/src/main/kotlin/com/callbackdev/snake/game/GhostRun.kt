package com.callbackdev.snake.game

/**
 * A recorded per-tick head trajectory of a best run, replayed as a translucent
 * "ghost" snake in later runs of the same (mode × level × scale) slot
 * (Step 6.9.12).
 *
 * Only the head cell and the snake's length are stored per tick: the body at any
 * tick is exactly the trail of the last `length` head positions - just as a live
 * snake's body is the trail its head left behind - so the full ghost can be
 * reconstructed faithfully while keeping the log compact (a few KB for a
 * multi-minute run). Index 0 is the start pose (before the first tick); index
 * `i` is the pose after tick `i`, so it lines up 1:1 with [GameState.elapsedTicks].
 *
 * [boardWidth]/[boardHeight] pin the grid the run was recorded on, so a stored
 * ghost from a differently sized board can be detected and ignored.
 */
class GhostRun(
    val boardWidth: Int,
    val boardHeight: Int,
    private val headsX: IntArray,
    private val headsY: IntArray,
    private val lengths: IntArray,
) {
    /** Number of recorded ticks (index 0 = the start pose, before the first tick). */
    val tickCount: Int get() = headsX.size

    /** The highest valid tick index (the tick the recorded run died on). */
    val lastTick: Int get() = headsX.size - 1

    fun isEmpty(): Boolean = headsX.isEmpty()

    /**
     * The reconstructed body (head first) at tick [index], clamped to the log.
     * The body is the trail of the last `length` head positions, exactly as a
     * live snake's body is the trail its head left behind.
     */
    fun bodyAt(index: Int): List<Position> {
        if (headsX.isEmpty()) return emptyList()
        val i = index.coerceIn(0, headsX.size - 1)
        val len = lengths[i].coerceAtLeast(1)
        val body = ArrayList<Position>(len)
        for (j in 0 until len) {
            val k = (i - j).coerceAtLeast(0)
            body.add(Position(headsX[k], headsY[k]))
        }
        return body
    }

    /**
     * A flat integer encoding: a small header ([FORMAT_VERSION], width, height,
     * tick count) followed by one packed int per tick. The data-layer codec turns
     * this into the persisted string; keeping the packing here (pure Kotlin) makes
     * the trajectory format unit-testable and independent of the Android storage.
     */
    fun toInts(): IntArray {
        val out = IntArray(HEADER_SIZE + headsX.size)
        out[0] = FORMAT_VERSION
        out[1] = boardWidth
        out[2] = boardHeight
        out[3] = headsX.size
        for (i in headsX.indices) out[HEADER_SIZE + i] = pack(headsX[i], headsY[i], lengths[i])
        return out
    }

    companion object {
        const val FORMAT_VERSION = 1
        private const val HEADER_SIZE = 4

        /** Max ticks kept for a single ghost; bounds the stored size on a marathon run. */
        const val MAX_TICKS = 6_000

        /** Below this many recorded ticks a ghost is not worth storing or showing. */
        private const val MIN_TICKS = 4

        private fun pack(x: Int, y: Int, len: Int): Int =
            (len.coerceIn(0, 0xFFFF) shl 16) or (y.coerceIn(0, 0xFF) shl 8) or x.coerceIn(0, 0xFF)

        /** Rebuilds a [GhostRun] from [toInts] output, or null if malformed / wrong version. */
        fun fromInts(data: IntArray): GhostRun? {
            if (data.size < HEADER_SIZE) return null
            if (data[0] != FORMAT_VERSION) return null
            val width = data[1]
            val height = data[2]
            val count = data[3]
            if (count < 0 || data.size != HEADER_SIZE + count) return null
            val xs = IntArray(count)
            val ys = IntArray(count)
            val lens = IntArray(count)
            for (i in 0 until count) {
                val p = data[HEADER_SIZE + i]
                xs[i] = p and 0xFF
                ys[i] = (p ushr 8) and 0xFF
                lens[i] = (p ushr 16) and 0xFFFF
            }
            return GhostRun(width, height, xs, ys, lens)
        }

        /**
         * Builds a [GhostRun] from a recorded trajectory (parallel lists of head
         * cells and snake lengths), or null if it is too short to be worth storing.
         */
        fun of(
            boardWidth: Int,
            boardHeight: Int,
            headsX: List<Int>,
            headsY: List<Int>,
            lengths: List<Int>,
        ): GhostRun? {
            val n = minOf(headsX.size, headsY.size, lengths.size)
            if (n < MIN_TICKS) return null
            return GhostRun(
                boardWidth,
                boardHeight,
                IntArray(n) { headsX[it] },
                IntArray(n) { headsY[it] },
                IntArray(n) { lengths[it] },
            )
        }
    }
}

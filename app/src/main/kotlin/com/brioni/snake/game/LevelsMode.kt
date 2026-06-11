package com.brioni.snake.game

import kotlin.math.abs
import kotlin.math.min

/**
 * Rules and board shapes for [GameMode.Levels]: ten designed levels that repeat
 * forever, one speed cycle faster each lap, played with a stock of lives.
 *
 * Unlike the other modes there are no scattered obstacle blocks — each level
 * reshapes the *playable area* itself through wall cells ([shapeFor]) that the
 * renderer paints as "outside the board". Shapes are procedural over the
 * responsive [BoardDimensions] (boards differ per device aspect and per
 * [BoardScale]); the cut unit scales with the grid so Cozy boards get minimal
 * cuts and Epic boards get bolder ones. Every shape keeps a protected zone
 * around the centre spawn clear and leaves all playable cells connected
 * (asserted by unit tests).
 */
object LevelsMode {

    /** Designed levels per speed cycle; after level 10 the cycle wraps. */
    const val LEVEL_COUNT = 10

    /** Lives at the start of a run. */
    const val START_LIVES = 3

    /** Lives are capped here; extra-life food past the cap pays points instead. */
    const val MAX_LIVES = 5

    /** Points awarded by an extra-life food when lives are already at the cap. */
    const val LIFE_CAP_BONUS = 150

    /** Foods (any category) to eat before advancing to the next level. */
    const val LEVEL_FOOD_GOAL = 12

    /**
     * The difficulty [Level] this mode's scores are keyed on. The selector is
     * hidden and ignored in this mode — the speed comes from [tickMillisFor] —
     * but [ScoreKey] is a (mode × level × scale) triple, so one level is pinned.
     */
    val SCORE_LEVEL = Level.Beginner

    /** Base pace of Speed 1, the per-cycle step, and the fastest allowed pace. */
    const val BASE_TICK_MS = 170L
    const val STEP_PER_CYCLE_MS = 15L
    const val FLOOR_TICK_MS = 80L

    /** The tick interval for a 1-based [speedCycle] ("Speed x" in the HUD). */
    fun tickMillisFor(speedCycle: Int): Long =
        (BASE_TICK_MS - (speedCycle - 1) * STEP_PER_CYCLE_MS).coerceAtLeast(FLOOR_TICK_MS)

    /**
     * The wall cells for the 1-based [levelIndex] on [board]. Deterministic —
     * shapes are designed, not random. The protected centre spawn zone is
     * always subtracted and results are clamped to the board.
     */
    fun shapeFor(levelIndex: Int, board: BoardDimensions): Set<Position> {
        require(levelIndex in 1..LEVEL_COUNT) { "levelIndex must be in 1..$LEVEL_COUNT" }
        val raw = when (levelIndex) {
            1 -> emptySet() // Open Field: the classic rectangle
            2 -> cutCorners(board)
            3 -> twinPillars(board)
            4 -> crossfire(board)
            5 -> hourglass(board)
            6 -> crossedBlades(board)
            7 -> octagon(board)
            8 -> colonnade(board)
            9 -> threeChambers(board)
            else -> vault(board)
        }
        val clearZone = protectedCenter(board)
        return raw.filterTo(LinkedHashSet()) { cell ->
            cell.x in 0 until board.width && cell.y in 0 until board.height && cell !in clearZone
        }
    }

    /**
     * The clear zone around the centre that no shape may block: the snake
     * spawns at (w/2, h/2..h/2+2) heading Up, so the zone extends further
     * upward than downward. Narrower on small (Cozy) boards.
     */
    fun protectedCenter(board: BoardDimensions): Set<Position> {
        val cx = board.width / 2
        val cy = board.height / 2
        val halfW = if (board.width >= 16) 2 else 1
        return buildSet {
            for (x in (cx - halfW)..(cx + halfW)) {
                for (y in (cy - 4)..(cy + 3)) {
                    if (x in 0 until board.width && y in 0 until board.height) add(Position(x, y))
                }
            }
        }
    }

    /** Cut size unit: 1 on Cozy (12 short side), 2 on Classic (18), 3 on Epic (26). */
    private fun cutUnit(board: BoardDimensions): Int =
        (min(board.width, board.height) / 8).coerceAtLeast(1)

    /** Level 2 — Cut Corners: triangular notches in the four corners. */
    private fun cutCorners(b: BoardDimensions): Set<Position> {
        val cut = 2 * cutUnit(b)
        return buildSet {
            for (x in 0 until b.width) {
                for (y in 0 until b.height) {
                    val dx = min(x, b.width - 1 - x)
                    val dy = min(y, b.height - 1 - y)
                    if (dx + dy < cut) add(Position(x, y))
                }
            }
        }
    }

    /** Level 3 — Twin Pillars: two vertical bars at w/5 and its mirror. */
    private fun twinPillars(b: BoardDimensions): Set<Position> {
        val barW = cutUnit(b)
        val leftX = b.width / 5
        val rightX = b.width - leftX - barW
        val y0 = b.height / 4
        val y1 = b.height - 1 - b.height / 4
        return buildSet {
            for (y in y0..y1) {
                for (x in leftX until leftX + barW) add(Position(x, y))
                for (x in rightX until rightX + barW) add(Position(x, y))
            }
        }
    }

    /** Level 4 — Crossfire: four plus-shaped cutouts at the quarter points. */
    private fun crossfire(b: BoardDimensions): Set<Position> {
        val k = cutUnit(b)
        val qx = b.width / 4
        val qy = b.height / 4
        val centers = listOf(
            Position(qx, qy),
            Position(b.width - 1 - qx, qy),
            Position(qx, b.height - 1 - qy),
            Position(b.width - 1 - qx, b.height - 1 - qy),
        )
        return buildSet {
            centers.forEach { c ->
                for (i in -k..k) {
                    add(Position(c.x + i, c.y))
                    add(Position(c.x, c.y + i))
                }
            }
        }
    }

    /**
     * Level 5 — The Hourglass: triangular wedges grow out of the left and right
     * walls at mid-height, pinching the board into a narrow central waist with
     * open lanes above and below. Mirrored across both axes.
     */
    private fun hourglass(b: BoardDimensions): Set<Position> {
        val len = (b.width / 3).coerceAtLeast(2) // wedge depth from each side wall
        val maxHalf = (b.height / 4).coerceAtLeast(2) // wedge half-height at the wall
        val cy = b.height / 2
        return buildSet {
            for (d in 0 until len) {
                val half = maxHalf * (len - d) / len // tapers toward the centre
                for (y in (cy - half)..(cy + half)) {
                    add(Position(d, y))
                    add(Position(b.width - 1 - d, y))
                }
            }
        }
    }

    /**
     * Level 6 — Crossed Blades: four diagonal blades run from the corners toward
     * the centre, carving the board into four chambers that connect only through
     * the protected spawn zone. The blade is an 8-connected staircase, which a
     * 4-way mover can never slip through; thicker on Epic-sized grids.
     */
    private fun crossedBlades(b: BoardDimensions): Set<Position> {
        val cx = b.width / 2
        val cy = b.height / 2
        val steps = maxOf(cx, cy)
        if (steps == 0) return emptySet()
        val thick = cutUnit(b) >= 3
        // One corner-to-centre blade, then its four mirror images.
        val blade = buildSet {
            for (i in 0..steps) {
                val x = i * cx / steps
                val y = i * cy / steps
                add(Position(x, y))
                if (thick) add(Position(x + 1, y))
            }
        }
        return buildSet {
            blade.forEach { cell ->
                add(cell)
                add(Position(b.width - 1 - cell.x, cell.y))
                add(Position(cell.x, b.height - 1 - cell.y))
                add(Position(b.width - 1 - cell.x, b.height - 1 - cell.y))
            }
        }
    }

    /** Level 7 — The Octagon: diamond-cut corner wedges soften the rectangle. */
    private fun octagon(b: BoardDimensions): Set<Position> {
        val cx = (b.width - 1) / 2f
        val cy = (b.height - 1) / 2f
        val hw = b.width / 2f
        val hh = b.height / 2f
        return buildSet {
            for (x in 0 until b.width) {
                for (y in 0 until b.height) {
                    if (abs(x - cx) / hw + abs(y - cy) / hh > 1.3f) add(Position(x, y))
                }
            }
        }
    }

    /**
     * Level 8 — The Colonnade: a regular lattice of single-cell pillars centred
     * on the spawn column, turning the whole board into a slalom. The border
     * ring stays pillar-free so the outer lane is always open; the lattice is
     * wider-spaced on Cozy grids. Gaps fit a 2×2 maxi food exactly.
     */
    private fun colonnade(b: BoardDimensions): Set<Position> {
        val spacing = if (cutUnit(b) <= 1) 4 else 3
        val cx = b.width / 2
        val cy = b.height / 2
        return buildSet {
            for (x in 1 until b.width - 1) {
                for (y in 1 until b.height - 1) {
                    if ((x - cx).mod(spacing) == 0 && (y - cy).mod(spacing) == 0) {
                        add(Position(x, y))
                    }
                }
            }
        }
    }

    /** Level 9 — Three Chambers: two horizontal dividers, each pierced twice. */
    private fun threeChambers(b: BoardDimensions): Set<Position> {
        val passW = (cutUnit(b) + 1).coerceAtLeast(2)
        val y1 = b.height / 4
        val y2 = b.height - 1 - b.height / 4
        val px1 = (b.width / 4 - passW / 2).coerceAtLeast(1)
        val px2 = b.width - px1 - passW
        val passages = (px1 until px1 + passW).toSet() + (px2 until px2 + passW).toSet()
        return buildSet {
            for (x in 0 until b.width) {
                if (x in passages) continue
                add(Position(x, y1))
                add(Position(x, y2))
            }
        }
    }

    /** Level 10 — The Vault: an inner ring around the spawn with four offset gaps. */
    private fun vault(b: BoardDimensions): Set<Position> {
        val inset = (min(b.width, b.height) / 4).coerceAtLeast(3)
        val left = inset
        val right = b.width - 1 - inset
        val top = inset
        val bottom = b.height - 1 - inset
        if (right - left < 4 || bottom - top < 4) return emptySet()
        val gapW = (cutUnit(b) + 1).coerceAtLeast(2)
        val walls = LinkedHashSet<Position>()
        for (x in left..right) {
            walls.add(Position(x, top))
            walls.add(Position(x, bottom))
        }
        for (y in top..bottom) {
            walls.add(Position(left, y))
            walls.add(Position(right, y))
        }
        // One gap per side, offset clockwise for a spiral feel.
        for (i in 0 until gapW) {
            walls.remove(Position(left + 1 + i, top)) // top: near the left corner
            walls.remove(Position(right, top + 1 + i)) // right: near the top
            walls.remove(Position(right - 1 - i, bottom)) // bottom: near the right
            walls.remove(Position(left, bottom - 1 - i)) // left: near the bottom
        }
        return walls
    }
}

package com.brioni.snake.game

import kotlin.math.abs
import kotlin.math.min

/**
 * Rules and board shapes for [GameMode.Levels]: fifteen designed levels that
 * repeat forever, one speed cycle faster each lap, played with a stock of lives.
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

    /** Designed levels per speed cycle; after level 15 the cycle wraps. */
    const val LEVEL_COUNT = 15

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
            10 -> vault(board)
            11 -> lattice(board)
            12 -> pinwheel(board)
            13 -> crossVault(board)
            14 -> diamondField(board)
            else -> citadel(board)
        }
        val clearZone = protectedCenter(board)
        return raw.filterTo(LinkedHashSet()) { cell ->
            cell.x in 0 until board.width && cell.y in 0 until board.height && cell !in clearZone
        }
    }

    /**
     * The environmental hazards for the 1-based [levelIndex] on [board]
     * (Step 6.9.7): time-phased [Gate]s (moving walls) and [TeleportPair]s
     * (portals). Deterministic - designed per level, not random - and
     * **sanitised** so the result is always safe: gate/teleport cells never sit
     * on a static wall or the protected spawn zone, teleport pads are always two
     * distinct playable cells, and gates are dropped (most-recent first) until
     * every playable cell stays reachable with **all** gates closed, so a closing
     * gate can never trap the snake. Levels without hazards return
     * [LevelHazards.EMPTY].
     */
    fun hazardsFor(levelIndex: Int, board: BoardDimensions): LevelHazards {
        require(levelIndex in 1..LEVEL_COUNT) { "levelIndex must be in 1..$LEVEL_COUNT" }
        val walls = shapeFor(levelIndex, board)
        val raw = when (levelIndex) {
            1 -> openFieldHazards(board)
            3 -> twinPillarHazards(board)
            5 -> hourglassHazards(board)
            7 -> octagonHazards(board)
            9 -> threeChamberHazards(board)
            10 -> vaultHazards(board)
            11 -> latticeHazards(board)
            12 -> pinwheelHazards(board)
            13 -> crossVaultHazards(board)
            14 -> diamondFieldHazards(board)
            15 -> citadelHazards(board)
            else -> LevelHazards.EMPTY
        }
        return sanitize(raw, board, walls)
    }

    /**
     * Filters [raw] hazards down to a safe, well-formed set on [board]:
     *  - gate cells must be in bounds, off the static [walls] and off the
     *    protected centre; empty gates are dropped;
     *  - teleport pads must be in bounds, off walls / gates / the protected
     *    centre, and the two pads must be distinct; invalid pairs are dropped;
     *  - finally gates are trimmed (newest first) until the board stays fully
     *    connected with every remaining gate treated as closed, guaranteeing no
     *    gate cycle can ever seal the snake into a dead pocket.
     */
    private fun sanitize(
        raw: LevelHazards,
        board: BoardDimensions,
        walls: Set<Position>,
    ): LevelHazards {
        val clearZone = protectedCenter(board)
        fun valid(cell: Position) =
            cell.x in 0 until board.width && cell.y in 0 until board.height &&
                cell !in walls && cell !in clearZone

        val gateCells = HashSet<Position>()
        var gates = raw.gates.mapNotNull { gate ->
            val cells = gate.cells.filterTo(LinkedHashSet()) { valid(it) }
            if (cells.isEmpty()) null else gate.copy(cells = cells)
        }
        gates.forEach { gateCells.addAll(it.cells) }

        val teleports = raw.teleports.filter { pair ->
            pair.a != pair.b && pair.cells.all { valid(it) && it !in gateCells }
        }

        // Trim gates (most-recently-added first) until closing them all still
        // leaves every playable cell reachable from the spawn.
        val spawn = startingHead(board)
        while (gates.isNotEmpty()) {
            val closed = gates.flatMapTo(HashSet()) { it.cells }
            val blocked = walls + closed
            val playable = board.width * board.height - blocked.size
            if (reachableCount(spawn, blocked, board) == playable) break
            gates = gates.dropLast(1)
        }

        return LevelHazards(gates, teleports)
    }

    /** The head spawn cell (matches [GameEngine]'s centred three-cell snake). */
    private fun startingHead(board: BoardDimensions) = Position(board.width / 2, board.height / 2)

    /** Cells reachable from [start] with 4-way moves avoiding [blocked]. */
    private fun reachableCount(start: Position, blocked: Set<Position>, board: BoardDimensions): Int {
        if (start in blocked) return 0
        val seen = HashSet<Position>()
        val stack = ArrayDeque<Position>()
        seen.add(start)
        stack.addLast(start)
        while (stack.isNotEmpty()) {
            val cell = stack.removeLast()
            for (dir in Direction.entries) {
                val n = cell.step(dir)
                if (n.x !in 0 until board.width || n.y !in 0 until board.height) continue
                if (n in blocked || !seen.add(n)) continue
                stack.addLast(n)
            }
        }
        return seen.size
    }

    // --- Per-level hazard geometry (mirrors the wall shapes above) ------------

    /**
     * Level 1 - Open Field: two crossed teleport pairs at the quarter points
     * (NW-SE and NE-SW), turning the empty rectangle into a portal playground.
     */
    private fun openFieldHazards(b: BoardDimensions): LevelHazards {
        val qx = b.width / 4
        val qy = b.height / 4
        return LevelHazards(
            teleports = listOf(
                TeleportPair(Position(qx, qy), Position(b.width - 1 - qx, b.height - 1 - qy)),
                TeleportPair(Position(b.width - 1 - qx, qy), Position(qx, b.height - 1 - qy)),
            ),
        )
    }

    /**
     * Level 3 - Twin Pillars: two free-standing horizontal gate bars above and
     * below the pillars, out of phase, so the snake times its way through the
     * central corridor. The bars stop short of the side walls, so the open ends
     * are always a way around - they obstruct, never trap.
     */
    private fun twinPillarHazards(b: BoardDimensions): LevelHazards {
        val margin = (b.width / 6).coerceAtLeast(2)
        val fromX = margin
        val toX = b.width - 1 - margin
        if (toX - fromX < 2) return LevelHazards.EMPTY
        val cy = b.height / 2
        val topRow = (cy - b.height / 5).coerceAtLeast(1)
        val bottomRow = (cy + b.height / 5).coerceAtMost(b.height - 2)
        val half = Gate.DEFAULT_PERIOD / 2
        return LevelHazards(
            gates = listOf(
                horizontalGate(fromX, toX, topRow, offset = 0),
                horizontalGate(fromX, toX, bottomRow, offset = half),
            ),
        )
    }

    /**
     * Level 5 - The Hourglass: a teleport pair linking the open top and bottom
     * lanes, a shortcut straight through the pinched waist for the bold.
     */
    private fun hourglassHazards(b: BoardDimensions): LevelHazards {
        val cx = b.width / 2
        return LevelHazards(
            teleports = listOf(
                TeleportPair(Position(cx, b.height / 5), Position(cx, b.height - 1 - b.height / 5)),
            ),
        )
    }

    /**
     * Level 7 - The Octagon: two horizontal gate bars sweeping the wide central
     * chamber, out of phase, with open ends near the bevelled corners to slip past.
     */
    private fun octagonHazards(b: BoardDimensions): LevelHazards {
        val margin = (b.width / 4).coerceAtLeast(2)
        val fromX = margin
        val toX = b.width - 1 - margin
        if (toX - fromX < 2) return LevelHazards.EMPTY
        val cy = b.height / 2
        val topRow = (cy - b.height / 6).coerceAtLeast(1)
        val bottomRow = (cy + b.height / 6).coerceAtMost(b.height - 2)
        val half = Gate.DEFAULT_PERIOD / 2
        return LevelHazards(
            gates = listOf(
                horizontalGate(fromX, toX, topRow, offset = 0),
                horizontalGate(fromX, toX, bottomRow, offset = half),
            ),
        )
    }

    /**
     * Level 9 - Three Chambers: a gate sealing one doorway in each divider (the
     * top divider's left passage, the bottom divider's right passage), out of
     * phase. Each divider always keeps its other passage open, so the chambers
     * stay linked - timing a closing door just costs a detour.
     */
    private fun threeChamberHazards(b: BoardDimensions): LevelHazards {
        val passW = (cutUnit(b) + 1).coerceAtLeast(2)
        val y1 = b.height / 4
        val y2 = b.height - 1 - b.height / 4
        val px1 = (b.width / 4 - passW / 2).coerceAtLeast(1)
        val px2 = b.width - px1 - passW
        val topDoor = (px1 until px1 + passW).map { Position(it, y1) }.toSet()
        val bottomDoor = (px2 until px2 + passW).map { Position(it, y2) }.toSet()
        val half = Gate.DEFAULT_PERIOD / 2
        return LevelHazards(
            gates = listOf(
                Gate(topDoor, offsetTicks = 0),
                Gate(bottomDoor, offsetTicks = half),
            ),
        )
    }

    /**
     * Level 10 - The Vault: a teleport pair punching a shortcut from inside the
     * ring to a far outside corner, plus a gate that re-seals the top doorway on
     * a cycle (the vault's other three gaps keep it reachable throughout).
     */
    private fun vaultHazards(b: BoardDimensions): LevelHazards {
        val inset = (min(b.width, b.height) / 4).coerceAtLeast(3)
        val left = inset
        val right = b.width - 1 - inset
        val top = inset
        val bottom = b.height - 1 - inset
        if (right - left < 4 || bottom - top < 4) return LevelHazards.EMPTY
        val gapW = (cutUnit(b) + 1).coerceAtLeast(2)
        // The top gap (carved near the left corner in vault()) becomes a gate.
        val topGate = (0 until gapW).map { Position(left + 1 + it, top) }.toSet()
        val inside = Position(left + 1, bottom - 1)
        val outside = Position((b.width - 2).coerceAtLeast(0), 1)
        return LevelHazards(
            gates = listOf(Gate(topGate)),
            teleports = listOf(TeleportPair(inside, outside)),
        )
    }

    /** A horizontal moving-wall gate from [fromX]..[toX] (inclusive) on [row]. */
    private fun horizontalGate(fromX: Int, toX: Int, row: Int, offset: Int): Gate {
        val cells = (fromX..toX).mapTo(LinkedHashSet()) { Position(it, row) }
        return Gate(cells, offsetTicks = offset)
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

    /**
     * Level 11 — The Lattice: a quincunx of single-cell pillars on alternate rows
     * (the rows between are clear), offset every other pillar row so the slalom
     * still staggers. Skipping every other row roughly halves the old all-rows
     * density, opening proper weaving lanes while staying clearly harder than the
     * Colonnade. The pillars are isolated (never adjacent), so the board always
     * stays connected.
     */
    private fun lattice(b: BoardDimensions): Set<Position> {
        val cx = b.width / 2
        val cy = b.height / 2
        return buildSet {
            for (x in 1 until b.width - 1) {
                for (y in 1 until b.height - 1) {
                    val u = x - cx
                    val v = y - cy
                    val on = when {
                        v.mod(2) != 0 -> false // clear rows between the pillar rows
                        v.mod(4) == 0 -> u.mod(4) == 0
                        else -> (u - 2).mod(4) == 0 // offset the in-between pillar rows
                    }
                    if (on) add(Position(x, y))
                }
            }
        }
    }

    /** Level 11 — a single long teleport pair across the lattice (NW <-> SE corner). */
    private fun latticeHazards(b: BoardDimensions): LevelHazards {
        val qx = b.width / 4
        val qy = b.height / 4
        return LevelHazards(
            teleports = listOf(
                TeleportPair(Position(qx, qy), Position(b.width - 1 - qx, b.height - 1 - qy)),
            ),
        )
    }

    /**
     * Level 12 — Pinwheel: four open-ended vanes spin out of the centre (180°
     * rotational), each reaching off in a different direction so they never
     * enclose a pocket. The board stays open around every vane tip.
     */
    private fun pinwheel(b: BoardDimensions): Set<Position> {
        val cx = b.width / 2
        val cy = b.height / 2
        val arm = (min(b.width, b.height) / 3).coerceAtLeast(3)
        val off = (cutUnit(b) + 1)
        return buildSet {
            for (i in 0 until arm) {
                // Top vane sweeps right, bottom vane sweeps left.
                add(Position((cx + i).coerceAtMost(b.width - 2), cy - off))
                add(Position((cx - i).coerceAtLeast(1), cy + off))
                // Left vane sweeps up, right vane sweeps down.
                add(Position(cx - off, (cy - i).coerceAtLeast(1)))
                add(Position(cx + off, (cy + i).coerceAtMost(b.height - 2)))
            }
        }
    }

    /**
     * Level 12 — two free-standing horizontal gate bars above and below the vanes,
     * out of phase, with open ends to slip past while the pinwheel funnels the path.
     */
    private fun pinwheelHazards(b: BoardDimensions): LevelHazards {
        val margin = (b.width / 5).coerceAtLeast(2)
        val fromX = margin
        val toX = b.width - 1 - margin
        if (toX - fromX < 2) return LevelHazards.EMPTY
        val cy = b.height / 2
        val topRow = (cy - b.height / 4).coerceAtLeast(1)
        val bottomRow = (cy + b.height / 4).coerceAtMost(b.height - 2)
        val half = Gate.DEFAULT_PERIOD / 2
        return LevelHazards(
            gates = listOf(
                horizontalGate(fromX, toX, topRow, offset = 0),
                horizontalGate(fromX, toX, bottomRow, offset = half),
            ),
        )
    }

    /**
     * Level 13 — Cross Vault: a thin central cross whose four arms stop short of
     * the walls, so the quadrants connect round the open border ring. The centre
     * is cleared by the protected spawn zone. Hazards add crossed teleports and
     * gates that threaten to seal the go-around lanes.
     */
    private fun crossVault(b: BoardDimensions): Set<Position> {
        val cx = b.width / 2
        val cy = b.height / 2
        val m = (min(b.width, b.height) / 5).coerceAtLeast(2)
        val t = cutUnit(b) - 1 // arm half-thickness: 0 on Cozy, 1 Classic, 2 Epic
        return buildSet {
            for (y in m..b.height - 1 - m) for (dx in -t..t) add(Position(cx + dx, y))
            for (x in m..b.width - 1 - m) for (dy in -t..t) add(Position(x, cy + dy))
        }
    }

    /**
     * Level 13 — crossed teleports linking opposite quadrants, plus two vertical
     * gates extending the cross's top and bottom arms toward the border (out of
     * phase). The left/right arm ends and the portals keep the board reachable.
     */
    private fun crossVaultHazards(b: BoardDimensions): LevelHazards {
        val cx = b.width / 2
        val m = (min(b.width, b.height) / 5).coerceAtLeast(2)
        val topGate = (1 until m).map { Position(cx, it) }.toSet()
        val bottomGate = (b.height - m until b.height - 1).map { Position(cx, it) }.toSet()
        val half = Gate.DEFAULT_PERIOD / 2
        val qx = b.width / 4
        val qy = b.height / 4
        return LevelHazards(
            gates = listOf(Gate(topGate, offsetTicks = 0), Gate(bottomGate, offsetTicks = half)),
            teleports = listOf(
                TeleportPair(Position(qx, qy), Position(b.width - 1 - qx, b.height - 1 - qy)),
                TeleportPair(Position(b.width - 1 - qx, qy), Position(qx, b.height - 1 - qy)),
            ),
        )
    }

    /**
     * Level 14 — Diamond Field: a regular lattice of small diamond pillars (a
     * centre cell with four orthogonal arms). The diamonds are spaced well apart
     * so the board stays connected; a teleport pair and a moving gate stir it up.
     */
    private fun diamondField(b: BoardDimensions): Set<Position> {
        val cx = b.width / 2
        val cy = b.height / 2
        val step = if (cutUnit(b) <= 1) 6 else 5
        return buildSet {
            for (x in 2 until b.width - 2) {
                for (y in 2 until b.height - 2) {
                    if ((x - cx).mod(step) == 0 && (y - cy).mod(step) == 0) {
                        add(Position(x, y))
                        add(Position(x - 1, y)); add(Position(x + 1, y))
                        add(Position(x, y - 1)); add(Position(x, y + 1))
                    }
                }
            }
        }
    }

    /** Level 14 — a vertical teleport pair plus a central moving gate bar. */
    private fun diamondFieldHazards(b: BoardDimensions): LevelHazards {
        val cx = b.width / 2
        val margin = (b.width / 4).coerceAtLeast(2)
        val fromX = margin
        val toX = b.width - 1 - margin
        val topTele = Position(cx, b.height / 6)
        val bottomTele = Position(cx, b.height - 1 - b.height / 6)
        val gateRow = (b.height / 2 - b.height / 5).coerceAtLeast(1)
        val gates = if (toX - fromX >= 2) listOf(horizontalGate(fromX, toX, gateRow, offset = 0)) else emptyList()
        return LevelHazards(
            gates = gates,
            teleports = listOf(TeleportPair(topTele, bottomTele)),
        )
    }

    /**
     * Level 15 — The Citadel (finale): two nested rings around the spawn, the
     * outer one with clockwise-spiralled gaps and the inner one with centred gaps,
     * so the path corkscrews inward. A teleport offers a daring escape and a gate
     * re-seals one outer gap; the rings' other gaps keep everything reachable.
     */
    private fun citadel(b: BoardDimensions): Set<Position> {
        val outer = ringWithGaps(b, (min(b.width, b.height) / 6).coerceAtLeast(2), cutUnit(b) + 1, spiral = true)
        val inner = ringWithGaps(b, (min(b.width, b.height) / 3).coerceAtLeast(4), cutUnit(b) + 1, spiral = false)
        return outer + inner
    }

    /** Level 15 — a teleport escape from deep inside to a far corner, plus a gate over one outer gap. */
    private fun citadelHazards(b: BoardDimensions): LevelHazards {
        val inset = (min(b.width, b.height) / 6).coerceAtLeast(2)
        val left = inset
        val top = inset
        val gapW = (cutUnit(b) + 1).coerceAtLeast(2)
        if (b.width - 1 - inset - left < 4 || b.height - 1 - inset - top < 4) return LevelHazards.EMPTY
        // Re-seal the outer ring's top gap (carved near the left corner by spiral).
        val topGate = (0 until gapW).map { Position(left + 1 + it, top) }.toSet()
        val inside = Position(b.width / 2, b.height / 2 + (b.height / 3 - 1).coerceAtLeast(1))
        val outside = Position(1, 1)
        return LevelHazards(
            gates = listOf(Gate(topGate)),
            teleports = listOf(TeleportPair(inside, outside)),
        )
    }

    /**
     * A rectangular ring of walls at [inset] from each edge with one gap per side.
     * [spiral] offsets the gaps clockwise (a corkscrew feel); otherwise they are
     * centred on each side. Returns empty if the ring would be too small.
     */
    private fun ringWithGaps(b: BoardDimensions, inset: Int, gapW: Int, spiral: Boolean): Set<Position> {
        val left = inset
        val right = b.width - 1 - inset
        val top = inset
        val bottom = b.height - 1 - inset
        if (right - left < 4 || bottom - top < 4) return emptySet()
        val g = gapW.coerceAtLeast(2)
        val walls = LinkedHashSet<Position>()
        for (x in left..right) {
            walls.add(Position(x, top))
            walls.add(Position(x, bottom))
        }
        for (y in top..bottom) {
            walls.add(Position(left, y))
            walls.add(Position(right, y))
        }
        if (spiral) {
            for (i in 0 until g) {
                walls.remove(Position(left + 1 + i, top))
                walls.remove(Position(right, top + 1 + i))
                walls.remove(Position(right - 1 - i, bottom))
                walls.remove(Position(left, bottom - 1 - i))
            }
        } else {
            val mx = (left + right) / 2
            val my = (top + bottom) / 2
            for (i in 0 until g) {
                walls.remove(Position(mx - g / 2 + i, top))
                walls.remove(Position(mx - g / 2 + i, bottom))
                walls.remove(Position(left, my - g / 2 + i))
                walls.remove(Position(right, my - g / 2 + i))
            }
        }
        return walls
    }
}

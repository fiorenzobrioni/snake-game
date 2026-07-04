package com.callbackdev.snake.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.callbackdev.snake.game.BoardTerrain
import com.callbackdev.snake.game.Gate
import com.callbackdev.snake.game.GameState
import com.callbackdev.snake.game.Position
import com.callbackdev.snake.game.TeleportPair
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renderers for the Campaign environmental hazards (Step 6.9.7): time-phased
 * **moving-wall gates** drawn as energy barriers between projector nodes, and
 * **teleport pads** drawn as swirling portals. Both are painted under the snake
 * (inside the board clip) so the head reads as sliding into a barrier or through
 * a portal. Geometry stays in the model; this only translates state to pixels.
 */

/**
 * The gate barrier's plasma family (base energy, hot core) for a terrain, so the
 * "electric walls" feel native to the stage they stand on: warm plasma on the
 * dark Arcade floor, golden sunlight on the Meadow lawn, bioluminescent aqua in
 * the Abyss, violet plasma under the Nebula, ember heat on the Dunes and a cold
 * electric blue on the Glacier. Every pair stays hot/saturated enough to read as
 * "danger" against its own floor.
 */
private fun gateEnergyFor(terrain: BoardTerrain): Pair<Color, Color> = when (terrain) {
    BoardTerrain.Arcade -> Color(0xFFFF6E40) to Color(0xFFFFE0B2)
    BoardTerrain.Meadow -> Color(0xFFFFB300) to Color(0xFFFFF3C4)
    BoardTerrain.Abyss -> Color(0xFF40E8FF) to Color(0xFFE0FBFF)
    BoardTerrain.Nebula -> Color(0xFFB05CFF) to Color(0xFFF0DDFF)
    BoardTerrain.Dunes -> Color(0xFFFF8A3D) to Color(0xFFFFE8C2)
    BoardTerrain.Glacier -> Color(0xFF2E8BFF) to Color(0xFFD6E8FF)
}

/** The strobe colour while a gate is about to slam shut (universal warning cue). */
private val GateWarn = Color(0xFFFFD54F)

/** The metallic projector nodes the barrier spans between. */
private val GateNode = Color(0xFFB0BEC5)

/** Distinct, skin-independent portal hues; paired pads share one so the link reads. */
private val PortalColors = listOf(
    Color(0xFF26C6DA), // cyan
    Color(0xFF7C4DFF), // violet
    Color(0xFF00E5FF), // electric blue
    Color(0xFFFF4081), // magenta
)

/** Portal accent for the teleport pair at [index]. */
fun portalColor(index: Int): Color = PortalColors[index.mod(PortalColors.size)]

/**
 * The barrier opacity for [gate] on the displayed frame [tick], eased across the
 * inter-tick fraction [f] so the force field smoothly forms as it closes and
 * dissolves as it opens (rather than snapping at the tick boundary). 0 = fully
 * open (invisible barrier), 1 = fully closed (solid, lethal).
 */
private fun gateSolidity(gate: Gate, tick: Int, f: Float): Float {
    val openNow = gate.isOpenAt(tick)
    val openNext = gate.isOpenAt(tick + 1)
    return when {
        !openNow && !openNext -> 1f
        openNow && openNext -> 0f
        openNow && !openNext -> f // closing across this tick
        else -> 1f - f // opening across this tick
    }
}

/** Draws every gate on [state] for the current frame, in [terrain]-themed plasma. */
fun DrawScope.drawGates(
    state: GameState,
    tick: Int,
    f: Float,
    seconds: Double,
    cell: Float,
    originX: Float,
    originY: Float,
    reduceMotion: Boolean,
    terrain: BoardTerrain = BoardTerrain.Arcade,
) {
    if (state.gates.isEmpty()) return
    val (energy, energyHot) = gateEnergyFor(terrain)
    state.gates.forEach { gate ->
        drawGate(gate, tick, f, seconds, cell, originX, originY, reduceMotion, energy, energyHot)
    }
}

private fun DrawScope.drawGate(
    gate: Gate,
    tick: Int,
    f: Float,
    seconds: Double,
    cell: Float,
    originX: Float,
    originY: Float,
    reduceMotion: Boolean,
    energy: Color,
    energyHot: Color,
) {
    val solidity = gateSolidity(gate, tick, f)
    val closingSoon = gate.isClosingSoonAt(tick)
    val corner = CornerRadius(cell * 0.18f, cell * 0.18f)

    // Always-visible rail: a faint footprint so the player can read the gate's
    // path even while it is wide open.
    gate.cells.forEach { c ->
        val tl = Offset(originX + c.x * cell, originY + c.y * cell)
        drawRoundRect(
            color = energy.copy(alpha = 0.10f + 0.05f * solidity),
            topLeft = Offset(tl.x + cell * 0.18f, tl.y + cell * 0.18f),
            size = Size(cell * 0.64f, cell * 0.64f),
            cornerRadius = corner,
            style = Stroke(width = (cell * 0.05f).coerceAtLeast(1f)),
        )
    }

    if (solidity > 0.01f) {
        // Energy barrier: a glowing plasma fill with a moving electric scanline
        // and a hot core, alpha-driven by the smoothed open/close envelope.
        val flicker = if (reduceMotion) 1f else 0.82f + 0.18f * sin(seconds * 22.0).toFloat()
        gate.cells.forEach { c ->
            val left = originX + c.x * cell
            val top = originY + c.y * cell
            val sz = Size(cell + 0.5f, cell + 0.5f)
            // Soft outer glow.
            drawRoundRect(
                color = energy.copy(alpha = 0.28f * solidity * flicker),
                topLeft = Offset(left - cell * 0.06f, top - cell * 0.06f),
                size = Size(sz.width + cell * 0.12f, sz.height + cell * 0.12f),
                cornerRadius = CornerRadius(corner.x * 1.4f, corner.y * 1.4f),
            )
            // Plasma body, lit hotter through the middle.
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        energy.copy(alpha = 0.85f * solidity),
                        energyHot.copy(alpha = 0.95f * solidity * flicker),
                        energy.copy(alpha = 0.85f * solidity),
                    ),
                    startY = top,
                    endY = top + cell,
                ),
                topLeft = Offset(left, top),
                size = sz,
                cornerRadius = corner,
            )
        }
        // A single hot scanline sweeping along the barrier for an energised feel.
        if (!reduceMotion) {
            gate.cells.forEach { c ->
                val left = originX + c.x * cell
                val top = originY + c.y * cell
                val sweep = ((sin(seconds * 3.0 + c.x * 0.6 + c.y * 0.6) + 1.0) / 2.0).toFloat()
                val ly = top + sweep * cell
                drawLine(
                    color = energyHot.copy(alpha = 0.5f * solidity),
                    start = Offset(left, ly),
                    end = Offset(left + cell, ly),
                    strokeWidth = cell * 0.08f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }

    // Closing warning: a strobing outline so a slam-shut is never a surprise.
    if (closingSoon && !reduceMotion) {
        val blink = 0.45f + 0.55f * sin(seconds * 26.0).toFloat()
        gate.cells.forEach { c ->
            val left = originX + c.x * cell
            val top = originY + c.y * cell
            drawRoundRect(
                color = GateWarn.copy(alpha = (0.85f * blink).coerceIn(0f, 1f)),
                topLeft = Offset(left, top),
                size = Size(cell, cell),
                cornerRadius = corner,
                style = Stroke(width = (cell * 0.10f).coerceAtLeast(2f)),
            )
        }
    }

    // Projector nodes anchoring the two ends of the gate (the emitters).
    val ends = gateEnds(gate.cells)
    ends.forEach { c ->
        val center = Offset(originX + (c.x + 0.5f) * cell, originY + (c.y + 0.5f) * cell)
        drawCircle(GateNode, cell * 0.22f, center)
        drawCircle(
            color = (if (solidity > 0.5f) energyHot else GateNode).copy(alpha = 0.9f),
            radius = cell * 0.11f,
            center = center,
        )
    }
}

/** The two extreme cells of a straight gate (its projector anchors). */
private fun gateEnds(cells: Set<Position>): List<Position> {
    if (cells.isEmpty()) return emptyList()
    val first = cells.minWith(compareBy({ it.y }, { it.x }))
    val last = cells.maxWith(compareBy({ it.y }, { it.x }))
    return if (first == last) listOf(first) else listOf(first, last)
}

/** Draws every teleport pad on [state] for the current frame. */
fun DrawScope.drawTeleports(
    state: GameState,
    seconds: Double,
    cell: Float,
    originX: Float,
    originY: Float,
    reduceMotion: Boolean,
    overlay: Boolean = false,
) {
    if (state.teleports.isEmpty()) return
    state.teleports.forEachIndexed { index, pair ->
        val color = portalColor(index)
        // The paired pads counter-spin so they read as linked yet distinct ends.
        drawPortal(pair.a, color, seconds, spin = seconds, cell, originX, originY, reduceMotion, overlay)
        drawPortal(pair.b, color, seconds, spin = -seconds, cell, originX, originY, reduceMotion, overlay)
    }
}

/**
 * A swirling portal disc: an outer halo, two counter-rotating arc rings, a bright
 * rim and a dark vortex core. [spin] drives the arc rotation (one pad of a pair
 * spins the opposite way, hinting they are linked yet distinct entry/exit).
 *
 * When [overlay] is true this is the lighter pass drawn *over* the snake: it skips
 * the outer halo, tints the throat instead of blackening it, and runs at reduced
 * opacity, so a body segment crossing a pad reads as half-transparent / phasing
 * (the snake can pass straight through a portal). The footprint stays within the
 * single pad cell so the active area never looks larger than it is.
 */
private fun DrawScope.drawPortal(
    at: Position,
    color: Color,
    seconds: Double,
    spin: Double,
    cell: Float,
    originX: Float,
    originY: Float,
    reduceMotion: Boolean,
    overlay: Boolean,
) {
    val center = Offset(originX + (at.x + 0.5f) * cell, originY + (at.y + 0.5f) * cell)
    val pulse = if (reduceMotion) 1f else 0.9f + 0.1f * sin(seconds * 3.4).toFloat()
    val r = cell * 0.46f * pulse
    // The over-snake pass is dimmer so it veils rather than hides the body.
    val a = if (overlay) 0.5f else 1f

    // Outer halo - under-pass only, and kept tight so it does not bleed into the
    // neighbouring cells (which made the active area look bigger than one cell).
    if (!overlay) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.5f), Color.Transparent),
                center = center,
                radius = r * 1.55f,
            ),
            radius = r * 1.55f,
            center = center,
        )
    }
    // Vortex throat: solid+dark under the snake, a soft colour tint over it.
    drawCircle(
        brush = Brush.radialGradient(
            colors = if (overlay) {
                listOf(color.copy(alpha = 0.35f), Color.Transparent)
            } else {
                listOf(Color.Black.copy(alpha = 0.85f), color.copy(alpha = 0.25f))
            },
            center = center,
            radius = r,
        ),
        radius = r,
        center = center,
    )
    // Counter-rotating arc rings.
    val base: Double = if (reduceMotion) 0.0 else spin
    for (k in 0 until 2) {
        val dir = if (k == 0) 1.0 else -1.0
        val start = ((base * 90.0 * dir) + k * 60.0).toFloat()
        drawArc(
            color = color.copy(alpha = 0.9f * a),
            startAngle = start,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = Offset(center.x - r * (0.82f - k * 0.18f), center.y - r * (0.82f - k * 0.18f)),
            size = Size(r * 2 * (0.82f - k * 0.18f), r * 2 * (0.82f - k * 0.18f)),
            style = Stroke(width = cell * 0.07f, cap = StrokeCap.Round),
        )
    }
    // Bright rim.
    drawCircle(
        color = lightenColor(color, 0.4f).copy(alpha = 0.9f * a),
        radius = r,
        center = center,
        style = Stroke(width = cell * 0.06f),
    )
}

/** Mixes [c] toward white by [f] - a local copy so this file stays self-contained. */
private fun lightenColor(c: Color, f: Float): Color = Color(
    red = c.red + (1f - c.red) * f,
    green = c.green + (1f - c.green) * f,
    blue = c.blue + (1f - c.blue) * f,
    alpha = c.alpha,
)

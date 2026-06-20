package com.brioni.snake.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.brioni.snake.game.BoardDimensions
import com.brioni.snake.game.Direction
import com.brioni.snake.game.EffectKind
import com.brioni.snake.game.Food
import com.brioni.snake.game.FoodCategory
import com.brioni.snake.game.FoodEffect
import com.brioni.snake.game.FoodTier
import com.brioni.snake.game.GameState
import com.brioni.snake.game.Position
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the board on a Compose [Canvas]. The grid logic stays in the model;
 * this renders state with Phase 2 polish:
 *  - smooth inter-tick motion (interpolating each segment from [previousSnake]
 *    to the current snake over one tick),
 *  - a gradient background, bevelled obstacles, pulsing food and a glowing,
 *    eyed snake head,
 *  - a particle burst on eat.
 *
 * The board is scaled to fit and centred while keeping its cell aspect ratio.
 */

/** Final window of a Ghost (Star) effect over which the warning blink ramps up. */
private const val GHOST_WARN_MS = 2_000f

/**
 * Height (cells) of the luminous boundary barrier in the 3D views. Tunable: this is
 * the only knob for how tall the neon border energy-field stands above the floor.
 */
private const val WALL_HEIGHT = 0.7f

/** Mixes [c] toward white by [f] (0..1), forcing full opacity — for bright rails. */
private fun brighten(c: Color, f: Float): Color = Color(
    red = c.red + (1f - c.red) * f,
    green = c.green + (1f - c.green) * f,
    blue = c.blue + (1f - c.blue) * f,
    alpha = 1f,
)

/** Darkens [c] toward black by fraction [f] (0 = unchanged, 1 = black). */
private fun darken(c: Color, f: Float): Color = Color(
    red = c.red * (1f - f),
    green = c.green * (1f - f),
    blue = c.blue * (1f - f),
    alpha = c.alpha,
)

/** Linear interpolation between two screen points. */
private fun lerpOffset(a: Offset, b: Offset, t: Float): Offset =
    Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/**
 * The screen->UV homography for a projected quad whose corners [p0]..[p3] are the
 * images of UV (0,0), (1,0), (1,1), (0,1). Returns the 3x3 (row-major, 9 floats)
 * mapping a homogeneous screen point to homogeneous UV, or null if degenerate. Lets
 * the wall shader flow its energy *along the wall* in correct perspective. Built via
 * Heckbert's square->quad map, then inverted.
 */
private fun screenToUvHomography(p0: Offset, p1: Offset, p2: Offset, p3: Offset): FloatArray? {
    val dx1 = p1.x - p2.x
    val dx2 = p3.x - p2.x
    val dy1 = p1.y - p2.y
    val dy2 = p3.y - p2.y
    val sx = p0.x - p1.x + p2.x - p3.x
    val sy = p0.y - p1.y + p2.y - p3.y
    val denom = dx1 * dy2 - dx2 * dy1
    if (abs(denom) < 1e-6f) return null
    val g = (sx * dy2 - dx2 * sy) / denom
    val h = (dx1 * sy - sx * dy1) / denom
    // M maps homogeneous UV -> homogeneous screen; invert it for screen -> UV.
    val m = floatArrayOf(
        p1.x - p0.x + g * p1.x, p3.x - p0.x + h * p3.x, p0.x,
        p1.y - p0.y + g * p1.y, p3.y - p0.y + h * p3.y, p0.y,
        g, h, 1f,
    )
    return invert3x3(m)
}

/** Inverse of a row-major 3x3, or null when (near-)singular. */
private fun invert3x3(m: FloatArray): FloatArray? {
    val a = m[0]; val b = m[1]; val c = m[2]
    val d = m[3]; val e = m[4]; val f = m[5]
    val g = m[6]; val h = m[7]; val i = m[8]
    val ca = e * i - f * h
    val cb = -(d * i - f * g)
    val cc = d * h - e * g
    val det = a * ca + b * cb + c * cc
    if (abs(det) < 1e-9f) return null
    val inv = 1f / det
    return floatArrayOf(
        ca * inv, (c * h - b * i) * inv, (b * f - c * e) * inv,
        cb * inv, (a * i - c * g) * inv, (c * d - a * f) * inv,
        cc * inv, (b * g - a * h) * inv, (a * e - b * d) * inv,
    )
}

/** Fills a polygon (screen-space [px]) with a top-to-bottom vertical gradient. */
private fun DrawScope.fillFace(px: List<Offset>, top: Color, bottom: Color) {
    val path = Path().apply {
        moveTo(px[0].x, px[0].y)
        for (i in 1 until px.size) lineTo(px[i].x, px[i].y)
        close()
    }
    drawPath(
        path,
        brush = Brush.verticalGradient(listOf(top, bottom), startY = px.minOf { it.y }, endY = px.maxOf { it.y }),
    )
}

@Composable
fun GameBoard(
    state: GameState,
    previousSnake: List<Position>,
    tickTimeNanos: Long,
    tickMillis: Long,
    running: Boolean,
    eatEvent: EatEvent?,
    eatEventId: Int,
    floatingText: FloatingTextEvent?,
    floatingTextId: Int,
    textMeasurer: TextMeasurer,
    palette: SkinPalette,
    borderColor: Color = palette.boardBorder,
    outsideColor: Color = Color.Black,
    cameraBlend: Float = 0f,
    fixedNorth: Boolean = false,
    electricField: Boolean = true,
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val particles: SnapshotStateList<Particle> = remember { emptyList<Particle>().toMutableStateList() }
    val floatingTexts: SnapshotStateList<FloatingText> = remember { emptyList<FloatingText>().toMutableStateList() }
    // Levels mode: the playable-area outline (in cell units) that replaces the
    // rectangular frame when the board has a designed wall shape.
    val boundary = remember(state.walls, state.board) { boundaryEdges(state.walls, state.board) }
    // AGSL effects (always available — minSdk is 33), created once.
    val shaders = remember { BoardShaders() }
    // A monotonic per-frame timestamp (same clock as tickTimeNanos) that pulses
    // a redraw each frame; the interpolation fraction is *derived* from it and
    // tickTimeNanos at draw time, so it can never lag a committed move.
    var frameNanos by remember { mutableLongStateOf(System.nanoTime()) }

    // Chase-cam yaw, eased toward the heading so a 90° turn swings smoothly
    // rather than snapping. Only meaningful while the 3D view is on screen.
    val yawAnim = remember { Animatable(yawFor(state.direction)) }
    LaunchedEffect(state.direction) {
        val target = yawAnim.value + shortestDelta(yawAnim.value, yawFor(state.direction))
        yawAnim.animateTo(target, tween(durationMillis = 150, easing = FastOutSlowInEasing))
    }

    // A single frame-driven loop runs only while the game is running: it
    // advances the particles and writes [frameNanos] to force a redraw each
    // frame. When not running it clears any leftover particles.
    LaunchedEffect(running) {
        if (!running) {
            particles.clear()
            floatingTexts.clear()
            return@LaunchedEffect
        }
        var lastNanos = System.nanoTime()
        while (true) {
            withFrameNanos { }
            val now = System.nanoTime()
            val dt = ((now - lastNanos) / 1_000_000_000.0).toFloat()
            lastNanos = now
            updateParticles(particles, dt)
            updateFloatingTexts(floatingTexts, dt)
            frameNanos = now
        }
    }

    LaunchedEffect(eatEventId) {
        val event = eatEvent
        // Reduce-motion suppresses the particle bursts (the floating "+N" labels stay).
        if (eatEventId > 0 && event != null && !reduceMotion) {
            val cx = event.cell.x + event.span / 2f
            val cy = event.cell.y + event.span / 2f
            when (event.style) {
                BurstStyle.Eat -> emitEatBurst(particles, cx, cy, event.color, event.span)
                BurstStyle.Implode -> emitImplodeBurst(particles, cx, cy, event.color, event.span)
                BurstStyle.Vanish -> emitVanishBurst(particles, cx, cy, event.color, event.span)
            }
        }
    }

    LaunchedEffect(floatingTextId) {
        val event = floatingText
        if (floatingTextId > 0 && event != null) {
            emitFloatingText(
                floatingTexts,
                event.cell.x + event.span / 2f,
                event.cell.y + event.span / 2f,
                event.text,
                event.color,
            )
        }
    }

    Canvas(modifier = modifier) {
        val board = state.board
        // Reserve a margin large enough that the framing border (a stroke centred
        // on the board edge) is never clipped — otherwise its outer half can slip
        // off-screen at the bottom when the board fills the play area. Two-pass:
        // size with a base margin, then grow the margin to cover that border's
        // half-width and re-fit.
        fun fit(margin: Float): Float =
            min((size.width - 2 * margin) / board.width, (size.height - 2 * margin) / board.height)

        val baseMargin = 6.dp.toPx()
        val probe = fit(baseMargin)
        val borderHalf = (probe * 0.12f).coerceAtLeast(2f) / 2f
        val margin = maxOf(baseMargin, borderHalf + 1f)
        val cell = fit(margin)
        if (cell <= 0f) return@Canvas

        // Derive everything from the per-frame clock and the tick snapshot, so
        // reading `frameNanos` both redraws each frame and stays consistent with
        // the committed state (no separate, potentially-stale fraction state).
        val now = frameNanos
        val periodNanos = (tickMillis.coerceAtLeast(1L) * 1_000_000L).toFloat()
        val f = if (running) ((now - tickTimeNanos).toFloat() / periodNanos).coerceIn(0f, 1f) else 1f
        val seconds = now / 1_000_000_000.0
        val pulse = if (running) 0.9f + 0.1f * (sin(seconds * 6.0).toFloat() * 0.5f + 0.5f) else 1f
        // Wrapped time keeps float precision stable across a long session.
        val time = (seconds % 1_000.0).toFloat()

        val boardWidth = cell * board.width
        val boardHeight = cell * board.height
        val originX = (size.width - boardWidth) / 2f
        val originY = (size.height - boardHeight) / 2f

        // Shared snake shimmer: Ghost (Star) blinks faster toward expiry.
        val ghostEffect = state.effectTimers.firstOrNull { it.kind == EffectKind.Ghost }
        val snakeAlpha = if (ghostEffect != null) {
            val warnT = (1f - ghostEffect.remainingMs.toFloat() / GHOST_WARN_MS).coerceIn(0f, 1f)
            val hz = 9f + warnT * 26f
            val amp = 0.15f + warnT * 0.45f
            val base = 0.5f - warnT * 0.30f
            (base + amp * (sin(seconds * hz).toFloat() * 0.5f + 0.5f)).coerceIn(0.1f, 1f)
        } else {
            1f
        }

        // The 3D (chase-cam) hazard renders a perspective view; otherwise the flat
        // top-down board. The blend animates the tilt between the two.
        if (cameraBlend > 0.001f) {
            draw3DScene(
                state = state,
                previousSnake = previousSnake,
                f = f,
                cameraBlend = cameraBlend,
                yaw = yawAnim.value,
                fixedNorth = fixedNorth,
                cell = cell,
                originX = originX,
                originY = originY,
                boardWidth = boardWidth,
                boardHeight = boardHeight,
                boundary = boundary,
                palette = palette,
                shaders = shaders,
                particles = particles,
                floatingTexts = floatingTexts,
                snakeAlpha = snakeAlpha,
                time = time,
                textMeasurer = textMeasurer,
                borderColor = borderColor,
                electricField = electricField,
            )
            return@Canvas
        }

        drawBoardBackground(board, cell, originX, originY, boardWidth, boardHeight, palette, shaders, time)

        // Clip dynamic content to the board so the head can slide "into" a wall
        // on the fatal tick without painting over the HUD.
        clipRect(originX, originY, originX + boardWidth, originY + boardHeight) {
            // Levels mode: wall cells read as "outside the board" — they wear
            // the screen background, and the shaped frame is drawn on top later.
            state.walls.forEach { wall ->
                drawRect(
                    color = outsideColor,
                    topLeft = Offset(originX + wall.x * cell, originY + wall.y * cell),
                    size = Size(cell + 0.5f, cell + 0.5f), // overdraw to hide seams
                )
            }
            state.obstacles.forEach { obstacle ->
                drawObstacle(cell, originX + obstacle.x * cell, originY + obstacle.y * cell, palette)
            }
            state.debris.forEach { d ->
                drawDebris(cell, originX + d.cell.x * cell, originY + d.cell.y * cell, d.life, palette)
            }
            state.foods.forEach { food ->
                drawFood(food, cell, originX, originY, pulse, textMeasurer, palette, shaders, time)
            }
            // Ghost (Star): the snake shimmers while invincible (snakeAlpha is
            // computed once above and reused by both the 2D and 3D renderers).
            val snake = state.snake
            for (k in snake.indices.reversed()) {
                val to = snake[k]
                val from = if (previousSnake.isEmpty()) {
                    to
                } else {
                    previousSnake[k.coerceAtMost(previousSnake.lastIndex)]
                }
                val cx = lerp(from.x.toFloat(), to.x.toFloat(), f)
                val cy = lerp(from.y.toFloat(), to.y.toFloat(), f)
                drawSnakeSegment(
                    isHead = k == 0,
                    left = originX + cx * cell,
                    top = originY + cy * cell,
                    cell = cell,
                    direction = state.direction,
                    palette = palette,
                    alpha = if (k == 0) (snakeAlpha + 0.2f).coerceAtMost(1f) else snakeAlpha,
                    shaders = shaders,
                    time = time,
                    headGlow = hotGlow(palette.headGlow, state.combo),
                )
            }
            particles.forEach { p ->
                drawCircle(
                    color = p.color.copy(alpha = 0.85f * p.fade),
                    radius = p.radiusCells * cell,
                    center = Offset(originX + p.x * cell, originY + p.y * cell),
                )
            }
            floatingTexts.forEach { t ->
                // Fade out and ease the rise; a dark outline keeps it readable on
                // any skin. Drawn over everything inside the board clip.
                val alpha = t.fade
                val centerX = originX + t.x * cell
                val centerY = originY + t.y * cell
                val layout = textMeasurer.measure(
                    text = t.text,
                    style = TextStyle(
                        color = t.color.copy(alpha = alpha),
                        fontSize = (cell * 0.95f).toSp(),
                        fontWeight = FontWeight.Black,
                    ),
                )
                val shadow = textMeasurer.measure(
                    text = t.text,
                    style = TextStyle(
                        color = Color.Black.copy(alpha = 0.45f * alpha),
                        fontSize = (cell * 0.95f).toSp(),
                        fontWeight = FontWeight.Black,
                    ),
                )
                val left = centerX - layout.size.width / 2f
                val top = centerY - layout.size.height / 2f
                drawText(shadow, topLeft = Offset(left + cell * 0.04f, top + cell * 0.04f))
                drawText(layout, topLeft = Offset(left, top))
            }
            // Freeze: a cool frost vignette over the board while the effect runs.
            if (state.hasEffect(EffectKind.Freeze)) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, SpecialVisuals.FreezeColor.copy(alpha = 0.18f)),
                        center = Offset(originX + boardWidth / 2f, originY + boardHeight / 2f),
                        radius = maxOf(boardWidth, boardHeight) * 0.72f,
                    ),
                    topLeft = Offset(originX, originY),
                    size = Size(boardWidth, boardHeight),
                )
            }
        }

        // Framing border painted on top. With a shaped board (Levels mode) the
        // frame follows the playable area's outline instead of the rectangle.
        val borderWidth = (cell * 0.12f).coerceAtLeast(2f)
        if (boundary.isEmpty()) {
            drawRect(
                color = borderColor,
                topLeft = Offset(originX, originY),
                size = Size(boardWidth, boardHeight),
                style = Stroke(width = borderWidth),
            )
        } else {
            boundary.forEach { e ->
                drawLine(
                    color = borderColor,
                    start = Offset(originX + e.x1 * cell, originY + e.y1 * cell),
                    end = Offset(originX + e.x2 * cell, originY + e.y2 * cell),
                    strokeWidth = borderWidth,
                    cap = StrokeCap.Square, // square caps close the corner joints
                )
            }
        }
    }
}

/**
 * A single continuous run of boundary wall: a straight segment from (ax,ay) to
 * (bx,by) in cell units, carrying its unit outward normal (nx,ny). One run spans a
 * whole board edge (or a whole straight stretch of a campaign wall) so it can be
 * extruded and drawn as one seam-free strip rather than one box per cell.
 */
private data class WallRun(
    val ax: Float,
    val ay: Float,
    val bx: Float,
    val by: Float,
    val nx: Float,
    val ny: Float,
)

/** One edge (in cell units) between a playable cell and a wall / out-of-board. */
private data class BoundaryEdge(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    /** Unit outward normal (away from the play area) - used to extrude 3D walls. */
    val nx: Float = 0f,
    val ny: Float = 0f,
)

/**
 * The outline of the playable area for a shaped (Levels mode) board: every edge
 * separating a playable cell from a wall cell or the outside. Empty when the
 * board has no walls, selecting the plain rectangular frame instead. Each edge
 * carries its outward normal so the 3D renderer can extrude walls away from play.
 */
private fun boundaryEdges(walls: Set<Position>, board: BoardDimensions): List<BoundaryEdge> {
    if (walls.isEmpty()) return emptyList()
    fun blocked(x: Int, y: Int): Boolean =
        x < 0 || x >= board.width || y < 0 || y >= board.height || Position(x, y) in walls
    val edges = ArrayList<BoundaryEdge>()
    for (y in 0 until board.height) {
        for (x in 0 until board.width) {
            if (Position(x, y) in walls) continue
            if (blocked(x - 1, y)) edges.add(BoundaryEdge(x.toFloat(), y.toFloat(), x.toFloat(), y + 1f, -1f, 0f))
            if (blocked(x + 1, y)) edges.add(BoundaryEdge(x + 1f, y.toFloat(), x + 1f, y + 1f, 1f, 0f))
            if (blocked(x, y - 1)) edges.add(BoundaryEdge(x.toFloat(), y.toFloat(), x + 1f, y.toFloat(), 0f, -1f))
            if (blocked(x, y + 1)) edges.add(BoundaryEdge(x.toFloat(), y + 1f, x + 1f, y + 1f, 0f, 1f))
        }
    }
    return edges
}

/**
 * The continuous wall runs to draw for the 3D view. For a plain rectangular board
 * (no [boundary]) this is the four edges, corner to corner (the barrier has no
 * thickness, so adjacent edges meet exactly at the corner line - no overlap). For a
 * shaped (Levels mode) board the unit [boundary] edges are merged into maximal
 * straight runs (same line + same outward normal, contiguous), so each straight
 * stretch is one continuous barrier.
 */
private fun wallRuns(boundary: List<BoundaryEdge>, board: BoardDimensions): List<WallRun> {
    if (boundary.isEmpty()) {
        val w = board.width.toFloat()
        val h = board.height.toFloat()
        return listOf(
            WallRun(0f, 0f, w, 0f, 0f, -1f), // top
            WallRun(0f, h, w, h, 0f, 1f), // bottom
            WallRun(0f, 0f, 0f, h, -1f, 0f), // left
            WallRun(w, 0f, w, h, 1f, 0f), // right
        )
    }
    val runs = ArrayList<WallRun>()
    // Horizontal runs (constant y, normal along y): group by (y, ny), merge over x.
    boundary.filter { it.y1 == it.y2 && it.ny != 0f }
        .groupBy { it.y1 to it.ny }
        .forEach { (key, edges) ->
            val (y, ny) = key
            val lefts = edges.map { minOf(it.x1, it.x2) }.sorted()
            var start = lefts.first()
            var end = start + 1f
            for (i in 1 until lefts.size) {
                if (lefts[i] <= end + 1e-3f) {
                    end = maxOf(end, lefts[i] + 1f)
                } else {
                    runs.add(WallRun(start, y, end, y, 0f, ny))
                    start = lefts[i]
                    end = start + 1f
                }
            }
            runs.add(WallRun(start, y, end, y, 0f, ny))
        }
    // Vertical runs (constant x, normal along x): group by (x, nx), merge over y.
    boundary.filter { it.x1 == it.x2 && it.nx != 0f }
        .groupBy { it.x1 to it.nx }
        .forEach { (key, edges) ->
            val (x, nx) = key
            val tops = edges.map { minOf(it.y1, it.y2) }.sorted()
            var start = tops.first()
            var end = start + 1f
            for (i in 1 until tops.size) {
                if (tops[i] <= end + 1e-3f) {
                    end = maxOf(end, tops[i] + 1f)
                } else {
                    runs.add(WallRun(x, start, x, end, nx, 0f))
                    start = tops[i]
                    end = start + 1f
                }
            }
            runs.add(WallRun(x, start, x, end, nx, 0f))
        }
    return runs
}

private fun DrawScope.drawBoardBackground(
    board: BoardDimensions,
    cell: Float,
    originX: Float,
    originY: Float,
    boardWidth: Float,
    boardHeight: Float,
    palette: SkinPalette,
    shaders: BoardShaders,
    time: Float,
) {
    // AGSL: the gradient brought to life with drifting glows + vignette.
    shaders.background.setFloatUniform("origin", originX, originY)
    shaders.background.setFloatUniform("resolution", boardWidth, boardHeight)
    shaders.background.setFloatUniform("time", time)
    drawRect(
        brush = shaders.backgroundBrush,
        topLeft = Offset(originX, originY),
        size = Size(boardWidth, boardHeight),
    )

    if (cell < 10f) return // skip the grid on dense boards where it is just noise
    for (x in 0..board.width) {
        val lineX = originX + x * cell
        drawLine(palette.gridLine, Offset(lineX, originY), Offset(lineX, originY + boardHeight), 1f)
    }
    for (y in 0..board.height) {
        val lineY = originY + y * cell
        drawLine(palette.gridLine, Offset(originX, lineY), Offset(originX + boardWidth, lineY), 1f)
    }
}

private fun DrawScope.drawObstacle(cell: Float, left: Float, top: Float, palette: SkinPalette) {
    val inset = cell * 0.08f
    val side = cell - 2 * inset
    val corner = cell * palette.cornerFactor.coerceAtMost(0.4f)
    val radius = CornerRadius(corner, corner)
    drawRoundRect(
        color = palette.obstacleShadow,
        topLeft = Offset(left + inset, top + inset + cell * 0.04f),
        size = Size(side, side),
        cornerRadius = radius,
    )
    drawRoundRect(
        color = palette.obstacle,
        topLeft = Offset(left + inset, top + inset),
        size = Size(side, side),
        cornerRadius = radius,
    )
    // Top bevel highlight.
    drawRoundRect(
        color = palette.obstacleHighlight.copy(alpha = 0.6f),
        topLeft = Offset(left + inset + side * 0.18f, top + inset + side * 0.16f),
        size = Size(side * 0.64f, side * 0.22f),
        cornerRadius = CornerRadius(cell * 0.12f, cell * 0.12f),
    )
}

private fun DrawScope.drawFood(
    food: Food,
    cell: Float,
    originX: Float,
    originY: Float,
    pulse: Float,
    textMeasurer: TextMeasurer,
    palette: SkinPalette,
    shaders: BoardShaders,
    time: Float,
) {
    if (food.category == FoodCategory.Special) {
        drawSpecialFood(food, cell, originX, originY, pulse, textMeasurer)
        return
    }
    val color = palette.foodColor(food)
    val extent = cell * food.span
    val centerX = originX + food.position.x * cell + extent / 2f
    val centerY = originY + food.position.y * cell + extent / 2f
    val radius = extent * 0.42f * pulse

    // "Rare" pieces (maxi / mystery / huge) get a halo; with AGSL it pulses.
    // Flat skins (useGlow == false) skip the halo for a crisp look.
    val rare = palette.useGlow && (food.span >= 2 || food.isMystery || food.tier == FoodTier.Huge)
    val haloRadius = radius * 1.9f
    if (rare) {
        shaders.foodHalo.setFloatUniform("center", centerX, centerY)
        shaders.foodHalo.setFloatUniform("radius", haloRadius)
        shaders.foodHalo.setFloatUniform("time", time)
        shaders.foodHalo.setColorUniform("ringColor", color.toArgb())
        drawCircle(brush = shaders.foodHaloBrush, radius = haloRadius, center = Offset(centerX, centerY))
    }
    // Pixel skin draws blocky square food; others keep the round drop.
    if (palette.cornerFactor < 0.06f) {
        drawRoundRect(
            color = color,
            topLeft = Offset(centerX - radius, centerY - radius),
            size = Size(radius * 2f, radius * 2f),
            cornerRadius = CornerRadius(radius * 0.16f, radius * 0.16f),
        )
    } else {
        drawCircle(color = color, radius = radius, center = Offset(centerX, centerY))
    }

    when {
        // Mystery pieces hide their amount behind a "?" in the snake's palette.
        food.isMystery -> drawGlyph(textMeasurer, "?", centerX, centerY, radius * 1.25f, Color.White)
        // Shrink food reads as "hollow" with a darker inner ring.
        food.category == FoodCategory.Shrink -> {
            drawCircle(
                color = Color.Black.copy(alpha = 0.28f),
                radius = radius * 0.5f,
                center = Offset(centerX, centerY),
                style = Stroke(width = radius * 0.18f),
            )
        }
        else -> Unit
    }

    // Glossy shine (skipped under the glyph so it stays crisp).
    if (!food.isMystery) {
        drawCircle(
            color = Color.White.copy(alpha = 0.30f),
            radius = radius * 0.30f,
            center = Offset(centerX - radius * 0.30f, centerY - radius * 0.30f),
        )
    }
}

/** Draws [glyph] centred on a food, sized to [sizePx], in the game's style. */
private fun DrawScope.drawGlyph(
    textMeasurer: TextMeasurer,
    glyph: String,
    centerX: Float,
    centerY: Float,
    sizePx: Float,
    color: Color,
) {
    val layout = textMeasurer.measure(
        text = glyph,
        style = TextStyle(color = color, fontSize = sizePx.toSp(), fontWeight = FontWeight.Black),
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(centerX - layout.size.width / 2f, centerY - layout.size.height / 2f),
    )
}

/**
 * Draws a Phase 6.2 special: a maxi disc in the power-up's universal accent
 * colour (so a colour always means the same effect across skins) with a halo and
 * a distinctive symbol on top.
 */
private fun DrawScope.drawSpecialFood(
    food: Food,
    cell: Float,
    originX: Float,
    originY: Float,
    pulse: Float,
    textMeasurer: TextMeasurer,
) {
    val accent = SpecialVisuals.accent(food.effect)
    val extent = cell * food.span
    val centerX = originX + food.position.x * cell + extent / 2f
    val centerY = originY + food.position.y * cell + extent / 2f
    val radius = extent * 0.40f * pulse

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.5f), Color.Transparent),
            center = Offset(centerX, centerY),
            radius = radius * 2.1f,
        ),
        radius = radius * 2.1f,
        center = Offset(centerX, centerY),
    )
    drawCircle(color = accent, radius = radius, center = Offset(centerX, centerY))
    drawCircle(
        color = Color.Black.copy(alpha = 0.18f),
        radius = radius,
        center = Offset(centerX, centerY),
        style = Stroke(width = radius * 0.12f),
    )

    val ink = Color(0xFF10151C)
    drawSpecialSymbol(food.effect, centerX, centerY, radius * 0.92f, ink, textMeasurer)
}

/** Dispatches the on-disc icon for each special effect. */
private fun DrawScope.drawSpecialSymbol(
    effect: FoodEffect,
    cx: Float,
    cy: Float,
    r: Float,
    color: Color,
    textMeasurer: TextMeasurer,
) {
    when (effect) {
        is FoodEffect.Haste -> drawBolt(cx, cy, r, color)
        is FoodEffect.Slow -> drawSpiral(cx, cy, r, color)
        is FoodEffect.Ghost -> drawStarShape(cx, cy, r, points = 5, innerRatio = 0.45f, color = color)
        is FoodEffect.Freeze -> drawSnowflake(cx, cy, r, color)
        is FoodEffect.Jackpot -> drawGlyph(textMeasurer, "$", cx, cy, r * 1.7f, color)
        is FoodEffect.Burst -> drawStarShape(cx, cy, r, points = 8, innerRatio = 0.42f, color = color)
        is FoodEffect.Quake -> drawCrack(cx, cy, r, color)
        is FoodEffect.TimeBonus -> drawClock(cx, cy, r, color, plus = true)
        is FoodEffect.TimePenalty -> drawClock(cx, cy, r, color, plus = false)
        is FoodEffect.ExtraLife -> drawSnakeHeadIcon(cx, cy, r, color)
        else -> Unit
    }
}

/** A tiny snake head (rounded square + upward eyes) — the extra-life bonus icon. */
private fun DrawScope.drawSnakeHeadIcon(cx: Float, cy: Float, r: Float, color: Color) {
    val side = r * 1.25f
    drawRoundRect(
        color = color,
        topLeft = Offset(cx - side / 2f, cy - side / 2f),
        size = Size(side, side),
        cornerRadius = CornerRadius(side * 0.3f, side * 0.3f),
    )
    val eyeRadius = side * 0.14f
    for (sign in intArrayOf(-1, 1)) {
        val ex = cx + sign * side * 0.2f
        val ey = cy - side * 0.14f
        drawCircle(Color.White, eyeRadius, Offset(ex, ey))
        drawCircle(color, eyeRadius * 0.5f, Offset(ex, ey - eyeRadius * 0.2f))
    }
}

/** A clock face with a small +/- badge — the Time Attack bonus / penalty blocks. */
private fun DrawScope.drawClock(cx: Float, cy: Float, r: Float, color: Color, plus: Boolean) {
    val ring = r * 0.74f
    drawCircle(color, ring, Offset(cx, cy), style = Stroke(width = r * 0.16f))
    // Hands: one up, one to the right.
    drawLine(color, Offset(cx, cy), Offset(cx, cy - ring * 0.58f), strokeWidth = r * 0.15f, cap = StrokeCap.Round)
    drawLine(color, Offset(cx, cy), Offset(cx + ring * 0.44f, cy), strokeWidth = r * 0.15f, cap = StrokeCap.Round)
    // +/- badge at the bottom-right.
    val bx = cx + r * 0.62f
    val by = cy + r * 0.62f
    val s = r * 0.32f
    drawLine(color, Offset(bx - s, by), Offset(bx + s, by), strokeWidth = r * 0.17f, cap = StrokeCap.Round)
    if (plus) drawLine(color, Offset(bx, by - s), Offset(bx, by + s), strokeWidth = r * 0.17f, cap = StrokeCap.Round)
}

/** An n-pointed star (used for Star and the spiky Explosion burst). */
private fun DrawScope.drawStarShape(cx: Float, cy: Float, r: Float, points: Int, innerRatio: Float, color: Color) {
    val path = Path()
    val verts = points * 2
    for (i in 0 until verts) {
        val rr = if (i % 2 == 0) r else r * innerRatio
        val ang = -PI / 2 + i * PI / points
        val x = cx + (cos(ang) * rr).toFloat()
        val y = cy + (sin(ang) * rr).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawBolt(cx: Float, cy: Float, r: Float, color: Color) {
    val pts = listOf(
        -0.10f to -0.62f, 0.30f to -0.62f, 0.00f to -0.10f,
        0.26f to -0.10f, -0.16f to 0.62f, -0.02f to 0.06f, -0.30f to 0.06f,
    )
    val path = Path()
    pts.forEachIndexed { i, (px, py) ->
        val x = cx + px * r
        val y = cy + py * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawSnowflake(cx: Float, cy: Float, r: Float, color: Color) {
    val stroke = r * 0.16f
    for (k in 0 until 3) {
        val ang = k * PI / 3
        val dx = cos(ang).toFloat()
        val dy = sin(ang).toFloat()
        drawLine(
            color,
            Offset(cx - dx * r, cy - dy * r),
            Offset(cx + dx * r, cy + dy * r),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        // Two small V-branches near each tip.
        for (sign in intArrayOf(-1, 1)) {
            val tipX = cx + dx * r * sign
            val tipY = cy + dy * r * sign
            val bAng = ang + sign * 0.9
            val bx = cos(bAng).toFloat() * r * 0.32f
            val by = sin(bAng).toFloat() * r * 0.32f
            drawLine(color, Offset(tipX, tipY), Offset(tipX - bx, tipY - by), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawSpiral(cx: Float, cy: Float, r: Float, color: Color) {
    val path = Path()
    val steps = 64
    val turns = 2.4
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val ang = t * turns * 2 * PI
        val rr = r * (0.92f - 0.78f * t)
        val x = cx + (cos(ang) * rr).toFloat()
        val y = cy + (sin(ang) * rr).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(width = r * 0.14f, cap = StrokeCap.Round))
}

private fun DrawScope.drawCrack(cx: Float, cy: Float, r: Float, color: Color) {
    val pts = listOf(
        -0.7f to -0.1f, -0.35f to 0.28f, -0.05f to -0.22f,
        0.25f to 0.22f, 0.62f to -0.16f,
    )
    for (i in 0 until pts.size - 1) {
        val (ax, ay) = pts[i]
        val (bx, by) = pts[i + 1]
        drawLine(
            color,
            Offset(cx + ax * r, cy + ay * r),
            Offset(cx + bx * r, cy + by * r),
            strokeWidth = r * 0.16f,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * The severed tail left behind by an Explosion: drawn as a block in the very
 * same style as a normal snake-body segment (so it reads as the detached tail,
 * not a stray pellet). Still lethal and time-limited, so it fades out as its
 * timer runs down.
 */
private fun DrawScope.drawDebris(cell: Float, left: Float, top: Float, life: Float, palette: SkinPalette) {
    val alpha = 0.35f + 0.55f * life
    val inset = cell * 0.06f
    val side = cell - 2 * inset
    val topLeft = Offset(left + inset, top + inset)
    val corner = cell * palette.cornerFactor
    val radius = CornerRadius(corner, corner)
    drawRoundRect(
        color = palette.snakeBody.copy(alpha = alpha),
        topLeft = topLeft,
        size = Size(side, side),
        cornerRadius = radius,
    )
    drawRoundRect(
        color = palette.snakeOutline.copy(alpha = alpha),
        topLeft = topLeft,
        size = Size(side, side),
        cornerRadius = radius,
        style = Stroke(width = cell * 0.06f),
    )
}

/** A fiery accent the head glow blends toward as the combo climbs. */
private val ComboHotGlow = Color(0xFFFF5722)

/**
 * Heats the head [base] glow toward [ComboHotGlow] as the eat-[combo] climbs:
 * unchanged up to x2, ramping to fully fiery around x8 - the "on fire" cue.
 */
private fun hotGlow(base: Color, combo: Int): Color {
    val heat = ((combo - 2) / 6f).coerceIn(0f, 1f)
    if (heat <= 0f) return base
    return Color(
        red = base.red + (ComboHotGlow.red - base.red) * heat,
        green = base.green + (ComboHotGlow.green - base.green) * heat,
        blue = base.blue + (ComboHotGlow.blue - base.blue) * heat,
        alpha = base.alpha,
    )
}

private fun DrawScope.drawSnakeSegment(
    isHead: Boolean,
    left: Float,
    top: Float,
    cell: Float,
    direction: Direction,
    palette: SkinPalette,
    alpha: Float,
    shaders: BoardShaders,
    time: Float,
    headGlow: Color = palette.headGlow,
) {
    val centerX = left + cell / 2f
    val centerY = top + cell / 2f

    if (isHead && palette.useGlow) {
        // AGSL: a pulsing, gently rotating glow halo.
        val glowRadius = cell * 1.1f
        shaders.glow.setFloatUniform("center", centerX, centerY)
        shaders.glow.setFloatUniform("radius", glowRadius)
        shaders.glow.setFloatUniform("time", time)
        shaders.glow.setColorUniform("glowColor", headGlow.toArgb())
        drawCircle(brush = shaders.glowBrush, radius = glowRadius, center = Offset(centerX, centerY))
    }

    val inset = cell * 0.06f
    val side = cell - 2 * inset
    val topLeft = Offset(left + inset, top + inset)
    val corner = cell * palette.cornerFactor
    val radius = CornerRadius(corner, corner)
    val fill = (if (isHead) palette.snakeHead else palette.snakeBody).copy(alpha = alpha)
    drawRoundRect(
        color = fill,
        topLeft = topLeft,
        size = Size(side, side),
        cornerRadius = radius,
    )
    drawRoundRect(
        color = palette.snakeOutline.copy(alpha = alpha),
        topLeft = topLeft,
        size = Size(side, side),
        cornerRadius = radius,
        style = Stroke(width = cell * 0.06f),
    )

    if (isHead) drawEyes(centerX, centerY, cell, direction, palette)
}

private fun DrawScope.drawEyes(
    centerX: Float,
    centerY: Float,
    cell: Float,
    direction: Direction,
    palette: SkinPalette,
) {
    val forwardX = direction.dx.toFloat()
    val forwardY = direction.dy.toFloat()
    // Perpendicular to travel, to seat the two eyes side by side.
    val perpX = -forwardY
    val perpY = forwardX
    val forward = cell * 0.16f
    val spread = cell * 0.2f
    val eyeRadius = cell * 0.11f
    val pupilRadius = cell * 0.055f

    for (sign in intArrayOf(-1, 1)) {
        val ex = centerX + forwardX * forward + perpX * spread * sign
        val ey = centerY + forwardY * forward + perpY * spread * sign
        drawCircle(Color.White, eyeRadius, Offset(ex, ey))
        drawCircle(
            palette.snakeEye,
            pupilRadius,
            Offset(ex + forwardX * cell * 0.03f, ey + forwardY * cell * 0.03f),
        )
    }
}

/**
 * The 3D (chase-cam) hazard view. Renders the board in perspective from behind
 * and above the snake's head via [blendedCam]: a receding floor grid, the
 * play-area boundary, then the obstacles / debris / food / snake projected as
 * billboards and raised quads, depth-sorted far-to-near (painter's algorithm).
 * [cameraBlend] eases the whole scene between the flat top-down (≈0) and the full
 * chase-cam (1), so the same code drives the tilt-in / tilt-out cinematic.
 */
private fun DrawScope.draw3DScene(
    state: GameState,
    previousSnake: List<Position>,
    f: Float,
    cameraBlend: Float,
    yaw: Float,
    fixedNorth: Boolean,
    cell: Float,
    originX: Float,
    originY: Float,
    boardWidth: Float,
    boardHeight: Float,
    boundary: List<BoundaryEdge>,
    palette: SkinPalette,
    shaders: BoardShaders,
    particles: List<Particle>,
    floatingTexts: List<FloatingText>,
    snakeAlpha: Float,
    time: Float,
    textMeasurer: TextMeasurer,
    borderColor: Color,
    electricField: Boolean,
) {
    val board = state.board
    val snake = state.snake

    // Interpolated head centre anchors the camera.
    val headTo = snake.first()
    val headFrom = if (previousSnake.isEmpty()) headTo else previousSnake.first()
    val hx = lerp(headFrom.x.toFloat(), headTo.x.toFloat(), f) + 0.5f
    val hy = lerp(headFrom.y.toFloat(), headTo.y.toFloat(), f) + 0.5f

    val aspect = boardWidth / boardHeight
    val cam = blendedCam(hx, hy, board.width.toFloat(), board.height.toFloat(), yaw, cameraBlend, aspect, fixedNorth)

    val centerX = originX + boardWidth / 2f
    val centerY = originY + boardHeight / 2f
    val halfW = boardWidth / 2f
    val halfH = boardHeight / 2f
    fun toPixel(p: Proj) = Offset(centerX + p.sx * halfW, centerY - p.sy * halfH)
    fun scaleAt(depth: Float) = cam.focal / depth * halfH

    val t = smoothstep(cameraBlend)

    // Full-bleed backdrop: cover the whole canvas (not just the board rectangle)
    // so no flat board-shaped panel shows through behind the perspective scene.
    // A fog gradient deepens toward the top (distance).
    shaders.background.setFloatUniform("origin", 0f, 0f)
    shaders.background.setFloatUniform("resolution", size.width, size.height)
    shaders.background.setFloatUniform("time", time)
    drawRect(brush = shaders.backgroundBrush, topLeft = Offset.Zero, size = size)
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(palette.boardTop.copy(alpha = 0.65f * t), Color.Transparent),
            startY = 0f,
            endY = size.height * 0.6f,
        ),
        topLeft = Offset.Zero,
        size = Size(size.width, size.height * 0.6f),
    )

    // A straight world line (z constant), clipped to the near plane so segments
    // that pass behind the camera don't smear.
    fun worldLine(ax: Float, ay: Float, bx: Float, by: Float, z: Float, color: Color, width: Float) {
        val clip = cam.clipNear(cam.cameraSpace(Vec3(ax, ay, z)), cam.cameraSpace(Vec3(bx, by, z))) ?: return
        val pa = cam.projectCamera(clip.first)
        val pb = cam.projectCamera(clip.second)
        if (!pa.visible || !pb.visible) return
        drawLine(color, toPixel(pa), toPixel(pb), width)
    }

    // Solid floor: fill the board plane with the dark board gradient so the ground
    // reads as a real surface (corposità) rather than a bare wireframe, while
    // staying black-leaning and on-brand with the dark theme. Drawn only when the
    // whole quad is in front of the camera (otherwise the receding grid carries it).
    run {
        // Clip the floor quad to the near plane (rather than dropping it whenever a
        // corner falls behind the camera) so the ground stays a continuous filled
        // surface as the camera sweeps over it.
        val floorCam = cam.clipPolygonNear(
            listOf(
                cam.cameraSpace(Vec3(0f, 0f, 0f)),
                cam.cameraSpace(Vec3(board.width.toFloat(), 0f, 0f)),
                cam.cameraSpace(Vec3(board.width.toFloat(), board.height.toFloat(), 0f)),
                cam.cameraSpace(Vec3(0f, board.height.toFloat(), 0f)),
            ),
        )
        if (floorCam.size >= 3) {
            val pix = floorCam.map { toPixel(cam.projectCamera(it)) }
            val path = Path().apply {
                moveTo(pix[0].x, pix[0].y)
                for (i in 1 until pix.size) lineTo(pix[i].x, pix[i].y)
                close()
            }
            drawPath(
                path,
                brush = Brush.verticalGradient(
                    colors = listOf(palette.boardBottom, palette.boardTop),
                    startY = pix.minOf { it.y },
                    endY = pix.maxOf { it.y },
                ),
            )
        }
    }

    // Receding floor grid (over the solid floor for a subtle surface texture).
    val gridColor = palette.gridLine.copy(alpha = (palette.gridLine.alpha * (0.5f + 0.5f * t)).coerceIn(0f, 1f))
    for (x in 0..board.width) worldLine(x.toFloat(), 0f, x.toFloat(), board.height.toFloat(), 0f, gridColor, 1f)
    for (y in 0..board.height) worldLine(0f, y.toFloat(), board.width.toFloat(), y.toFloat(), 0f, gridColor, 1f)

    // Depth-sorted scene items (drawn far -> near).
    val items = ArrayList<Pair<Float, DrawScope.() -> Unit>>()

    // Luminous boundary barrier. Each arena side is a single translucent "energy
    // field" quad (boundary line up to WALL_HEIGHT) with glowing neon rails along the
    // floor edge, the top edge and the two vertical ends. The barrier has no
    // thickness, so: (1) it never hides the snake/food behind the back edges - you
    // see through it; (2) perpendicular sides meet at a corner *line* with no area
    // overlap, killing the corner z-fighting/flicker of the old solid boxes; and
    // (3) a double-sided filled quad reads correctly from every angle (no "hollow"
    // look). WALL_HEIGHT is the single height knob.
    val wallTop = lerpF(0f, WALL_HEIGHT, t) // grows with the tilt; flat collapses to the outline
    val coreWidth = (cell * 0.05f).coerceAtLeast(1.5f) * (0.6f + 0.4f * t)
    // Lift the (often dark) skin border to a vivid neon for the structural rails, and
    // add a gentle breathing pulse for the energy-field feel.
    val neon = brighten(borderColor, 0.55f)
    val pulse = 0.86f + 0.14f * (sin(time * 1.6f) * 0.5f + 0.5f)
    // The energy field itself glows in the skin's vivid accent (the head-glow colour:
    // lime for Classic, cyan for Neon, ...) so it reads as electric/plasma, not grey.
    val electric = brighten(palette.headGlow, 0.1f)
    val fieldAlpha = 0.18f * t

    // Draws a glowing neon line between two world points: a soft wide halo, the
    // coloured core and a hot near-white centre (cheap additive-looking bloom). The
    // segment is near-clipped so it never streaks to a screen corner near the camera.
    fun glowLine(a: Vec3, b: Vec3, width: Float, intensity: Float) {
        val clip = cam.clipNear(a, b) ?: return
        val pa = cam.projectCamera(clip.first)
        val pb = cam.projectCamera(clip.second)
        if (!pa.visible || !pb.visible) return
        val p0 = toPixel(pa)
        val p1 = toPixel(pb)
        drawLine(neon.copy(alpha = 0.10f * intensity), p0, p1, width * 4.5f, cap = StrokeCap.Round)
        drawLine(neon.copy(alpha = 0.22f * intensity), p0, p1, width * 2.3f, cap = StrokeCap.Round)
        drawLine(neon.copy(alpha = 0.95f * intensity), p0, p1, width, cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.6f * intensity), p0, p1, width * 0.4f, cap = StrokeCap.Round)
    }

    // Draws one continuous barrier run from (ax,ay) to (bx,by) along a board edge.
    fun addWallRun(ax: Float, ay: Float, bx: Float, by: Float) {
        fun cs(x: Float, y: Float, z: Float) = cam.cameraSpace(Vec3(x, y, z))
        val a0 = cs(ax, ay, 0f); val b0 = cs(bx, by, 0f) // floor edge
        val a1 = cs(ax, ay, wallTop); val b1 = cs(bx, by, wallTop) // top edge
        val field = cam.clipPolygonNear(listOf(a0, b0, b1, a1))
        if (field.size < 3) return
        val sortKey = field.map { it.z }.average().toFloat()
        items.add(sortKey to {
            // Translucent energy field. The base is always a faint vertical gradient;
            // when the electric effect is on (and the whole quad is in front of the
            // camera, so its perspective UV is well-defined) an AGSL plasma flow is
            // layered on top, mapped along the wall via a screen->UV homography.
            val pix = field.map { toPixel(cam.projectCamera(it)) }
            val path = Path().apply {
                moveTo(pix[0].x, pix[0].y)
                for (i in 1 until pix.size) lineTo(pix[i].x, pix[i].y)
                close()
            }
            val fieldTint = if (electricField) electric else neon
            drawPath(
                path,
                brush = Brush.verticalGradient(
                    colors = listOf(fieldTint.copy(alpha = fieldAlpha * 1.4f), fieldTint.copy(alpha = fieldAlpha * 0.4f)),
                    startY = pix.minOf { it.y },
                    endY = pix.maxOf { it.y },
                ),
            )
            val wallShader = shaders.wallField
            val wallBrush = shaders.wallFieldBrush
            if (electricField && wallShader != null && wallBrush != null && wallTop > 0.06f && field.size == 4) {
                screenToUvHomography(pix[0], pix[1], pix[2], pix[3])?.let { hg ->
                    wallShader.setFloatUniform("h0", hg[0], hg[1], hg[2])
                    wallShader.setFloatUniform("h1", hg[3], hg[4], hg[5])
                    wallShader.setFloatUniform("h2", hg[6], hg[7], hg[8])
                    wallShader.setFloatUniform("time", time)
                    wallShader.setFloatUniform("intensity", t)
                    wallShader.setColorUniform("fieldColor", electric.toArgb())
                    drawPath(path, brush = wallBrush)
                }
            }
            // Glowing rails: top edge (brightest), floor edge, and the vertical ends
            // (corner posts). Adjacent runs share the corner verticals exactly, so the
            // overlap is identical geometry - it reads as one crisp post, no flicker.
            glowLine(a1, b1, coreWidth, pulse) // top rail
            glowLine(a0, b0, coreWidth * 0.85f, 0.7f * pulse) // floor rail (dimmer)
            glowLine(a0, a1, coreWidth * 0.85f, 0.9f * pulse) // vertical end A
            glowLine(b0, b1, coreWidth * 0.85f, 0.9f * pulse) // vertical end B
        })
    }

    wallRuns(boundary, board).forEach { r -> addWallRun(r.ax, r.ay, r.bx, r.by) }

    fun addRaisedQuad(cx: Float, cy: Float, fill: Color, outline: Color, height: Float, alpha: Float, banded: Boolean = false) {
        val cc = cam.cameraSpace(Vec3(cx, cy, height))
        if (cc.z <= Cam.NEAR) return
        val m = 0.06f
        val top = listOf(
            cam.project(Vec3(cx - 0.5f + m, cy - 0.5f + m, height)),
            cam.project(Vec3(cx + 0.5f - m, cy - 0.5f + m, height)),
            cam.project(Vec3(cx + 0.5f - m, cy + 0.5f - m, height)),
            cam.project(Vec3(cx - 0.5f + m, cy + 0.5f - m, height)),
        )
        if (top.any { !it.visible }) return
        val base = listOf(
            cam.project(Vec3(cx - 0.5f + m, cy - 0.5f + m, 0f)),
            cam.project(Vec3(cx + 0.5f - m, cy - 0.5f + m, 0f)),
            cam.project(Vec3(cx + 0.5f - m, cy + 0.5f - m, 0f)),
            cam.project(Vec3(cx - 0.5f + m, cy + 0.5f - m, 0f)),
        )
        items.add(cc.z to {
            // Side faces give the tile some bulk. Banded blocks (obstacles) get a
            // stronger top-to-base shade plus a seam line for a solid, textured read.
            if (base.all { it.visible }) {
                for (i in 0 until 4) {
                    val j = (i + 1) % 4
                    val bp = toPixel(base[i]); val bq = toPixel(base[j])
                    val tp = toPixel(top[i]); val tq = toPixel(top[j])
                    val face = Path().apply {
                        moveTo(bp.x, bp.y); lineTo(bq.x, bq.y); lineTo(tq.x, tq.y); lineTo(tp.x, tp.y); close()
                    }
                    if (banded) {
                        drawPath(
                            face,
                            brush = Brush.verticalGradient(
                                colors = listOf(fill.copy(alpha = 0.85f * alpha), darken(fill, 0.5f).copy(alpha = 0.85f * alpha)),
                                startY = minOf(tp.y, tq.y),
                                endY = maxOf(bp.y, bq.y),
                            ),
                        )
                        val seam = darken(fill, 0.6f).copy(alpha = 0.5f * alpha)
                        drawLine(seam, lerpOffset(bp, tp, 0.55f), lerpOffset(bq, tq, 0.55f), 1.5f)
                    } else {
                        drawPath(face, fill.copy(alpha = 0.5f * alpha))
                    }
                }
            }
            val face = Path().apply {
                moveTo(toPixel(top[0]).x, toPixel(top[0]).y)
                for (i in 1 until 4) lineTo(toPixel(top[i]).x, toPixel(top[i]).y)
                close()
            }
            drawPath(face, if (banded) brighten(fill, 0.08f).copy(alpha = alpha) else fill.copy(alpha = alpha))
            drawPath(face, outline.copy(alpha = alpha), style = Stroke(width = (scaleAt(cc.z) * 0.04f).coerceAtLeast(1f)))
        })
    }

    state.obstacles.forEach { o ->
        addRaisedQuad(o.x + 0.5f, o.y + 0.5f, palette.obstacle, palette.obstacleHighlight, ZTOP * 1.2f, 1f, banded = true)
    }

    state.debris.forEach { d ->
        // The severed tail: a raised block in the snake-body style (matching the
        // top-down view), fading out with its timer, rather than a floating ball.
        val alpha = 0.35f + 0.55f * d.life
        addRaisedQuad(d.cell.x + 0.5f, d.cell.y + 0.5f, palette.snakeBody, palette.snakeOutline, ZTOP, alpha)
    }

    state.foods.forEach { food ->
        // Render food as a beveled cube occupying its whole footprint (1×1 or the
        // 2×2 maxi), so it reads as a solid 3D object and the cells it covers are
        // obvious. A small outer inset keeps it clear of the grid lines.
        val om = 0.08f
        val x0 = food.position.x + om
        val y0 = food.position.y + om
        val x1 = food.position.x + food.span - om
        val y1 = food.position.y + food.span - om
        val cxc = (x0 + x1) / 2f
        val cyc = (y0 + y1) / 2f
        val height = ZTOP * (0.9f + 0.35f * (food.span - 1))
        val cc = cam.cameraSpace(Vec3(cxc, cyc, height))
        if (cc.z <= Cam.NEAR) return@forEach
        val capP = cam.projectCamera(cc)
        if (!capP.visible) return@forEach
        val center = toPixel(capP)
        val color = if (food.category == FoodCategory.Special) SpecialVisuals.accent(food.effect) else palette.foodColor(food)
        val bevel = (x1 - x0).coerceAtMost(y1 - y0) * 0.2f
        val shoulder = height * 0.8f
        fun pr(x: Float, y: Float, z: Float) = cam.project(Vec3(x, y, z))
        val baseC = listOf(pr(x0, y0, 0f), pr(x1, y0, 0f), pr(x1, y1, 0f), pr(x0, y1, 0f))
        val shC = listOf(pr(x0, y0, shoulder), pr(x1, y0, shoulder), pr(x1, y1, shoulder), pr(x0, y1, shoulder))
        val capC = listOf(
            pr(x0 + bevel, y0 + bevel, height), pr(x1 - bevel, y0 + bevel, height),
            pr(x1 - bevel, y1 - bevel, height), pr(x0 + bevel, y1 - bevel, height),
        )
        if ((baseC + shC + capC).any { !it.visible }) return@forEach
        val rad = (scaleAt(cc.z) * 0.5f * food.span).coerceAtLeast(2f)
        val outline = brighten(color, 0.4f)
        items.add(cc.z to {
            // Glow halo behind the cube.
            drawCircle(
                brush = Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent), center = center, radius = rad * 2.2f),
                radius = rad * 2.2f,
                center = center,
            )
            // Vertical body faces (base -> shoulder), shaded for solidity.
            for (i in 0 until 4) {
                val j = (i + 1) % 4
                fillFace(listOf(baseC[i], baseC[j], shC[j], shC[i]).map { toPixel(it) }, darken(color, 0.12f), darken(color, 0.5f))
            }
            // Chamfer faces (shoulder -> cap) give the smoothed-corner look.
            for (i in 0 until 4) {
                val j = (i + 1) % 4
                fillFace(listOf(shC[i], shC[j], capC[j], capC[i]).map { toPixel(it) }, brighten(color, 0.18f), darken(color, 0.05f))
            }
            // Top cap.
            val capPix = capC.map { toPixel(it) }
            val capPath = Path().apply {
                moveTo(capPix[0].x, capPix[0].y)
                for (i in 1 until 4) lineTo(capPix[i].x, capPix[i].y)
                close()
            }
            drawPath(capPath, brighten(color, 0.28f))
            drawPath(capPath, outline, style = Stroke(width = (scaleAt(cc.z) * 0.04f).coerceAtLeast(1f)))
            when {
                food.category == FoodCategory.Special -> drawSpecialSymbol(food.effect, center.x, center.y, rad * 0.8f, Color(0xFF10151C), textMeasurer)
                food.isMystery -> drawGlyph(textMeasurer, "?", center.x, center.y, rad * 1.0f, Color.White)
            }
        })
    }

    for (k in snake.indices) {
        val to = snake[k]
        val from = if (previousSnake.isEmpty()) to else previousSnake[k.coerceAtMost(previousSnake.lastIndex)]
        val cx = lerp(from.x.toFloat(), to.x.toFloat(), f) + 0.5f
        val cy = lerp(from.y.toFloat(), to.y.toFloat(), f) + 0.5f
        val isHead = k == 0
        val fill = if (isHead) palette.snakeHead else palette.snakeBody
        addRaisedQuad(cx, cy, fill, palette.snakeOutline, ZTOP, if (isHead) (snakeAlpha + 0.2f).coerceAtMost(1f) else snakeAlpha)
        if (isHead && palette.useGlow) {
            val cc = cam.cameraSpace(Vec3(cx, cy, ZTOP))
            if (cc.z > Cam.NEAR) {
                val gp = cam.projectCamera(cc)
                if (gp.visible) {
                    val center = toPixel(gp)
                    val gr = (scaleAt(cc.z) * 1.0f).coerceAtLeast(4f)
                    // Slightly farther depth so the halo sorts behind the head.
                    items.add(cc.z + 0.01f to {
                        shaders.glow.setFloatUniform("center", center.x, center.y)
                        shaders.glow.setFloatUniform("radius", gr)
                        shaders.glow.setFloatUniform("time", time)
                        shaders.glow.setColorUniform("glowColor", hotGlow(palette.headGlow, state.combo).toArgb())
                        drawCircle(brush = shaders.glowBrush, radius = gr, center = center)
                    })
                }
            }
        }
    }

    items.sortByDescending { it.first }
    items.forEach { it.second.invoke(this) }

    // Overlays projected as billboards.
    particles.forEach { pt ->
        val cc = cam.cameraSpace(Vec3(pt.x, pt.y, 0.05f))
        if (cc.z <= Cam.NEAR) return@forEach
        val p = cam.projectCamera(cc)
        if (!p.visible) return@forEach
        val r = (scaleAt(cc.z) * pt.radiusCells).coerceAtLeast(1f)
        drawCircle(pt.color.copy(alpha = 0.85f * pt.fade), r, toPixel(p))
    }
    floatingTexts.forEach { tx ->
        val cc = cam.cameraSpace(Vec3(tx.x, tx.y, ZTOP))
        if (cc.z <= Cam.NEAR) return@forEach
        val p = cam.projectCamera(cc)
        if (!p.visible) return@forEach
        val center = toPixel(p)
        val fontPx = (scaleAt(cc.z) * 0.95f).coerceIn(8f, cell * 2f)
        val layout = textMeasurer.measure(
            text = tx.text,
            style = TextStyle(color = tx.color.copy(alpha = tx.fade), fontSize = fontPx.toSp(), fontWeight = FontWeight.Black),
        )
        drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
    }
}

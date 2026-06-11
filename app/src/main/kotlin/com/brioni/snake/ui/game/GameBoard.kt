package com.brioni.snake.ui.game

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
        if (eatEventId > 0 && event != null) {
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
                drawDebris(cell, originX + d.cell.x * cell, originY + d.cell.y * cell, d.life)
            }
            state.foods.forEach { food ->
                drawFood(food, cell, originX, originY, pulse, textMeasurer, palette, shaders, time)
            }
            // Ghost (Star): the snake shimmers while invincible, then blinks
            // faster and deeper over the final GHOST_WARN_MS so the player gets a
            // clear "about to expire" warning and can steer somewhere safe.
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

/** One edge (in cell units) between a playable cell and a wall / out-of-board. */
private data class BoundaryEdge(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

/**
 * The outline of the playable area for a shaped (Levels mode) board: every edge
 * separating a playable cell from a wall cell or the outside. Empty when the
 * board has no walls, selecting the plain rectangular frame instead.
 */
private fun boundaryEdges(walls: Set<Position>, board: BoardDimensions): List<BoundaryEdge> {
    if (walls.isEmpty()) return emptyList()
    fun blocked(x: Int, y: Int): Boolean =
        x < 0 || x >= board.width || y < 0 || y >= board.height || Position(x, y) in walls
    val edges = ArrayList<BoundaryEdge>()
    for (y in 0 until board.height) {
        for (x in 0 until board.width) {
            if (Position(x, y) in walls) continue
            if (blocked(x - 1, y)) edges.add(BoundaryEdge(x.toFloat(), y.toFloat(), x.toFloat(), y + 1f))
            if (blocked(x + 1, y)) edges.add(BoundaryEdge(x + 1f, y.toFloat(), x + 1f, y + 1f))
            if (blocked(x, y - 1)) edges.add(BoundaryEdge(x.toFloat(), y.toFloat(), x + 1f, y.toFloat()))
            if (blocked(x, y + 1)) edges.add(BoundaryEdge(x.toFloat(), y + 1f, x + 1f, y + 1f))
        }
    }
    return edges
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

/** A lethal, time-limited explosion block; fades as its timer runs down. */
private fun DrawScope.drawDebris(cell: Float, left: Float, top: Float, life: Float) {
    val inset = cell * 0.08f
    val side = cell - 2 * inset
    val color = SpecialVisuals.ExplosionColor
    val alpha = 0.35f + 0.55f * life
    val radius = CornerRadius(cell * 0.18f, cell * 0.18f)
    drawRoundRect(
        color = color.copy(alpha = alpha),
        topLeft = Offset(left + inset, top + inset),
        size = Size(side, side),
        cornerRadius = radius,
    )
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.35f * life),
        topLeft = Offset(left + inset, top + inset),
        size = Size(side, side),
        cornerRadius = radius,
        style = Stroke(width = cell * 0.05f),
    )
    // A small crack mark.
    drawCrack(left + cell / 2f, top + cell / 2f, side * 0.32f, Color.Black.copy(alpha = 0.4f * life))
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
) {
    val centerX = left + cell / 2f
    val centerY = top + cell / 2f

    if (isHead && palette.useGlow) {
        // AGSL: a pulsing, gently rotating glow halo.
        val glowRadius = cell * 1.1f
        shaders.glow.setFloatUniform("center", centerX, centerY)
        shaders.glow.setFloatUniform("radius", glowRadius)
        shaders.glow.setFloatUniform("time", time)
        shaders.glow.setColorUniform("glowColor", palette.headGlow.toArgb())
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

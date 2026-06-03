package com.brioni.snake.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.util.lerp
import com.brioni.snake.game.BoardSize
import com.brioni.snake.game.Direction
import com.brioni.snake.game.Food
import com.brioni.snake.game.FoodType
import com.brioni.snake.game.GameState
import com.brioni.snake.game.Position
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
@Composable
fun GameBoard(
    state: GameState,
    previousSnake: List<Position>,
    tickId: Int,
    tickMillis: Long,
    running: Boolean,
    eatEvent: EatEvent?,
    eatEventId: Int,
    modifier: Modifier = Modifier,
) {
    val particles: SnapshotStateList<Particle> = remember { emptyList<Particle>().toMutableStateList() }
    var fraction by remember { mutableFloatStateOf(1f) }
    var frameNanos by remember { mutableLongStateOf(0L) }

    val currentTickId by rememberUpdatedState(tickId)
    val currentTickMillis by rememberUpdatedState(tickMillis.coerceAtLeast(1L))

    // A single frame-driven loop runs only while the game is running: it
    // advances the interpolation fraction and the particles, and writes
    // [frameNanos] to force a redraw each frame. When not running it leaves a
    // static frame (fraction = 1) and clears any leftover particles.
    LaunchedEffect(running) {
        if (!running) {
            fraction = 1f
            particles.clear()
            return@LaunchedEffect
        }
        var lastFrame = withFrameNanos { it }
        var tickStart = lastFrame
        var seenTick = currentTickId
        // Stay put until the next tick arrives, so resuming from pause doesn't
        // replay the last move's interpolation.
        fraction = 1f
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - lastFrame) / 1_000_000_000.0).toFloat()
            lastFrame = now
            if (currentTickId != seenTick) {
                seenTick = currentTickId
                tickStart = now
            }
            fraction = (((now - tickStart) / 1_000_000.0).toFloat() / currentTickMillis).coerceIn(0f, 1f)
            updateParticles(particles, dt)
            frameNanos = now
        }
    }

    LaunchedEffect(eatEventId) {
        val event = eatEvent
        if (eatEventId > 0 && event != null) {
            val cx = event.cell.x + event.span / 2f
            val cy = event.cell.y + event.span / 2f
            emitEatBurst(particles, cx, cy, GameColors.foodColor(event.type), event.span)
        }
    }

    Canvas(modifier = modifier) {
        val board = state.board
        val cell = min(size.width / board.width, size.height / board.height)
        if (cell <= 0f) return@Canvas

        // Read animation state so the Canvas redraws each frame while running.
        val now = frameNanos
        val f = fraction
        val seconds = now / 1_000_000_000.0
        val pulse = if (running) 0.9f + 0.1f * (sin(seconds * 6.0).toFloat() * 0.5f + 0.5f) else 1f
        val starAngle = if (running) (seconds * 0.9).toFloat() else 0f

        val boardWidth = cell * board.width
        val boardHeight = cell * board.height
        val originX = (size.width - boardWidth) / 2f
        val originY = (size.height - boardHeight) / 2f

        drawBoardBackground(board, cell, originX, originY, boardWidth, boardHeight)

        // Clip dynamic content to the board so the head can slide "into" a wall
        // on the fatal tick without painting over the HUD.
        clipRect(originX, originY, originX + boardWidth, originY + boardHeight) {
            state.obstacles.forEach { obstacle ->
                drawObstacle(cell, originX + obstacle.x * cell, originY + obstacle.y * cell)
            }
            state.foods.forEach { food ->
                drawFood(food, cell, originX, originY, pulse, starAngle)
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
                )
            }
            particles.forEach { p ->
                drawCircle(
                    color = p.color.copy(alpha = 0.85f * p.fade),
                    radius = p.radiusCells * cell,
                    center = Offset(originX + p.x * cell, originY + p.y * cell),
                )
            }
        }

        // Framing border painted on top.
        drawRect(
            color = GameColors.BoardBorder,
            topLeft = Offset(originX, originY),
            size = Size(boardWidth, boardHeight),
            style = Stroke(width = (cell * 0.12f).coerceAtLeast(2f)),
        )
    }
}

private fun DrawScope.drawBoardBackground(
    board: BoardSize,
    cell: Float,
    originX: Float,
    originY: Float,
    boardWidth: Float,
    boardHeight: Float,
) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(GameColors.BoardTop, GameColors.BoardBottom),
            startY = originY,
            endY = originY + boardHeight,
        ),
        topLeft = Offset(originX, originY),
        size = Size(boardWidth, boardHeight),
    )

    if (cell < 10f) return // skip the grid on dense boards where it is just noise
    for (x in 0..board.width) {
        val lineX = originX + x * cell
        drawLine(GameColors.GridLine, Offset(lineX, originY), Offset(lineX, originY + boardHeight), 1f)
    }
    for (y in 0..board.height) {
        val lineY = originY + y * cell
        drawLine(GameColors.GridLine, Offset(originX, lineY), Offset(originX + boardWidth, lineY), 1f)
    }
}

private fun DrawScope.drawObstacle(cell: Float, left: Float, top: Float) {
    val inset = cell * 0.08f
    val side = cell - 2 * inset
    val radius = CornerRadius(cell * 0.2f, cell * 0.2f)
    drawRoundRect(
        color = GameColors.ObstacleShadow,
        topLeft = Offset(left + inset, top + inset + cell * 0.04f),
        size = Size(side, side),
        cornerRadius = radius,
    )
    drawRoundRect(
        color = GameColors.Obstacle,
        topLeft = Offset(left + inset, top + inset),
        size = Size(side, side),
        cornerRadius = radius,
    )
    // Top bevel highlight.
    drawRoundRect(
        color = GameColors.ObstacleHighlight.copy(alpha = 0.6f),
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
    starAngle: Float,
) {
    val color = GameColors.foodColor(food.type)
    val extent = cell * food.span
    val centerX = originX + food.position.x * cell + extent / 2f
    val centerY = originY + food.position.y * cell + extent / 2f

    if (food.type == FoodType.Blue) {
        drawStar(centerX, centerY, extent * 0.46f * pulse, extent * 0.2f * pulse, starAngle, color)
        return
    }

    val radius = extent * 0.42f * pulse
    // Soft halo for the rarer (mega/gold) foods.
    if (food.span >= 2 || food.type == FoodType.Gold) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.35f), Color.Transparent),
                center = Offset(centerX, centerY),
                radius = radius * 1.9f,
            ),
            radius = radius * 1.9f,
            center = Offset(centerX, centerY),
        )
    }
    drawCircle(color = color, radius = radius, center = Offset(centerX, centerY))
    drawCircle(
        color = Color.White.copy(alpha = 0.35f),
        radius = radius * 0.32f,
        center = Offset(centerX - radius * 0.28f, centerY - radius * 0.28f),
    )
}

private fun DrawScope.drawSnakeSegment(
    isHead: Boolean,
    left: Float,
    top: Float,
    cell: Float,
    direction: Direction,
) {
    val centerX = left + cell / 2f
    val centerY = top + cell / 2f

    if (isHead) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(GameColors.HeadGlow.copy(alpha = 0.45f), Color.Transparent),
                center = Offset(centerX, centerY),
                radius = cell * 1.1f,
            ),
            radius = cell * 1.1f,
            center = Offset(centerX, centerY),
        )
    }

    val inset = cell * 0.06f
    val side = cell - 2 * inset
    val topLeft = Offset(left + inset, top + inset)
    val radius = CornerRadius(cell * 0.3f, cell * 0.3f)
    drawRoundRect(
        color = if (isHead) GameColors.SnakeHead else GameColors.SnakeBody,
        topLeft = topLeft,
        size = Size(side, side),
        cornerRadius = radius,
    )
    drawRoundRect(
        color = GameColors.SnakeOutline,
        topLeft = topLeft,
        size = Size(side, side),
        cornerRadius = radius,
        style = Stroke(width = cell * 0.06f),
    )

    if (isHead) drawEyes(centerX, centerY, cell, direction)
}

private fun DrawScope.drawEyes(centerX: Float, centerY: Float, cell: Float, direction: Direction) {
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
            GameColors.SnakeEye,
            pupilRadius,
            Offset(ex + forwardX * cell * 0.03f, ey + forwardY * cell * 0.03f),
        )
    }
}

private fun DrawScope.drawStar(
    centerX: Float,
    centerY: Float,
    outer: Float,
    inner: Float,
    angleOffset: Float,
    color: Color,
) {
    val path = Path()
    for (i in 0 until 10) {
        val angle = i * Math.PI / 5.0 - Math.PI / 2.0 + angleOffset
        val radius = if (i % 2 == 0) outer else inner
        val x = (centerX + cos(angle) * radius).toFloat()
        val y = (centerY + sin(angle) * radius).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = color)
}

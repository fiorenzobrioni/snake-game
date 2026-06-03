package com.brioni.snake.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.brioni.snake.game.BoardSize
import com.brioni.snake.game.Food
import com.brioni.snake.game.FoodType
import com.brioni.snake.game.GameState
import com.brioni.snake.game.Position
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the board on a Compose [Canvas]: background + grid, obstacles, foods
 * and the snake. The grid logic stays in the model; this only renders state.
 * The board is scaled to fit and centred while keeping its cell aspect ratio.
 */
@Composable
fun GameBoard(state: GameState, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val board = state.board
        val cell = min(size.width / board.width, size.height / board.height)
        if (cell <= 0f) return@Canvas

        val boardWidth = cell * board.width
        val boardHeight = cell * board.height
        val originX = (size.width - boardWidth) / 2f
        val originY = (size.height - boardHeight) / 2f

        fun px(cellX: Int) = originX + cellX * cell
        fun py(cellY: Int) = originY + cellY * cell

        // Background panel + framing border.
        drawRect(
            color = GameColors.BoardFill,
            topLeft = Offset(originX, originY),
            size = Size(boardWidth, boardHeight),
        )
        drawGrid(board, cell, originX, originY, boardWidth, boardHeight)
        drawRect(
            color = GameColors.BoardBorder,
            topLeft = Offset(originX, originY),
            size = Size(boardWidth, boardHeight),
            style = Stroke(width = cell * 0.12f),
        )

        // Obstacles.
        val obstacleInset = cell * 0.08f
        state.obstacles.forEach { cellPos ->
            drawRect(
                color = GameColors.Obstacle,
                topLeft = Offset(px(cellPos.x) + obstacleInset, py(cellPos.y) + obstacleInset),
                size = Size(cell - 2 * obstacleInset, cell - 2 * obstacleInset),
            )
        }

        // Foods.
        state.foods.forEach { food -> drawFood(food, cell, ::px, ::py) }

        // Snake — tail first so the head paints on top.
        for (i in state.snake.indices.reversed()) {
            drawSnakeSegment(state.snake[i], isHead = i == 0, cell = cell, px = ::px, py = ::py)
        }
    }
}

private fun DrawScope.drawGrid(
    board: BoardSize,
    cell: Float,
    originX: Float,
    originY: Float,
    boardWidth: Float,
    boardHeight: Float,
) {
    // Skip the grid on dense boards where lines would just be noise.
    if (cell < 10f) return
    val strokeWidth = 1f
    for (x in 0..board.width) {
        val lineX = originX + x * cell
        drawLine(
            color = GameColors.GridLine,
            start = Offset(lineX, originY),
            end = Offset(lineX, originY + boardHeight),
            strokeWidth = strokeWidth,
        )
    }
    for (y in 0..board.height) {
        val lineY = originY + y * cell
        drawLine(
            color = GameColors.GridLine,
            start = Offset(originX, lineY),
            end = Offset(originX + boardWidth, lineY),
            strokeWidth = strokeWidth,
        )
    }
}

private fun DrawScope.drawSnakeSegment(
    cellPos: Position,
    isHead: Boolean,
    cell: Float,
    px: (Int) -> Float,
    py: (Int) -> Float,
) {
    val inset = cell * 0.06f
    val side = cell - 2 * inset
    val topLeft = Offset(px(cellPos.x) + inset, py(cellPos.y) + inset)
    val radius = CornerRadius(cell * 0.28f, cell * 0.28f)
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
}

private fun DrawScope.drawFood(
    food: Food,
    cell: Float,
    px: (Int) -> Float,
    py: (Int) -> Float,
) {
    val color = GameColors.foodColor(food.type)
    val extent = cell * food.span
    val centerX = px(food.position.x) + extent / 2f
    val centerY = py(food.position.y) + extent / 2f

    if (food.type == FoodType.Blue) {
        drawStar(centerX, centerY, outer = extent * 0.46f, inner = extent * 0.2f, color = color)
        return
    }

    val radius = extent * 0.42f
    drawCircle(color = color, radius = radius, center = Offset(centerX, centerY))
    // A subtle highlight to lift it off the dark board.
    drawCircle(
        color = Color.White.copy(alpha = 0.35f),
        radius = radius * 0.32f,
        center = Offset(centerX - radius * 0.28f, centerY - radius * 0.28f),
    )
}

private fun DrawScope.drawStar(
    centerX: Float,
    centerY: Float,
    outer: Float,
    inner: Float,
    color: Color,
) {
    val path = Path()
    for (i in 0 until 10) {
        val angle = i * Math.PI / 5.0 - Math.PI / 2.0
        val radius = if (i % 2 == 0) outer else inner
        val x = (centerX + cos(angle) * radius).toFloat()
        val y = (centerY + sin(angle) * radius).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = color)
}

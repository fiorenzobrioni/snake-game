package com.brioni.snake.ui.game

import androidx.compose.ui.graphics.Color
import com.brioni.snake.game.FoodType

/**
 * Palette for the Canvas renderer. Mirrors the colour identity of v1.0.0
 * (lime/red/gold/blue foods, gray obstacles, green snake with a bright head)
 * while staying consistent with the dark Material 3 theme.
 */
object GameColors {
    val BoardFill = Color(0xFF0C1014)
    val GridLine = Color(0x14FFFFFF)
    val BoardBorder = Color(0xFF2A3340)

    val Obstacle = Color(0xFF5A6470)
    val ObstacleEdge = Color(0xFF3A424C)

    val SnakeBody = Color(0xFF3FA34D)
    val SnakeHead = Color(0xFF7CFC00)
    val SnakeOutline = Color(0xFF1E5128)

    private val Green = Color(0xFF53D769)
    private val Red = Color(0xFFE53935)
    private val Gold = Color(0xFFFFC107)
    private val Blue = Color(0xFF29B6F6)

    fun foodColor(type: FoodType): Color = when (type) {
        FoodType.Green, FoodType.MegaGreen -> Green
        FoodType.Red, FoodType.MegaRed -> Red
        FoodType.Gold, FoodType.MegaGold -> Gold
        FoodType.Blue -> Blue
    }
}

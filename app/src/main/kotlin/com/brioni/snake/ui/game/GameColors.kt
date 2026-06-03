package com.brioni.snake.ui.game

import androidx.compose.ui.graphics.Color
import com.brioni.snake.game.FoodType

/**
 * Palette for the Canvas renderer. Mirrors the colour identity of v1.0.0
 * (lime/red/gold/blue foods, gray obstacles, green snake with a bright head)
 * while staying consistent with the dark Material 3 theme. Phase 2 adds the
 * background gradient, head glow and obstacle shading colours.
 */
object GameColors {
    // Board background — a subtle vertical gradient with a framed border.
    val BoardTop = Color(0xFF121A22)
    val BoardBottom = Color(0xFF0A0E13)
    val GridLine = Color(0x10FFFFFF)
    val BoardBorder = Color(0xFF2A3340)

    // Obstacles — drawn as bevelled blocks.
    val Obstacle = Color(0xFF5A6470)
    val ObstacleHighlight = Color(0xFF79838F)
    val ObstacleShadow = Color(0xFF353C45)

    // Snake.
    val SnakeBody = Color(0xFF3FA34D)
    val SnakeBodyDark = Color(0xFF2E7D3A)
    val SnakeHead = Color(0xFF7CFC00)
    val SnakeOutline = Color(0xFF1E5128)
    val SnakeEye = Color(0xFF0A0E13)
    val HeadGlow = Color(0xFF7CFC00)

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

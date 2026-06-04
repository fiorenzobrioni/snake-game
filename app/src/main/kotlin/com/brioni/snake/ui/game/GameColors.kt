package com.brioni.snake.ui.game

import androidx.compose.ui.graphics.Color
import com.brioni.snake.game.Food
import com.brioni.snake.game.FoodCategory
import com.brioni.snake.game.FoodTier

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

    // Grow foods — a green family deepening with the magnitude tier.
    private val GrowSmall = Color(0xFF9CCC65)
    private val GrowMedium = Color(0xFF53D769)
    private val GrowLarge = Color(0xFF2E9E4F)
    private val GrowHuge = Color(0xFF1B7A3D)
    private val GrowMystery = Color(0xFF4DE0A6)

    // Shrink foods — a warm family heating up with the tier.
    private val ShrinkSmall = Color(0xFFFFCA28)
    private val ShrinkMedium = Color(0xFFFF9800)
    private val ShrinkLarge = Color(0xFFE53935)
    private val ShrinkMystery = Color(0xFFFF5252)

    /** Colour identity of a food, from its category and magnitude tier. */
    fun foodColor(food: Food): Color = when (food.category) {
        FoodCategory.Grow -> when (food.tier) {
            FoodTier.Small -> GrowSmall
            FoodTier.Medium -> GrowMedium
            FoodTier.Large -> GrowLarge
            FoodTier.Huge -> GrowHuge
            FoodTier.Mystery -> GrowMystery
        }
        FoodCategory.Shrink -> when (food.tier) {
            FoodTier.Small -> ShrinkSmall
            FoodTier.Medium -> ShrinkMedium
            FoodTier.Mystery -> ShrinkMystery
            else -> ShrinkLarge
        }
        // Reserved for the Phase 6 specials; neutral until those ship.
        FoodCategory.Special -> Color(0xFFB388FF)
    }
}

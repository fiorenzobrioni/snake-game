package com.brioni.snake.ui.game

import androidx.compose.ui.graphics.Color
import com.brioni.snake.game.Food
import com.brioni.snake.game.FoodCategory
import com.brioni.snake.game.FoodTier
import com.brioni.snake.game.Skin

/**
 * The full set of colours and style flags the Canvas renderer needs, bundled so
 * a whole look can be swapped atomically by selecting a [Skin]. Every renderer
 * reads from a [SkinPalette] rather than a hard-coded object, which keeps the
 * skin switch a single data lookup ([paletteFor]) instead of branches scattered
 * through the drawing code.
 *
 * @param rounded  cells drawn with rounded corners (false → crisp pixel squares).
 * @param useGlow  emit the radial head glow / food halos (false → flat styling).
 */
data class SkinPalette(
    // Board background — a vertical gradient with a framed border.
    val boardTop: Color,
    val boardBottom: Color,
    val gridLine: Color,
    val boardBorder: Color,
    // Obstacles — drawn as bevelled blocks.
    val obstacle: Color,
    val obstacleHighlight: Color,
    val obstacleShadow: Color,
    // Snake.
    val snakeBody: Color,
    val snakeHead: Color,
    val snakeOutline: Color,
    val snakeEye: Color,
    val headGlow: Color,
    // Grow foods — deepening with the magnitude tier.
    val growSmall: Color,
    val growMedium: Color,
    val growLarge: Color,
    val growHuge: Color,
    val growMystery: Color,
    // Shrink foods — a warm family heating up with the tier.
    val shrinkSmall: Color,
    val shrinkMedium: Color,
    val shrinkLarge: Color,
    val shrinkMystery: Color,
    // Special power-up / hazard pieces (Phase 6.2).
    val special: Color,
    // Style flags.
    val rounded: Boolean,
    val useGlow: Boolean,
) {
    /** Colour identity of a food, from its category and magnitude tier. */
    fun foodColor(food: Food): Color = when (food.category) {
        FoodCategory.Grow -> when (food.tier) {
            FoodTier.Small -> growSmall
            FoodTier.Medium -> growMedium
            FoodTier.Large -> growLarge
            FoodTier.Huge -> growHuge
            FoodTier.Mystery -> growMystery
        }
        FoodCategory.Shrink -> when (food.tier) {
            FoodTier.Small -> shrinkSmall
            FoodTier.Medium -> shrinkMedium
            FoodTier.Mystery -> shrinkMystery
            else -> shrinkLarge
        }
        FoodCategory.Special -> special
    }
}

/** The palette for a [skin]. [Skin.Classic] reproduces the pre-skin look exactly. */
fun paletteFor(skin: Skin): SkinPalette = when (skin) {
    Skin.Classic -> ClassicPalette
    Skin.Neon -> NeonPalette
    Skin.Retro -> RetroPalette
    Skin.Pixel -> PixelPalette
}

/** Original identity: lime snake, green/warm foods, gray obstacles, dark gradient. */
private val ClassicPalette = SkinPalette(
    boardTop = Color(0xFF121A22),
    boardBottom = Color(0xFF0A0E13),
    gridLine = Color(0x10FFFFFF),
    boardBorder = Color(0xFF2A3340),
    obstacle = Color(0xFF5A6470),
    obstacleHighlight = Color(0xFF79838F),
    obstacleShadow = Color(0xFF353C45),
    snakeBody = Color(0xFF3FA34D),
    snakeHead = Color(0xFF7CFC00),
    snakeOutline = Color(0xFF1E5128),
    snakeEye = Color(0xFF0A0E13),
    headGlow = Color(0xFF7CFC00),
    growSmall = Color(0xFF9CCC65),
    growMedium = Color(0xFF53D769),
    growLarge = Color(0xFF2E9E4F),
    growHuge = Color(0xFF1B7A3D),
    growMystery = Color(0xFF4DE0A6),
    shrinkSmall = Color(0xFFFFCA28),
    shrinkMedium = Color(0xFFFF9800),
    shrinkLarge = Color(0xFFE53935),
    shrinkMystery = Color(0xFFFF5252),
    special = Color(0xFFB388FF),
    rounded = true,
    useGlow = true,
)

/** Saturated cyan/magenta on near-black with boosted glow. */
private val NeonPalette = SkinPalette(
    boardTop = Color(0xFF0B0F1A),
    boardBottom = Color(0xFF04050A),
    gridLine = Color(0x2200E5FF),
    boardBorder = Color(0xFF00E5FF),
    obstacle = Color(0xFF3A2A5A),
    obstacleHighlight = Color(0xFFB388FF),
    obstacleShadow = Color(0xFF170F2E),
    snakeBody = Color(0xFF00E5FF),
    snakeHead = Color(0xFF7CFFF6),
    snakeOutline = Color(0xFF006E8C),
    snakeEye = Color(0xFF04050A),
    headGlow = Color(0xFF00E5FF),
    growSmall = Color(0xFF8CFF6B),
    growMedium = Color(0xFF39FF14),
    growLarge = Color(0xFF00E676),
    growHuge = Color(0xFF00C853),
    growMystery = Color(0xFF18FFFF),
    shrinkSmall = Color(0xFFFF9CE0),
    shrinkMedium = Color(0xFFFF2D95),
    shrinkLarge = Color(0xFFFF1744),
    shrinkMystery = Color(0xFFFF5CA2),
    special = Color(0xFFFFEA00),
    rounded = true,
    useGlow = true,
)

/** Warm phosphor arcade palette; flat styling that pairs with the CRT filter. */
private val RetroPalette = SkinPalette(
    boardTop = Color(0xFF1A1206),
    boardBottom = Color(0xFF0C0903),
    gridLine = Color(0x18FFB000),
    boardBorder = Color(0xFF5A3A12),
    obstacle = Color(0xFF7A5A2A),
    obstacleHighlight = Color(0xFFB88A45),
    obstacleShadow = Color(0xFF3A2810),
    snakeBody = Color(0xFF8FBF3F),
    snakeHead = Color(0xFFCDE86B),
    snakeOutline = Color(0xFF3F5A1E),
    snakeEye = Color(0xFF0C0903),
    headGlow = Color(0xFFCDE86B),
    growSmall = Color(0xFFB7D86B),
    growMedium = Color(0xFF8FBF3F),
    growLarge = Color(0xFF6FA02A),
    growHuge = Color(0xFF52801C),
    growMystery = Color(0xFFE0C341),
    shrinkSmall = Color(0xFFFFC15A),
    shrinkMedium = Color(0xFFFF9A3D),
    shrinkLarge = Color(0xFFE0531E),
    shrinkMystery = Color(0xFFFF7A45),
    special = Color(0xFFFFD166),
    rounded = true,
    useGlow = false,
)

/** Flat, square, glow-free pixel-art styling. */
private val PixelPalette = SkinPalette(
    boardTop = Color(0xFF14161F),
    boardBottom = Color(0xFF0B0C12),
    gridLine = Color(0x14FFFFFF),
    boardBorder = Color(0xFF2A2E3A),
    obstacle = Color(0xFF6B6B6B),
    obstacleHighlight = Color(0xFF8C8C8C),
    obstacleShadow = Color(0xFF3A3A3A),
    snakeBody = Color(0xFF4CAF50),
    snakeHead = Color(0xFF8BC34A),
    snakeOutline = Color(0xFF255D2A),
    snakeEye = Color(0xFF0B0C12),
    headGlow = Color(0xFF8BC34A),
    growSmall = Color(0xFFAED581),
    growMedium = Color(0xFF66BB6A),
    growLarge = Color(0xFF43A047),
    growHuge = Color(0xFF2E7D32),
    growMystery = Color(0xFF26C6DA),
    shrinkSmall = Color(0xFFFFB74D),
    shrinkMedium = Color(0xFFFF8A65),
    shrinkLarge = Color(0xFFE53935),
    shrinkMystery = Color(0xFFEF5350),
    special = Color(0xFFB388FF),
    rounded = false,
    useGlow = false,
)

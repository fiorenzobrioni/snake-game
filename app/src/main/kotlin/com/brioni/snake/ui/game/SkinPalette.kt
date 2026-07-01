package com.brioni.snake.ui.game

import androidx.compose.ui.graphics.Color
import com.brioni.snake.game.Food
import com.brioni.snake.game.FoodCategory
import com.brioni.snake.game.FoodTier
import com.brioni.snake.game.Skin

/**
 * The "material" a skin uses for its power-up / hazard tokens (see
 * [drawSpecialToken]). The effect's identity accent colour stays constant across
 * skins; only the frame's *look* changes, so a token feels native to its skin
 * while a colour + symbol always means the same effect.
 *
 * [tokenCorner] is the token's corner radius as a fraction of its radius: `null`
 * draws a disc, `0f` a hard square, higher values increasingly rounded squares.
 */
enum class SpecialStyle(val tokenCorner: Float?) {
    /** Classic: a glossy enamel disc. */
    Enamel(null),

    /** Neon: a hollow neon-tube disc - dark centre, bright rim. */
    Neon(null),

    /** Retro: a warm, bevelled phosphor tile. */
    Phosphor(0.32f),

    /** Pixel: a hard-edged pixel tile with a corner highlight. */
    Pixel(0.0f),

    /** Aurora: a frosted, translucent glass gem. */
    Glass(0.44f),

    /** Ember: a molten token in a dark iron bezel. */
    Ember(0.36f),
}

/**
 * The full set of colours and style flags the Canvas renderer needs, bundled so
 * a whole look can be swapped atomically by selecting a [Skin]. Every renderer
 * reads from a [SkinPalette] rather than a hard-coded object, which keeps the
 * skin switch a single data lookup ([paletteFor]) instead of branches scattered
 * through the drawing code.
 *
 * @param cornerFactor corner radius as a fraction of a cell (0 → crisp pixel
 *                     squares, ~0.5 → bubbly rounded). Shapes obstacles, snake
 *                     and (regular) food, so skins differ in form, not just hue.
 * @param useGlow      emit the radial head glow / food halos (false → flat styling).
 * @param segmentedBody draw the snake body as discrete tapered blocks instead of a
 *                     continuous tube. Independent of [useGlow], so a glowing skin
 *                     can still wear a per-segment body (reads better through
 *                     teleports / the invincibility shimmer).
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
    val cornerFactor: Float,
    val useGlow: Boolean,
    val segmentedBody: Boolean,
    // The material used to draw power-up / hazard tokens (see [drawSpecialToken]).
    val specialStyle: SpecialStyle,
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
    Skin.Aurora -> AuroraPalette
    Skin.Ember -> EmberPalette
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
    cornerFactor = 0.30f,
    useGlow = true,
    segmentedBody = false,
    specialStyle = SpecialStyle.Enamel,
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
    cornerFactor = 0.50f,
    useGlow = true,
    segmentedBody = false,
    specialStyle = SpecialStyle.Neon,
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
    cornerFactor = 0.16f,
    useGlow = false,
    segmentedBody = true,
    specialStyle = SpecialStyle.Phosphor,
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
    cornerFactor = 0.0f,
    useGlow = false,
    segmentedBody = true,
    specialStyle = SpecialStyle.Pixel,
)

/**
 * Aurora borealis: a cool indigo board lit by teal/green/violet light, with glow.
 * Wears a segmented body so the colourful pieces shimmer through teleports and the
 * Star invincibility blink.
 */
private val AuroraPalette = SkinPalette(
    boardTop = Color(0xFF101A2E),
    boardBottom = Color(0xFF050811),
    gridLine = Color(0x1A4DE6C8),
    boardBorder = Color(0xFF2BD6C0),
    obstacle = Color(0xFF2A3A5A),
    obstacleHighlight = Color(0xFF6FA8D6),
    obstacleShadow = Color(0xFF131D33),
    snakeBody = Color(0xFF2BE0B0),
    snakeHead = Color(0xFF7CFFD6),
    snakeOutline = Color(0xFF12604F),
    snakeEye = Color(0xFF050811),
    headGlow = Color(0xFF3BE8C8),
    growSmall = Color(0xFF9CF0C0),
    growMedium = Color(0xFF4DE6A0),
    growLarge = Color(0xFF2BC78C),
    growHuge = Color(0xFF1E9E78),
    growMystery = Color(0xFFB68CFF),
    shrinkSmall = Color(0xFFFFD28C),
    shrinkMedium = Color(0xFFFF9F6B),
    shrinkLarge = Color(0xFFFF5C8A),
    shrinkMystery = Color(0xFFFF7AC0),
    special = Color(0xFFC299FF),
    cornerFactor = 0.22f,
    useGlow = true,
    segmentedBody = true,
    specialStyle = SpecialStyle.Glass,
)

/**
 * Ember: a dark charcoal board with a warm underglow and a molten orange snake,
 * intense glow. Segmented body so the lava pieces trail brightly through teleports
 * and the Star shimmer.
 */
private val EmberPalette = SkinPalette(
    boardTop = Color(0xFF241008),
    boardBottom = Color(0xFF0C0603),
    gridLine = Color(0x1AFF7A2A),
    boardBorder = Color(0xFFFF7A2A),
    obstacle = Color(0xFF4A2A1A),
    obstacleHighlight = Color(0xFF8C5A38),
    obstacleShadow = Color(0xFF1E0F08),
    snakeBody = Color(0xFFFF7A1A),
    snakeHead = Color(0xFFFFC24D),
    snakeOutline = Color(0xFF7A2E08),
    snakeEye = Color(0xFF0C0603),
    headGlow = Color(0xFFFF6A1A),
    growSmall = Color(0xFFFFE08A),
    growMedium = Color(0xFFFFC24D),
    growLarge = Color(0xFFFF9A2E),
    growHuge = Color(0xFFFF6A1A),
    growMystery = Color(0xFFFFE36B),
    shrinkSmall = Color(0xFFB388FF),
    shrinkMedium = Color(0xFF8C5CFF),
    shrinkLarge = Color(0xFFE53935),
    shrinkMystery = Color(0xFFFF5252),
    special = Color(0xFF66E0FF),
    cornerFactor = 0.18f,
    useGlow = true,
    segmentedBody = true,
    specialStyle = SpecialStyle.Ember,
)

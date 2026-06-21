package com.brioni.snake.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The app's branded action buttons - a premium, modern restyle of the menu-style
 * Material [androidx.compose.material3.Button] / [androidx.compose.material3.OutlinedButton].
 *
 * The look intentionally moves away from the flat, fully-rounded "pill": defined
 * 15dp corners give it a crisper, more deliberate silhouette; a top-lit vertical
 * gradient and a hairline rim add volume; a soft coloured lift shadow grounds it;
 * and a tactile press-scale + ripple make it feel responsive. [SnakeButton] is
 * the filled, high-emphasis variant; [SnakeOutlinedButton] is the glassy,
 * lower-emphasis one. Both are drop-in replacements (same `onClick` / `enabled` /
 * `RowScope` content API) used everywhere those Material buttons were.
 */

private val SnakeButtonShape = RoundedCornerShape(15.dp)
private val SnakeButtonMinHeight = 52.dp
private val SnakeButtonHPadding = 24.dp
private val SnakeButtonVPadding = 14.dp
// Fixed so every tile is identical regardless of one- vs two-line labels (tall
// enough for an icon plus a two-line label).
private val MenuTileHeight = 82.dp
private val MenuIconButtonSize = 44.dp
private val MenuIconButtonShape = RoundedCornerShape(13.dp)

/** Mixes [c] toward white by [f] (0..1), preserving alpha. */
private fun lighten(c: Color, f: Float): Color = Color(
    red = c.red + (1f - c.red) * f,
    green = c.green + (1f - c.green) * f,
    blue = c.blue + (1f - c.blue) * f,
    alpha = c.alpha,
)

/** Darkens [c] toward black by fraction [f] (0 = unchanged, 1 = black). */
private fun darken(c: Color, f: Float): Color = Color(
    red = c.red * (1f - f),
    green = c.green * (1f - f),
    blue = c.blue * (1f - f),
    alpha = c.alpha,
)

/** Filled, high-emphasis action button (primary). */
@Composable
fun SnakeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        label = "snakeButtonScale",
    )
    // A near-black green ink keeps the label crisp on the bright fill, on any skin.
    val ink = Color(0xFF08120A)
    val fill = Brush.verticalGradient(
        listOf(lighten(primary, 0.18f), primary, darken(primary, 0.22f)),
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .heightIn(min = SnakeButtonMinHeight)
            .shadow(
                elevation = if (pressed) 3.dp else 12.dp,
                shape = SnakeButtonShape,
                ambientColor = primary,
                spotColor = primary,
            )
            .clip(SnakeButtonShape)
            .background(fill)
            .border(1.dp, lighten(primary, 0.45f).copy(alpha = 0.55f), SnakeButtonShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Color.White),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = SnakeButtonHPadding, vertical = SnakeButtonVPadding),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            ProvideTextStyle(MaterialTheme.typography.titleSmall) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }
    }
}

/** Glassy, lower-emphasis action button (secondary), with a gradient rim. */
@Composable
fun SnakeOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val primary = scheme.primary
    val onBackground = scheme.onBackground
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        label = "snakeOutlinedScale",
    )
    // A faint glass fill (so it is not dead-flat) with a top-to-bottom gradient rim.
    val fill = Brush.verticalGradient(
        listOf(onBackground.copy(alpha = 0.08f), onBackground.copy(alpha = 0.02f)),
    )
    val rim = Brush.verticalGradient(
        listOf(primary.copy(alpha = 0.70f), primary.copy(alpha = 0.22f)),
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .heightIn(min = SnakeButtonMinHeight)
            .clip(SnakeButtonShape)
            .background(fill)
            .border(BorderStroke(1.5.dp, rim), SnakeButtonShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = primary),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = SnakeButtonHPadding, vertical = SnakeButtonVPadding),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides onBackground) {
            ProvideTextStyle(MaterialTheme.typography.titleSmall) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }
    }
}

/**
 * A compact, square-ish menu tile with an icon stacked over a short label. Shares
 * the glassy rim look of [SnakeOutlinedButton] so the grouped "shelf" rows on the
 * menu read as one family. Designed to sit in a [Row] with `Modifier.weight(1f)`.
 */
@Composable
fun MenuTile(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val primary = scheme.primary
    val onBackground = scheme.onBackground
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        label = "menuTileScale",
    )
    val fill = Brush.verticalGradient(
        listOf(onBackground.copy(alpha = 0.08f), onBackground.copy(alpha = 0.02f)),
    )
    val rim = Brush.verticalGradient(
        listOf(primary.copy(alpha = 0.70f), primary.copy(alpha = 0.22f)),
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .height(MenuTileHeight)
            .clip(SnakeButtonShape)
            .background(fill)
            .border(BorderStroke(1.5.dp, rim), SnakeButtonShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = primary),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * A small glassy icon-only button for low-emphasis "overflow" actions (e.g. the
 * Settings / Credits entries pinned in the menu's top corner).
 */
@Composable
fun MenuIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val primary = scheme.primary
    val onBackground = scheme.onBackground
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.92f else 1f,
        label = "menuIconScale",
    )
    val fill = Brush.verticalGradient(
        listOf(onBackground.copy(alpha = 0.08f), onBackground.copy(alpha = 0.02f)),
    )
    val rim = Brush.verticalGradient(
        listOf(primary.copy(alpha = 0.70f), primary.copy(alpha = 0.22f)),
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .size(MenuIconButtonSize)
            .clip(MenuIconButtonShape)
            .background(fill)
            .border(BorderStroke(1.5.dp, rim), MenuIconButtonShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = primary, bounded = false),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = primary,
            modifier = Modifier.size(22.dp),
        )
    }
}

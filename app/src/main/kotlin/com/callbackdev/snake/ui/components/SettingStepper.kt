package com.callbackdev.snake.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A one-line **ordered** setting: its name and current value on one row, a
 * segmented gauge of numbered notches under it, and an optional caption saying
 * what the choice changes.
 *
 * It replaces the wrapping chip rows the pre-game setup used to stack: every
 * option stays visible and one tap away (no horizontal scrolling), the filled
 * notches read the setting as a *level* at a glance - which is exactly what
 * difficulty, pace, growth and board size are - and the whole screen fits
 * without scrolling. Only use it for genuinely ordered scales; unordered
 * choices (the game modes) keep a labelled layout of their own.
 *
 * @param options short notch labels, usually the 1-based numbers.
 * @param optionNames the full name of each option, for screen readers.
 */
@Composable
fun SettingStepper(
    title: String,
    valueLabel: String,
    options: List<String>,
    optionNames: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    caption: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val dim by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.42f,
        label = "stepperEnabled",
    )
    Column(modifier = modifier.fillMaxWidth().graphicsLayer { alpha = dim }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onBackground.copy(alpha = 0.85f),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.primary,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            options.forEachIndexed { index, label ->
                Notch(
                    label = label,
                    name = optionNames.getOrElse(index) { label },
                    title = title,
                    state = when {
                        index == selectedIndex -> NotchState.Selected
                        index < selectedIndex -> NotchState.Below
                        else -> NotchState.Empty
                    },
                    enabled = enabled,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onBackground.copy(alpha = 0.62f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
            )
        }
    }
}

/** How a single notch of the gauge is painted. */
private enum class NotchState { Selected, Below, Empty }

/** The visible bar is [NotchVisualHeight]; the row is taller to keep the tap area comfortable. */
private val NotchVisualHeight = 34.dp
private val NotchTouchHeight = 42.dp
private val NotchShape = RoundedCornerShape(9.dp)

@Composable
private fun Notch(
    label: String,
    name: String,
    title: String,
    state: NotchState,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val primary = scheme.primary
    val onBackground = scheme.onBackground
    // Filled below the selection (the "level reached"), brightest at the
    // selection itself, glass beyond it: the row reads as a gauge, not a row of
    // equal buttons.
    val fill = when (state) {
        NotchState.Selected -> Brush.verticalGradient(
            listOf(lightenColor(primary, 0.18f), primary, darkenColor(primary, 0.20f)),
        )
        NotchState.Below -> Brush.verticalGradient(
            listOf(primary.copy(alpha = 0.34f), primary.copy(alpha = 0.16f)),
        )
        NotchState.Empty -> Brush.verticalGradient(
            listOf(onBackground.copy(alpha = 0.08f), onBackground.copy(alpha = 0.02f)),
        )
    }
    val rim = when (state) {
        NotchState.Selected -> lightenColor(primary, 0.45f).copy(alpha = 0.65f)
        NotchState.Below -> primary.copy(alpha = 0.35f)
        NotchState.Empty -> onBackground.copy(alpha = 0.16f)
    }
    val ink = when (state) {
        // The same near-black ink the filled buttons use: crisp on every terrain accent.
        NotchState.Selected -> Color(0xFF0A0E10)
        NotchState.Below -> onBackground.copy(alpha = 0.85f)
        NotchState.Empty -> onBackground.copy(alpha = 0.5f)
    }
    val description = "$title: $name"
    // The tap area is taller than the bar (comfortable thumb target), so the
    // press feedback is moved onto the bar itself: the ripple is drawn - and
    // clipped - inside the visible shape instead of flooding the taller row,
    // plus the press-scale the rest of the app's controls use.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.94f else 1f,
        label = "notchScale",
    )

    Box(
        modifier = modifier
            .height(NotchTouchHeight)
            .selectable(
                selected = state == NotchState.Selected,
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NotchVisualHeight)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(NotchShape)
                .background(fill)
                .border(BorderStroke(1.dp, rim), NotchShape)
                .indication(interaction, ripple(color = primary)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (state == NotchState.Selected) FontWeight.Bold else FontWeight.Normal,
                color = ink,
                maxLines = 1,
            )
        }
    }
}

/**
 * An **unordered** choice laid out as a grid of glassy cards, for the game mode:
 * every mode stays on screen (a scrolling chip row used to hide them) and the
 * selected one is filled like the gauge's active notch, so the two controls read
 * as one family. [columns] cards per row, in the given [options] order.
 */
@Composable
fun SettingCardGrid(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    caption: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onBackground.copy(alpha = 0.85f),
            maxLines = 1,
        )
        options.chunked(columns).forEachIndexed { rowIndex, rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                rowOptions.forEachIndexed { columnIndex, label ->
                    val index = rowIndex * columns + columnIndex
                    ChoiceCard(
                        label = label,
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keep a short last row aligned with the ones above it.
                repeat(columns - rowOptions.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onBackground.copy(alpha = 0.62f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
            )
        }
    }
}

private val ChoiceCardHeight = 42.dp

@Composable
private fun ChoiceCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val primary = scheme.primary
    val onBackground = scheme.onBackground
    val fill = if (selected) {
        Brush.verticalGradient(listOf(lightenColor(primary, 0.18f), primary, darkenColor(primary, 0.20f)))
    } else {
        Brush.verticalGradient(listOf(onBackground.copy(alpha = 0.08f), onBackground.copy(alpha = 0.02f)))
    }
    val rim = if (selected) lightenColor(primary, 0.45f).copy(alpha = 0.65f) else primary.copy(alpha = 0.28f)
    val ink = if (selected) Color(0xFF0A0E10) else onBackground.copy(alpha = 0.85f)

    Box(
        modifier = modifier
            .height(ChoiceCardHeight)
            .clip(NotchShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(NotchShape)
                .background(fill)
                .border(BorderStroke(1.dp, rim), NotchShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/** Mixes [c] toward white by [f] (0..1), preserving alpha. */
private fun lightenColor(c: Color, f: Float): Color = Color(
    red = c.red + (1f - c.red) * f,
    green = c.green + (1f - c.green) * f,
    blue = c.blue + (1f - c.blue) * f,
    alpha = c.alpha,
)

/** Darkens [c] toward black by fraction [f] (0 = unchanged, 1 = black). */
private fun darkenColor(c: Color, f: Float): Color = Color(
    red = c.red * (1f - f),
    green = c.green * (1f - f),
    blue = c.blue * (1f - f),
    alpha = c.alpha,
)

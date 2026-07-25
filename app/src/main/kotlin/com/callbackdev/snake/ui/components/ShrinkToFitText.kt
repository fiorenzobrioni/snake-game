package com.callbackdev.snake.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

/**
 * A single-line [Text] that **shrinks its font until it fits** instead of
 * ellipsising. Used where the string is the whole point and clipping it would
 * lose information: the HUD score (which grows a digit at a time) and the screen
 * titles (the branded Orbitron face is wide, so "Daily Challenge" would truncate
 * on a normal phone).
 *
 * It converges by re-laying out at [SHRINK_STEP] smaller each time the previous
 * pass overflowed, down to [minScale] - a couple of frames at most, and the
 * scale is remembered per string so a stable title settles once.
 */
@Composable
fun ShrinkToFitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Bold,
    textAlign: TextAlign? = null,
    minScale: Float = 0.55f,
) {
    var scale by remember(text) { mutableFloatStateOf(1f) }
    Text(
        text = text,
        style = style,
        color = color,
        fontWeight = fontWeight,
        fontSize = style.fontSize * scale,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { if (it.hasVisualOverflow && scale > minScale) scale *= SHRINK_STEP },
        modifier = modifier,
    )
}

/** How much each retry shrinks the font. Small enough to look deliberate, not stepped. */
private const val SHRINK_STEP = 0.92f

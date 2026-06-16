package com.brioni.snake.ui.game

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.brioni.snake.R
import com.brioni.snake.game.Direction
import kotlin.math.abs

/**
 * A [Modifier] that turns drag gestures into [Direction] changes. Emits as soon
 * as the accumulated drag passes [thresholdPx] on either axis, then resets, so
 * the player can keep steering with continuous swipes.
 */
fun Modifier.swipeToSteer(
    thresholdPx: Float = 48f,
    onSwipe: (Direction) -> Unit,
): Modifier = pointerInput(Unit) {
    var dx = 0f
    var dy = 0f
    detectDragGestures(
        onDragStart = { dx = 0f; dy = 0f },
        onDragEnd = { dx = 0f; dy = 0f },
        onDragCancel = { dx = 0f; dy = 0f },
        onDrag = { change, drag ->
            change.consume()
            dx += drag.x
            dy += drag.y
            if (abs(dx) >= thresholdPx || abs(dy) >= thresholdPx) {
                val direction = if (abs(dx) > abs(dy)) {
                    if (dx > 0) Direction.Right else Direction.Left
                } else {
                    if (dy > 0) Direction.Down else Direction.Up
                }
                onSwipe(direction)
                dx = 0f
                dy = 0f
            }
        },
    )
}

/**
 * The 3D-mode swipe variant: a swipe steers **relative to the heading** instead
 * of by absolute direction, because in the chase-cam view "left/right" is what
 * makes sense. A horizontal swipe turns left / right; vertical swipes are ignored
 * (forward is implicit and a backward turn would be a reversal anyway).
 */
fun Modifier.swipeToSteerRelative(
    thresholdPx: Float = 48f,
    onLeft: () -> Unit,
    onRight: () -> Unit,
): Modifier = pointerInput(Unit) {
    var dx = 0f
    var dy = 0f
    detectDragGestures(
        onDragStart = { dx = 0f; dy = 0f },
        onDragEnd = { dx = 0f; dy = 0f },
        onDragCancel = { dx = 0f; dy = 0f },
        onDrag = { change, drag ->
            change.consume()
            dx += drag.x
            dy += drag.y
            if (abs(dx) >= thresholdPx && abs(dx) > abs(dy)) {
                if (dx > 0) onRight() else onLeft()
                dx = 0f
                dy = 0f
            } else if (abs(dy) >= thresholdPx) {
                // Vertical swipe carries no meaning in 3D; reset so it can't build
                // up and later trip a stray horizontal turn.
                dx = 0f
                dy = 0f
            }
        },
    )
}

/**
 * The default scheme: two large half-width buttons that turn the snake left /
 * right **relative to its heading** (Left = counter-clockwise, Right =
 * clockwise). They fill the bottom of the screen, split in half, for easy
 * thumb reach with either hand.
 */
@Composable
fun RelativeControls(
    onLeft: () -> Unit,
    onRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TurnButton(
            glyph = "↺",
            descriptionRes = R.string.turn_left,
            onClick = onLeft,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        TurnButton(
            glyph = "↻",
            descriptionRes = R.string.turn_right,
            onClick = onRight,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun TurnButton(
    glyph: String,
    descriptionRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(descriptionRes)
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * On-screen D-pad arranged as a cross. Complements swipe steering for players
 * who prefer buttons. Arrows are drawn with Unicode glyphs to avoid pulling in
 * the extended Material icon dependency.
 */
@Composable
fun DirectionPad(
    onDirection: (Direction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DirectionButton("▲", R.string.dir_up) { onDirection(Direction.Up) }
        Row(horizontalArrangement = Arrangement.spacedBy(72.dp)) {
            DirectionButton("◀", R.string.dir_left) { onDirection(Direction.Left) }
            DirectionButton("▶", R.string.dir_right) { onDirection(Direction.Right) }
        }
        DirectionButton("▼", R.string.dir_down) { onDirection(Direction.Down) }
    }
}

@Composable
private fun DirectionButton(
    glyph: String,
    descriptionRes: Int,
    onClick: () -> Unit,
) {
    val description = stringResource(descriptionRes)
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .size(64.dp)
            .semantics { contentDescription = description },
    ) {
        Text(text = glyph, style = MaterialTheme.typography.titleLarge)
    }
}

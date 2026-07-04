package com.callbackdev.snake.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.callbackdev.snake.R
import com.callbackdev.snake.game.Direction
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * The swipe distance (in px) the player must drag before a steer fires, as a
 * function of the persisted sensitivity (0..1). Higher sensitivity → a shorter
 * flick. The midpoint 0.5 returns exactly [TUNED_SWIPE_THRESHOLD_PX], the value
 * the game shipped with, so the default feel is unchanged.
 */
fun swipeThresholdPx(sensitivity: Float): Float {
    val s = sensitivity.coerceIn(0f, 1f)
    return MAX_SWIPE_THRESHOLD_PX - s * (MAX_SWIPE_THRESHOLD_PX - MIN_SWIPE_THRESHOLD_PX)
}

/** Most forgiving (longest required flick) at sensitivity 0. */
private const val MAX_SWIPE_THRESHOLD_PX = 76f
/** Most responsive (shortest required flick) at sensitivity 1. */
private const val MIN_SWIPE_THRESHOLD_PX = 20f
/** The original, well-tuned default that 0.5 must reproduce: (76 + 20) / 2 = 48. */
private const val TUNED_SWIPE_THRESHOLD_PX = 48f

/**
 * A [Modifier] that turns drag gestures into [Direction] changes. Emits as soon
 * as the accumulated drag passes [thresholdPx] on an axis, then resets. Within a
 * single gesture each distinct direction is emitted **at most once** (tracked via
 * `emitted`): a continuous drag would otherwise re-cross the threshold several
 * times and fire the same direction repeatedly. A fresh flick is a new gesture,
 * so quick successive swipes still steer freely.
 */
fun Modifier.swipeToSteer(
    thresholdPx: Float = TUNED_SWIPE_THRESHOLD_PX,
    onSwipe: (Direction) -> Unit,
): Modifier = pointerInput(thresholdPx) {
    var dx = 0f
    var dy = 0f
    var emitted: Direction? = null
    detectDragGestures(
        onDragStart = { dx = 0f; dy = 0f; emitted = null },
        onDragEnd = { dx = 0f; dy = 0f; emitted = null },
        onDragCancel = { dx = 0f; dy = 0f; emitted = null },
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
                if (direction != emitted) {
                    onSwipe(direction)
                    emitted = direction
                }
                dx = 0f
                dy = 0f
            }
        },
    )
}

/**
 * A [Modifier] for the [com.callbackdev.snake.game.ControlScheme.TapTurn] scheme: a tap
 * on the left half of the area turns the snake left (counter-clockwise), the right
 * half turns it right (clockwise), relative to its heading - comfortable
 * one-handed play with no on-screen buttons stealing board space.
 */
fun Modifier.tapToTurn(
    onLeft: () -> Unit,
    onRight: () -> Unit,
): Modifier = pointerInput(Unit) {
    detectTapGestures { offset ->
        if (offset.x < size.width / 2f) onLeft() else onRight()
    }
}

/**
 * On-screen D-pad drawn as a single, compact "wedge dial": one rounded-square
 * key split by its two diagonals into four triangular wedges (top = Up,
 * right = Right, bottom = Down, left = Left), with a small dead-zone hub in the
 * middle. Because it is one tight control - not a spread-out cross of separate
 * buttons - the thumb barely moves between directions (which matters when the
 * snake is fast), and it occupies far less height, leaving more room for the
 * board. A tap is routed to a wedge by its angle from the centre, the pressed
 * wedge lights up, and four directional chevrons are drawn from the active
 * [palette]. Discrete directions are also exposed as accessibility actions.
 */
@Composable
fun DirectionPad(
    onDirection: (Direction) -> Unit,
    palette: SkinPalette,
    modifier: Modifier = Modifier,
) {
    val accent = palette.snakeHead
    var pressedDir by remember { mutableStateOf<Direction?>(null) }
    val scale by animateFloatAsState(
        targetValue = if (pressedDir != null) 0.97f else 1f,
        label = "dpadScale",
    )
    val shape = RoundedCornerShape(DPadCorner)
    val fill = Brush.verticalGradient(
        listOf(
            mix(palette.boardTop, accent, 0.16f),
            mix(palette.boardBottom, accent, 0.06f),
        ),
    )
    val rim = Brush.verticalGradient(
        listOf(accent.copy(alpha = 0.75f), accent.copy(alpha = 0.22f)),
    )

    val upDesc = stringResource(R.string.dir_up)
    val rightDesc = stringResource(R.string.dir_right)
    val downDesc = stringResource(R.string.dir_down)
    val leftDesc = stringResource(R.string.dir_left)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(DPadSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(elevation = 10.dp, shape = shape, ambientColor = accent, spotColor = accent)
                .clip(shape)
                .background(fill)
                .border(BorderStroke(1.5.dp, rim), shape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            val dir = wedgeOf(offset, size.width.toFloat(), size.height.toFloat())
                            if (dir != null) {
                                pressedDir = dir
                                onDirection(dir)
                            }
                            tryAwaitRelease()
                            pressedDir = null
                        },
                    )
                }
                .semantics {
                    // A single control, but each direction is reachable as a
                    // discrete action for TalkBack users.
                    contentDescription = "$upDesc / $rightDesc / $downDesc / $leftDesc"
                    customActions = listOf(
                        CustomAccessibilityAction(upDesc) { onDirection(Direction.Up); true },
                        CustomAccessibilityAction(rightDesc) { onDirection(Direction.Right); true },
                        CustomAccessibilityAction(downDesc) { onDirection(Direction.Down); true },
                        CustomAccessibilityAction(leftDesc) { onDirection(Direction.Left); true },
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawWedgeDial(accent = accent, pressed = pressedDir, useGlow = palette.useGlow)
            }
        }
    }
}

private val DPadSize = 156.dp
private val DPadCorner = 28.dp
/** Radius of the central dead zone (a fraction of the dial's short side). */
private const val DPadDeadZoneFraction = 0.17f

/**
 * Maps a tap [offset] inside a [w] x [h] dial to the wedge it falls in, split by
 * the two diagonals (|dx| vs |dy|). Taps inside the central dead zone return null.
 */
private fun wedgeOf(offset: Offset, w: Float, h: Float): Direction? {
    val dx = offset.x - w / 2f
    val dy = offset.y - h / 2f
    if (hypot(dx, dy) < min(w, h) * DPadDeadZoneFraction) return null
    return if (abs(dx) > abs(dy)) {
        if (dx > 0) Direction.Right else Direction.Left
    } else {
        // Screen y grows downward, so a positive dy is Down.
        if (dy > 0) Direction.Down else Direction.Up
    }
}

/**
 * Draws the wedge dial: four diagonal-split wedges (the pressed one washed with
 * accent), faint dividers, a glassy top sheen, a directional chevron set out into
 * each wedge, and a central hub marking the dead zone.
 */
private fun DrawScope.drawWedgeDial(accent: Color, pressed: Direction?, useGlow: Boolean) {
    val w = size.width
    val h = size.height
    val c = Offset(w / 2f, h / 2f)
    val tl = Offset(0f, 0f)
    val tr = Offset(w, 0f)
    val br = Offset(w, h)
    val bl = Offset(0f, h)

    fun wedge(a: Offset, b: Offset): Path = Path().apply {
        moveTo(c.x, c.y)
        lineTo(a.x, a.y)
        lineTo(b.x, b.y)
        close()
    }
    val wedges = mapOf(
        Direction.Up to wedge(tl, tr),
        Direction.Right to wedge(tr, br),
        Direction.Down to wedge(br, bl),
        Direction.Left to wedge(bl, tl),
    )
    // Light up the pressed wedge.
    pressed?.let { dir ->
        drawPath(wedges.getValue(dir), color = accent.copy(alpha = if (useGlow) 0.30f else 0.20f))
    }
    // Faint diagonal dividers between the wedges.
    val divider = accent.copy(alpha = 0.22f)
    val dividerWidth = w * 0.012f
    drawLine(divider, tl, br, strokeWidth = dividerWidth)
    drawLine(divider, tr, bl, strokeWidth = dividerWidth)
    // A subtle top sheen sells the glass.
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (useGlow) 0.10f else 0.06f),
            0.5f to Color.Transparent,
            startY = 0f,
            endY = h,
        ),
    )
    // A chevron set out into each wedge, toward its edge.
    val reach = min(w, h) * 0.085f
    val outset = min(w, h) * 0.30f
    val centers = mapOf(
        Direction.Up to Offset(c.x, c.y - outset),
        Direction.Right to Offset(c.x + outset, c.y),
        Direction.Down to Offset(c.x, c.y + outset),
        Direction.Left to Offset(c.x - outset, c.y),
    )
    for (dir in Direction.entries) {
        val tint = if (dir == pressed) lighten(accent, 0.25f) else accent
        drawChevron(tint, dir, centers.getValue(dir), reach)
    }
    // Centre hub marks the dead zone.
    val hub = min(w, h) * DPadDeadZoneFraction
    drawCircle(color = accent.copy(alpha = 0.14f), radius = hub, center = c)
    drawCircle(color = accent.copy(alpha = 0.5f), radius = hub, center = c, style = Stroke(width = dividerWidth))
}

/** Draws a crisp filled chevron pointing in [direction], centred on [center]. */
private fun DrawScope.drawChevron(color: Color, direction: Direction, center: Offset, reach: Float) {
    // Across-axis half-width; the tip leads, the two wings trail.
    val wing = reach * 1.05f
    val path = Path()
    when (direction) {
        Direction.Up -> {
            path.moveTo(center.x, center.y - reach)
            path.lineTo(center.x - wing, center.y + reach * 0.7f)
            path.lineTo(center.x + wing, center.y + reach * 0.7f)
        }
        Direction.Down -> {
            path.moveTo(center.x, center.y + reach)
            path.lineTo(center.x - wing, center.y - reach * 0.7f)
            path.lineTo(center.x + wing, center.y - reach * 0.7f)
        }
        Direction.Left -> {
            path.moveTo(center.x - reach, center.y)
            path.lineTo(center.x + reach * 0.7f, center.y - wing)
            path.lineTo(center.x + reach * 0.7f, center.y + wing)
        }
        Direction.Right -> {
            path.moveTo(center.x + reach, center.y)
            path.lineTo(center.x - reach * 0.7f, center.y - wing)
            path.lineTo(center.x - reach * 0.7f, center.y + wing)
        }
    }
    path.close()
    // A rounded join softens the triangle's corners for a premium, non-jagged look.
    drawPath(path, color = color, style = Stroke(width = reach * 0.5f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawPath(path, color = color)
}

/** Linearly mixes [base] toward [other] by fraction [f] (0..1), keeping base alpha. */
private fun mix(base: Color, other: Color, f: Float): Color = Color(
    red = base.red + (other.red - base.red) * f,
    green = base.green + (other.green - base.green) * f,
    blue = base.blue + (other.blue - base.blue) * f,
    alpha = base.alpha,
)

/** Mixes [c] toward white by [f] (0..1), preserving alpha. */
private fun lighten(c: Color, f: Float): Color = Color(
    red = c.red + (1f - c.red) * f,
    green = c.green + (1f - c.green) * f,
    blue = c.blue + (1f - c.blue) * f,
    alpha = c.alpha,
)

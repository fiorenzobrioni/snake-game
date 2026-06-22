package com.brioni.snake.ui.game

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.brioni.snake.R
import com.brioni.snake.game.Direction
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

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
 * A [Modifier] for the [com.brioni.snake.game.ControlScheme.TapTurn] scheme: a tap
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
 * The two-button scheme: two large half-width buttons that turn the snake left /
 * right **relative to its heading** (Left = counter-clockwise, Right =
 * clockwise). They fill the bottom of the screen, split in half, for easy
 * thumb reach with either hand. Styled from the active [palette] so the buttons
 * belong to the current skin.
 */
@Composable
fun RelativeControls(
    onLeft: () -> Unit,
    onRight: () -> Unit,
    palette: SkinPalette,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(104.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ControlButton(
            palette = palette,
            descriptionRes = R.string.turn_left,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            onClick = onLeft,
        ) { color -> drawTurnArrow(color = color, clockwise = false) }
        ControlButton(
            palette = palette,
            descriptionRes = R.string.turn_right,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            onClick = onRight,
        ) { color -> drawTurnArrow(color = color, clockwise = true) }
    }
}

/**
 * On-screen D-pad arranged as a cross. Complements swipe steering for players who
 * prefer buttons. Arrows are drawn as crisp, perfectly centred vector chevrons on
 * a [Canvas] (no Unicode glyphs, which sat off-centre in the button box), tinted
 * from the active [palette].
 */
@Composable
fun DirectionPad(
    onDirection: (Direction) -> Unit,
    palette: SkinPalette,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DirectionButton(palette, Direction.Up, R.string.dir_up) { onDirection(Direction.Up) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            DirectionButton(palette, Direction.Left, R.string.dir_left) { onDirection(Direction.Left) }
            // A central hub block keeps the cross visually anchored.
            Spacer(modifier = Modifier.size(DPadButtonSize))
            DirectionButton(palette, Direction.Right, R.string.dir_right) { onDirection(Direction.Right) }
        }
        DirectionButton(palette, Direction.Down, R.string.dir_down) { onDirection(Direction.Down) }
    }
}

private val DPadButtonSize = 68.dp
private val ControlButtonShape = RoundedCornerShape(20.dp)
private val DPadButtonShape = RoundedCornerShape(18.dp)

@Composable
private fun DirectionButton(
    palette: SkinPalette,
    direction: Direction,
    descriptionRes: Int,
    onClick: () -> Unit,
) {
    ControlButton(
        palette = palette,
        descriptionRes = descriptionRes,
        modifier = Modifier.size(DPadButtonSize),
        shape = DPadButtonShape,
        onClick = onClick,
    ) { color -> drawDirectionArrow(color = color, direction = direction) }
}

/**
 * A premium glassy control button: a top-lit gradient fill tinted by the skin, a
 * coloured rim, a soft lift shadow, and a tactile press-scale + ripple. The icon
 * is drawn by [draw] on a full-size [Canvas] so it is always perfectly centred.
 */
@Composable
private fun ControlButton(
    palette: SkinPalette,
    descriptionRes: Int,
    modifier: Modifier = Modifier,
    shape: androidx.compose.foundation.shape.RoundedCornerShape = ControlButtonShape,
    onClick: () -> Unit,
    draw: DrawScope.(Color) -> Unit,
) {
    val description = stringResource(descriptionRes)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        label = "controlButtonScale",
    )
    val accent = palette.snakeHead
    // A dark glassy panel that takes a faint tint from the skin's snake colour,
    // lit from the top so the button reads as a raised, tactile key.
    val fill = Brush.verticalGradient(
        listOf(
            mix(palette.boardTop, accent, 0.16f),
            mix(palette.boardBottom, accent, 0.06f),
        ),
    )
    val rim = Brush.verticalGradient(
        listOf(accent.copy(alpha = 0.75f), accent.copy(alpha = 0.22f)),
    )
    // The pressed state brightens the icon and lifts the accent; glow skins push
    // the icon a touch brighter to echo their luminous identity.
    val iconColor = if (pressed) lighten(accent, 0.25f) else accent

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (pressed) 2.dp else 10.dp,
                shape = shape,
                ambientColor = accent,
                spotColor = accent,
            )
            .clip(shape)
            .background(fill)
            .border(BorderStroke(1.5.dp, rim), shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = accent),
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // A subtle top sheen sells the glass; then the icon.
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (palette.useGlow) 0.10f else 0.06f),
                    0.5f to Color.Transparent,
                    startY = 0f,
                    endY = size.height,
                ),
            )
            draw(iconColor)
        }
    }
}

/**
 * Draws a clean curved rotation arrow centred in the [DrawScope], used by the
 * relative turn buttons. [clockwise] true draws a clockwise (turn-right) arc,
 * false a counter-clockwise (turn-left) one, each capped with a tangent arrowhead.
 */
private fun DrawScope.drawTurnArrow(color: Color, clockwise: Boolean) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = min(size.width, size.height) * 0.20f
    val stroke = radius * 0.40f
    val sweep = 250f
    // Mirror the start so both arrows sit symmetrically with the gap (arrowhead)
    // toward the bottom-inner side.
    val startAngle = if (clockwise) -50f else -130f
    val effectiveSweep = if (clockwise) sweep else -sweep
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = effectiveSweep,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
    // Arrowhead at the arc's end, oriented along the direction of travel.
    val endRad = Math.toRadians((startAngle + effectiveSweep).toDouble())
    val tip = Offset(
        center.x + radius * cos(endRad).toFloat(),
        center.y + radius * sin(endRad).toFloat(),
    )
    // Tangent (direction of travel) at the tip; flips with the sweep direction.
    val travel = if (clockwise) {
        Offset((-sin(endRad)).toFloat(), cos(endRad).toFloat())
    } else {
        Offset(sin(endRad).toFloat(), (-cos(endRad)).toFloat())
    }
    val back = Offset(-travel.x, -travel.y)
    val headLen = radius * 0.95f
    drawLine(color, tip, tip + rotate(back, 32.0) * headLen, stroke, cap = StrokeCap.Round)
    drawLine(color, tip, tip + rotate(back, -32.0) * headLen, stroke, cap = StrokeCap.Round)
}

/** Draws a crisp, perfectly centred filled chevron pointing in [direction]. */
private fun DrawScope.drawDirectionArrow(color: Color, direction: Direction) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val reach = min(size.width, size.height) * 0.22f
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

/** Rotates [v] by [deg] degrees (screen space, y-down). */
private fun rotate(v: Offset, deg: Double): Offset {
    val a = Math.toRadians(deg)
    val ca = cos(a).toFloat()
    val sa = sin(a).toFloat()
    return Offset(v.x * ca - v.y * sa, v.x * sa + v.y * ca)
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

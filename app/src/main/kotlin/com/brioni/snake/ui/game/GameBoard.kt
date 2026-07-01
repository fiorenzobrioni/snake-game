package com.brioni.snake.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.brioni.snake.game.BoardDimensions
import com.brioni.snake.game.Debris
import com.brioni.snake.game.Direction
import com.brioni.snake.game.EffectKind
import com.brioni.snake.game.Food
import com.brioni.snake.game.FoodCategory
import com.brioni.snake.game.FoodEffect
import com.brioni.snake.game.FoodTier
import com.brioni.snake.game.GameState
import com.brioni.snake.game.Position
import com.brioni.snake.game.isHazard
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Draws the board on a Compose [Canvas]. The grid logic stays in the model;
 * this renders state with Phase 2 polish:
 *  - smooth inter-tick motion (interpolating each segment from [previousSnake]
 *    to the current snake over one tick),
 *  - a gradient background, bevelled obstacles, pulsing food and a glowing,
 *    eyed snake head,
 *  - a particle burst on eat.
 *
 * The board is scaled to fit and centred while keeping its cell aspect ratio.
 */

/** Final window of a Ghost (Star) effect over which the warning blink ramps up. */
private const val GHOST_WARN_MS = 2_000f

/** Universal danger red for the hazard telegraph (skin-independent so it always reads as "danger"). */
private val HazardWarnColor = Color(0xFFFF1E1E)

/** Mixes [c] toward white by [f] (0..1), preserving alpha — for highlights/sheen. */
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

/**
 * A static, in-game-accurate snake emblem (used under the menu wordmark). It
 * draws through the exact same [drawSnake] renderer as gameplay - the tapered,
 * shaded tube with a glossy glowing head for glow skins, two-tone blocky segments
 * for flat skins - so it reflects the selected [palette] faithfully. It is drawn
 * straight and still (no slither), head leading on the right, and the chain fills
 * the given width so callers can match it to the title's width.
 */
@Composable
fun SnakeEmblem(palette: SkinPalette, modifier: Modifier = Modifier) {
    val shaders = remember { BoardShaders() }
    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        // Cell size from the height, leaving headroom so the head glow / drop
        // shadow can bleed softly (the Canvas draw is not clipped to its bounds).
        val cell = size.height * 0.72f
        if (cell <= 0f) return@Canvas
        val cy = size.height / 2f
        val step = cell // one cell of spacing between centres, like adjacent board cells
        val span = size.width - cell // small inset so head/tail sit inside the width
        val n = (span / step).toInt().coerceAtLeast(1) + 1
        val totalWidth = step * (n - 1)
        val startX = (size.width - totalWidth) / 2f
        // Head is index 0 (rightmost); increasing index walks left toward the tail.
        val centers = ArrayList<Offset>(n)
        for (i in 0 until n) {
            centers.add(Offset(startX + step * (n - 1 - i), cy))
        }
        drawSnake(
            centers = centers,
            cell = cell,
            direction = Direction.Right,
            palette = palette,
            bodyAlpha = 1f,
            headAlpha = 1f,
            shaders = shaders,
            time = 0f,
            headGlow = palette.headGlow,
        )
    }
}

@Composable
fun GameBoard(
    state: GameState,
    previousSnake: List<Position>,
    tickTimeNanos: Long,
    tickMillis: Long,
    running: Boolean,
    eatEvent: EatEvent?,
    eatEventId: Int,
    floatingText: FloatingTextEvent?,
    floatingTextId: Int,
    hazardWarn: HazardWarnEvent?,
    hazardWarnId: Int,
    teleportEvent: TeleportEvent?,
    teleportEventId: Int,
    bodyBurst: BodyBurstEvent?,
    bodyBurstId: Int,
    textMeasurer: TextMeasurer,
    palette: SkinPalette,
    borderColor: Color = palette.boardBorder,
    outsideColor: Color = Color.Black,
    reduceMotion: Boolean = false,
    // Keeps the particle/redraw loop alive across the brief death-burst and
    // level-vanish transitions, after `running` has already gone false.
    effectsActive: Boolean = running,
    modifier: Modifier = Modifier,
) {
    val particles: SnapshotStateList<Particle> = remember { emptyList<Particle>().toMutableStateList() }
    val floatingTexts: SnapshotStateList<FloatingText> = remember { emptyList<FloatingText>().toMutableStateList() }
    // Levels mode: the playable-area outline (in cell units) that replaces the
    // rectangular frame when the board has a designed wall shape.
    val boundary = remember(state.walls, state.board) { boundaryEdges(state.walls, state.board) }
    // AGSL effects (always available — minSdk is 33), created once.
    val shaders = remember { BoardShaders() }
    // A monotonic per-frame timestamp (same clock as tickTimeNanos) that pulses
    // a redraw each frame; the interpolation fraction is *derived* from it and
    // tickTimeNanos at draw time, so it can never lag a committed move.
    var frameNanos by remember { mutableLongStateOf(System.nanoTime()) }

    // Hazard telegraph: a 1→0 envelope driving the danger flash over the piece
    // the snake is about to eat. Restarted on each new warning; suppressed under
    // reduce-motion (the pre-haptic still fires from the ViewModel).
    val hazardPulse = remember { Animatable(0f) }
    LaunchedEffect(hazardWarnId) {
        if (hazardWarnId > 0 && hazardWarn != null && !reduceMotion) {
            hazardPulse.snapTo(1f)
            hazardPulse.animateTo(0f, tween(durationMillis = 460, easing = FastOutLinearInEasing))
        }
    }

    // Snake dissolve envelope (1 = solid, 0 = gone): driven by a whole-snake burst
    // so the body fades out as it explodes (death) or teleports away (level-up).
    // Stays at 1 during normal play, and is reset to 1 whenever effects stop.
    val dissolve = remember { Animatable(1f) }

    // A single frame-driven loop runs while the game is running *or* a transition
    // burst is still playing: it advances the particles and writes [frameNanos] to
    // force a redraw each frame. When neither is active it clears leftover
    // particles and resets the dissolve so the next snake draws solid.
    LaunchedEffect(effectsActive) {
        if (!effectsActive) {
            particles.clear()
            floatingTexts.clear()
            dissolve.snapTo(1f)
            return@LaunchedEffect
        }
        var lastNanos = System.nanoTime()
        while (true) {
            withFrameNanos { }
            val now = System.nanoTime()
            val dt = ((now - lastNanos) / 1_000_000_000.0).toFloat()
            lastNanos = now
            updateParticles(particles, dt)
            updateFloatingTexts(floatingTexts, dt)
            frameNanos = now
        }
    }

    // Whole-snake burst: spray a per-cell burst head-to-tail and fade the body out
    // over the matching window. Death uses the fiery explosion; a level-up uses the
    // gentler vanish/teleport sparkle. Suppressed under reduce-motion.
    //
    // [handledBurst] is initialised to the *current* id so the first composition of a
    // fresh GameBoard (e.g. Menu -> Game) is a no-op: without it, a leftover burst from
    // the previous run would replay on the new snake and leave [dissolve] stuck at 0
    // (an invisible body, only the eyes moving).
    val handledBurst = remember { mutableIntStateOf(bodyBurstId) }
    LaunchedEffect(bodyBurstId) {
        val event = bodyBurst
        if (bodyBurstId != handledBurst.intValue && event != null && !reduceMotion && event.cells.isNotEmpty()) {
            handledBurst.intValue = bodyBurstId
            // Kept just under the ViewModel's hold (DEATH_ANIM_MS / LEVEL_VANISH_MS, both
            // ~1000 ms) so the body has fully faded by the time the overlay / countdown
            // takes over. [blast] still selects the burst flavour below.
            val blast = event.style == BurstStyle.Blast
            val durationMs = 900
            dissolve.snapTo(1f)
            // Fade the body out alongside the staggered bursts.
            launch { dissolve.animateTo(0f, tween(durationMillis = durationMs, easing = FastOutLinearInEasing)) }
            // Spread the emissions over the first ~60% of the window, head first.
            val stepDelay = ((durationMs * 0.6f) / event.cells.size).toLong().coerceIn(5L, 36L)
            for (cell in event.cells) {
                val cx = cell.x + 0.5f
                val cy = cell.y + 0.5f
                if (blast) {
                    emitExplosionBurst(particles, cx, cy, palette.headGlow, 1)
                } else {
                    emitVanishBurst(particles, cx, cy, palette.snakeBody, 1)
                    emitEatBurst(particles, cx, cy, palette.headGlow, 1)
                }
                delay(stepDelay)
            }
        }
    }

    // Eat "pop": an expanding shockwave ring on each eat / blast, layered under
    // the particle spray. A 0→1 envelope keyed on the eat id, faded as it grows.
    val eatRing = remember { Animatable(0f) }
    LaunchedEffect(eatEventId) {
        val event = eatEvent
        // Reduce-motion suppresses the particle bursts (the floating "+N" labels stay).
        if (eatEventId > 0 && event != null && !reduceMotion) {
            val cx = event.cell.x + event.span / 2f
            val cy = event.cell.y + event.span / 2f
            when (event.style) {
                BurstStyle.Eat -> emitEatBurst(particles, cx, cy, event.color, event.span, event.combo)
                BurstStyle.Implode -> emitImplodeBurst(particles, cx, cy, event.color, event.span)
                BurstStyle.Vanish -> emitVanishBurst(particles, cx, cy, event.color, event.span)
                BurstStyle.Blast -> emitExplosionBurst(particles, cx, cy, event.color, event.span)
            }
            // Always reset first: a previous ring animation may have just been
            // cancelled mid-flight by this very event (e.g. an eat immediately
            // followed by a food vanishing). Without this reset the cancelled ring
            // would stay frozen at a mid value and linger on the board as a stray
            // circle. Only the non-vanish styles then play the expanding ring.
            eatRing.snapTo(0f)
            if (event.style != BurstStyle.Vanish) {
                eatRing.animateTo(1f, tween(durationMillis = if (event.style == BurstStyle.Blast) 520 else 360, easing = FastOutLinearInEasing))
            }
        }
    }

    // Portal jump: an implosion at the entry pad and an outward burst at the exit,
    // in that pair's portal colour. Suppressed under reduce-motion.
    LaunchedEffect(teleportEventId) {
        val event = teleportEvent
        if (teleportEventId > 0 && event != null && !reduceMotion) {
            // Burst in the colour of the pair that fired (fall back to the first).
            val index = state.teleports.indexOfFirst { event.from in it.cells || event.to in it.cells }
            val color = portalColor(index.coerceAtLeast(0))
            emitImplodeBurst(particles, event.from.x + 0.5f, event.from.y + 0.5f, color, 1)
            emitEatBurst(particles, event.to.x + 0.5f, event.to.y + 0.5f, color, 1)
        }
    }

    LaunchedEffect(floatingTextId) {
        val event = floatingText
        if (floatingTextId > 0 && event != null) {
            emitFloatingText(
                floatingTexts,
                event.cell.x + event.span / 2f,
                event.cell.y + event.span / 2f,
                event.text,
                event.color,
            )
        }
    }

    Canvas(modifier = modifier) {
        val board = state.board
        // Reserve a margin large enough that the framing border (a stroke centred
        // on the board edge) is never clipped — otherwise its outer half can slip
        // off-screen at the bottom when the board fills the play area. Two-pass:
        // size with a base margin, then grow the margin to cover that border's
        // half-width and re-fit.
        fun fit(margin: Float): Float =
            min((size.width - 2 * margin) / board.width, (size.height - 2 * margin) / board.height)

        val baseMargin = 6.dp.toPx()
        val probe = fit(baseMargin)
        val borderHalf = (probe * 0.12f).coerceAtLeast(2f) / 2f
        val margin = maxOf(baseMargin, borderHalf + 1f)
        val cell = fit(margin)
        if (cell <= 0f) return@Canvas

        // Derive everything from the per-frame clock and the tick snapshot, so
        // reading `frameNanos` both redraws each frame and stays consistent with
        // the committed state (no separate, potentially-stale fraction state).
        val now = frameNanos
        val periodNanos = (tickMillis.coerceAtLeast(1L) * 1_000_000L).toFloat()
        val f = if (running) ((now - tickTimeNanos).toFloat() / periodNanos).coerceIn(0f, 1f) else 1f
        val seconds = now / 1_000_000_000.0
        val pulse = if (running) 0.9f + 0.1f * (sin(seconds * 6.0).toFloat() * 0.5f + 0.5f) else 1f
        // Wrapped time keeps float precision stable across a long session.
        val time = (seconds % 1_000.0).toFloat()

        val boardWidth = cell * board.width
        val boardHeight = cell * board.height
        val originX = (size.width - boardWidth) / 2f
        val originY = (size.height - boardHeight) / 2f

        // Shared snake shimmer: Ghost (Star) blinks faster toward expiry.
        val ghostEffect = state.effectTimers.firstOrNull { it.kind == EffectKind.Ghost }
        val snakeAlpha = if (ghostEffect != null) {
            val warnT = (1f - ghostEffect.remainingMs.toFloat() / GHOST_WARN_MS).coerceIn(0f, 1f)
            val hz = 9f + warnT * 26f
            val amp = 0.15f + warnT * 0.45f
            val base = 0.5f - warnT * 0.30f
            (base + amp * (sin(seconds * hz).toFloat() * 0.5f + 0.5f)).coerceIn(0.1f, 1f)
        } else {
            1f
        }

        drawBoardBackground(board, cell, originX, originY, boardWidth, boardHeight, palette, shaders, time)

        // Clip dynamic content to the board so the head can slide "into" a wall
        // on the fatal tick without painting over the HUD.
        clipRect(originX, originY, originX + boardWidth, originY + boardHeight) {
            // Levels mode: wall cells read as "outside the board" — they wear
            // the screen background, and the shaped frame is drawn on top later.
            state.walls.forEach { wall ->
                drawRect(
                    color = outsideColor,
                    topLeft = Offset(originX + wall.x * cell, originY + wall.y * cell),
                    size = Size(cell + 0.5f, cell + 0.5f), // overdraw to hide seams
                )
            }
            state.obstacles.forEach { obstacle ->
                drawObstacle(cell, originX + obstacle.x * cell, originY + obstacle.y * cell, palette)
            }
            drawDebris(state.debris, cell, originX, originY, palette)
            // Levels (Step 6.9.7): teleport portals and moving-wall gates, painted
            // under the snake so it reads as sliding into a barrier / through a portal.
            drawTeleports(state, seconds, cell, originX, originY, reduceMotion)
            // Paused/intro: f is pinned to 1; feed gates 0 so a transitioning gate
            // shows its true current open/closed state rather than mid-morph.
            drawGates(state, state.elapsedTicks, if (running) f else 0f, seconds, cell, originX, originY, reduceMotion)
            state.foods.forEach { food ->
                drawFood(food, cell, originX, originY, pulse, textMeasurer, palette, shaders, time)
            }
            // Ghost (Star): the snake shimmers while invincible (snakeAlpha is
            // computed once above). The interpolated cell centres feed both the
            // smooth-tube renderer (rounded skins) and the blocky one (flat skins).
            val snake = state.snake
            val centers = ArrayList<Offset>(snake.size)
            for (k in snake.indices) {
                val to = snake[k]
                val from = if (previousSnake.isEmpty()) to else previousSnake[k.coerceAtMost(previousSnake.lastIndex)]
                val cx = lerp(from.x.toFloat(), to.x.toFloat(), f)
                val cy = lerp(from.y.toFloat(), to.y.toFloat(), f)
                centers.add(Offset(originX + (cx + 0.5f) * cell, originY + (cy + 0.5f) * cell))
            }
            // Fold in the dissolve envelope so the body fades as it bursts apart on
            // death / vanishes on a level-up (1f during normal play = no change).
            val fade = dissolve.value
            drawSnake(
                centers = centers,
                cell = cell,
                direction = state.direction,
                palette = palette,
                bodyAlpha = snakeAlpha * fade,
                headAlpha = (snakeAlpha + 0.2f).coerceAtMost(1f) * fade,
                shaders = shaders,
                time = time,
                headGlow = hotGlow(palette.headGlow, state.combo),
            )
            // A lighter portal pass over the snake: where the body lies on a pad it
            // shows through as half-transparent, selling that the snake phases through.
            drawTeleports(state, seconds, cell, originX, originY, reduceMotion, overlay = true)
            // Hazard telegraph: a danger flash over the piece the snake is about
            // to eat (one tick before contact). Suppressed under reduce-motion -
            // the envelope simply stays at 0 there.
            val warnT = hazardPulse.value
            if (warnT > 0.001f && hazardWarn != null) {
                drawHazardWarning(
                    left = originX + hazardWarn.cell.x * cell,
                    top = originY + hazardWarn.cell.y * cell,
                    side = cell * hazardWarn.span,
                    cell = cell,
                    envelope = warnT,
                    seconds = seconds,
                )
            }
            // Eat "pop": an expanding shockwave ring under the spray.
            val ringT = eatRing.value
            if (ringT in 0.001f..0.999f && eatEvent != null && !reduceMotion) {
                val ev = eatEvent
                val rc = Offset(originX + (ev.cell.x + ev.span / 2f) * cell, originY + (ev.cell.y + ev.span / 2f) * cell)
                val big = ev.style == BurstStyle.Blast
                val maxR = cell * (if (big) 3.4f else 1.7f) * ev.span.coerceAtLeast(1)
                val r = maxR * ringT
                val a = (1f - ringT) * (if (big) 0.9f else 0.6f)
                drawCircle(
                    color = lighten(ev.color, 0.3f).copy(alpha = a),
                    radius = r,
                    center = rc,
                    style = Stroke(width = (cell * (if (big) 0.16f else 0.10f)) * (1f - ringT) + 1f),
                )
            }
            particles.forEach { p ->
                val center = Offset(originX + p.x * cell, originY + p.y * cell)
                // Shrink a touch as it dies; glowing sparks get a soft additive halo
                // and a hot inner core for a punchier, more "premium" read.
                val r = p.radiusCells * cell * (0.55f + 0.45f * p.fade)
                if (p.glow) {
                    drawCircle(color = p.color.copy(alpha = 0.22f * p.fade), radius = r * 2.4f, center = center)
                }
                drawCircle(color = p.color.copy(alpha = (0.9f * p.fade).coerceAtMost(1f)), radius = r, center = center)
                if (p.glow) {
                    drawCircle(color = Color.White.copy(alpha = 0.5f * p.fade * p.fade), radius = r * 0.42f, center = center)
                }
            }
            floatingTexts.forEach { t ->
                // Fade out and ease the rise; a dark outline keeps it readable on
                // any skin. Drawn over everything inside the board clip.
                val alpha = t.fade
                val centerX = originX + t.x * cell
                val centerY = originY + t.y * cell
                val layout = textMeasurer.measure(
                    text = t.text,
                    style = TextStyle(
                        color = t.color.copy(alpha = alpha),
                        fontSize = (cell * 0.95f).toSp(),
                        fontWeight = FontWeight.Black,
                    ),
                )
                val shadow = textMeasurer.measure(
                    text = t.text,
                    style = TextStyle(
                        color = Color.Black.copy(alpha = 0.45f * alpha),
                        fontSize = (cell * 0.95f).toSp(),
                        fontWeight = FontWeight.Black,
                    ),
                )
                val left = centerX - layout.size.width / 2f
                val top = centerY - layout.size.height / 2f
                drawText(shadow, topLeft = Offset(left + cell * 0.04f, top + cell * 0.04f))
                drawText(layout, topLeft = Offset(left, top))
            }
            // Freeze: a cool frost vignette over the board while the effect runs.
            if (state.hasEffect(EffectKind.Freeze)) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, SpecialVisuals.FreezeColor.copy(alpha = 0.18f)),
                        center = Offset(originX + boardWidth / 2f, originY + boardHeight / 2f),
                        radius = maxOf(boardWidth, boardHeight) * 0.72f,
                    ),
                    topLeft = Offset(originX, originY),
                    size = Size(boardWidth, boardHeight),
                )
            }
        }

        // Framing border painted on top. With a shaped board (Levels mode) the
        // frame follows the playable area's outline instead of the rectangle.
        val borderWidth = (cell * 0.12f).coerceAtLeast(2f)
        if (boundary.isEmpty()) {
            drawRect(
                color = borderColor,
                topLeft = Offset(originX, originY),
                size = Size(boardWidth, boardHeight),
                style = Stroke(width = borderWidth),
            )
        } else {
            boundary.forEach { e ->
                drawLine(
                    color = borderColor,
                    start = Offset(originX + e.x1 * cell, originY + e.y1 * cell),
                    end = Offset(originX + e.x2 * cell, originY + e.y2 * cell),
                    strokeWidth = borderWidth,
                    cap = StrokeCap.Square, // square caps close the corner joints
                )
            }
        }
    }
}

/** One edge (in cell units) between a playable cell and a wall / out-of-board. */
private data class BoundaryEdge(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
)

/**
 * The outline of the playable area for a shaped (Levels mode) board: every edge
 * separating a playable cell from a wall cell or the outside. Empty when the
 * board has no walls, selecting the plain rectangular frame instead.
 */
private fun boundaryEdges(walls: Set<Position>, board: BoardDimensions): List<BoundaryEdge> {
    if (walls.isEmpty()) return emptyList()
    fun blocked(x: Int, y: Int): Boolean =
        x < 0 || x >= board.width || y < 0 || y >= board.height || Position(x, y) in walls
    val edges = ArrayList<BoundaryEdge>()
    for (y in 0 until board.height) {
        for (x in 0 until board.width) {
            if (Position(x, y) in walls) continue
            if (blocked(x - 1, y)) edges.add(BoundaryEdge(x.toFloat(), y.toFloat(), x.toFloat(), y + 1f))
            if (blocked(x + 1, y)) edges.add(BoundaryEdge(x + 1f, y.toFloat(), x + 1f, y + 1f))
            if (blocked(x, y - 1)) edges.add(BoundaryEdge(x.toFloat(), y.toFloat(), x + 1f, y.toFloat()))
            if (blocked(x, y + 1)) edges.add(BoundaryEdge(x.toFloat(), y + 1f, x + 1f, y + 1f))
        }
    }
    return edges
}

private fun DrawScope.drawBoardBackground(
    board: BoardDimensions,
    cell: Float,
    originX: Float,
    originY: Float,
    boardWidth: Float,
    boardHeight: Float,
    palette: SkinPalette,
    shaders: BoardShaders,
    time: Float,
) {
    // AGSL: the gradient brought to life with drifting glows + vignette.
    shaders.background.setFloatUniform("origin", originX, originY)
    shaders.background.setFloatUniform("resolution", boardWidth, boardHeight)
    shaders.background.setFloatUniform("time", time)
    drawRect(
        brush = shaders.backgroundBrush,
        topLeft = Offset(originX, originY),
        size = Size(boardWidth, boardHeight),
    )

    if (cell < 10f) return // skip the grid on dense boards where it is just noise
    for (x in 0..board.width) {
        val lineX = originX + x * cell
        drawLine(palette.gridLine, Offset(lineX, originY), Offset(lineX, originY + boardHeight), 1f)
    }
    for (y in 0..board.height) {
        val lineY = originY + y * cell
        drawLine(palette.gridLine, Offset(originX, lineY), Offset(originX + boardWidth, lineY), 1f)
    }
}

private fun DrawScope.drawObstacle(cell: Float, left: Float, top: Float, palette: SkinPalette) {
    val inset = cell * 0.08f
    val side = cell - 2 * inset
    val corner = cell * palette.cornerFactor.coerceAtMost(0.4f)
    val radius = CornerRadius(corner, corner)
    drawRoundRect(
        color = palette.obstacleShadow,
        topLeft = Offset(left + inset, top + inset + cell * 0.04f),
        size = Size(side, side),
        cornerRadius = radius,
    )
    drawRoundRect(
        color = palette.obstacle,
        topLeft = Offset(left + inset, top + inset),
        size = Size(side, side),
        cornerRadius = radius,
    )
    // Top bevel highlight.
    drawRoundRect(
        color = palette.obstacleHighlight.copy(alpha = 0.6f),
        topLeft = Offset(left + inset + side * 0.18f, top + inset + side * 0.16f),
        size = Size(side * 0.64f, side * 0.22f),
        cornerRadius = CornerRadius(cell * 0.12f, cell * 0.12f),
    )
}

/**
 * The hazard telegraph drawn over the [side]-wide cell square at ([left], [top])
 * the tick before the snake would eat a hazard. [envelope] is a 1→0 fade; it
 * layers a steady danger highlight, a strobing alarm border and an outward
 * "radar ping" ring that expands as the warning subsides. Universally red so it
 * reads as danger on every skin.
 */
private fun DrawScope.drawHazardWarning(
    left: Float,
    top: Float,
    side: Float,
    cell: Float,
    envelope: Float,
    seconds: Double,
) {
    val corner = CornerRadius(cell * 0.28f, cell * 0.28f)
    // Steady highlight over the cells: "the danger is here".
    drawRoundRect(
        color = HazardWarnColor.copy(alpha = 0.30f * envelope),
        topLeft = Offset(left, top),
        size = Size(side, side),
        cornerRadius = corner,
    )
    // Strobing alarm border hugging the square.
    val blink = 0.55f + 0.45f * sin(seconds * 30.0).toFloat()
    drawRoundRect(
        color = HazardWarnColor.copy(alpha = (0.9f * envelope * blink).coerceIn(0f, 1f)),
        topLeft = Offset(left, top),
        size = Size(side, side),
        cornerRadius = corner,
        style = Stroke(width = (cell * 0.12f).coerceAtLeast(2f)),
    )
    // Outward ping ring that expands as the warning fades.
    val pad = cell * (0.12f + 0.85f * (1f - envelope))
    drawRoundRect(
        color = HazardWarnColor.copy(alpha = 0.85f * envelope),
        topLeft = Offset(left - pad, top - pad),
        size = Size(side + 2 * pad, side + 2 * pad),
        cornerRadius = CornerRadius(corner.x + pad, corner.y + pad),
        style = Stroke(width = (cell * 0.09f).coerceAtLeast(1.5f)),
    )
}

private fun DrawScope.drawFood(
    food: Food,
    cell: Float,
    originX: Float,
    originY: Float,
    pulse: Float,
    textMeasurer: TextMeasurer,
    palette: SkinPalette,
    shaders: BoardShaders,
    time: Float,
) {
    if (food.category == FoodCategory.Special) {
        drawSpecialFood(food, cell, originX, originY, pulse, textMeasurer, palette)
        return
    }
    val color = palette.foodColor(food)
    val extent = cell * food.span
    val centerX = originX + food.position.x * cell + extent / 2f
    val centerY = originY + food.position.y * cell + extent / 2f
    val radius = extent * 0.42f * pulse

    // "Rare" pieces (maxi / mystery / huge) get a halo; with AGSL it pulses.
    // Flat skins (useGlow == false) skip the halo for a crisp look.
    val rare = palette.useGlow && (food.span >= 2 || food.isMystery || food.tier == FoodTier.Huge)
    val haloRadius = radius * 1.9f
    if (rare) {
        shaders.foodHalo.setFloatUniform("center", centerX, centerY)
        shaders.foodHalo.setFloatUniform("radius", haloRadius)
        shaders.foodHalo.setFloatUniform("time", time)
        shaders.foodHalo.setColorUniform("ringColor", color.toArgb())
        drawCircle(brush = shaders.foodHaloBrush, radius = haloRadius, center = Offset(centerX, centerY))
    }
    // Volume: a radial gradient lit from the top-left, with a darker rim. Flat
    // skins are grounded with a soft drop shadow (glow skins lift via the halo).
    val highlight = lighten(color, 0.34f)
    val shade = darken(color, 0.26f)
    val gradient = Brush.radialGradient(
        colors = listOf(highlight, color, shade),
        center = Offset(centerX - radius * 0.3f, centerY - radius * 0.34f),
        radius = radius * 1.5f,
    )
    // Flat skins (Retro / Pixel) draw rounded-square food matching their blocky
    // items - corner from the skin's cornerFactor (0 = crisp Pixel, lightly
    // rounded Retro); glow skins (Classic / Neon) keep the round drop. The drop
    // shadow that grounds flat-skin food follows the same square shape.
    if (!palette.useGlow) {
        val tl = Offset(centerX - radius, centerY - radius)
        val sz = Size(radius * 2f, radius * 2f)
        val cr = radius * 2f * palette.cornerFactor
        val rad = CornerRadius(cr, cr)
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.22f),
            topLeft = Offset(tl.x + radius * 0.12f, tl.y + radius * 0.3f),
            size = sz,
            cornerRadius = rad,
        )
        drawRoundRect(brush = gradient, topLeft = tl, size = sz, cornerRadius = rad)
        drawRoundRect(color = shade.copy(alpha = 0.6f), topLeft = tl, size = sz, cornerRadius = rad, style = Stroke(width = radius * 0.1f))
    } else {
        drawCircle(brush = gradient, radius = radius, center = Offset(centerX, centerY))
        drawCircle(color = shade.copy(alpha = 0.5f), radius = radius, center = Offset(centerX, centerY), style = Stroke(width = radius * 0.08f))
    }

    when {
        // Mystery pieces hide their amount behind a "?" in the snake's palette.
        food.isMystery -> drawGlyph(textMeasurer, "?", centerX, centerY, radius * 1.25f, Color.White)
        // Shrink food reads as "hollow" with a darker inner ring.
        food.category == FoodCategory.Shrink -> {
            drawCircle(
                color = Color.Black.copy(alpha = 0.28f),
                radius = radius * 0.5f,
                center = Offset(centerX, centerY),
                style = Stroke(width = radius * 0.18f),
            )
        }
        else -> Unit
    }

    // Glossy shine (skipped under the glyph so it stays crisp).
    if (!food.isMystery) {
        drawCircle(
            color = Color.White.copy(alpha = 0.30f),
            radius = radius * 0.30f,
            center = Offset(centerX - radius * 0.30f, centerY - radius * 0.30f),
        )
    }
}

/**
 * Draws a Phase 6.2 special via the shared premium [drawSpecialToken]: a coined,
 * bevelled maxi piece in the power-up's universal accent colour (so a colour
 * always means the same effect across skins) wearing the current skin's material
 * ([SkinPalette.specialStyle]), an embossed symbol, and a notched danger bezel
 * for hazards. The same renderer backs the first-run tutorial, so the piece is
 * identical wherever a player meets it.
 */
private fun DrawScope.drawSpecialFood(
    food: Food,
    cell: Float,
    originX: Float,
    originY: Float,
    pulse: Float,
    textMeasurer: TextMeasurer,
    palette: SkinPalette,
) {
    val extent = cell * food.span
    val centerX = originX + food.position.x * cell + extent / 2f
    val centerY = originY + food.position.y * cell + extent / 2f
    val radius = extent * 0.33f * pulse
    drawSpecialToken(centerX, centerY, radius, food.effect, palette, textMeasurer, pulse)
}

/**
 * The severed tail left behind by an Explosion, drawn with the *exact* live-snake
 * graphics in the current skin so it reads as a piece that genuinely detached -
 * the same shaded tube (rounded skins) or blocky segments (flat skins), the same
 * colours and the same trunk-to-tip taper, never a stray pellet. Only the head is
 * omitted (it is a tail) and the whole run fades out as its lethal timer expires.
 *
 * The severed tail keeps its original ordering, so contiguous debris cells are
 * grouped into chains and each chain is rendered as one continuous tapering body.
 */
private fun DrawScope.drawDebris(
    debris: List<Debris>,
    cell: Float,
    originX: Float,
    originY: Float,
    palette: SkinPalette,
) {
    if (debris.isEmpty()) return
    // Split the (ordered) debris into runs of orthogonally-adjacent cells; each
    // run is one severed tail and is drawn as a single continuous body.
    val chains = ArrayList<List<Debris>>()
    var current = ArrayList<Debris>()
    for (d in debris) {
        val last = current.lastOrNull()
        if (last == null || isAdjacentCell(last.cell, d.cell)) {
            current.add(d)
        } else {
            chains.add(current)
            current = arrayListOf(d)
        }
    }
    if (current.isNotEmpty()) chains.add(current)

    for (chain in chains) {
        val centers = chain.map { d ->
            Offset(originX + (d.cell.x + 0.5f) * cell, originY + (d.cell.y + 0.5f) * cell)
        }
        // Cells severed together share a timer, so the chain fades as one piece.
        val life = chain.minOf { it.life }
        val alpha = (0.32f + 0.6f * life).coerceIn(0f, 1f)
        if (palette.segmentedBody) {
            drawSnakeBlocks(centers, cell, palette, alpha)
        } else {
            drawSnakeTube(centers, cell, palette, alpha)
        }
    }
}

/** Orthogonally adjacent (4-neighbour) cells - used to chain a severed tail. */
private fun isAdjacentCell(a: Position, b: Position): Boolean =
    abs(a.x - b.x) + abs(a.y - b.y) == 1

/** A fiery accent the head glow blends toward as the combo climbs. */
private val ComboHotGlow = Color(0xFFFF5722)

/**
 * Heats the head [base] glow toward [ComboHotGlow] as the eat-[combo] climbs:
 * unchanged up to x2, ramping to fully fiery around x8 - the "on fire" cue.
 */
private fun hotGlow(base: Color, combo: Int): Color {
    val heat = ((combo - 2) / 6f).coerceIn(0f, 1f)
    if (heat <= 0f) return base
    return Color(
        red = base.red + (ComboHotGlow.red - base.red) * heat,
        green = base.green + (ComboHotGlow.green - base.green) * heat,
        blue = base.blue + (ComboHotGlow.blue - base.blue) * heat,
        alpha = base.alpha,
    )
}

// Snake body width (fraction of a cell) at the trunk, the tail tip, and how many
// trailing segments taper down to the tip.
private const val SNAKE_BODY_MAX = 0.84f
private const val SNAKE_TAIL_MIN = 0.34f
private const val SNAKE_TAIL_SEGMENTS = 4f

/** Trunk-to-tail body width (px) for tube segment [i] of [n]. */
private fun snakeWidth(i: Int, n: Int, cell: Float): Float {
    val taper = ((n - 1 - i) / SNAKE_TAIL_SEGMENTS).coerceIn(0f, 1f)
    return cell * (SNAKE_TAIL_MIN + (SNAKE_BODY_MAX - SNAKE_TAIL_MIN) * taper)
}

/** Trunk-to-tail block side (px) for blocky-skin segment [i] of [n]. */
private fun blockSide(i: Int, n: Int, cell: Float): Float {
    val taper = ((n - 1 - i) / SNAKE_TAIL_SEGMENTS).coerceIn(0f, 1f)
    return cell * 0.9f * (0.5f + 0.5f * taper)
}

/**
 * Draws the whole snake from interpolated cell [centers] (head = index 0). Tube
 * skins get a smooth, shaded, **tapered tube** with a glossy head; segmented skins
 * ([SkinPalette.segmentedBody]) keep crisp blocky segments. The head is drawn last,
 * on top. The head-glow halo is independent ([SkinPalette.useGlow]).
 */
private fun DrawScope.drawSnake(
    centers: List<Offset>,
    cell: Float,
    direction: Direction,
    palette: SkinPalette,
    bodyAlpha: Float,
    headAlpha: Float,
    shaders: BoardShaders,
    time: Float,
    headGlow: Color,
) {
    if (centers.isEmpty()) return
    val head = centers.first()
    // Head glow halo first, so it sits beneath the body/head (rounded skins only).
    if (palette.useGlow) {
        val glowRadius = cell * 1.15f
        shaders.glow.setFloatUniform("center", head.x, head.y)
        shaders.glow.setFloatUniform("radius", glowRadius)
        shaders.glow.setFloatUniform("time", time)
        shaders.glow.setColorUniform("glowColor", headGlow.toArgb())
        drawCircle(brush = shaders.glowBrush, radius = glowRadius, center = head)
    }
    if (palette.segmentedBody) {
        drawSnakeBlocks(centers, cell, palette, bodyAlpha)
        drawBlockHead(head, cell, direction, palette, headAlpha)
    } else {
        drawSnakeTube(centers, cell, palette, bodyAlpha)
        drawRoundHead(head, cell, direction, palette, headAlpha)
    }
}

/**
 * The rounded-skin body: a continuous, seam-free tube built from round-capped
 * capsules with tapering width, layered as drop shadow → outline → fill → a
 * centre sheen so it reads as a shaded cylinder.
 */
private fun DrawScope.drawSnakeTube(
    centers: List<Offset>,
    cell: Float,
    palette: SkinPalette,
    alpha: Float,
) {
    val n = centers.size
    val outline = cell * 0.055f
    val outlineColor = palette.snakeOutline.copy(alpha = alpha)
    val bodyColor = palette.snakeBody.copy(alpha = alpha)
    val sheenColor = lighten(palette.snakeBody, 0.26f).copy(alpha = 0.5f * alpha)
    val shadowColor = Color.Black.copy(alpha = 0.22f * alpha)

    if (n == 1) {
        val r = snakeWidth(0, n, cell) / 2f
        drawCircle(outlineColor, r + outline, centers[0])
        drawCircle(bodyColor, r, centers[0])
        return
    }

    // joints fill the small notch on the outer side of each bend; only the opaque
    // outline/fill passes need them (the shadow and sheen passes can skip them).
    fun pass(color: Color, offset: Offset, joints: Boolean, widthOf: (Int) -> Float) {
        for (i in 0 until n - 1) {
            drawLine(color, centers[i] + offset, centers[i + 1] + offset, strokeWidth = widthOf(i).coerceAtLeast(1f), cap = StrokeCap.Round)
        }
        if (joints) {
            for (i in 0 until n) {
                val r = widthOf(i) / 2f
                if (r > 0.4f) drawCircle(color, r, centers[i] + offset)
            }
        }
    }

    pass(shadowColor, Offset(cell * 0.05f, cell * 0.09f), joints = false) { snakeWidth(it, n, cell) + 2 * outline }
    pass(outlineColor, Offset.Zero, joints = true) { snakeWidth(it, n, cell) + 2 * outline }
    pass(bodyColor, Offset.Zero, joints = true) { snakeWidth(it, n, cell) }
    pass(sheenColor, Offset(0f, -cell * 0.07f), joints = false) { snakeWidth(it, n, cell) * 0.45f }
}

/** The rounded-skin head: a glossy disc with a top sheen and direction-aware eyes. */
private fun DrawScope.drawRoundHead(
    center: Offset,
    cell: Float,
    direction: Direction,
    palette: SkinPalette,
    alpha: Float,
) {
    val r = cell * 0.46f
    val outline = cell * 0.055f
    drawCircle(palette.snakeOutline.copy(alpha = alpha), r + outline, center)
    drawCircle(palette.snakeHead.copy(alpha = alpha), r, center)
    drawCircle(
        color = lighten(palette.snakeHead, 0.32f).copy(alpha = 0.5f * alpha),
        radius = r * 0.52f,
        center = center + Offset(0f, -r * 0.32f),
    )
    drawEyes(center.x, center.y, cell, direction, palette, alpha)
}

/**
 * The flat-skin body: crisp square (or lightly rounded) segments with a unified
 * drop shadow and a two-tone top-highlight / bottom-shade for a touch of volume,
 * tapering toward the tail. Preserves the blocky/pixel identity of flat skins.
 */
private fun DrawScope.drawSnakeBlocks(
    centers: List<Offset>,
    cell: Float,
    palette: SkinPalette,
    alpha: Float,
) {
    val n = centers.size
    val corner = cell * palette.cornerFactor
    val rad = CornerRadius(corner, corner)
    val shadowOff = Offset(cell * 0.05f, cell * 0.08f)

    for (i in 0 until n) {
        val side = blockSide(i, n, cell)
        val c = centers[i]
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.20f * alpha),
            topLeft = Offset(c.x - side / 2f + shadowOff.x, c.y - side / 2f + shadowOff.y),
            size = Size(side, side),
            cornerRadius = rad,
        )
    }
    for (i in n - 1 downTo 0) {
        val side = blockSide(i, n, cell)
        val c = centers[i]
        val tl = Offset(c.x - side / 2f, c.y - side / 2f)
        drawRoundRect(palette.snakeBody.copy(alpha = alpha), tl, Size(side, side), rad)
        drawRect(lighten(palette.snakeBody, 0.18f).copy(alpha = 0.45f * alpha), Offset(tl.x, tl.y), Size(side, side * 0.4f))
        drawRect(darken(palette.snakeBody, 0.25f).copy(alpha = 0.4f * alpha), Offset(tl.x, tl.y + side * 0.64f), Size(side, side * 0.36f))
        drawRoundRect(palette.snakeOutline.copy(alpha = alpha), tl, Size(side, side), rad, style = Stroke(width = cell * 0.06f))
    }
}

/** The flat-skin head: a larger two-tone block with direction-aware eyes. */
private fun DrawScope.drawBlockHead(
    center: Offset,
    cell: Float,
    direction: Direction,
    palette: SkinPalette,
    alpha: Float,
) {
    val side = cell * 0.92f
    val corner = cell * palette.cornerFactor
    val rad = CornerRadius(corner, corner)
    val tl = Offset(center.x - side / 2f, center.y - side / 2f)
    drawRoundRect(palette.snakeHead.copy(alpha = alpha), tl, Size(side, side), rad)
    drawRect(lighten(palette.snakeHead, 0.2f).copy(alpha = 0.45f * alpha), Offset(tl.x, tl.y), Size(side, side * 0.4f))
    drawRoundRect(palette.snakeOutline.copy(alpha = alpha), tl, Size(side, side), rad, style = Stroke(width = cell * 0.06f))
    drawEyes(center.x, center.y, cell, direction, palette, alpha)
}

private fun DrawScope.drawEyes(
    centerX: Float,
    centerY: Float,
    cell: Float,
    direction: Direction,
    palette: SkinPalette,
    alpha: Float = 1f,
) {
    val forwardX = direction.dx.toFloat()
    val forwardY = direction.dy.toFloat()
    // Perpendicular to travel, to seat the two eyes side by side.
    val perpX = -forwardY
    val perpY = forwardX
    val forward = cell * 0.16f
    val spread = cell * 0.2f
    val eyeRadius = cell * 0.11f
    val pupilRadius = cell * 0.055f

    for (sign in intArrayOf(-1, 1)) {
        val ex = centerX + forwardX * forward + perpX * spread * sign
        val ey = centerY + forwardY * forward + perpY * spread * sign
        // Honour the head alpha so the eyes fade *with* the head during the death /
        // level-up dissolve (otherwise they linger as floating eyes).
        drawCircle(Color.White.copy(alpha = alpha), eyeRadius, Offset(ex, ey))
        drawCircle(
            palette.snakeEye.copy(alpha = alpha),
            pupilRadius,
            Offset(ex + forwardX * cell * 0.03f, ey + forwardY * cell * 0.03f),
        )
    }
}

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
import androidx.compose.ui.graphics.BlendMode
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
import com.brioni.snake.game.BoardTerrain
import com.brioni.snake.game.Debris
import com.brioni.snake.game.Direction
import com.brioni.snake.game.EffectKind
import com.brioni.snake.game.Food
import com.brioni.snake.game.FoodCategory
import com.brioni.snake.game.FoodEffect
import com.brioni.snake.game.FoodTier
import com.brioni.snake.game.GameState
import com.brioni.snake.game.Position
import com.brioni.snake.game.TeleportPair
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
 * An in-game-accurate snake emblem (used under the menu wordmark and on the
 * Settings skin cards). It draws through the exact same [drawSnake] renderer as
 * gameplay - the tapered, shaded tube with a glossy glowing head for glow skins,
 * two-tone blocky segments for flat skins - so it reflects the selected
 * [palette] faithfully. Head leading on the right, the chain fills the given
 * width so callers can match it to the title's width.
 *
 * With the defaults it is drawn straight and still (the menu wordmark's static
 * emblem). Passing an advancing [time] animates the per-skin body materials
 * (Neon's filament, Aurora's flowing hues, Ember's lava), and a non-zero
 * [waveAmplitude] (as a fraction of the height) makes the body **slither**: a
 * sine wave travelling tailward so the head reads as leading the motion.
 * [cellFraction] sets the segment size from the height (smaller leaves room for
 * the wave); [contentAlpha] dims the whole snake (locked skin cards).
 */
@Composable
fun SnakeEmblem(
    palette: SkinPalette,
    modifier: Modifier = Modifier,
    time: Float = 0f,
    waveAmplitude: Float = 0f,
    cellFraction: Float = 0.72f,
    contentAlpha: Float = 1f,
) {
    val shaders = remember { BoardShaders() }
    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        // Cell size from the height, leaving headroom so the head glow / drop
        // shadow / slither wave can bleed softly (the Canvas draw is not clipped
        // to its bounds).
        val cell = size.height * cellFraction
        if (cell <= 0f) return@Canvas
        val cy = size.height / 2f
        val step = cell // one cell of spacing between centres, like adjacent board cells
        val span = size.width - cell // small inset so head/tail sit inside the width
        val n = (span / step).toInt().coerceAtLeast(1) + 1
        val totalWidth = step * (n - 1)
        val startX = (size.width - totalWidth) / 2f
        val amp = size.height * waveAmplitude
        // Head is index 0 (rightmost); increasing index walks left toward the tail.
        val centers = ArrayList<Offset>(n)
        for (i in 0 until n) {
            val y = if (amp > 0f) cy + amp * sin(i * 0.85f - time * 2.6f) else cy
            centers.add(Offset(startX + step * (n - 1 - i), y))
        }
        drawSnake(
            centers = centers,
            cell = cell,
            direction = Direction.Right,
            palette = palette,
            bodyAlpha = contentAlpha,
            headAlpha = contentAlpha,
            shaders = shaders,
            time = time,
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
    terrain: BoardTerrain = BoardTerrain.Default,
    borderColor: Color = palette.boardBorder,
    outsideColor: Color = Color.Black,
    reduceMotion: Boolean = false,
    // Pause-resume countdown: pulse a locator beacon + direction chevron on the
    // head so the player re-finds the snake before motion restarts.
    resumeHighlight: Boolean = false,
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

        drawBoardBackground(board, cell, originX, originY, boardWidth, boardHeight, palette, terrain, shaders, time)

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
            drawDebris(state.debris, cell, originX, originY, palette, time)
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
            val centers = interpolatedSnakeCenters(
                snake, previousSnake, state.teleports, f, cell, originX, originY,
            )
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
            // Resume locator: while the pause-resume countdown ticks, a beacon
            // pulses on the head and a chevron points along the travel direction,
            // so the eye finds the snake and the first move can be planned.
            if (resumeHighlight) {
                centers.firstOrNull()?.let { head ->
                    drawResumeBeacon(head, state.direction, cell, palette, seconds, reduceMotion)
                }
            }
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
    terrain: BoardTerrain,
    shaders: BoardShaders,
    time: Float,
) {
    val layer = shaders.terrainLayer(terrain)
    if (layer == null) {
        // Default terrain: the skin's own gradient brought to life with drifting
        // glows + vignette, its endpoints fed from the active palette.
        shaders.background.setFloatUniform("origin", originX, originY)
        shaders.background.setFloatUniform("resolution", boardWidth, boardHeight)
        shaders.background.setFloatUniform("time", time)
        shaders.background.setColorUniform("topColor", palette.boardTop.toArgb())
        shaders.background.setColorUniform("bottomColor", palette.boardBottom.toArgb())
    } else {
        // A standalone terrain floor; cellPx grid-aligns its features (e.g.
        // the Meadow checker).
        layer.shader.setFloatUniform("origin", originX, originY)
        layer.shader.setFloatUniform("resolution", boardWidth, boardHeight)
        layer.shader.setFloatUniform("time", time)
        layer.shader.setFloatUniform("cellPx", cell)
    }
    drawRect(
        brush = layer?.brush ?: shaders.backgroundBrush,
        topLeft = Offset(originX, originY),
        size = Size(boardWidth, boardHeight),
    )

    if (cell < 10f) return // skip the grid on dense boards where it is just noise
    val gridLine = terrainGridLine(terrain, palette)
    for (x in 0..board.width) {
        val lineX = originX + x * cell
        drawLine(gridLine, Offset(lineX, originY), Offset(lineX, originY + boardHeight), 1f)
    }
    for (y in 0..board.height) {
        val lineY = originY + y * cell
        drawLine(gridLine, Offset(originX, lineY), Offset(originX + boardWidth, lineY), 1f)
    }
}

/**
 * The pause-resume locator: a steady ring + soft glow hugging the snake's head,
 * two expanding pulse rings (suppressed under reduce-motion) and a pulsing
 * chevron one cell ahead pointing along the current travel [direction]. Drawn
 * in the skin's head-glow accent plus white, so it reads on every terrain.
 */
private fun DrawScope.drawResumeBeacon(
    head: Offset,
    direction: Direction,
    cell: Float,
    palette: SkinPalette,
    seconds: Double,
    reduceMotion: Boolean,
) {
    val accent = lighten(palette.headGlow, 0.25f)
    // Soft fill glow, then a steady ring hugging the head.
    drawCircle(accent.copy(alpha = 0.16f), radius = cell * 1.5f, center = head)
    drawCircle(accent.copy(alpha = 0.9f), radius = cell * 0.85f, center = head, style = Stroke(cell * 0.10f))
    if (!reduceMotion) {
        // Two expanding, fading sonar rings, half a period apart.
        for (k in 0..1) {
            val t = ((seconds * 0.9 + k * 0.5) % 1.0).toFloat()
            drawCircle(
                color = Color.White.copy(alpha = (1f - t) * 0.5f),
                radius = cell * (0.9f + 2.2f * t),
                center = head,
                style = Stroke(cell * 0.08f),
            )
        }
    }
    // Direction chevron ahead of the head: base corners across the travel axis,
    // tip pointing where the snake will go on the first tick.
    val (dx, dy) = when (direction) {
        Direction.Up -> 0f to -1f
        Direction.Down -> 0f to 1f
        Direction.Left -> -1f to 0f
        Direction.Right -> 1f to 0f
    }
    val px = -dy
    val py = dx
    val pulse = if (reduceMotion) 1f else 0.7f + 0.3f * sin(seconds * 5.0).toFloat()
    val tip = Offset(head.x + dx * cell * 1.95f, head.y + dy * cell * 1.95f)
    val baseX = head.x + dx * cell * 1.35f
    val baseY = head.y + dy * cell * 1.35f
    val halfW = cell * 0.42f
    val chevron = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(baseX + px * halfW, baseY + py * halfW)
        lineTo(baseX - px * halfW, baseY - py * halfW)
        close()
    }
    drawPath(chevron, color = accent.copy(alpha = 0.55f * pulse))
    drawPath(chevron, color = Color.White.copy(alpha = 0.85f * pulse), style = Stroke(cell * 0.07f))
}

/**
 * The board's framing border over a terrain floor: the skin's own border on the
 * Default floor (which wears the skin's colours), else a frame matched to the
 * terrain - the frame belongs to the stage, not to the snake, so a Meadow lawn
 * is edged in hedge green rather than, say, Ember's orange. Used by the dark
 * theme; the light theme keeps its branded primary frame.
 */
fun terrainBoardBorder(terrain: BoardTerrain, palette: SkinPalette): Color = when (terrain) {
    BoardTerrain.Default -> palette.boardBorder
    BoardTerrain.Meadow -> Color(0xFF5C9638)
    BoardTerrain.Abyss -> Color(0xFF3FB8D8)
    BoardTerrain.Nebula -> Color(0xFF8C7BFF)
    BoardTerrain.Dunes -> Color(0xFFC08A4E)
    BoardTerrain.Glacier -> Color(0xFF9CCFE8)
}

/**
 * The grid-line tint over a terrain floor: the skin's own line on the Default
 * floor, else a whisper matched to the terrain (dark on the lit floors, tinted
 * on the glowing ones) so the grid stays legible without fighting the shader.
 */
private fun terrainGridLine(terrain: BoardTerrain, palette: SkinPalette): Color = when (terrain) {
    BoardTerrain.Default -> palette.gridLine
    BoardTerrain.Meadow -> Color(0x1A000000)
    BoardTerrain.Abyss -> Color(0x1466D9FF)
    BoardTerrain.Nebula -> Color(0x10FFFFFF)
    BoardTerrain.Dunes -> Color(0x16000000)
    BoardTerrain.Glacier -> Color(0x1A0A2038)
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
 * the same body material ([SkinPalette.snakeStyle]), colours and trunk-to-tip
 * taper, never a stray pellet. Only the head is omitted (it is a tail) and the
 * whole run fades out as its lethal timer expires.
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
    time: Float,
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
        // Draw the debris in the skin's own body material (head omitted).
        drawSnakeBody(centers, cell, palette, alpha, time)
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
 * Interpolated pixel centres for the snake between the previous and current tick.
 * Adjacent segments tween straight from their old cell to the new one. A segment
 * that jumped through a teleport portal this interval (old and new cell are not
 * neighbours) is instead **routed through the pad**: for the first half of the
 * tick it slides from its old cell into the entry pad, then it appears at the
 * exit pad - so the head visibly dives into the portal and re-emerges at its
 * partner rather than streaking across the board. The two sides of a portal stay
 * far apart on purpose; the body renderers break the tube there (see
 * [isBrokenSpan]). A Ghost board-wrap has no pad, so it just snaps at the midpoint.
 */
private fun interpolatedSnakeCenters(
    snake: List<Position>,
    previousSnake: List<Position>,
    teleports: List<TeleportPair>,
    f: Float,
    cell: Float,
    originX: Float,
    originY: Float,
): List<Offset> {
    fun toOffset(px: Float, py: Float) =
        Offset(originX + (px + 0.5f) * cell, originY + (py + 0.5f) * cell)
    val centers = ArrayList<Offset>(snake.size)
    for (k in snake.indices) {
        val to = snake[k]
        val from = if (previousSnake.isEmpty()) to else previousSnake[k.coerceAtMost(previousSnake.lastIndex)]
        if (abs(from.x - to.x) + abs(from.y - to.y) <= 1) {
            centers.add(toOffset(lerp(from.x.toFloat(), to.x.toFloat(), f), lerp(from.y.toFloat(), to.y.toFloat(), f)))
            continue
        }
        // Non-adjacent: a portal jump (or a Ghost wrap). `to` is the exit pad, so
        // its partner is the entry pad the segment slid onto to trigger the jump.
        val entry = teleports.firstNotNullOfOrNull { it.exitFor(to) }
        if (entry != null && f < 0.5f) {
            val t = f * 2f
            centers.add(toOffset(lerp(from.x.toFloat(), entry.x.toFloat(), t), lerp(from.y.toFloat(), entry.y.toFloat(), t)))
        } else {
            // Second half of the jump, or a padless wrap: sit at the exit cell.
            centers.add(toOffset(to.x.toFloat(), to.y.toFloat()))
        }
    }
    return centers
}

/**
 * True when two consecutive body centres are farther apart than a normal step -
 * they sit on opposite sides of a teleport portal (or a board wrap), so the body
 * renderers must not draw a connecting tube across the gap.
 */
private fun isBrokenSpan(a: Offset, b: Offset, cell: Float): Boolean {
    val dx = a.x - b.x
    val dy = a.y - b.y
    val limit = 1.6f * cell
    return dx * dx + dy * dy > limit * limit
}

/**
 * Draws the whole snake from interpolated cell [centers] (head = index 0). The
 * body material and head are chosen by [SkinPalette.snakeStyle] (tube / chiselled
 * blocks / neon / aurora / molten); the head is drawn last, on top. The AGSL
 * head-glow halo is independent ([SkinPalette.useGlow]).
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
    // Head glow halo first, so it sits beneath the body/head (glow skins only).
    if (palette.useGlow) {
        val glowRadius = cell * 1.15f
        shaders.glow.setFloatUniform("center", head.x, head.y)
        shaders.glow.setFloatUniform("radius", glowRadius)
        shaders.glow.setFloatUniform("time", time)
        shaders.glow.setColorUniform("glowColor", headGlow.toArgb())
        drawCircle(brush = shaders.glowBrush, radius = glowRadius, center = head)
    }
    drawSnakeBody(centers, cell, palette, bodyAlpha, time)
    drawSnakeHeadStyled(head, cell, direction, palette, headAlpha, time)
}

/** Dispatches the body renderer for the skin's [SnakeStyle]. Shared with debris. */
private fun DrawScope.drawSnakeBody(
    centers: List<Offset>,
    cell: Float,
    palette: SkinPalette,
    alpha: Float,
    time: Float,
) {
    when (palette.snakeStyle) {
        SnakeStyle.Tube -> drawSnakeTube(centers, cell, palette, alpha)
        SnakeStyle.Blocks -> drawSnakeBlocks(centers, cell, palette, alpha)
        SnakeStyle.NeonTube -> drawSnakeNeon(centers, cell, palette, alpha, time)
        SnakeStyle.AuroraRibbon -> drawSnakeAurora(centers, cell, palette, alpha, time)
        SnakeStyle.Molten -> drawSnakeMolten(centers, cell, palette, alpha, time)
    }
}

/** Dispatches the head renderer for the skin's [SnakeStyle]. */
private fun DrawScope.drawSnakeHeadStyled(
    head: Offset,
    cell: Float,
    direction: Direction,
    palette: SkinPalette,
    alpha: Float,
    time: Float,
) {
    when (palette.snakeStyle) {
        SnakeStyle.Blocks -> drawBlockHead(head, cell, direction, palette, alpha)
        SnakeStyle.NeonTube -> drawNeonHead(head, cell, direction, palette, alpha)
        SnakeStyle.AuroraRibbon -> drawAuroraHead(head, cell, direction, palette, alpha, time)
        SnakeStyle.Molten -> drawMoltenHead(head, cell, direction, palette, alpha, time)
        SnakeStyle.Tube -> drawRoundHead(head, cell, direction, palette, alpha)
    }
}

/**
 * A continuous, seam-free chain of round-capped capsules along [centers] (with
 * optional joint discs to fill bend notches). The reusable backbone of the tube,
 * neon and molten bodies. [blendMode] enables additive passes for glow/filament.
 */
private fun DrawScope.strokeChain(
    centers: List<Offset>,
    cell: Float,
    joints: Boolean,
    color: Color,
    offset: Offset = Offset.Zero,
    blendMode: BlendMode = BlendMode.SrcOver,
    widthOf: (Int) -> Float,
) {
    val n = centers.size
    for (i in 0 until n - 1) {
        // Skip the capsule that would bridge a teleport portal (or wrap) seam.
        if (isBrokenSpan(centers[i], centers[i + 1], cell)) continue
        drawLine(
            color,
            centers[i] + offset,
            centers[i + 1] + offset,
            strokeWidth = widthOf(i).coerceAtLeast(1f),
            cap = StrokeCap.Round,
            blendMode = blendMode,
        )
    }
    if (joints) {
        for (i in 0 until n) {
            val r = widthOf(i) / 2f
            if (r > 0.4f) drawCircle(color, r, centers[i] + offset, blendMode = blendMode)
        }
    }
}

/**
 * Classic tube body: a continuous, seam-free tube built from round-capped
 * capsules with tapering width, layered as drop shadow → outline → fill → an
 * upper sheen and a crisp top specular line so it reads as a glossy cylinder.
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
    val sheenColor = lighten(palette.snakeBody, 0.30f).copy(alpha = 0.55f * alpha)
    val specColor = lighten(palette.snakeBody, 0.55f).copy(alpha = 0.5f * alpha)
    val shadowColor = Color.Black.copy(alpha = 0.22f * alpha)

    if (n == 1) {
        val r = snakeWidth(0, n, cell) / 2f
        drawCircle(outlineColor, r + outline, centers[0])
        drawCircle(bodyColor, r, centers[0])
        return
    }

    strokeChain(centers, cell, joints = false, color = shadowColor, offset = Offset(cell * 0.05f, cell * 0.09f)) { snakeWidth(it, n, cell) + 2 * outline }
    strokeChain(centers, cell, joints = true, color = outlineColor) { snakeWidth(it, n, cell) + 2 * outline }
    strokeChain(centers, cell, joints = true, color = bodyColor) { snakeWidth(it, n, cell) }
    strokeChain(centers, cell, joints = false, color = sheenColor, offset = Offset(0f, -cell * 0.07f)) { snakeWidth(it, n, cell) * 0.45f }
    strokeChain(centers, cell, joints = false, color = specColor, offset = Offset(0f, -cell * 0.13f)) { (snakeWidth(it, n, cell) * 0.16f).coerceAtLeast(1.5f) }
}

/** The tube head: a glossy disc with a top sheen, a crisp specular dot and eyes. */
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
    drawCircle(
        color = lighten(palette.snakeHead, 0.55f).copy(alpha = 0.6f * alpha),
        radius = r * 0.18f,
        center = center + Offset(-r * 0.34f, -r * 0.40f),
    )
    drawEyes(center.x, center.y, cell, direction, palette, alpha)
}

/**
 * The flat-skin body: crisp square (or lightly rounded) segments, each with a
 * unified drop shadow and a **volumetric diagonal gradient** (top-left lit,
 * bottom-right shaded) plus a small specular corner, for a chiselled premium look
 * that still preserves the blocky/pixel identity of flat skins.
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
        drawChiselledBlock(tl, side, rad, palette.snakeBody, palette.snakeOutline, cell, alpha)
    }
}

/** One chiselled block: diagonal-lit gradient fill, specular corner and outline. */
private fun DrawScope.drawChiselledBlock(
    tl: Offset,
    side: Float,
    rad: CornerRadius,
    fill: Color,
    outline: Color,
    cell: Float,
    alpha: Float,
) {
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(lighten(fill, 0.24f), fill, darken(fill, 0.26f)),
            start = Offset(tl.x, tl.y),
            end = Offset(tl.x + side, tl.y + side),
        ),
        topLeft = tl,
        size = Size(side, side),
        cornerRadius = rad,
        alpha = alpha,
    )
    drawRoundRect(
        color = lighten(fill, 0.5f).copy(alpha = 0.5f * alpha),
        topLeft = Offset(tl.x + side * 0.14f, tl.y + side * 0.12f),
        size = Size(side * 0.34f, side * 0.16f),
        cornerRadius = CornerRadius(side * 0.08f, side * 0.08f),
    )
    drawRoundRect(outline.copy(alpha = alpha), tl, Size(side, side), rad, style = Stroke(width = cell * 0.06f))
}

/** The flat-skin head: a larger chiselled block with direction-aware eyes. */
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
    drawChiselledBlock(tl, side, rad, palette.snakeHead, palette.snakeOutline, cell, alpha)
    drawEyes(center.x, center.y, cell, direction, palette, alpha)
}

// --- Neon skin: a hollow, glowing neon tube ------------------------------------

/**
 * Neon body: a hollow glass tube - a wide soft additive halo, a glowing wall, a
 * dark hollow core and a bright, gently pulsing filament down the centre.
 */
private fun DrawScope.drawSnakeNeon(
    centers: List<Offset>,
    cell: Float,
    palette: SkinPalette,
    alpha: Float,
    time: Float,
) {
    val n = centers.size
    val glow = palette.headGlow
    val body = palette.snakeBody
    strokeChain(centers, cell, joints = false, color = glow.copy(alpha = 0.10f * alpha), blendMode = BlendMode.Plus) { snakeWidth(it, n, cell) * 1.7f }
    strokeChain(centers, cell, joints = true, color = body.copy(alpha = 0.60f * alpha)) { snakeWidth(it, n, cell) }
    strokeChain(centers, cell, joints = true, color = Color(0xFF04050A).copy(alpha = 0.80f * alpha)) { snakeWidth(it, n, cell) * 0.52f }
    val flicker = 0.6f + 0.4f * sin(time * 8.0).toFloat()
    strokeChain(centers, cell, joints = false, color = lighten(body, 0.55f).copy(alpha = (0.75f * flicker * alpha).coerceIn(0f, 1f)), blendMode = BlendMode.Plus) {
        (snakeWidth(it, n, cell) * 0.16f).coerceAtLeast(1.5f)
    }
}

/** Neon head: a glowing hollow ring with a bright inner rim and eyes. */
private fun DrawScope.drawNeonHead(
    center: Offset,
    cell: Float,
    direction: Direction,
    palette: SkinPalette,
    alpha: Float,
) {
    val r = cell * 0.46f
    val body = palette.snakeBody
    drawCircle(palette.headGlow.copy(alpha = 0.18f * alpha), r * 1.7f, center, blendMode = BlendMode.Plus)
    drawCircle(body.copy(alpha = 0.9f * alpha), r * 0.8f, center, style = Stroke(width = r * 0.42f))
    drawCircle(lighten(body, 0.5f).copy(alpha = 0.9f * alpha), r * 0.8f, center, style = Stroke(width = r * 0.12f))
    drawEyes(center.x, center.y, cell, direction, palette, alpha)
}

// --- Aurora skin: a flowing multi-hue ribbon -----------------------------------

/** The Aurora hue stops (teal → cyan → blue → violet → green), cycled along the body. */
private val AuroraStops = listOf(
    Color(0xFF2BE0B0),
    Color(0xFF3AD1E0),
    Color(0xFF7C9CFF),
    Color(0xFFB68CFF),
    Color(0xFF5CE6A6),
)

/** The flowing aurora colour at fractional body position [u] (0 = head) and [time]. */
private fun auroraColor(u: Float, time: Float): Color {
    val m = AuroraStops.size
    var p = (u * 1.6f - time * 0.35f) % 1f
    if (p < 0f) p += 1f
    val seg = p * m
    val i = seg.toInt() % m
    val f = seg - seg.toInt()
    return mixColor(AuroraStops[i], AuroraStops[(i + 1) % m], f)
}

/**
 * Aurora body: a tapering ribbon whose hue flows along its length and drifts over
 * time, with a soft additive glow beneath and an upper sheen - like a curtain of
 * northern lights following the snake.
 */
private fun DrawScope.drawSnakeAurora(
    centers: List<Offset>,
    cell: Float,
    palette: SkinPalette,
    alpha: Float,
    time: Float,
) {
    val n = centers.size
    // Outer flowing glow. (Broken spans skip the connecting line - a portal seam.)
    for (i in 0 until n - 1) {
        if (isBrokenSpan(centers[i], centers[i + 1], cell)) continue
        val col = auroraColor(i.toFloat() / n, time)
        drawLine(col.copy(alpha = 0.12f * alpha), centers[i], centers[i + 1], strokeWidth = snakeWidth(i, n, cell) * 1.5f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
    }
    // Body: per-segment flowing colour with joint discs for seamless bends. The
    // joint disc is kept even across a portal seam so a lone segment still reads.
    for (i in 0 until n - 1) {
        val col = auroraColor(i.toFloat() / n, time).copy(alpha = 0.96f * alpha)
        if (!isBrokenSpan(centers[i], centers[i + 1], cell)) {
            drawLine(col, centers[i], centers[i + 1], strokeWidth = snakeWidth(i, n, cell), cap = StrokeCap.Round)
        }
        drawCircle(col, snakeWidth(i, n, cell) / 2f, centers[i])
    }
    if (n > 0) {
        val col = auroraColor((n - 1).toFloat() / n, time).copy(alpha = 0.96f * alpha)
        drawCircle(col, snakeWidth(n - 1, n, cell) / 2f, centers[n - 1])
    }
    // Upper sheen.
    for (i in 0 until n - 1) {
        if (isBrokenSpan(centers[i], centers[i + 1], cell)) continue
        drawLine(Color.White.copy(alpha = 0.26f * alpha), centers[i] + Offset(0f, -cell * 0.09f), centers[i + 1] + Offset(0f, -cell * 0.09f), strokeWidth = snakeWidth(i, n, cell) * 0.4f, cap = StrokeCap.Round)
    }
}

/** Aurora head: a glowing disc in the leading hue with a bright sheen and eyes. */
private fun DrawScope.drawAuroraHead(
    center: Offset,
    cell: Float,
    direction: Direction,
    palette: SkinPalette,
    alpha: Float,
    time: Float,
) {
    val r = cell * 0.46f
    val col = auroraColor(0f, time)
    drawCircle(col.copy(alpha = 0.22f * alpha), r * 1.7f, center, blendMode = BlendMode.Plus)
    drawCircle(col.copy(alpha = alpha), r, center)
    drawCircle(Color.White.copy(alpha = 0.5f * alpha), r * 0.5f, center + Offset(0f, -r * 0.32f))
    drawEyes(center.x, center.y, cell, direction, palette, alpha)
}

// --- Ember skin: molten rock with a lava vein ----------------------------------

/** A near-white hot core the lava vein blends toward at its hottest (the head). */
private val LavaHot = Color(0xFFFFE9A8)

/**
 * Ember body: a dark rock crust with a bright molten-lava vein running through it,
 * hotter and brighter toward the head and gently pulsing, so the snake reads as
 * cooling lava rather than a plain orange tube.
 */
private fun DrawScope.drawSnakeMolten(
    centers: List<Offset>,
    cell: Float,
    palette: SkinPalette,
    alpha: Float,
    time: Float,
) {
    val n = centers.size
    val crust = darken(palette.snakeBody, 0.72f)
    val crustHi = darken(palette.snakeBody, 0.45f)
    val glow = palette.headGlow
    strokeChain(centers, cell, joints = false, color = Color.Black.copy(alpha = 0.35f * alpha)) { snakeWidth(it, n, cell) + cell * 0.02f }
    strokeChain(centers, cell, joints = true, color = crust.copy(alpha = alpha)) { snakeWidth(it, n, cell) }
    strokeChain(centers, cell, joints = false, color = crustHi.copy(alpha = 0.6f * alpha), offset = Offset(0f, -cell * 0.06f)) { snakeWidth(it, n, cell) * 0.5f }
    // Molten vein: additive, hotter toward the head, pulsing.
    val denom = (n - 1).coerceAtLeast(1)
    for (i in 0 until n - 1) {
        val heat = 1f - i.toFloat() / denom
        val pulse = 0.55f + 0.45f * sin(time * 4.0 - i * 0.6).toFloat()
        val col = mixColor(glow, LavaHot, heat)
        val a = ((0.18f + 0.7f * heat) * pulse * alpha).coerceIn(0f, 1f)
        if (!isBrokenSpan(centers[i], centers[i + 1], cell)) {
            drawLine(col.copy(alpha = a), centers[i], centers[i + 1], strokeWidth = snakeWidth(i, n, cell) * 0.42f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
        }
        drawCircle(col.copy(alpha = (a * 0.9f).coerceIn(0f, 1f)), snakeWidth(i, n, cell) * 0.22f, centers[i], blendMode = BlendMode.Plus)
    }
}

/** Ember head: a dark crust disc with a pulsing molten core, hot halo and eyes. */
private fun DrawScope.drawMoltenHead(
    center: Offset,
    cell: Float,
    direction: Direction,
    palette: SkinPalette,
    alpha: Float,
    time: Float,
) {
    val r = cell * 0.46f
    val crust = darken(palette.snakeBody, 0.72f)
    val glow = palette.headGlow
    val pulse = 0.6f + 0.4f * sin(time * 4.0).toFloat()
    drawCircle(glow.copy(alpha = (0.28f * pulse * alpha).coerceIn(0f, 1f)), r * 1.9f, center, blendMode = BlendMode.Plus)
    drawCircle(crust.copy(alpha = alpha), r, center)
    drawCircle(mixColor(glow, LavaHot, 0.6f).copy(alpha = (0.85f * pulse * alpha).coerceIn(0f, 1f)), r * 0.62f, center, blendMode = BlendMode.Plus)
    drawEyes(center.x, center.y, cell, direction, palette, alpha)
}

/** Linear interpolation between two colours (including alpha) by [f] in 0..1. */
private fun mixColor(a: Color, b: Color, f: Float): Color = Color(
    red = a.red + (b.red - a.red) * f,
    green = a.green + (b.green - a.green) * f,
    blue = a.blue + (b.blue - a.blue) * f,
    alpha = a.alpha + (b.alpha - a.alpha) * f,
)

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

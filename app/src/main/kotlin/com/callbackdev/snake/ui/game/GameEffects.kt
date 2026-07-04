package com.callbackdev.snake.ui.game

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A single particle for the board bursts (eat / shrink / vanish / explosion).
 * Positions and velocities are stored in **cell space** (grid units), so the
 * effect is resolution-independent: the renderer converts to pixels with the
 * same mapping it uses for the board.
 *
 * @param glow      when true the renderer draws a soft additive halo behind the
 *                  particle (used for the brighter eat / explosion sparks).
 * @param drag      per-second velocity damping (higher = stops sooner).
 * @param gravity   downward pull in cells/sec² (embers fall, sparks float).
 */
class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val color: Color,
    val radiusCells: Float,
    val glow: Boolean = false,
    val drag: Float = 0.9f,
    val gravity: Float = GRAVITY,
) {
    /** Remaining life as a 0..1 fraction, for fading/scaling. */
    val fade: Float get() = (life / maxLife).coerceIn(0f, 1f)
}

/** Default gravity pulling particles down, in cells per second². */
private const val GRAVITY = 14f

/** A warm near-white the hottest sparks tint toward at high combo. */
private val SparkHot = Color(0xFFFFF3C4)

/** Linear blend between two colours (alpha included). */
private fun mix(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * k,
        green = a.green + (b.green - a.green) * k,
        blue = a.blue + (b.blue - a.blue) * k,
        alpha = a.alpha + (b.alpha - a.alpha) * k,
    )
}

private const val TAU = 2f * Math.PI.toFloat()

/**
 * Spawns a radial burst at cell [centerX], [centerY] coloured by the eaten food.
 * Larger ([span] = 2) foods get a wider, longer burst; a higher [combo] makes it
 * denser, faster and hotter (sparks tint toward white) - the "juice" that rewards
 * a streak. A handful of fast white sparks lead the burst for extra snap.
 */
fun emitEatBurst(
    target: MutableList<Particle>,
    centerX: Float,
    centerY: Float,
    color: Color,
    span: Int,
    combo: Int = 1,
    random: Random = Random.Default,
) {
    val heat = ((combo - 1) / 4f).coerceIn(0f, 1f) // 0 at x1 → 1 at x5+
    val boost = 1f + heat // up to ~2× density at max combo
    val count = ((if (span >= 2) 22 else 14) * boost).toInt()
    val speedBase = (if (span >= 2) 9f else 6.5f) * (1f + 0.25f * heat)
    repeat(count) {
        val angle = random.nextFloat() * TAU
        val speed = speedBase * (0.4f + random.nextFloat())
        val life = 0.45f + random.nextFloat() * 0.35f
        // Some particles run hot (toward white) - more of them as the combo climbs.
        val hot = random.nextFloat() < 0.25f + 0.4f * heat
        target.add(
            Particle(
                x = centerX,
                y = centerY,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                life = life,
                maxLife = life,
                color = if (hot) mix(color, SparkHot, 0.55f) else color,
                radiusCells = 0.09f + random.nextFloat() * 0.11f,
                glow = true,
            ),
        )
    }
    // Lead sparks: a few fast, bright, short streaks for snap.
    repeat(if (span >= 2) 6 else 4) {
        val angle = random.nextFloat() * TAU
        val speed = speedBase * (1.2f + random.nextFloat() * 0.8f)
        val life = 0.22f + random.nextFloat() * 0.18f
        target.add(
            Particle(
                x = centerX,
                y = centerY,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                life = life,
                maxLife = life,
                color = SparkHot,
                radiusCells = 0.05f + random.nextFloat() * 0.05f,
                glow = true,
                drag = 1.4f,
            ),
        )
    }
}

/**
 * Spawns an *inward* burst for shrinking food: particles start on a ring and
 * converge on the centre, reading as an implosion rather than an explosion.
 */
fun emitImplodeBurst(
    target: MutableList<Particle>,
    centerX: Float,
    centerY: Float,
    color: Color,
    span: Int,
    random: Random = Random.Default,
) {
    val count = if (span >= 2) 20 else 14
    val ringBase = if (span >= 2) 1.1f else 0.7f
    repeat(count) {
        val angle = random.nextFloat() * TAU
        val ring = ringBase * (0.7f + random.nextFloat() * 0.5f)
        val life = 0.35f + random.nextFloat() * 0.25f
        // Velocity points back toward the centre so the ring collapses inward.
        val speed = (2.2f + random.nextFloat() * 1.4f) * ring
        target.add(
            Particle(
                x = centerX + cos(angle) * ring,
                y = centerY + sin(angle) * ring,
                vx = -cos(angle) * speed,
                vy = -sin(angle) * speed,
                life = life,
                maxLife = life,
                color = color,
                radiusCells = 0.07f + random.nextFloat() * 0.08f,
                glow = true,
                gravity = 0f, // an implosion shouldn't sag downward
            ),
        )
    }
}

/**
 * Spawns a soft "vanish" burst for an ignored food that timed out: a sparse
 * spray of particles drifting *upward* and fading quickly, reading as the food
 * dissolving rather than being eaten.
 */
fun emitVanishBurst(
    target: MutableList<Particle>,
    centerX: Float,
    centerY: Float,
    color: Color,
    span: Int,
    random: Random = Random.Default,
) {
    val count = if (span >= 2) 14 else 9
    val faded = color.copy(alpha = 0.7f)
    repeat(count) {
        // Mostly upward, with a little horizontal scatter.
        val spread = (random.nextFloat() - 0.5f) * 3.2f
        val rise = 3.5f + random.nextFloat() * 2.5f
        val life = 0.35f + random.nextFloat() * 0.25f
        target.add(
            Particle(
                x = centerX + (random.nextFloat() - 0.5f) * span,
                y = centerY + (random.nextFloat() - 0.5f) * span,
                vx = spread,
                vy = -rise,
                life = life,
                maxLife = life,
                color = faded,
                radiusCells = 0.06f + random.nextFloat() * 0.07f,
            ),
        )
    }
}

/** Hot core, ember and smoke tints for the Explosion detonation. */
private val BlastCore = Color(0xFFFFF1B0)
private val BlastEmber = Color(0xFFFF8A3D)
private val BlastSmoke = Color(0xFF6B5446)

/**
 * Spawns a fiery two-tone detonation for the Explosion hazard: a dense burst of
 * fast hot sparks and slower orange embers that arc down under gravity, plus a
 * little drifting smoke - much bigger and longer than a normal eat burst, to sell
 * the severing of the snake. [accent] is the explosion's red accent.
 */
fun emitExplosionBurst(
    target: MutableList<Particle>,
    centerX: Float,
    centerY: Float,
    accent: Color,
    span: Int,
    random: Random = Random.Default,
) {
    // Fast hot sparks shooting outward.
    repeat(30) {
        val angle = random.nextFloat() * TAU
        val speed = 11f * (0.5f + random.nextFloat())
        val life = 0.4f + random.nextFloat() * 0.4f
        target.add(
            Particle(
                x = centerX,
                y = centerY,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                life = life,
                maxLife = life,
                color = mix(BlastCore, accent, random.nextFloat() * 0.6f),
                radiusCells = 0.08f + random.nextFloat() * 0.12f,
                glow = true,
                drag = 1.1f,
            ),
        )
    }
    // Slower embers that arc and fall.
    repeat(18) {
        val angle = random.nextFloat() * TAU
        val speed = 5.5f * (0.4f + random.nextFloat())
        val life = 0.6f + random.nextFloat() * 0.6f
        target.add(
            Particle(
                x = centerX,
                y = centerY,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - 1.5f, // a little initial lift
                life = life,
                maxLife = life,
                color = BlastEmber,
                radiusCells = 0.06f + random.nextFloat() * 0.08f,
                glow = true,
                gravity = 20f,
                drag = 0.5f,
            ),
        )
    }
    // A few slow smoke puffs drifting up.
    repeat(8) {
        val angle = random.nextFloat() * TAU
        val speed = 1.8f * random.nextFloat()
        val life = 0.7f + random.nextFloat() * 0.5f
        target.add(
            Particle(
                x = centerX,
                y = centerY,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - 1.2f,
                life = life,
                maxLife = life,
                color = BlastSmoke.copy(alpha = 0.5f),
                radiusCells = 0.16f + random.nextFloat() * 0.14f,
                gravity = -3f, // smoke rises
                drag = 1.6f,
            ),
        )
    }
}

/**
 * A short-lived piece of floating text that rises and fades — used to call out
 * the seconds a Time Attack block adds or removes ("+5s" / "-3s"). Stored in
 * **cell space** like [Particle] so the renderer maps it with the board scale.
 */
class FloatingText(
    var x: Float,
    var y: Float,
    val vy: Float,
    var life: Float,
    val maxLife: Float,
    val text: String,
    val color: Color,
) {
    /** Remaining life as a 0..1 fraction, for fading. */
    val fade: Float get() = (life / maxLife).coerceIn(0f, 1f)
}

/** Spawns a floating label at cell [centerX], [centerY] that drifts upward. */
fun emitFloatingText(
    target: MutableList<FloatingText>,
    centerX: Float,
    centerY: Float,
    text: String,
    color: Color,
) {
    target.add(
        FloatingText(
            x = centerX,
            y = centerY,
            vy = -2.2f, // cells/sec upward drift
            life = 1.1f,
            maxLife = 1.1f,
            text = text,
            color = color,
        ),
    )
}

/** Advances each floating label by [dt] seconds and removes the expired ones. */
fun updateFloatingTexts(items: MutableList<FloatingText>, dt: Float) {
    if (items.isEmpty()) return
    val iterator = items.iterator()
    while (iterator.hasNext()) {
        val t = iterator.next()
        t.life -= dt
        if (t.life <= 0f) {
            iterator.remove()
            continue
        }
        t.y += t.vy * dt
    }
}

/**
 * Advances every particle by [dt] seconds and removes the dead ones. Mutates
 * [particles] in place; the caller forces a redraw each frame. Each particle
 * carries its own gravity and drag so eat sparks, falling embers and rising
 * smoke can coexist.
 */
fun updateParticles(particles: MutableList<Particle>, dt: Float) {
    if (particles.isEmpty()) return
    val iterator = particles.iterator()
    while (iterator.hasNext()) {
        val p = iterator.next()
        p.life -= dt
        if (p.life <= 0f) {
            iterator.remove()
            continue
        }
        p.x += p.vx * dt
        p.y += p.vy * dt
        p.vy += p.gravity * dt
        val damp = (1f - p.drag * dt).coerceAtLeast(0f)
        p.vx *= damp
        p.vy *= damp
    }
}

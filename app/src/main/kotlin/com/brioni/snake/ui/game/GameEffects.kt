package com.brioni.snake.ui.game

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A single particle for the eat burst (step 2.6). Positions and velocities are
 * stored in **cell space** (grid units), so the effect is resolution-
 * independent: the renderer converts to pixels with the same mapping it uses
 * for the board.
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
) {
    /** Remaining life as a 0..1 fraction, for fading/scaling. */
    val fade: Float get() = (life / maxLife).coerceIn(0f, 1f)
}

/** Gravity pulling particles down, in cells per second². */
private const val GRAVITY = 14f

/**
 * Spawns a radial burst at cell [centerX], [centerY] coloured by the eaten food.
 * Larger ([span] = 2) foods get a slightly wider, longer burst.
 */
fun emitEatBurst(
    target: MutableList<Particle>,
    centerX: Float,
    centerY: Float,
    color: Color,
    span: Int,
    random: Random = Random.Default,
) {
    val count = if (span >= 2) 22 else 14
    val speedBase = if (span >= 2) 9f else 6.5f
    repeat(count) {
        val angle = random.nextFloat() * (2f * Math.PI.toFloat())
        val speed = speedBase * (0.4f + random.nextFloat())
        val life = 0.45f + random.nextFloat() * 0.35f
        target.add(
            Particle(
                x = centerX,
                y = centerY,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                life = life,
                maxLife = life,
                color = color,
                radiusCells = 0.10f + random.nextFloat() * 0.10f,
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
        val angle = random.nextFloat() * (2f * Math.PI.toFloat())
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
                radiusCells = 0.08f + random.nextFloat() * 0.08f,
            ),
        )
    }
}

/**
 * Advances every particle by [dt] seconds and removes the dead ones. Mutates
 * [particles] in place; the caller forces a redraw each frame.
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
        p.vy += GRAVITY * dt
        p.vx *= (1f - 0.9f * dt) // mild drag
    }
}

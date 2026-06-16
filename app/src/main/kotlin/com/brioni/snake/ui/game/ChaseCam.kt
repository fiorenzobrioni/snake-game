package com.brioni.snake.ui.game

import com.brioni.snake.game.Direction
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin perspective camera for the 3D (chase-cam) hazard. No Android /
 * Compose imports, so the projection math is unit-testable on the JVM.
 *
 * World units are board cells, with x to the right, y downward (matching the
 * board grid) and z up out of the board plane. The camera is a pinhole with an
 * orthonormal basis ([right], [up], [fwd]); [project] maps a world point to
 * normalized screen coordinates plus the view-space depth used for painter's
 * sorting. The caller turns the normalized coordinates into pixels (see
 * [GameBoard]); both axes scale by half the board's pixel height so cells stay
 * square, with [aspect] (boardWidth / boardHeight) folded into the x term.
 */

/** A 3D point (or vector) in world / camera space, in cell units. */
data class Vec3(val x: Float, val y: Float, val z: Float)

/**
 * A projected point: [sx]/[sy] are normalized screen coordinates (origin centre,
 * +x right, +y up, roughly [-1, 1]); [depth] is the view-space distance; and
 * [visible] is false when the point is at or behind the near plane.
 */
data class Proj(val sx: Float, val sy: Float, val depth: Float, val visible: Boolean)

class Cam(
    val pos: Vec3,
    val right: Vec3,
    val up: Vec3,
    val fwd: Vec3,
    val focal: Float,
    val aspect: Float,
) {
    /** Transforms a world point into camera space (right, up, forward=depth). */
    fun cameraSpace(p: Vec3): Vec3 {
        val dx = p.x - pos.x
        val dy = p.y - pos.y
        val dz = p.z - pos.z
        return Vec3(
            dx * right.x + dy * right.y + dz * right.z,
            dx * up.x + dy * up.y + dz * up.z,
            dx * fwd.x + dy * fwd.y + dz * fwd.z,
        )
    }

    /** Perspective-divides a camera-space point into normalized screen coords. */
    fun projectCamera(c: Vec3): Proj {
        if (c.z <= NEAR) return Proj(0f, 0f, c.z, visible = false)
        return Proj(focal * c.x / c.z / aspect, focal * c.y / c.z, c.z, visible = true)
    }

    /** Projects a world point straight to normalized screen coords. */
    fun project(p: Vec3): Proj = projectCamera(cameraSpace(p))

    /**
     * Clips a camera-space segment to the near plane. Returns null when both ends
     * are behind it (fully culled); otherwise the visible portion, with any end
     * that was behind the plane pulled forward onto it. Lets the grid draw
     * straight world lines that pass behind the camera without artefacts.
     */
    fun clipNear(a: Vec3, b: Vec3): Pair<Vec3, Vec3>? {
        val aIn = a.z > NEAR
        val bIn = b.z > NEAR
        if (!aIn && !bIn) return null
        if (aIn && bIn) return a to b
        val t = (NEAR - a.z) / (b.z - a.z)
        val m = Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, NEAR)
        return if (aIn) a to m else m to b
    }

    companion object {
        /** Points nearer than this (in cell units along the view) are culled. */
        const val NEAR = 0.05f
    }
}

/** Heading angle in the board plane. Right=0, Down=π/2, Left=π, Up=-π/2. */
fun yawFor(dir: Direction): Float = atan2(dir.dy.toFloat(), dir.dx.toFloat())

// Camera poses. "Flat" is a near-straight-down top-down that closely matches the
// 2D renderer; "chase" sits behind and above the head looking along the heading.
private const val FLAT_PITCH = 1.4835f // ~85° down (avoids the 90° basis singularity)
private const val CHASE_PITCH = 0.62f // ~35.5° down
private const val CAM_BACK = 2.6f // cells behind the head centre
private const val CAM_HEIGHT = 2.2f // cells above the plane
private const val FOCAL_FLAT = 1.0f
private const val FOCAL_CHASE = 1.4f
private val FLAT_YAW = (-PI / 2).toFloat() // "Up": screen-up = world -y, matching 2D

/** Raised top-face height (cells) used to give snake/obstacle quads some bulk. */
const val ZTOP = 0.42f

/**
 * Builds the camera for blend [blend] in [0, 1]: 0 = flat top-down framing the
 * whole board, 1 = chase-cam behind the head at [yawTarget]. Smoothstepped so the
 * tilt eases in and out. [headX]/[headY] are the head's cell centre.
 */
fun blendedCam(
    headX: Float,
    headY: Float,
    boardW: Float,
    boardH: Float,
    yawTarget: Float,
    blend: Float,
    aspect: Float,
): Cam {
    val t = smoothstep(blend)
    val anchorX = lerpF(boardW / 2f, headX, t)
    val anchorY = lerpF(boardH / 2f, headY, t)
    val yaw = lerpAngle(FLAT_YAW, yawTarget, t)
    val pitch = lerpF(FLAT_PITCH, CHASE_PITCH, t)
    val back = lerpF(0f, CAM_BACK, t)
    val focal = lerpF(FOCAL_FLAT, FOCAL_CHASE, t)
    // Height that frames the board vertically at the flat pose (focal*boardH/2).
    val height = lerpF(FOCAL_FLAT * boardH / 2f, CAM_HEIGHT, t)
    return chaseCam(anchorX, anchorY, yaw, pitch, back, height, focal, aspect)
}

/**
 * Assembles a [Cam] from high-level parameters. The camera sits [back] cells
 * behind the [anchorX]/[anchorY] point along [yaw] and [height] cells up, looking
 * down by [pitch] (radians, positive = downward).
 */
fun chaseCam(
    anchorX: Float,
    anchorY: Float,
    yaw: Float,
    pitch: Float,
    back: Float,
    height: Float,
    focal: Float,
    aspect: Float,
): Cam {
    val cy = cos(yaw)
    val sy = sin(yaw)
    val pos = Vec3(anchorX - cy * back, anchorY - sy * back, height)
    val cp = cos(pitch)
    val sp = sin(pitch)
    val fwd = normalize(Vec3(cp * cy, cp * sy, -sp))
    val right = normalize(cross(Vec3(0f, 0f, 1f), fwd))
    val up = normalize(cross(fwd, right))
    return Cam(pos, right, up, fwd, focal, aspect)
}

/** Linear interpolation between two angles along the shortest path. */
fun lerpAngle(a: Float, b: Float, t: Float): Float = a + shortestDelta(a, b) * t

/** Signed shortest angular delta from [a] to [b], in (-π, π]. */
fun shortestDelta(a: Float, b: Float): Float {
    val twoPi = (2 * PI).toFloat()
    var d = (b - a) % twoPi
    if (d > PI) d -= twoPi
    if (d < -PI) d += twoPi
    return d
}

internal fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

internal fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun cross(a: Vec3, b: Vec3): Vec3 = Vec3(
    a.y * b.z - a.z * b.y,
    a.z * b.x - a.x * b.z,
    a.x * b.y - a.y * b.x,
)

private fun normalize(v: Vec3): Vec3 {
    val len = sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
    return if (len <= 1e-6f) v else Vec3(v.x / len, v.y / len, v.z / len)
}

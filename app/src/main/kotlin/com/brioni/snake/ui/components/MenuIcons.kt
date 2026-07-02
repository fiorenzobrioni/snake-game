package com.brioni.snake.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Bespoke, hand-authored menu glyphs. The bundled `material-icons-core` set is
 * tiny and its nearest fits read wrong (Refresh looks like "reload", List is
 * anonymous), while pulling in `material-icons-extended` would bloat the APK
 * for five drawings — and a game whose every sprite is drawn in code deserves
 * icons cut from the same cloth. All are 24x24, single-colour (drawn in white,
 * recoloured by [androidx.compose.material3.Icon]'s tint) and built from the
 * same chunky geometry so they read as one family at tile size.
 */
object MenuIcons {

    /** Custom Game: three slider tracks with offset knobs (a "tune" board). */
    val Tune: ImageVector by lazy {
        icon("MenuTune") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(4f, 6.5f); lineTo(20f, 6.5f)
                moveTo(4f, 12f); lineTo(20f, 12f)
                moveTo(4f, 17.5f); lineTo(20f, 17.5f)
            }
            path(fill = SolidColor(Color.White)) {
                circle(14.6f, 6.5f, 2.3f)
                circle(8.6f, 12f, 2.3f)
                circle(16.4f, 17.5f, 2.3f)
            }
        }
    }

    /** Daily Challenge: a calendar - solid header, two pins, one marked day. */
    val Calendar: ImageVector by lazy {
        icon("MenuCalendar") {
            // Frame: a solid header band with an open grid area cut below it.
            path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
                roundRect(3.6f, 5.2f, 20.4f, 20f, 2.6f)
                roundRect(5.4f, 9.8f, 18.6f, 18.2f, 1.2f)
            }
            // Binder pins sticking up through the header.
            path(fill = SolidColor(Color.White)) {
                roundRect(7.1f, 2.8f, 8.9f, 7f, 0.9f)
                roundRect(15.1f, 2.8f, 16.9f, 7f, 0.9f)
            }
            // Today: a marked day plus its entry line.
            path(fill = SolidColor(Color.White)) {
                circle(9.3f, 13.6f, 1.6f)
                roundRect(12.6f, 12.7f, 16.6f, 14.5f, 0.9f)
            }
        }
    }

    /** Random Challenge: a die on its five face. */
    val Dice: ImageVector by lazy {
        icon("MenuDice") {
            path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
                roundRect(3.4f, 3.4f, 20.6f, 20.6f, 4.4f)
                roundRect(5.3f, 5.3f, 18.7f, 18.7f, 2.9f)
            }
            path(fill = SolidColor(Color.White)) {
                circle(8.4f, 8.4f, 1.65f)
                circle(15.6f, 8.4f, 1.65f)
                circle(12f, 12f, 1.65f)
                circle(8.4f, 15.6f, 1.65f)
                circle(15.6f, 15.6f, 1.65f)
            }
        }
    }

    /** Records: a trophy cup with handles, stem and base. */
    val Trophy: ImageVector by lazy {
        icon("MenuTrophy") {
            path(fill = SolidColor(Color.White)) {
                moveTo(6.4f, 3.6f)
                lineTo(17.6f, 3.6f)
                lineTo(17.6f, 8.4f)
                curveTo(17.6f, 11.5f, 15.2f, 13.6f, 12f, 13.6f)
                curveTo(8.8f, 13.6f, 6.4f, 11.5f, 6.4f, 8.4f)
                close()
            }
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(6.3f, 5.4f)
                curveTo(3.7f, 5.4f, 3.5f, 9.4f, 6.9f, 10.3f)
                moveTo(17.7f, 5.4f)
                curveTo(20.3f, 5.4f, 20.5f, 9.4f, 17.1f, 10.3f)
            }
            path(fill = SolidColor(Color.White)) {
                roundRect(10.9f, 13.4f, 13.1f, 16.4f, 0.6f)
                roundRect(7.9f, 16.4f, 16.1f, 18.9f, 1.2f)
            }
        }
    }

    /** Achievements: a ribboned medal ring with a star at its heart. */
    val Medal: ImageVector by lazy {
        icon("MenuMedal") {
            // The V of ribbon tails, tucking behind the ring.
            path(fill = SolidColor(Color.White)) {
                moveTo(7.9f, 3f); lineTo(10.8f, 3f); lineTo(12.7f, 9.6f); lineTo(9.9f, 10.7f); close()
                moveTo(16.1f, 3f); lineTo(13.2f, 3f); lineTo(11.3f, 9.6f); lineTo(14.1f, 10.7f); close()
            }
            path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) {
                circle(12f, 14.6f, 5.4f)
                circle(12f, 14.6f, 3.9f)
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 11.9f)
                lineTo(12.68f, 13.67f)
                lineTo(14.57f, 13.77f)
                lineTo(13.09f, 14.96f)
                lineTo(13.59f, 16.78f)
                lineTo(12f, 15.75f)
                lineTo(10.41f, 16.78f)
                lineTo(10.91f, 14.96f)
                lineTo(9.43f, 13.77f)
                lineTo(11.32f, 13.67f)
                close()
            }
        }
    }
}

/** A 24x24 icon builder with the shared defaults. */
private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()

/** Appends a full circle at ([cx], [cy]) with radius [r] (two half arcs). */
private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
    arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
    close()
}

/** Appends a rounded rectangle from ([l], [t]) to ([r], [b]) with corner [rad]. */
private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
    moveTo(l + rad, t)
    lineTo(r - rad, t)
    arcTo(rad, rad, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = r, y1 = t + rad)
    lineTo(r, b - rad)
    arcTo(rad, rad, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = r - rad, y1 = b)
    lineTo(l + rad, b)
    arcTo(rad, rad, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = l, y1 = b - rad)
    lineTo(l, t + rad)
    arcTo(rad, rad, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = l + rad, y1 = t)
    close()
}

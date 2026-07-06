package io.github.seijikohara.femto.ui.home.components.driving

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.seijikohara.femto.R

/** The eight compass points used by the driving-face heading badge. */
internal enum class CompassDirection { N, NE, E, SE, S, SW, W, NW }

/**
 * Map a heading in degrees (0 = north, clockwise) to the nearest of eight compass
 * points. Pure so the sector maths is JVM-testable; normalizes any real degree
 * (negative, ≥360) into 0..360 first.
 */
internal fun compassDirectionOf(bearingDeg: Float): CompassDirection {
    val normalized = ((bearingDeg % 360f) + 360f) % 360f
    val sector = (((normalized + 22.5f) % 360f) / 45f).toInt()
    return CompassDirection.entries[sector]
}

/** The localized single/double-letter label for this point (N, NE, …). */
@Composable
internal fun CompassDirection.label(): String =
    stringResource(
        when (this) {
            CompassDirection.N -> R.string.driving_compass_n
            CompassDirection.NE -> R.string.driving_compass_ne
            CompassDirection.E -> R.string.driving_compass_e
            CompassDirection.SE -> R.string.driving_compass_se
            CompassDirection.S -> R.string.driving_compass_s
            CompassDirection.SW -> R.string.driving_compass_sw
            CompassDirection.W -> R.string.driving_compass_w
            CompassDirection.NW -> R.string.driving_compass_nw
        },
    )

/**
 * Degrees (0 = north, clockwise) this eight-point compass direction snaps to —
 * the angle the driving-face heading glyph rotates to. Mirrors the same eight
 * 45°-wide sectors [compassDirectionOf] already snapped the raw GPS bearing
 * into, so the glyph's rotation never disagrees with the point it represents.
 */
internal val CompassDirection.degrees: Float
    get() =
        when (this) {
            CompassDirection.N -> 0f
            CompassDirection.NE -> 45f
            CompassDirection.E -> 90f
            CompassDirection.SE -> 135f
            CompassDirection.S -> 180f
            CompassDirection.SW -> 225f
            CompassDirection.W -> 270f
            CompassDirection.NW -> 315f
        }

package io.github.seijikohara.femto.ui.home.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.github.seijikohara.femto.data.location.TripScenePalette
import io.github.seijikohara.femto.data.location.lightSceneLineTone
import io.github.seijikohara.femto.ui.theme.LocalFemtoDarkTheme
import io.github.seijikohara.femto.ui.theme.TripSceneBackground
import io.github.seijikohara.femto.ui.theme.TripSceneBackgroundLight
import io.github.seijikohara.femto.ui.theme.TripSceneHeadLight

// Accent dimming for the dark scene's ground grid + chrome: keep the sci-fi
// floor subtle so it never competes with the hero speed line.
private const val DARK_GRID_SCALE = 0.42f

// The light grid takes the accent through the same jewel-tone transform as the
// speed line, then recedes toward the light backdrop — a soft coloured floor
// rather than a dark lattice over the near-white scene.
private const val LIGHT_GRID_RECEDE = 0.45f

/**
 * Build the flyover's [TripScenePalette] from the *rendered* theme — the
 * sanctioned [LocalFemtoDarkTheme] flag (which follows a forced ThemeMode, not
 * just the system) plus the Material scheme accent.
 *
 * Keyed on the dark flag ALONE, snapshotting the accent at each flip. The scheme
 * accent cross-fades over ~500ms on a theme/accent switch; keying on it would
 * rebuild the whole wireframe (and re-upload the native vertex buffer) every
 * frame of that fade. The grid tint therefore snaps to the accent at the light/
 * dark flip rather than cross-fading with it — an imperceptible trade for not
 * churning the renderer. Accent-only changes are made in Settings with the
 * flyover closed, so a stale grid there cannot be seen.
 */
@Composable
internal fun rememberTripScenePalette(): TripScenePalette {
    val dark = LocalFemtoDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    return remember(dark) {
        if (dark) {
            TripScenePalette(
                isDark = true,
                background = TripSceneBackground.rgb(),
                grid = primary.scaledRgb(DARK_GRID_SCALE),
                head = floatArrayOf(1f, 1f, 1f),
            )
        } else {
            val background = TripSceneBackgroundLight.rgb()
            val toned = lightSceneLineTone(primary.red, primary.green, primary.blue)
            TripScenePalette(
                isDark = false,
                background = background,
                grid =
                    FloatArray(3) { i ->
                        toned[i] + (background[i] - toned[i]) * LIGHT_GRID_RECEDE
                    },
                head = TripSceneHeadLight.rgb(),
            )
        }
    }
}

/** This scene backdrop as a Compose [Color] for the in-window states to paint. */
internal fun TripScenePalette.backgroundColor(): Color = Color(background[0], background[1], background[2])

private fun Color.rgb(): FloatArray = floatArrayOf(red, green, blue)

private fun Color.scaledRgb(scale: Float): FloatArray = floatArrayOf(red * scale, green * scale, blue * scale)

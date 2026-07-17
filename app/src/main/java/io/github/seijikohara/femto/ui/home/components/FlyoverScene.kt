package io.github.seijikohara.femto.ui.home.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.github.seijikohara.femto.data.location.TripScenePalette
import io.github.seijikohara.femto.ui.theme.LocalFemtoDarkTheme
import io.github.seijikohara.femto.ui.theme.TripSceneBackground
import io.github.seijikohara.femto.ui.theme.TripSceneBackgroundLight
import io.github.seijikohara.femto.ui.theme.TripSceneHeadLight

// Accent dimming for the ground grid + chrome: keep the sci-fi floor subtle so
// it never competes with the hero speed line. Scales the scheme primary down
// toward the scene — a touch dimmer on the dark scene, a touch darker on light
// so it stays legible against the near-white backdrop.
private const val DARK_GRID_SCALE = 0.42f
private const val LIGHT_GRID_SCALE = 0.5f

// Darken the turbo speed line for the light scene so it reads against the light
// backdrop under alpha-over blending (the stops are tuned bright-on-dark).
private const val LIGHT_LINE_SCALE = 0.55f

/**
 * Build the flyover's [TripScenePalette] from the *rendered* theme — the
 * sanctioned [LocalFemtoDarkTheme] flag (which follows a forced ThemeMode, not
 * just the system) plus the Material scheme accent. Remembered on those inputs
 * so the same instance is handed down until the theme actually changes, which is
 * what gates the wireframe rebuild and the native theme push.
 */
@Composable
internal fun rememberTripScenePalette(): TripScenePalette {
    val dark = LocalFemtoDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    return remember(dark, primary) {
        if (dark) {
            TripScenePalette(
                isDark = true,
                background = TripSceneBackground.rgb(),
                grid = primary.scaledRgb(DARK_GRID_SCALE),
                head = floatArrayOf(1f, 1f, 1f),
                lineScale = 1f,
            )
        } else {
            TripScenePalette(
                isDark = false,
                background = TripSceneBackgroundLight.rgb(),
                grid = primary.scaledRgb(LIGHT_GRID_SCALE),
                head = TripSceneHeadLight.rgb(),
                lineScale = LIGHT_LINE_SCALE,
            )
        }
    }
}

/** This scene backdrop as a Compose [Color] for the in-window states to paint. */
internal fun TripScenePalette.backgroundColor(): Color = Color(background[0], background[1], background[2])

private fun Color.rgb(): FloatArray = floatArrayOf(red, green, blue)

private fun Color.scaledRgb(scale: Float): FloatArray = floatArrayOf(red * scale, green * scale, blue * scale)

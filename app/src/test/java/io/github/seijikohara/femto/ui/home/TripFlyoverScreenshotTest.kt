package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.seijikohara.femto.data.location.TripGeometry
import io.github.seijikohara.femto.data.location.TripWireframe
import io.github.seijikohara.femto.testfixtures.ScreenshotCompareOptions
import io.github.seijikohara.femto.testfixtures.fakeTrackPoint
import io.github.seijikohara.femto.ui.home.components.TripFlyoverFallback
import io.github.seijikohara.femto.ui.home.components.backgroundColor
import io.github.seijikohara.femto.ui.home.components.rememberTripScenePalette
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sin

/**
 * Goldens for the trip-flyover scene in both themes, rendered through the 2D
 * fallback — which shares the baked wireframe (and therefore the exact scene
 * colours) with the native Vulkan path. This pins the "mesmerize" look: the
 * turbo gradient bright-on-dark, and the jewel-toned gradient on the light
 * scene (a plain darkening once collapsed it into near-black line work). The
 * frame is deterministic: fixed synthetic geometry, a fixed draw-on playhead,
 * and the orbit clock frozen just past the intro dolly so the camera sits at
 * its settled orbit distance.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class TripFlyoverScreenshotTest {
    @Test
    @Config(qualifiers = "w853dp-h512dp-mdpi")
    fun flyover_scene_dark() = capture("trip-flyover-dark-853x512", darkTheme = true)

    @Test
    @Config(qualifiers = "w853dp-h512dp-mdpi")
    fun flyover_scene_light() = capture("trip-flyover-light-853x512", darkTheme = false)

    private fun capture(
        name: String,
        darkTheme: Boolean,
    ) {
        captureRoboImage(filePath = "src/test/screenshots/$name.png", roborazziOptions = ScreenshotCompareOptions) {
            FemtoTheme(darkTheme = darkTheme) {
                FlyoverScene()
            }
        }
    }

    @Composable
    private fun FlyoverScene() {
        val palette = rememberTripScenePalette()
        val geometry = requireNotNull(TripGeometry.from(sCurveTrip()))
        Box(Modifier.fillMaxSize().background(palette.backgroundColor())) {
            TripFlyoverFallback(
                wireframe = TripWireframe.build(geometry, palette),
                progress = 0.85f,
                elapsed = 6f,
                palette = palette,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // The preview's climbing S-curve: exercises the speed gradient across its
    // range, the elevation curtain, and the grid in one deterministic frame.
    private fun sCurveTrip() =
        (0 until 60).map { i ->
            fakeTrackPoint(
                tripId = 0L,
                timeMs = i * 1_000L,
                latitude = 35.6580 + i * 0.0004,
                longitude = 139.7016 + 0.0008 * sin(i * 0.25),
                speedMps = 6f + 10f * (0.5f + 0.5f * sin(i * 0.4f)),
                altitudeM = 20.0 + i * 2.5,
            )
        }
}

package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.seijikohara.femto.data.clock.ClockTick
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.testfixtures.fakeAddress
import io.github.seijikohara.femto.testfixtures.fakeCalendarSnapshot
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.testfixtures.fakeSystemStatus
import io.github.seijikohara.femto.testfixtures.fakeTripState
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.home.components.DashboardScaffold
import io.github.seijikohara.femto.ui.home.components.GlassConfig
import io.github.seijikohara.femto.ui.home.components.MapConfig
import io.github.seijikohara.femto.ui.home.components.PanelVisibility
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime

/**
 * JVM/Robolectric screenshot regression for the main dashboard across the
 * head-unit display geometries. Each `@Config(qualifiers = …)` lays the dashboard
 * out in a real window of that size (the idiomatic Roborazzi way to test multiple
 * device sizes), so this catches visual breakage — clipping, overlap, truncation —
 * that the layout/content-presence instrumented test (DashboardResponsiveTest)
 * cannot. Runs on the JVM, so unlike androidTest it executes in CI
 * (verifyRoborazziDebug).
 *
 * Pinned to `sdk = 33` like the other Robolectric tests (sidesteps the compileSdk
 * gap). `location = null` keeps the map on its static fallback (no MapLibre GL).
 * Caveats baked into the goldens: Robolectric's software rasterizer does not render
 * the Haze RenderEffect blur (the glass overlays show their tint, not the frost),
 * and downloadable Google Fonts fall back to the system face — both deterministic.
 * Goldens must be recorded on the CI OS (Linux) so verifyRoborazziDebug matches; a
 * small changeThreshold absorbs residual sub-pixel antialiasing differences.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class DashboardScreenshotTest {
    @Test
    @Config(qualifiers = "w853dp-h512dp-xhdpi")
    fun dashboard_head_unit_5x3() = capture("head-unit-853x512")

    @Test
    @Config(qualifiers = "w640dp-h360dp-xhdpi")
    fun dashboard_landscape_16x9() = capture("landscape-640x360")

    @Test
    @Config(qualifiers = "w640dp-h240dp-xhdpi")
    fun dashboard_ultrawide_8x3() = capture("ultrawide-640x240")

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun dashboard_portrait() = capture("portrait-360x640")

    private fun capture(name: String) {
        captureRoboImage(filePath = "src/test/screenshots/dashboard-$name.png", roborazziOptions = OPTIONS) {
            FemtoTheme {
                DashboardScaffold(
                    uiState = STATE,
                    is24Hour = true,
                    showClockSeconds = true,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    mapConfig = MapConfig(),
                    panels = PanelVisibility(),
                    glassConfig = GlassConfig(),
                    onAction = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    private companion object {
        // A small tolerance absorbs sub-pixel antialiasing differences between the
        // golden-record host and the CI runner while still catching real layout
        // regressions.
        val OPTIONS =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            )

        val STATE =
            HomeUiState.Initial.copy(
                clock = ClockTick(LocalTime.of(14, 32), LocalDate.of(2026, 5, 1)),
                location = null,
                address = fakeAddress(),
                weather = fakeWeatherSnapshot(),
                calendar = fakeCalendarSnapshot(),
                musicState = MusicCardState.Playing(fakeNowPlaying()),
                systemStatus = fakeSystemStatus(),
                tripState = fakeTripState(),
            )
    }
}

package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.seijikohara.femto.data.clock.ClockTick
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.testfixtures.fakeAddress
import io.github.seijikohara.femto.testfixtures.fakeCalendarSnapshot
import io.github.seijikohara.femto.testfixtures.fakeLocation
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
 * JVM/Robolectric screenshot regression for the driving preset face — the
 * `activePreset = PresetId.DRIVING` counterpart to [DashboardScreenshotTest],
 * which only covers the default cockpit preset. Same rationale and caveats
 * apply (see that file's KDoc); this test isolates the driving-specific
 * layout (map full-bleed to the edges, hero speed readout) at the two
 * geometries most representative of where the driving face ships: the
 * reference head unit and a mainstream 16:9 unit.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class DashboardPresetScreenshotTest {
    @Test
    @Config(qualifiers = "w853dp-h512dp-mdpi")
    fun dashboard_driving_head_unit_853x512() = capture("head-unit-853x512")

    @Test
    @Config(qualifiers = "w1280dp-h720dp-mdpi")
    fun dashboard_driving_mainstream_1280x720() = capture("mainstream-1280x720")

    private fun capture(name: String) {
        captureRoboImage(filePath = "src/test/screenshots/dashboard-driving-$name.png", roborazziOptions = OPTIONS) {
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
                    activePreset = PresetId.DRIVING,
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
                // 09:00 so the fixture's 10:30 event is still upcoming today — the
                // default THROUGH_TOMORROW briefing scope then surfaces it (at 14:32 both
                // of today's events are past and the scope would show only weather).
                clock = ClockTick(LocalTime.of(9, 0), LocalDate.of(2026, 5, 1)),
                location = fakeLocation(bearingDegrees = 45f),
                address = fakeAddress(road = "Oak St"),
                weather = fakeWeatherSnapshot(),
                calendar = fakeCalendarSnapshot(),
                musicState = MusicCardState.Playing(fakeNowPlaying()),
                systemStatus = fakeSystemStatus(),
                tripState = fakeTripState(currentSpeedMs = 16.7),
            )
    }
}

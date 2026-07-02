package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.seijikohara.femto.testfixtures.fakeCalendarSnapshot
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.home.components.CalendarPanel
import io.github.seijikohara.femto.ui.home.components.WeatherPanel
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Calendar / weather maximize-panel goldens across a head-unit landscape and a
 * portrait geometry. Same recording flow as the music panel: goldens are
 * recorded on CI and committed from the artifact (macOS and Linux anti-alias
 * differently, so a local record would not match the CI runner).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class PanelScreenshotTest {
    @Test
    @Config(qualifiers = "w853dp-h512dp-mdpi")
    fun calendar_panel_head_unit() =
        capture("calendar-panel-head-unit-853x512") {
            CalendarPanel(
                snapshot = fakeCalendarSnapshot(),
                is24Hour = true,
                onOpenExternal = {},
                onClose = {},
                modifier = Modifier.fillMaxSize(),
            )
        }

    @Test
    @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun calendar_panel_portrait() =
        capture("calendar-panel-portrait-412x915") {
            CalendarPanel(
                snapshot = fakeCalendarSnapshot(),
                is24Hour = true,
                onOpenExternal = {},
                onClose = {},
                modifier = Modifier.fillMaxSize(),
            )
        }

    @Test
    @Config(qualifiers = "w853dp-h512dp-mdpi")
    fun weather_panel_head_unit() =
        capture("weather-panel-head-unit-853x512") {
            WeatherPanel(
                snapshot = fakeWeatherSnapshot(),
                temperatureUnit = TemperatureUnit.CELSIUS,
                speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                is24Hour = true,
                onOpenExternal = {},
                onClose = {},
                modifier = Modifier.fillMaxSize(),
            )
        }

    @Test
    @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun weather_panel_portrait() =
        capture("weather-panel-portrait-412x915") {
            WeatherPanel(
                snapshot = fakeWeatherSnapshot(),
                temperatureUnit = TemperatureUnit.CELSIUS,
                speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                is24Hour = true,
                onOpenExternal = {},
                onClose = {},
                modifier = Modifier.fillMaxSize(),
            )
        }

    private fun capture(
        name: String,
        content: @Composable () -> Unit,
    ) {
        captureRoboImage(filePath = "src/test/screenshots/$name.png", roborazziOptions = OPTIONS) {
            FemtoTheme { content() }
        }
    }

    private companion object {
        // Same tolerance as the dashboard / music-panel goldens: absorbs sub-pixel
        // antialiasing differences between the record host and the CI runner.
        val OPTIONS =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            )
    }
}

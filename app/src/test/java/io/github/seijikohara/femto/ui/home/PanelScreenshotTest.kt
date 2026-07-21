package io.github.seijikohara.femto.ui.home

import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.graphics.createBitmap
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.seijikohara.femto.data.apps.AppEntry
import io.github.seijikohara.femto.data.apps.DrawerIconSize
import io.github.seijikohara.femto.data.apps.DrawerLayout
import io.github.seijikohara.femto.testfixtures.ScreenshotCompareOptions
import io.github.seijikohara.femto.testfixtures.fakeCalendarSnapshot
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.drawer.AppDrawerUiState
import io.github.seijikohara.femto.ui.drawer.AppsPanel
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

    // Dark variant: the weather charts draw from a curated dark palette
    // (temperature ramp, precipitation blue, UV scale) that the light-theme
    // goldens above never exercise.
    @Test
    @Config(qualifiers = "w853dp-h512dp-mdpi")
    fun weather_panel_head_unit_dark() =
        capture("weather-panel-head-unit-853x512-dark", darkTheme = true) {
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

    // Apps maximize panel: the #273 fix is layout, not colour — the pinned dock,
    // the Recent row, and the full all-apps grid are visible together, none of
    // them collapsed to a single line. Head-unit + portrait geometries, plus a
    // dark variant (the grid/dock render over the dark glass chrome).
    @Test
    @Config(qualifiers = "w853dp-h512dp-mdpi")
    fun apps_panel_head_unit() = capture("apps-panel-head-unit-853x512") { AppsPanelSample() }

    @Test
    @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun apps_panel_portrait() = capture("apps-panel-portrait-412x915") { AppsPanelSample() }

    @Test
    @Config(qualifiers = "w853dp-h512dp-mdpi")
    fun apps_panel_head_unit_dark() =
        capture("apps-panel-head-unit-853x512-dark", darkTheme = true) { AppsPanelSample() }

    @Composable
    private fun AppsPanelSample() {
        // A 1x1 icon renders as a dot — the goldens verify the panel's LAYOUT
        // (pins + recents + grid coexisting), not icon art. Apps arrive
        // alphabetically sorted (AppsRepository.queryApps orders them), so the
        // fixture sorts to match — the A-Z rail buckets depend on it.
        val icon = createBitmap(1, 1)
        val apps =
            listOf("Maps", "Music", "Phone", "Messages", "Calendar", "Weather", "Camera", "Clock", "Notes", "Settings")
                .sorted()
                .map { AppEntry(ComponentName("com.example.${it.lowercase()}", ".Main"), it, icon) }
        // Recents are recency-ordered (not alphabetical), a distinct signal.
        val recentLabels = setOf("Maps", "Weather", "Phone", "Music")
        AppsPanel(
            uiState = AppDrawerUiState.Content(apps = apps, recentApps = apps.filter { it.label in recentLabels }),
            layout = DrawerLayout.GRID,
            iconSize = DrawerIconSize.MEDIUM,
            pinned = listOf("com.example.maps/.Main", "com.example.phone/.Main"),
            onLaunch = {},
            onTogglePin = {},
            onOpenAppInfo = {},
            onRequestUninstall = {},
            onToggleLayout = {},
            onSelectIconSize = {},
            onReorderPins = {},
            onRetry = {},
            onClose = {},
            modifier = Modifier.fillMaxSize(),
        )
    }

    private fun capture(
        name: String,
        darkTheme: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        captureRoboImage(filePath = "src/test/screenshots/$name.png", roborazziOptions = ScreenshotCompareOptions) {
            FemtoTheme(darkTheme = darkTheme) {
                if (darkTheme) {
                    // The glass panel is translucent; the light captures read fine
                    // over Robolectric's white window, but a dark capture needs a
                    // theme backdrop behind the glass or it renders milky.
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
                } else {
                    content()
                }
            }
        }
    }
}

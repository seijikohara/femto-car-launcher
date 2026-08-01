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
import io.github.seijikohara.femto.data.calendar.DayCell
import io.github.seijikohara.femto.data.calendar.EventItem
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
import java.time.LocalDate
import java.time.LocalTime

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

    // Dark variant: the agenda's per-calendar colour bars and dots are the one
    // thing here that a dark scheme can break — they are curated colours drawn
    // over the glass rather than Material roles that follow the theme. Weather
    // and apps already have a dark golden; the calendar was the gap.
    @Test
    @Config(qualifiers = "w853dp-h512dp-mdpi")
    fun calendar_panel_head_unit_dark() =
        capture("calendar-panel-head-unit-853x512-dark", darkTheme = true) {
            CalendarPanel(
                snapshot = COLOURED_CALENDAR,
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
            hidden = emptySet(),
            onLaunch = {},
            onTogglePin = {},
            onToggleHide = {},
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

    private companion object {
        // Two calendars with provider colours, so multipleCalendarsVisible opens the
        // colour-bar path. The default fixture leaves the bar off (one calendar, no
        // colours), which would have made a "colour bar" golden prove nothing.
        val COLOURED_CALENDAR =
            fakeCalendarSnapshot(
                multipleCalendarsVisible = true,
                days =
                    listOf(
                        DayCell(
                            LocalDate.of(2026, 5, 1),
                            "Fri",
                            listOf(
                                EventItem(
                                    LocalTime.of(10, 30),
                                    "Team standup",
                                    endTime = LocalTime.of(11, 0),
                                    location = "Room 4",
                                    color = 0xFF4285F4.toInt(),
                                ),
                                EventItem(
                                    LocalTime.of(14, 0),
                                    "Pick up kids",
                                    endTime = LocalTime.of(14, 30),
                                    color = 0xFFEA4335.toInt(),
                                ),
                            ),
                        ),
                        DayCell(LocalDate.of(2026, 5, 2), "Sat", emptyList()),
                        DayCell(
                            LocalDate.of(2026, 5, 3),
                            "Sun",
                            listOf(EventItem(LocalTime.of(9, 0), "Brunch", color = 0xFF34A853.toInt())),
                        ),
                        DayCell(
                            LocalDate.of(2026, 5, 6),
                            "Wed",
                            listOf(EventItem(null, "Holiday", color = 0xFFFBBC04.toInt())),
                        ),
                    ),
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

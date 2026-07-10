package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.testfixtures.fakeAddress
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.home.HomeUiState
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test

class DashboardScaffoldTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_all_panels_when_data_is_present() {
        // location = null keeps the map pane on its static fallback, so the test
        // exercises the scaffold layout without standing up a MapLibre GL surface.
        // The clock overlay self-times from the wall clock, so the dashboard has
        // no deterministic time string to assert; the map fallback, weather,
        // music, and dock panels carry the stable assertions instead.
        val uiState =
            HomeUiState.Initial.copy(
                location = null,
                address = fakeAddress(),
                weather = fakeWeatherSnapshot(),
                musicState = MusicCardState.Playing(fakeNowPlaying()),
            )
        rule.setContent {
            FemtoTheme {
                DashboardScaffold(
                    uiState = uiState,
                    is24Hour = true,
                    showClockSeconds = true,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    mapConfig = MapConfig(),
                    panels = PanelVisibility(),
                    glassConfig = GlassConfig(),
                    onAction = {},
                )
            }
        }
        rule.onNodeWithText("Map unavailable").assertIsDisplayed()
        rule.onNodeWithText("Strobe").assertIsDisplayed()
        rule.onNodeWithContentDescription("Apps").assertIsDisplayed()
    }

    @Test
    fun hides_info_panels_disabled_by_visibility_flags() {
        // Disabling every info-pane panel drops the whole info pane, so none of
        // their content renders while the map and dock stay put.
        val uiState =
            HomeUiState.Initial.copy(
                location = null,
                weather = fakeWeatherSnapshot(),
                musicState = MusicCardState.Playing(fakeNowPlaying()),
            )
        rule.setContent {
            FemtoTheme {
                DashboardScaffold(
                    uiState = uiState,
                    is24Hour = true,
                    showClockSeconds = true,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    mapConfig = MapConfig(),
                    panels = PanelVisibility(calendar = false, weather = false, music = false),
                    glassConfig = GlassConfig(),
                    onAction = {},
                )
            }
        }
        rule.onNodeWithText("Strobe").assertDoesNotExist()
        rule.onNodeWithText("Map unavailable").assertIsDisplayed()
        rule.onNodeWithContentDescription("Apps").assertIsDisplayed()
    }
}

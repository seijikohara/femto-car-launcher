package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.seijikohara.femto.data.display.MotionTier
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

    @Test
    fun expanding_the_weather_card_opens_the_panel_and_collapsing_returns_to_the_card() {
        // MotionTier.OFF makes maximize/minimize instant, so the panel's presence is
        // deterministic without waiting on enter/exit animations.
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
                    panels = PanelVisibility(),
                    glassConfig = GlassConfig(),
                    motionTier = MotionTier.OFF,
                    onAction = {},
                )
            }
        }
        // No panel is open yet, so the shared Collapse affordance (panel_collapse) is absent.
        rule.onNodeWithContentDescription("Collapse").assertDoesNotExist()
        // The weather card's whole body is the maximize affordance (weather_expand);
        // tapping it opens the full-screen weather panel, whose top bar shows Collapse.
        rule.onNodeWithContentDescription("Open full-screen weather").performClick()
        rule.onNodeWithContentDescription("Collapse").assertIsDisplayed()
        // Collapsing returns to the card: the panel and its Collapse affordance are gone.
        rule.onNodeWithContentDescription("Collapse").performClick()
        rule.onNodeWithContentDescription("Collapse").assertDoesNotExist()
    }

    @Test
    fun tapping_outside_an_open_panel_dismisses_it() {
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
                    panels = PanelVisibility(),
                    glassConfig = GlassConfig(),
                    motionTier = MotionTier.OFF,
                    onAction = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Open full-screen weather").performClick()
        rule.onNodeWithContentDescription("Collapse").assertIsDisplayed()
        // A tap on the margin ring outside the panel dismisses it. The mid-left
        // screen edge sits inside the ring on every orientation: the panel is
        // inset by the outer padding, and the cards/dock live on other edges.
        rule.onRoot().performTouchInput { click(Offset(5f, height / 2f)) }
        rule.onNodeWithContentDescription("Collapse").assertDoesNotExist()
    }
}

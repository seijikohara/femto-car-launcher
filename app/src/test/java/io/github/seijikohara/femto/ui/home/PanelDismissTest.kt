package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Contract tests for maximize-panel dismissal: a tap outside an open panel's
 * body closes it (the shared catcher in DashboardScaffold), and a tap ON the
 * body never does. Pins the TripPanel regression where the flyover's
 * surface-less root let body taps fall through to the catcher and close the
 * panel. Same Robolectric harness as [DashboardScreenshotTest]:
 * `location = null` keeps the map on its static fallback.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class PanelDismissTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<HomeAction>()

    private fun setDashboard() {
        actions.clear()
        rule.setContent {
            FemtoTheme {
                DashboardScaffold(
                    uiState = State,
                    is24Hour = true,
                    showClockSeconds = true,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    mapConfig = MapConfig(),
                    panels = PanelVisibility(),
                    glassConfig = GlassConfig(),
                    onAction = { actions += it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    // Every panel's collapse control is the open/closed probe. The music panel
    // labels its collapse "Collapse player"; the other four share "Collapse".
    private fun openPanel(
        trigger: String,
        collapse: String = "Collapse",
    ) {
        rule.onNodeWithContentDescription(trigger).performClick()
        rule.onNodeWithContentDescription(collapse).assertIsDisplayed()
    }

    private fun openTripPanel() = openPanel("Open trip flyover")

    // Bottom-centre, inside the dock bar's float margin: outside the overlay
    // box, over the map — the spot a user hits aiming "below the panel, next
    // to the dock". The overlay-box catcher never covered it.
    private fun tapBesideTheDock() = rule.onRoot().performTouchInput { click(Offset(centerX, height - 6f)) }

    private fun assertNoOpenPanel(collapse: String = "Collapse") =
        rule.onNodeWithContentDescription(collapse).assertDoesNotExist()

    @Test
    fun trip_panel_body_tap_does_not_dismiss() {
        setDashboard()
        openTripPanel()

        // Centre of the flyover body: inside the panel, clear of the HUD rows
        // and the collapse button.
        rule.onRoot().performTouchInput { click(center) }

        rule.onNodeWithContentDescription("Collapse").assertIsDisplayed()
    }

    @Test
    fun trip_panel_outside_tap_dismisses() {
        setDashboard()
        openTripPanel()

        // Top-left corner: the margin ring around the panel, over the map.
        rule.onRoot().performTouchInput { click(topLeft) }

        rule.onNodeWithContentDescription("Collapse").assertDoesNotExist()
    }

    @Test
    fun calendar_panel_dismisses_on_a_tap_beside_the_dock() {
        setDashboard()
        openPanel("Open full-screen calendar")
        tapBesideTheDock()
        assertNoOpenPanel()
    }

    @Test
    fun weather_panel_dismisses_on_a_tap_beside_the_dock() {
        setDashboard()
        openPanel("Open full-screen weather")
        tapBesideTheDock()
        assertNoOpenPanel()
    }

    @Test
    fun now_playing_panel_dismisses_on_a_tap_beside_the_dock() {
        setDashboard()
        openPanel("Open the full-screen player", collapse = "Collapse player")
        tapBesideTheDock()
        assertNoOpenPanel(collapse = "Collapse player")
    }

    @Test
    fun trip_panel_dismisses_on_a_tap_beside_the_dock() {
        setDashboard()
        openTripPanel()
        tapBesideTheDock()
        assertNoOpenPanel()
    }

    @Test
    fun apps_panel_dismisses_on_a_tap_beside_the_dock() {
        setDashboard()
        openPanel("Apps")
        tapBesideTheDock()
        assertNoOpenPanel()
    }

    @Test
    fun an_outside_tap_never_opens_maps_while_a_panel_is_open() {
        setDashboard()
        openPanel("Open full-screen calendar")

        tapBesideTheDock()

        // The tap must dismiss, not fall through to the map's OpenMaps tap —
        // launching an external app from a "close the panel" gesture is the
        // failure this pins.
        kotlin.test.assertTrue(actions.none { it is HomeAction.OpenMaps })
    }

    private companion object {
        val State =
            HomeUiState.Initial.copy(
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

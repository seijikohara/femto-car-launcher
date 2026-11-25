package io.github.seijikohara.femto.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.seijikohara.femto.data.places.PlaceTarget
import io.github.seijikohara.femto.data.places.ReceivedPlace
import io.github.seijikohara.femto.testfixtures.DashboardFixtures
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
import kotlin.test.assertEquals

/**
 * A place another app sent reaches the destination panel exactly once: the
 * dashboard opens the panel on it, the panel's host hands it to its ViewModel
 * and reports it handled (which empties the inbox), and a recomposition that
 * still carries the same place hands nothing over again. Without the report
 * the inbox would keep the place, and every recreation would reopen the panel.
 * The real panel host and ViewModel render here; `location = null` keeps the
 * map on its static fallback (as in PanelDismissTest).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class ReceivedPlaceDashboardTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<HomeAction>()
    private var received by mutableStateOf<ReceivedPlace?>(null)

    // Flipped to recompose the dashboard with everything else, the received place included, unchanged.
    private var is24Hour by mutableStateOf(true)

    private fun setDashboard() =
        rule.setContent {
            FemtoTheme {
                DashboardScaffold(
                    uiState = DashboardFixtures.state.copy(location = null),
                    is24Hour = is24Hour,
                    showClockSeconds = true,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    mapConfig = MapConfig(),
                    panels = PanelVisibility(),
                    glassConfig = GlassConfig(),
                    onAction = { actions += it },
                    clock = DashboardFixtures.fixedClock,
                    receivedPlace = received,
                )
            }
        }

    private val handled get() = actions.filterIsInstance<HomeAction.ReceivedPlaceHandled>()

    @Test
    fun a_received_place_opens_the_panel_and_is_reported_handled_once() {
        setDashboard()

        received = CASTLE
        rule.waitForIdle()

        rule.onNodeWithText("Matsuyama Castle").assertExists()
        assertEquals(listOf(HomeAction.ReceivedPlaceHandled(CASTLE)), handled)
    }

    @Test
    fun a_recomposition_with_the_same_place_hands_nothing_over_again() {
        setDashboard()
        received = CASTLE
        rule.waitForIdle()
        // Put the place away: a second hand-over would bring it back.
        rule.onNodeWithText("Search instead").performScrollTo().performClick()

        is24Hour = false
        rule.waitForIdle()

        rule.onNodeWithText("Matsuyama Castle").assertDoesNotExist()
        assertEquals(1, handled.size)
    }

    private companion object {
        val CASTLE = ReceivedPlace.Found(PlaceTarget.Point(33.845611, 132.765611), "Matsuyama Castle")
    }
}

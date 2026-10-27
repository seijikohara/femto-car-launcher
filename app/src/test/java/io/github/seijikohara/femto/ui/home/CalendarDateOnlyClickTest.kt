package io.github.seijikohara.femto.ui.home

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.calendar.CalendarSnapshot
import io.github.seijikohara.femto.testfixtures.DashboardFixtures
import io.github.seijikohara.femto.testfixtures.fakeCalendarSnapshot
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
 * The date-only calendar card's tap on the dashboard. A granted snapshot opens
 * the full-screen calendar panel; a denied or failed one still shows the head
 * but carries no click action, and a tap on it opens no panel and reaches
 * nothing beneath it. Same Robolectric harness as [PanelDismissTest], with the
 * weather card hidden so the card is the wrapped head the user taps.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class CalendarDateOnlyClickTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<HomeAction>()

    @Test
    fun `a granted snapshot opens the calendar panel`() {
        setDashboard(fakeCalendarSnapshot())
        rule.onNodeWithText(HeadMonth).assertHasClickAction()

        rule.onNodeWithContentDescription(string(R.string.calendar_expand)).performClick()

        // The open panel's collapse control is the open/closed probe, as in PanelDismissTest.
        rule.onNodeWithContentDescription(string(R.string.panel_collapse)).assertIsDisplayed()
    }

    @Test
    fun `a denied snapshot shows the head with no tap`() =
        assertHeadWithoutTap(fakeCalendarSnapshot(hasCalendarAccess = false), R.string.calendar_permission_denied)

    @Test
    fun `a failed snapshot shows the head with no tap`() =
        assertHeadWithoutTap(fakeCalendarSnapshot(queryFailed = true), R.string.calendar_query_failed)

    private fun assertHeadWithoutTap(
        snapshot: CalendarSnapshot,
        agendaHint: Int,
    ) {
        setDashboard(snapshot)
        rule.onNodeWithText(HeadMonth).assertIsDisplayed().assertHasNoClickAction()
        rule.onNodeWithText(string(agendaHint)).assertDoesNotExist()
        rule.onNodeWithContentDescription(string(R.string.calendar_expand)).assertDoesNotExist()

        rule.onNodeWithText(HeadMonth).performClick()

        rule.onNodeWithContentDescription(string(R.string.panel_collapse)).assertDoesNotExist()
        assertEquals(emptyList(), actions)
    }

    private fun setDashboard(calendar: CalendarSnapshot) {
        actions.clear()
        rule.setContent {
            FemtoTheme {
                DashboardScaffold(
                    uiState = DashboardFixtures.state.copy(calendar = calendar),
                    is24Hour = true,
                    showClockSeconds = true,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    mapConfig = MapConfig(),
                    panels = PanelVisibility(weather = false),
                    glassConfig = GlassConfig(),
                    onAction = { actions += it },
                    modifier = Modifier.fillMaxSize(),
                    calendarDateOnly = true,
                    clock = DashboardFixtures.fixedClock,
                    mapSurface = { _, _ -> Box(Modifier.fillMaxSize()) },
                )
            }
        }
    }

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private companion object {
        // The head's month line: the fixture's label, uppercased as the head draws it.
        val HeadMonth = fakeCalendarSnapshot().monthLabel.uppercase()
    }
}

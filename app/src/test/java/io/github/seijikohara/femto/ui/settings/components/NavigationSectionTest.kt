package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.seijikohara.femto.data.routing.RouteRowTime
import io.github.seijikohara.femto.ui.settings.NavigationSettingsUi
import io.github.seijikohara.femto.ui.settings.SettingsAction
import io.github.seijikohara.femto.ui.settings.SettingsUiState
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The Navigation category's rows (issue #389): three switches for what the route preview does besides the line on the
 * map, each on by default, the route row's time, the arrival time by default, and under Guidance the turn card's three
 * switches, its two refinements shown only while the card is on, and the switch that draws the vehicle on the line, on
 * by default. Each row reports its own action.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w853dp-h1600dp-mdpi")
class NavigationSectionTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<SettingsAction>()

    private fun setSection(navigation: NavigationSettingsUi = NavigationSettingsUi.Initial) {
        actions.clear()
        rule.setContent {
            FemtoTheme {
                NavigationSection(
                    uiState = SettingsUiState.Initial.copy(navigation = navigation),
                    onAction = { actions += it },
                )
            }
        }
    }

    @Test
    fun `every switch is on by default and the row shows the arrival time`() {
        setSection()

        rule.onNodeWithText(REROUTE).assertIsOn()
        rule.onNodeWithText(OTHER_ROUTES).assertIsOn()
        rule.onNodeWithText(RESTORE).assertIsOn()
        rule.onNodeWithText(ARRIVAL).assertIsSelected()
        rule.onNodeWithText(REMAINING).assertIsNotSelected()
        rule.onNodeWithText(VEHICLE_ON_LINE).assertIsOn()
        rule.onNodeWithText(TURN_CARD).assertIsOn()
        rule.onNodeWithText(NEXT_AFTER).assertIsOn()
        rule.onNodeWithText(STRAIGHT).assertIsOff()
    }

    @Test
    fun `the rows follow the stored settings`() {
        setSection(
            NavigationSettingsUi(
                rerouteAutomatically = false,
                suggestOtherRoutes = false,
                restoreRouteAfterRestart = false,
                routeRowTime = RouteRowTime.REMAINING,
                vehicleOnRouteLine = false,
                turnCard = true,
                turnCardNextAfter = false,
                turnCardStraight = true,
            ),
        )

        rule.onNodeWithText(REROUTE).assertIsOff()
        rule.onNodeWithText(OTHER_ROUTES).assertIsOff()
        rule.onNodeWithText(RESTORE).assertIsOff()
        rule.onNodeWithText(REMAINING).assertIsSelected()
        rule.onNodeWithText(VEHICLE_ON_LINE).assertIsOff()
        rule.onNodeWithText(TURN_CARD).assertIsOn()
        rule.onNodeWithText(NEXT_AFTER).assertIsOff()
        rule.onNodeWithText(STRAIGHT).assertIsOn()
    }

    @Test
    fun `each row reports its own setting`() {
        setSection()

        rule.onNodeWithText(REROUTE).performClick()
        rule.onNodeWithText(OTHER_ROUTES).performClick()
        rule.onNodeWithText(RESTORE).performClick()
        rule.onNodeWithText(REMAINING).performClick()
        rule.onNodeWithText(VEHICLE_ON_LINE).performClick()
        rule.onNodeWithText(NEXT_AFTER).performClick()
        rule.onNodeWithText(STRAIGHT).performClick()
        rule.onNodeWithText(TURN_CARD).performClick()

        assertEquals(
            listOf(
                SettingsAction.SetRerouteAutomatically(false),
                SettingsAction.SetSuggestOtherRoutes(false),
                SettingsAction.SetRestoreRouteAfterRestart(false),
                SettingsAction.SetRouteRowTime(RouteRowTime.REMAINING),
                SettingsAction.SetVehicleOnRouteLine(false),
                SettingsAction.SetTurnCardNextAfter(false),
                SettingsAction.SetTurnCardStraight(true),
                SettingsAction.SetTurnCard(false),
            ),
            actions,
        )
    }

    // The two refine the card, so with it off there is nothing for them to change.
    @Test
    fun `the turn card's two refinements show only while the card is on`() {
        setSection(NavigationSettingsUi.Initial.copy(turnCard = false))

        rule.onNodeWithText(TURN_CARD).assertIsOff()
        rule.onAllNodesWithText(NEXT_AFTER).assertCountEquals(0)
        rule.onAllNodesWithText(STRAIGHT).assertCountEquals(0)
    }

    @Test
    fun `the turn card's rows sit under Guidance, above the vehicle switch`() {
        setSection()

        val tops =
            listOf("Guidance", TURN_CARD, NEXT_AFTER, STRAIGHT, VEHICLE_ON_LINE).map {
                rule.onNodeWithText(it).getUnclippedBoundsInRoot().top
            }
        assertEquals(tops.sorted(), tops)
    }

    // The switch sits in a group of its own, under the turn guidance's header rather than the route's.
    @Test
    fun `the vehicle switch sits under the Guidance header and says what off does`() {
        setSection()

        rule.onNodeWithText("Guidance").assertExists()
        rule
            .onNodeWithText(
                "Draw the vehicle on the route line while it drives along it. Off draws it at the GPS position.",
            ).assertExists()
    }

    @Test
    fun `the restore row says that off deletes the kept route`() {
        setSection()

        rule
            .onNodeWithText(
                "Keep the route on this device so it returns when the launcher starts again. Off deletes the kept route.",
            ).assertExists()
    }

    private companion object {
        const val REROUTE = "Reroute automatically"
        const val OTHER_ROUTES = "Suggest other routes"
        const val RESTORE = "Bring the route back after a restart"
        const val ARRIVAL = "Arrival time"
        const val REMAINING = "Time remaining"
        const val VEHICLE_ON_LINE = "Keep the vehicle on the route"
        const val TURN_CARD = "Turn card"
        const val NEXT_AFTER = "Show the turn after next"
        const val STRAIGHT = "Show \"Continue straight\""
    }
}

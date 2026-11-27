package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import io.github.seijikohara.femto.data.voice.SpeechAvailability
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
 * switches, its two refinements and the Spoken prompts switch shown only while the card is on, and the switch that
 * draws the vehicle on the line, on by default. The Spoken prompts switch is disabled, and reads off, where the device
 * has no engine or no English voice. Each row reports its own action.
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
        rule.onNodeWithText(SPOKEN).assertIsOn()
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
                spokenPrompts = false,
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
        rule.onNodeWithText(SPOKEN).assertIsOff()
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
        rule.onNodeWithText(SPOKEN).performClick()
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
                SettingsAction.SetSpokenPrompts(false),
                SettingsAction.SetTurnCard(false),
            ),
            actions,
        )
    }

    // The two refine the card, so with it off there is nothing for them to change.
    @Test
    fun `the turn card's refinements and the spoken prompts show only while the card is on`() {
        setSection(NavigationSettingsUi.Initial.copy(turnCard = false))

        rule.onNodeWithText(TURN_CARD).assertIsOff()
        rule.onAllNodesWithText(NEXT_AFTER).assertCountEquals(0)
        rule.onAllNodesWithText(STRAIGHT).assertCountEquals(0)
        rule.onAllNodesWithText(SPOKEN).assertCountEquals(0)
    }

    @Test
    fun `the turn card's rows sit under Guidance, above the vehicle switch`() {
        setSection()

        val tops =
            listOf("Guidance", TURN_CARD, NEXT_AFTER, STRAIGHT, SPOKEN, VEHICLE_ON_LINE).map {
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

    // An engine not asked about yet must not lock the switch: the answer takes seconds on a cold engine.
    @Test
    fun `the spoken prompts switch works before the engines have been asked`() {
        setSection()

        rule.onNodeWithText(SPOKEN).assertIsEnabled()
        rule.onNodeWithText(SPOKEN_DESC).assertExists()
    }

    @Test
    fun `the spoken prompts switch works where an engine speaks English`() {
        setSection(NavigationSettingsUi.Initial.copy(speech = SpeechAvailability.Ready("com.example.tts")))

        rule.onNodeWithText(SPOKEN).assertIsEnabled()
        rule.onNodeWithText(SPOKEN_DESC).assertExists()
    }

    @Test
    fun `with no engine the spoken prompts switch is disabled, reads off and says why`() {
        setSection(NavigationSettingsUi.Initial.copy(speech = SpeechAvailability.NoEngine))

        rule.onNodeWithText(SPOKEN).assertIsNotEnabled()
        rule.onNodeWithText(SPOKEN).assertIsOff()
        rule.onNodeWithText("This device has no text-to-speech engine.").assertExists()
    }

    @Test
    fun `with no English voice the spoken prompts switch is disabled, reads off and says why`() {
        setSection(NavigationSettingsUi.Initial.copy(speech = SpeechAvailability.NoEnglish))

        rule.onNodeWithText(SPOKEN).assertIsNotEnabled()
        rule.onNodeWithText(SPOKEN).assertIsOff()
        rule.onNodeWithText("No text-to-speech engine on this device has an English voice.").assertExists()
    }

    @Test
    fun `a disabled spoken prompts switch reports nothing`() {
        setSection(NavigationSettingsUi.Initial.copy(speech = SpeechAvailability.NoEngine))

        rule.onNodeWithText(SPOKEN).performClick()

        assertEquals(emptyList(), actions)
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
        const val SPOKEN = "Spoken prompts"
        const val SPOKEN_DESC =
            "Say each turn before it comes, in English, through this device's text-to-speech engine."
    }
}

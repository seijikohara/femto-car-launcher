package io.github.seijikohara.femto.ui.destination

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.seijikohara.femto.data.location.VehicleMotion
import io.github.seijikohara.femto.data.places.PlaceTarget
import io.github.seijikohara.femto.data.voice.VoiceState
import io.github.seijikohara.femto.testfixtures.fakeSavedPlace
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * The destination panel's two motion states (issue #389, Safety): stopped,
 * everything is open; moving, the text field and every save / delete control
 * close while voice and a tap on a saved place keep working. Rendered at the
 * reference head-unit geometry.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class DestinationPanelTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<DestinationAction>()
    private val handoffs = mutableListOf<Pair<PlaceTarget, String>>()
    private var micTaps = 0
    private var mapsOpens = 0

    private val office = fakeSavedPlace(id = 1L, label = "Office")
    private val home = fakeSavedPlace(id = 2L, label = "Home", target = PlaceTarget.Point(35.681236, 139.767125))

    private fun setPanel(
        stationary: Boolean,
        query: String = "",
        currentPoint: PlaceTarget.Point? = PlaceTarget.Point(1.0, 2.0),
    ) = rule.setContent {
        FemtoTheme {
            DestinationPanel(
                uiState =
                    DestinationUiState(
                        query = query,
                        voice = VoiceState.Idle,
                        places = listOf(office, home),
                        motion = if (stationary) VehicleMotion.PARKED else VehicleMotion.MOVING,
                    ),
                currentPoint = currentPoint,
                currentAddress = "1-9-1 Marunouchi",
                onAction = { actions += it },
                onMicTap = { micTaps++ },
                onNavigate = { target, label -> handoffs += target to label },
                onOpenMaps = { mapsOpens++ },
                onClose = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    @Test
    fun stopped_the_text_field_is_enabled() {
        setPanel(stationary = true)
        rule.onNodeWithTag(DESTINATION_QUERY_TEST_TAG).assertIsEnabled()
    }

    @Test
    fun moving_the_text_field_is_disabled() {
        setPanel(stationary = false)
        rule.onNodeWithTag(DESTINATION_QUERY_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun moving_a_line_says_typing_waits_and_voice_works_now() {
        setPanel(stationary = false)
        rule.onNodeWithText("Typing works when stopped. Voice and saved places work now.").assertIsDisplayed()
    }

    @Test
    fun stopped_no_motion_line_shows() {
        setPanel(stationary = true)
        rule.onNodeWithText("Typing works when stopped. Voice and saved places work now.").assertDoesNotExist()
    }

    @Test
    fun stopped_the_save_controls_are_enabled() {
        setPanel(stationary = true, query = "Airport")
        rule.onNodeWithText("Save").performScrollTo().assertIsEnabled()
        rule.onNodeWithText("Save current location").performScrollTo().assertIsEnabled()
    }

    @Test
    fun moving_the_save_controls_are_disabled() {
        setPanel(stationary = false, query = "Airport")
        rule.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Save current location").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun save_current_location_is_disabled_without_a_fix() {
        setPanel(stationary = true, currentPoint = null)
        rule.onNodeWithText("Save current location").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun save_current_location_raises_the_fix_and_the_dashboard_address() {
        setPanel(stationary = true)
        rule.onNodeWithText("Save current location").performScrollTo().performClick()
        assertEquals(
            listOf<DestinationAction>(
                DestinationAction.SaveCurrentLocation(PlaceTarget.Point(1.0, 2.0), address = "1-9-1 Marunouchi"),
            ),
            actions,
        )
    }

    @Test
    fun stopped_a_saved_place_offers_delete() {
        setPanel(stationary = true)
        rule.onNodeWithContentDescription("Delete Office").performClick()
        assertEquals(listOf<DestinationAction>(DestinationAction.DeletePlace(office.id)), actions)
    }

    @Test
    fun moving_a_saved_place_offers_no_delete() {
        setPanel(stationary = false)
        rule.onNodeWithContentDescription("Delete Office").assertDoesNotExist()
    }

    @Test
    fun moving_a_tap_on_a_saved_place_hands_it_off_with_its_label() {
        setPanel(stationary = false)
        rule.onNodeWithText("Home").performClick()
        assertEquals(listOf(home.target to "Home"), handoffs)
    }

    @Test
    fun moving_the_mic_still_listens() {
        setPanel(stationary = false)
        rule.onNodeWithContentDescription("Microphone").performClick()
        assertEquals(1, micTaps)
    }

    @Test
    fun navigate_hands_off_the_trimmed_query() {
        setPanel(stationary = false, query = "  Central Station ")
        rule.onNodeWithText("Navigate").performScrollTo().performClick()
        assertEquals(listOf<Pair<PlaceTarget, String>>(PlaceTarget.Query("Central Station") to ""), handoffs)
    }

    @Test
    fun navigate_is_disabled_for_a_blank_query() {
        setPanel(stationary = true, query = " ")
        rule.onNodeWithText("Navigate").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun the_header_link_opens_the_maps_app() {
        setPanel(stationary = false)
        rule.onNodeWithContentDescription("Open maps app").performClick()
        assertEquals(1, mapsOpens)
    }
}

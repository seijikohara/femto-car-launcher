package io.github.seijikohara.femto.ui.destination

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
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
    // The activity rule's form, so a test can recreate the activity as a
    // configuration change does.
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<DestinationAction>()
    private val handoffs = mutableListOf<Pair<PlaceTarget, String>>()
    private var micTaps = 0
    private var mapsOpens = 0

    private val office = fakeSavedPlace(id = 1L, label = "Office")
    private val home = fakeSavedPlace(id = 2L, label = "Home", target = PlaceTarget.Point(35.681236, 139.767125))

    private fun setPanel(
        stationary: Boolean,
        query: String = "",
        lastFix: LastFix? = FIX,
        handOffAvailable: Boolean = true,
        routeAvailable: Boolean = false,
        geocoderAvailable: Boolean = true,
        route: RouteStep = RouteStep.Idle,
        pickedPoint: PlaceTarget.Point? = null,
    ) = rule.setContent {
        FemtoTheme {
            DestinationPanel(
                uiState =
                    DestinationUiState(
                        query = query,
                        voice = VoiceState.Idle,
                        places = listOf(office, home),
                        motion = if (stationary) VehicleMotion.PARKED else VehicleMotion.MOVING,
                        handOffAvailable = handOffAvailable,
                        routeAvailable = routeAvailable,
                        geocoderAvailable = geocoderAvailable,
                        route = route,
                        pickedPoint = pickedPoint,
                    ),
                lastFix = lastFix,
                currentAddress = "1-9-1 Marunouchi",
                visible = true,
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
        setPanel(stationary = true, lastFix = null)
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

    @Test
    fun closing_the_panel_cancels_the_recognizer() {
        var shown by mutableStateOf(true)
        rule.setContent {
            FemtoTheme {
                if (shown) {
                    DestinationPanel(
                        uiState =
                            DestinationUiState(
                                query = "",
                                voice = VoiceState.Listening(partial = "Cen"),
                                places = emptyList(),
                                motion = VehicleMotion.MOVING,
                            ),
                        lastFix = null,
                        currentAddress = "",
                        visible = true,
                        onAction = { actions += it },
                        onMicTap = {},
                        onNavigate = { _, _ -> },
                        onOpenMaps = {},
                        onClose = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        rule.waitForIdle()
        assertEquals(emptyList(), actions)

        shown = false
        rule.waitForIdle()

        // A mic left listening behind a closed panel would keep the microphone
        // open with nothing on screen to show it, and a stop would still
        // deliver a late result into the next open's query.
        assertEquals(listOf<DestinationAction>(DestinationAction.CancelListening), actions)
    }

    @Test
    fun a_saved_place_hand_off_cancels_recognition_before_the_launch() {
        val order = mutableListOf<String>()
        rule.setContent {
            FemtoTheme {
                DestinationPanel(
                    uiState =
                        DestinationUiState(
                            query = "",
                            voice = VoiceState.Listening(partial = "Cen"),
                            places = listOf(home),
                            motion = VehicleMotion.MOVING,
                        ),
                    lastFix = null,
                    currentAddress = "",
                    visible = true,
                    onAction = { order += it.toString() },
                    onMicTap = {},
                    onNavigate = { _, label -> order += "navigate $label" },
                    onOpenMaps = {},
                    onClose = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        rule.onNodeWithText("Home").performClick()
        // The launch can pause the activity before the panel's exit disposes
        // it, so the cancel must come first.
        assertEquals(listOf(DestinationAction.CancelListening.toString(), "navigate Home"), order)
    }

    @Test
    fun without_an_app_to_take_it_navigate_is_disabled() {
        setPanel(stationary = true, query = "Central Station", handOffAvailable = false)
        rule.onNodeWithText("Navigate").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun without_an_app_to_take_it_the_go_key_hands_off_nothing() {
        setPanel(stationary = true, query = "Central Station", handOffAvailable = false)
        rule.onNodeWithTag(DESTINATION_QUERY_TEST_TAG).performImeAction()
        assertEquals(emptyList(), handoffs)
    }

    @Test
    fun without_an_app_to_take_it_a_saved_place_tap_hands_off_nothing() {
        setPanel(stationary = true, handOffAvailable = false)
        rule.onNodeWithText("Home").performClick()
        assertEquals(emptyList(), handoffs)
    }

    @Test
    fun without_an_app_to_take_it_a_line_says_so_instead_of_the_moving_line() {
        // The moving line promises that saved places work now, which would not
        // hold with no app to hand them to.
        setPanel(stationary = false, handOffAvailable = false)
        rule.onNodeWithText(NO_HANDLER_LINE).assertIsDisplayed()
        rule.onNodeWithText("Typing works when stopped. Voice and saved places work now.").assertDoesNotExist()
    }

    @Test
    fun with_an_app_to_take_it_no_missing_app_line_shows() {
        setPanel(stationary = true)
        rule.onNodeWithText(NO_HANDLER_LINE).assertDoesNotExist()
    }

    @Test
    fun a_query_hand_off_cancels_recognition_before_the_launch() {
        setPanel(stationary = true, query = "Central Station")
        rule.onNodeWithText("Navigate").performScrollTo().performClick()
        assertEquals(listOf<DestinationAction>(DestinationAction.CancelListening), actions)
        assertEquals(listOf<Pair<PlaceTarget, String>>(PlaceTarget.Query("Central Station") to ""), handoffs)
    }

    // --- Route preview (issue #389 phase 3) -----------------------------------

    @Test
    fun without_routing_no_route_action_shows() {
        setPanel(stationary = true, query = "Imabari")
        rule.onNodeWithText("Route").assertDoesNotExist()
        rule.onNodeWithContentDescription("Route to Home").assertDoesNotExist()
    }

    @Test
    fun route_beside_navigate_raises_the_trimmed_query_from_the_fix() {
        setPanel(stationary = true, query = "  Imabari ", routeAvailable = true)
        rule.onNodeWithText("Route").performScrollTo().performClick()
        assertEquals(
            DestinationAction.RouteTo(PlaceTarget.Query("Imabari"), origin = FIX),
            actions.last(),
        )
    }

    @Test
    fun moving_a_saved_place_still_routes() {
        setPanel(stationary = false, routeAvailable = true)
        rule.onNodeWithContentDescription("Route to Home").performClick()
        assertEquals(DestinationAction.RouteTo(home.target, origin = FIX), actions.last())
    }

    @Test
    fun without_a_geocoder_only_a_saved_point_offers_a_route() {
        setPanel(stationary = true, query = "Imabari", routeAvailable = true, geocoderAvailable = false)
        rule.onNodeWithText("Route").assertDoesNotExist()
        rule.onNodeWithContentDescription("Route to Office").assertDoesNotExist()
        rule.onNodeWithContentDescription("Route to Home").assertIsDisplayed()
    }

    @Test
    fun a_download_confirmation_names_the_source_and_downloads_on_the_word() {
        setPanel(stationary = true, routeAvailable = true, route = RouteStep.ConfirmDownload(bytes = 48_000_000L))
        rule.onNodeWithText("brouter.de, built from OpenStreetMap", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Download").performClick()
        assertEquals(listOf<DestinationAction>(DestinationAction.ConfirmDownload), actions)
    }

    @Test
    fun a_failed_route_says_why_and_ok_puts_it_away() {
        setPanel(stationary = true, routeAvailable = true, route = RouteStep.Failed(RouteFailure.TIMED_OUT))
        rule.onNodeWithText("The route took too long to compute. Try a closer destination.").assertIsDisplayed()
        rule.onNodeWithText("OK").performClick()
        assertEquals(listOf<DestinationAction>(DestinationAction.DismissRoute), actions)
    }

    @Test
    fun finding_the_position_says_so_and_cancel_puts_it_away() {
        setPanel(stationary = true, routeAvailable = true, route = RouteStep.FindingPosition)
        rule.onNodeWithText("Finding your position…").assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(listOf<DestinationAction>(DestinationAction.DismissRoute), actions)
    }

    @Test
    fun without_the_location_permission_a_route_asks_for_it() {
        setPanel(
            stationary = true,
            routeAvailable = true,
            route = RouteStep.Failed(RouteFailure.NO_LOCATION_PERMISSION),
        )
        rule.onNodeWithText("Grant location access to route.", substring = true).assertIsDisplayed()
    }

    @Test
    fun a_version_mismatch_offers_the_data_again() {
        setPanel(
            stationary = true,
            routeAvailable = true,
            route = RouteStep.Failed(RouteFailure.DATA_VERSION_MISMATCH),
        )
        rule.onNodeWithText("Download again").performClick()
        assertEquals(listOf<DestinationAction>(DestinationAction.DownloadAgain), actions)
    }

    @Test
    fun closing_the_panel_stops_the_route_under_way() {
        var shown by mutableStateOf(true)
        rule.setContent {
            FemtoTheme {
                if (shown) RouteUnderWayPanel()
            }
        }
        rule.waitForIdle()

        shown = false
        rule.waitForIdle()

        assertEquals(listOf(DestinationAction.CancelListening, DestinationAction.DismissRoute), actions)
    }

    @Test
    fun a_configuration_change_keeps_the_route_under_way() {
        rule.setContent { FemtoTheme { RouteUnderWayPanel() } }
        rule.waitForIdle()

        // A rotation disposes the panel with the old activity, and the panel
        // comes back on the same ViewModel: the download goes on.
        rule.activityRule.scenario.recreate()

        assertEquals(listOf<DestinationAction>(DestinationAction.CancelListening), actions)
    }

    @Test
    fun a_configuration_change_while_the_panel_closes_leaves_no_route_under_way() {
        // The host's flag has turned and the exit animation still draws the
        // panel when the rotation disposes it. The panel does not come back, so
        // the route must have stopped as the flag turned.
        var visible by mutableStateOf(true)
        rule.setContent { FemtoTheme { RouteUnderWayPanel(visible = visible) } }
        rule.waitForIdle()

        visible = false
        rule.waitForIdle()
        rule.activityRule.scenario.recreate()

        assertEquals(listOf(DestinationAction.CancelListening, DestinationAction.DismissRoute), actions)
    }

    // --- newer routing data ----------------------------------------------------

    @Test
    fun newer_data_alone_offers_the_stored_data_and_never_blocks_the_route() {
        setPanel(
            stationary = true,
            routeAvailable = true,
            route = RouteStep.ConfirmDownload(1_000L, RouteDownload.NEWER),
        )

        rule.onNodeWithText("Cancel").assertDoesNotExist()
        rule.onNodeWithText("Use stored data").performClick()
        rule.onNodeWithText("Update").performClick()

        assertEquals(listOf(DestinationAction.UseStoredData, DestinationAction.ConfirmDownload), actions)
    }

    @Test
    fun missing_and_newer_data_say_the_missing_data_is_needed_and_can_be_cancelled() {
        setPanel(
            stationary = true,
            routeAvailable = true,
            route = RouteStep.ConfirmDownload(1_000L, RouteDownload.MISSING_AND_NEWER),
        )

        rule.onNodeWithText("which the route cannot do without", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Use stored data").assertDoesNotExist()
        rule.onNodeWithText("Cancel").performClick()

        assertEquals(listOf<DestinationAction>(DestinationAction.DismissRoute), actions)
    }

    // --- a point picked on the map ---------------------------------------------

    @Test
    fun a_picked_point_takes_the_search_place_with_its_coordinates() {
        setPanel(stationary = true, pickedPoint = PICKED)

        rule.onNodeWithText("35.65858, 139.74543").assertIsDisplayed()
        rule.onNodeWithTag(DESTINATION_QUERY_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun navigate_hands_the_picked_point_off_without_a_label() {
        setPanel(stationary = true, pickedPoint = PICKED)

        rule.onNodeWithText("Navigate").performClick()

        assertEquals(listOf<Pair<PlaceTarget, String>>(PICKED to ""), handoffs)
    }

    @Test
    fun route_routes_to_the_picked_point_where_routing_is_offered() {
        setPanel(stationary = true, pickedPoint = PICKED, routeAvailable = true)

        rule.onNodeWithText("Route").performClick()

        assertEquals(DestinationAction.RouteTo(PICKED, FIX), actions.last())
    }

    @Test
    fun without_routing_the_picked_point_offers_no_route() {
        setPanel(stationary = true, pickedPoint = PICKED, routeAvailable = false)

        rule.onNodeWithText("Route").assertDoesNotExist()
    }

    @Test
    fun moving_the_picked_point_cannot_be_saved_but_still_navigates() {
        setPanel(stationary = false, pickedPoint = PICKED)

        rule.onNodeWithText("Save point").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Navigate").assertIsEnabled()
    }

    @Test
    fun search_instead_puts_the_picked_point_away() {
        setPanel(stationary = true, pickedPoint = PICKED)

        rule.onNodeWithText("Search instead").performScrollTo().performClick()

        assertEquals(DestinationAction.ClearPickedPoint, actions.last())
    }

    @Test
    fun closing_the_panel_puts_the_picked_point_away() {
        var visible by mutableStateOf(true)
        rule.setContent {
            FemtoTheme {
                DestinationPanel(
                    uiState =
                        DestinationUiState(
                            query = "",
                            voice = VoiceState.Idle,
                            places = emptyList(),
                            motion = VehicleMotion.PARKED,
                            pickedPoint = PICKED,
                        ),
                    lastFix = null,
                    currentAddress = "",
                    visible = visible,
                    onAction = { actions += it },
                    onMicTap = {},
                    onNavigate = { _, _ -> },
                    onOpenMaps = {},
                    onClose = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        rule.waitForIdle()

        visible = false
        rule.waitForIdle()

        assertEquals(listOf(DestinationAction.CancelListening, DestinationAction.ClearPickedPoint), actions)
    }

    @Composable
    private fun RouteUnderWayPanel(visible: Boolean = true) =
        DestinationPanel(
            uiState =
                DestinationUiState(
                    query = "",
                    voice = VoiceState.Idle,
                    places = emptyList(),
                    motion = VehicleMotion.PARKED,
                    routeAvailable = true,
                    route = RouteStep.Downloading(receivedBytes = 1_000L, totalBytes = 2_000L),
                ),
            lastFix = null,
            currentAddress = "",
            visible = visible,
            onAction = { actions += it },
            onMicTap = {},
            onNavigate = { _, _ -> },
            onOpenMaps = {},
            onClose = {},
            modifier = Modifier.fillMaxSize(),
        )
}

// The fix the panel holds; its age is the ViewModel's to judge, so any time serves.
private val FIX = LastFix(PlaceTarget.Point(1.0, 2.0), elapsedRealtimeNanos = 0L)

// A point a long press on the map picked.
private val PICKED = PlaceTarget.Point(35.658581, 139.745433)

private const val NO_HANDLER_LINE =
    "No app on this device can open a destination. Install a maps or navigation app."

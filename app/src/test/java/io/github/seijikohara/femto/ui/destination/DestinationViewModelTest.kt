package io.github.seijikohara.femto.ui.destination

import io.github.seijikohara.femto.data.common.download.TransferResult
import io.github.seijikohara.femto.data.geocoding.PlaceLookup
import io.github.seijikohara.femto.data.location.CurrentFix
import io.github.seijikohara.femto.data.location.LOCATION_STALE_THRESHOLD_MS
import io.github.seijikohara.femto.data.location.VehicleMotion
import io.github.seijikohara.femto.data.places.MAX_PLACE_NAME_LENGTH
import io.github.seijikohara.femto.data.places.PlaceTarget
import io.github.seijikohara.femto.data.places.ReceivedPlace
import io.github.seijikohara.femto.data.places.SavedPlace
import io.github.seijikohara.femto.data.routing.ActiveRouteStore
import io.github.seijikohara.femto.data.routing.EngineOutcome
import io.github.seijikohara.femto.data.routing.REFERENCE_PRUNE_GRACE_MS
import io.github.seijikohara.femto.data.routing.ROUTING_TILE_CHECK_INTERVAL_MS
import io.github.seijikohara.femto.data.routing.RerouteStatus
import io.github.seijikohara.femto.data.routing.RouteConditions
import io.github.seijikohara.femto.data.routing.RoutePoint
import io.github.seijikohara.femto.data.voice.VoiceState
import io.github.seijikohara.femto.testfixtures.FakeCurrentFixSource
import io.github.seijikohara.femto.testfixtures.FakeDownloadStop
import io.github.seijikohara.femto.testfixtures.FakePlaceLocator
import io.github.seijikohara.femto.testfixtures.FakeRouteConditionsStore
import io.github.seijikohara.femto.testfixtures.FakeSavedPlacesStore
import io.github.seijikohara.femto.testfixtures.FakeSpeechInput
import io.github.seijikohara.femto.testfixtures.HeldDispatcher
import io.github.seijikohara.femto.testfixtures.RoutingFixture
import io.github.seijikohara.femto.testfixtures.fakeEngineTrack
import io.github.seijikohara.femto.testfixtures.fakeRoute
import io.github.seijikohara.femto.testfixtures.fakeSavedPlace
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DestinationViewModelTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private var store = FakeSavedPlacesStore()
    private val speech = FakeSpeechInput()
    private val motion = MutableStateFlow(VehicleMotion.PARKED)

    // What the package manager would answer: is there an app that takes a
    // destination? Flipped between checks to stand in for an install.
    private var handOffTarget = true

    // The routing stack of the last subscribedViewModel, and the route it sets.
    private lateinit var routing: RoutingFixture
    private val activeRoute = ActiveRouteStore()

    // The boot clock a route's start is judged by, the location grant, and
    // what the device answers when asked where it is now: nothing, unless a
    // test sets a position.
    private var nowNanos = NOW_NANOS
    private var locationGranted = true
    private val currentFix = FakeCurrentFixSource()

    // How often a route run has stopped the rerouter.
    private var rerouteCancels = 0

    // The candidate searches a found route started (its session and origin), and how often a route run stopped one.
    private val candidateStarts = mutableListOf<Pair<Long, RoutePoint>>()
    private var candidateCancels = 0

    // The route conditions kept on the device, as the last subscribedViewModel holds them.
    private var routeConditions = FakeRouteConditionsStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // uiState is shared WhileUiSubscribed, and the gate reads its current
    // value, so every test holds a subscriber the way the panel does. Routing
    // is off unless a test turns it on; its file and engine work runs on the
    // test scheduler, so a routing test advances it to see each step.
    private fun TestScope.subscribedViewModel(
        saved: List<SavedPlace> = emptyList(),
        motionFlow: Flow<VehicleMotion> = motion,
        routingAvailable: Boolean = false,
        placeLocator: FakePlaceLocator = FakePlaceLocator(),
        ioDispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
        conditions: RouteConditions = RouteConditions(),
        suggestOtherRoutes: suspend () -> Boolean = { true },
    ): DestinationViewModel {
        routing = RoutingFixture(tempFolder.root, StandardTestDispatcher(testScheduler))
        return DestinationViewModel(
            placesStore = FakeSavedPlacesStore(saved).also { store = it },
            motionFlow = motionFlow,
            speech = speech,
            hasHandOffTarget = { handOffTarget },
            routingAvailable = flowOf(routingAvailable),
            placeLocator = placeLocator,
            planner = routing.planner(),
            tiles = routing.tiles,
            activeRoute = activeRoute,
            conditionsStore = FakeRouteConditionsStore(conditions).also { routeConditions = it },
            nowElapsedRealtimeNanos = { nowNanos },
            hasLocationPermission = { locationGranted },
            currentFix = currentFix,
            nowEpochMs = { routing.nowEpochMs },
            cancelReroute = { rerouteCancels++ },
            ioDispatcher = ioDispatcher,
            startCandidates = { session, origin -> candidateStarts += session to origin },
            cancelCandidates = { candidateCancels++ },
            suggestOtherRoutes = suggestOtherRoutes,
        ).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        }
    }

    @Test
    fun typing_updates_the_query_while_stationary() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.QueryChanged("Airport"))
            assertEquals("Airport", viewModel.uiState.value.query)
        }

    @Test
    fun typing_is_ignored_while_moving() =
        runTest {
            motion.value = VehicleMotion.MOVING
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.QueryChanged("Airport"))
            assertEquals("", viewModel.uiState.value.query)
        }

    @Test
    fun typing_is_allowed_when_the_motion_is_unknown() =
        runTest {
            // Fail-open on purpose: a phone without a fix or the location
            // permission reads UNKNOWN for good, and voice plus saved places
            // alone would leave it no way to enter a new destination.
            motion.value = VehicleMotion.UNKNOWN
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.QueryChanged("Airport"))
            assertEquals("Airport", viewModel.uiState.value.query)
        }

    @Test
    fun typing_is_allowed_before_the_motion_verdict_arrives() =
        runTest {
            val viewModel = subscribedViewModel(motionFlow = emptyFlow())
            viewModel.onAction(DestinationAction.QueryChanged("Airport"))
            assertEquals("Airport", viewModel.uiState.value.query)
        }

    @Test
    fun saving_and_deleting_are_allowed_when_the_motion_is_unknown() =
        runTest {
            motion.value = VehicleMotion.UNKNOWN
            val place = fakeSavedPlace(id = 1L)
            val viewModel = subscribedViewModel(saved = listOf(place))
            viewModel.onAction(DestinationAction.QueryChanged("Airport"))
            viewModel.onAction(DestinationAction.SaveQuery)
            viewModel.onAction(DestinationAction.DeletePlace(place.id))
            assertEquals(listOf("Airport"), store.current.map { it.label })
        }

    @Test
    fun ui_state_reports_the_motion_gate() =
        runTest {
            val viewModel = subscribedViewModel()
            motion.value = VehicleMotion.MOVING
            assertEquals(false, viewModel.uiState.value.typingAllowed)
        }

    @Test
    fun a_voice_result_fills_the_query_while_moving_and_rearms_the_recognizer() =
        runTest {
            motion.value = VehicleMotion.MOVING
            val viewModel = subscribedViewModel()
            speech.mutableState.value = VoiceState.Result("Central Station")
            assertEquals("Central Station", viewModel.uiState.value.query)
            assertEquals(1, speech.resets)
        }

    @Test
    fun a_partial_voice_transcript_never_replaces_the_query() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.QueryChanged("Airport"))
            speech.mutableState.value = VoiceState.Listening(partial = "Cen")
            assertEquals("Airport", viewModel.uiState.value.query)
        }

    @Test
    fun listening_starts_and_stops_in_any_motion_state() =
        runTest {
            motion.value = VehicleMotion.MOVING
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.StartListening)
            viewModel.onAction(DestinationAction.StopListening)
            assertEquals(1 to 1, speech.starts to speech.stops)
        }

    @Test
    fun save_query_keeps_a_query_place_labelled_with_the_query_while_stationary() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.QueryChanged("  Airport  "))
            viewModel.onAction(DestinationAction.SaveQuery)
            assertEquals(
                listOf("Airport" to PlaceTarget.Query("Airport")),
                store.current.map { it.label to it.target },
            )
        }

    @Test
    fun save_query_is_ignored_while_moving() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.QueryChanged("Airport"))
            motion.value = VehicleMotion.MOVING
            viewModel.onAction(DestinationAction.SaveQuery)
            assertEquals(emptyList(), store.current)
        }

    @Test
    fun save_query_is_ignored_for_a_blank_query() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.QueryChanged("   "))
            viewModel.onAction(DestinationAction.SaveQuery)
            assertEquals(emptyList(), store.current)
        }

    @Test
    fun save_current_location_labels_the_fix_with_the_dashboard_address() =
        runTest {
            val viewModel = subscribedViewModel()
            val point = PlaceTarget.Point(35.681236, 139.767125)
            viewModel.onAction(DestinationAction.SaveCurrentLocation(point, address = "1-9-1 Marunouchi"))
            assertEquals(listOf("1-9-1 Marunouchi" to point), store.current.map { it.label to it.target })
        }

    @Test
    fun save_current_location_never_takes_the_typed_query_as_its_label() =
        runTest {
            // A query left over from a search would otherwise name the
            // parking spot after somewhere else ("Airport").
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.QueryChanged("Airport"))
            viewModel.onAction(
                DestinationAction.SaveCurrentLocation(PlaceTarget.Point(1.0, 2.0), address = "1-9-1 Marunouchi"),
            )
            assertEquals(listOf("1-9-1 Marunouchi"), store.current.map { it.label })
        }

    @Test
    fun save_current_location_without_an_address_ignores_the_typed_query() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.QueryChanged("Airport"))
            viewModel.onAction(DestinationAction.SaveCurrentLocation(PlaceTarget.Point(1.0, 2.0), address = " "))
            assertEquals(listOf("1.00000, 2.00000"), store.current.map { it.label })
        }

    @Test
    fun save_current_location_without_an_address_labels_the_place_with_its_coordinates() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.SaveCurrentLocation(PlaceTarget.Point(-0.0001, 2.5), address = ""))
            assertEquals(listOf("-0.00010, 2.50000"), store.current.map { it.label })
        }

    @Test
    fun save_current_location_is_ignored_while_moving() =
        runTest {
            motion.value = VehicleMotion.MOVING
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.SaveCurrentLocation(PlaceTarget.Point(1.0, 2.0), address = "Here"))
            assertEquals(emptyList(), store.current)
        }

    @Test
    fun delete_removes_the_place_while_stationary() =
        runTest {
            val kept = fakeSavedPlace(id = 1L, label = "Office")
            val dropped = fakeSavedPlace(id = 2L, label = "Gym")
            val viewModel = subscribedViewModel(saved = listOf(kept, dropped))
            viewModel.onAction(DestinationAction.DeletePlace(dropped.id))
            assertEquals(listOf(kept), viewModel.uiState.value.places)
        }

    @Test
    fun delete_is_ignored_while_moving() =
        runTest {
            motion.value = VehicleMotion.MOVING
            val place = fakeSavedPlace()
            val viewModel = subscribedViewModel(saved = listOf(place))
            viewModel.onAction(DestinationAction.DeletePlace(place.id))
            assertEquals(listOf(place), viewModel.uiState.value.places)
        }

    @Test
    fun rename_of_an_unnamed_place_starts_empty_and_of_a_named_place_with_its_name() =
        runTest {
            val unnamed = fakeSavedPlace(id = 1L, label = "Office")
            val named = fakeSavedPlace(id = 2L, label = "1-9-1 Marunouchi", name = "Home")
            val viewModel = subscribedViewModel(saved = listOf(unnamed, named))

            viewModel.onAction(DestinationAction.StartRename(unnamed.id))
            assertEquals(RenameDraft(unnamed.id, ""), viewModel.uiState.value.renaming)

            viewModel.onAction(DestinationAction.StartRename(named.id))
            assertEquals(RenameDraft(named.id, "Home"), viewModel.uiState.value.renaming)
        }

    // The label is past the name cap: a draft made of it would be saved cut, as a name, by a Save with no edit.
    @Test
    fun saving_an_unnamed_place_with_a_long_label_without_typing_stores_no_name() =
        runTest {
            val place = fakeSavedPlace(id = 1L, label = "a".repeat(MAX_PLACE_NAME_LENGTH + 40))
            val viewModel = subscribedViewModel(saved = listOf(place))
            viewModel.onAction(DestinationAction.StartRename(place.id))

            viewModel.onAction(DestinationAction.SaveRename)

            assertEquals(listOf(place), store.current)
        }

    @Test
    fun saving_a_named_place_with_an_empty_field_takes_its_name_away() =
        runTest {
            val place = fakeSavedPlace(id = 1L, label = "Office", name = "Work")
            val viewModel = subscribedViewModel(saved = listOf(place))
            viewModel.onAction(DestinationAction.StartRename(place.id))
            viewModel.onAction(DestinationAction.RenameChanged(""))

            viewModel.onAction(DestinationAction.SaveRename)

            assertNull(store.current.single().name)
        }

    @Test
    fun rename_of_a_place_that_is_not_there_does_not_start() =
        runTest {
            val viewModel = subscribedViewModel(saved = listOf(fakeSavedPlace(id = 1L)))

            viewModel.onAction(DestinationAction.StartRename(99L))

            assertNull(viewModel.uiState.value.renaming)
        }

    @Test
    fun saving_stores_the_trimmed_name_and_closes_the_field() =
        runTest {
            val place = fakeSavedPlace(id = 1L, label = "Office")
            val viewModel = subscribedViewModel(saved = listOf(place))
            viewModel.onAction(DestinationAction.StartRename(place.id))
            viewModel.onAction(DestinationAction.RenameChanged("  Work  "))

            viewModel.onAction(DestinationAction.SaveRename)

            assertEquals("Work", store.current.single().name)
            assertEquals(
                "Work",
                viewModel.uiState.value.places
                    .single()
                    .name,
            )
            assertNull(viewModel.uiState.value.renaming)
        }

    @Test
    fun saving_a_blank_name_takes_the_name_away() =
        runTest {
            val place = fakeSavedPlace(id = 1L, label = "Office", name = "Work")
            val viewModel = subscribedViewModel(saved = listOf(place))
            viewModel.onAction(DestinationAction.StartRename(place.id))
            viewModel.onAction(DestinationAction.RenameChanged("   "))

            viewModel.onAction(DestinationAction.SaveRename)

            assertNull(store.current.single().name)
        }

    @Test
    fun the_typed_name_is_cut_to_the_cap() =
        runTest {
            val place = fakeSavedPlace(id = 1L)
            val viewModel = subscribedViewModel(saved = listOf(place))
            viewModel.onAction(DestinationAction.StartRename(place.id))

            viewModel.onAction(DestinationAction.RenameChanged("a".repeat(MAX_PLACE_NAME_LENGTH + 20)))

            assertEquals(
                MAX_PLACE_NAME_LENGTH,
                viewModel.uiState.value.renaming
                    ?.text
                    ?.length,
            )
        }

    @Test
    fun cancel_stores_nothing() =
        runTest {
            val place = fakeSavedPlace(id = 1L, label = "Office")
            val viewModel = subscribedViewModel(saved = listOf(place))
            viewModel.onAction(DestinationAction.StartRename(place.id))
            viewModel.onAction(DestinationAction.RenameChanged("Work"))

            viewModel.onAction(DestinationAction.CancelRename)

            assertEquals(listOf(place), store.current)
            assertNull(viewModel.uiState.value.renaming)
        }

    @Test
    fun starting_to_move_closes_the_rename_without_saving() =
        runTest {
            val place = fakeSavedPlace(id = 1L, label = "Office")
            val viewModel = subscribedViewModel(saved = listOf(place))
            viewModel.onAction(DestinationAction.StartRename(place.id))
            viewModel.onAction(DestinationAction.RenameChanged("Work"))

            motion.value = VehicleMotion.MOVING

            assertNull(viewModel.uiState.value.renaming)
            assertEquals(listOf(place), store.current)

            // Stopping again does not bring the field back, and a late Save has nothing to store.
            motion.value = VehicleMotion.PARKED
            viewModel.onAction(DestinationAction.SaveRename)
            assertNull(viewModel.uiState.value.renaming)
            assertEquals(listOf(place), store.current)
        }

    @Test
    fun rename_is_ignored_while_moving() =
        runTest {
            motion.value = VehicleMotion.MOVING
            val place = fakeSavedPlace(id = 1L, label = "Office")
            val viewModel = subscribedViewModel(saved = listOf(place))

            viewModel.onAction(DestinationAction.StartRename(place.id))

            assertNull(viewModel.uiState.value.renaming)
        }

    @Test
    fun saving_a_name_while_moving_stores_nothing() =
        runTest {
            val place = fakeSavedPlace(id = 1L, label = "Office")
            val viewModel = subscribedViewModel(saved = listOf(place))
            viewModel.onAction(DestinationAction.StartRename(place.id))
            viewModel.onAction(DestinationAction.RenameChanged("Work"))
            motion.value = VehicleMotion.MOVING

            viewModel.onAction(DestinationAction.SaveRename)

            assertEquals(listOf(place), store.current)
        }

    @Test
    fun deleting_the_place_being_renamed_closes_the_field_and_a_save_brings_nothing_back() =
        runTest {
            val place = fakeSavedPlace(id = 1L, label = "Office")
            val viewModel = subscribedViewModel(saved = listOf(place))
            viewModel.onAction(DestinationAction.StartRename(place.id))
            viewModel.onAction(DestinationAction.DeletePlace(place.id))

            // Closed by the delete itself, before any Save: a Save of an open field for a gone place would
            // otherwise be what clears it.
            assertNull(viewModel.uiState.value.renaming)

            viewModel.onAction(DestinationAction.SaveRename)

            assertEquals(emptyList(), store.current)
        }

    @Test
    fun clear_query_empties_the_query_even_while_moving() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.QueryChanged("Airport"))
            motion.value = VehicleMotion.MOVING
            viewModel.onAction(DestinationAction.ClearQuery)
            assertEquals("", viewModel.uiState.value.query)
        }

    @Test
    fun cancel_listening_resets_the_recognizer_so_a_late_result_is_discarded() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.StartListening)
            viewModel.onAction(DestinationAction.CancelListening)
            // reset() cancels the engine: unlike stop(), it requests no final
            // result that could refill the query after a hand-off.
            assertEquals(listOf("start", "reset"), speech.calls)
            assertEquals(VoiceState.Idle, viewModel.uiState.value.voice)
        }

    @Test
    fun listening_after_a_failure_resets_the_recognizer_first() =
        runTest {
            val viewModel = subscribedViewModel()
            speech.mutableState.value = VoiceState.Failed(messageRes = 0)
            viewModel.onAction(DestinationAction.StartListening)
            // Some engines silently ignore startListening() after onError
            // unless cancelled first.
            assertEquals(listOf("reset", "start"), speech.calls)
        }

    @Test
    fun listening_from_idle_starts_without_a_reset() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.StartListening)
            assertEquals(listOf("start"), speech.calls)
        }

    @Test
    fun the_hand_off_reads_available_until_checked() =
        runTest {
            // A check that has not run must not block a hand-off that works.
            handOffTarget = false
            val viewModel = subscribedViewModel()
            assertEquals(true, viewModel.uiState.value.handOffAvailable)
        }

    @Test
    fun a_check_that_finds_no_app_turns_the_hand_off_off() =
        runTest {
            handOffTarget = false
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.CheckHandOff)
            assertEquals(false, viewModel.uiState.value.handOffAvailable)
        }

    @Test
    fun a_later_check_sees_an_app_installed_since() =
        runTest {
            handOffTarget = false
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.CheckHandOff)

            handOffTarget = true
            viewModel.onAction(DestinationAction.CheckHandOff)

            assertEquals(true, viewModel.uiState.value.handOffAvailable)
        }

    @Test
    fun a_later_check_sees_the_last_app_removed_since() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.CheckHandOff)

            handOffTarget = false
            viewModel.onAction(DestinationAction.CheckHandOff)

            assertEquals(false, viewModel.uiState.value.handOffAvailable)
        }

    // --- Route preview (issue #389 phase 3) -----------------------------------

    @Test
    fun with_routing_off_nothing_about_routes_shows_and_a_route_request_is_ignored() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = false)
            routing.installTile(TILE)

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(false to RouteStep.Idle, state.routeAvailable to state.route)
            assertEquals(false, state.canRoute(DESTINATION))
            assertEquals(emptyList(), routing.engine.requests)
        }

    @Test
    fun with_routing_on_a_point_and_text_can_be_routed() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)

            val state = viewModel.uiState.value
            assertEquals(true to true, state.canRoute(DESTINATION) to state.canRoute(PlaceTarget.Query("Imabari")))
        }

    @Test
    fun without_a_geocoder_only_a_point_can_be_routed() =
        runTest {
            val viewModel =
                subscribedViewModel(routingAvailable = true, placeLocator = FakePlaceLocator(available = false))

            viewModel.onAction(DestinationAction.RouteTo(PlaceTarget.Query("Imabari"), ORIGIN))

            val state = viewModel.uiState.value
            assertEquals(true to false, state.canRoute(DESTINATION) to state.canRoute(PlaceTarget.Query("Imabari")))
            assertEquals(RouteStep.Idle, state.route)
        }

    @Test
    fun without_a_fix_and_with_no_position_to_be_found_the_route_says_it_starts_from_one() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, origin = null))

            assertEquals(RouteStep.Failed(RouteFailure.NO_FIX), viewModel.uiState.value.route)
        }

    @Test
    fun an_old_fix_is_no_start() =
        runTest {
            // The location flow replays its last fix after the receiver goes
            // quiet: a head unit started in an underground car park holds one
            // from elsewhere, which must not become the start even when no
            // current position can be found in its place.
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.offerTile(TILE, sizeBytes = TILE_BYTES)
            nowNanos = STALE_NANOS

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.Failed(RouteFailure.NO_FIX), viewModel.uiState.value.route)
            assertEquals(emptyList(), routing.feed.probedUrls)
        }

    @Test
    fun a_fix_within_the_freshness_window_is_a_start() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installTile(TILE)
            routing.engine.outcome = fakeEngineTrack()
            nowNanos = NOW_NANOS + LOCATION_STALE_THRESHOLD_MS * 1_000_000L

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.Done, viewModel.uiState.value.route)
        }

    @Test
    fun a_fresh_fix_is_the_start_without_asking_for_the_current_position() =
        runTest {
            currentFix.answer = HERE
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installTile(TILE)
            routing.engine.outcome = fakeEngineTrack()

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(
                0 to listOf(ORIGIN.point.toRoutePoint()),
                currentFix.requests to routing.engine.requests.map { it.origin },
            )
        }

    @Test
    fun an_old_fix_gives_way_to_the_current_position_as_the_start() =
        runTest {
            // A parked vehicle: the flow forwards no fix until the vehicle has
            // moved the minimum distance, so the last one is old though the
            // receiver knows where it is.
            currentFix.answer = HERE
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installTile(TILE)
            routing.engine.outcome = fakeEngineTrack()
            nowNanos = STALE_NANOS

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.Done, viewModel.uiState.value.route)
            assertEquals(listOf(RoutePoint(HERE.latitude, HERE.longitude)), routing.engine.requests.map { it.origin })
        }

    @Test
    fun without_a_fix_the_current_position_is_the_start() =
        runTest {
            currentFix.answer = HERE
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installTile(TILE)
            routing.engine.outcome = fakeEngineTrack()

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, origin = null))
            advanceUntilIdle()

            assertEquals(listOf(RoutePoint(HERE.latitude, HERE.longitude)), routing.engine.requests.map { it.origin })
        }

    @Test
    fun the_route_says_it_is_finding_the_position_while_the_request_is_out() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            currentFix.gateRequest()
            nowNanos = STALE_NANOS

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.FindingPosition, viewModel.uiState.value.route)
        }

    @Test
    fun a_position_request_that_runs_out_of_time_is_no_fix() =
        runTest {
            // The source gives up after its time limit and answers with none.
            val viewModel = subscribedViewModel(routingAvailable = true)
            val gate = currentFix.gateRequest()
            nowNanos = STALE_NANOS
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(RouteStep.Failed(RouteFailure.NO_FIX), viewModel.uiState.value.route)
        }

    @Test
    fun dismissing_while_the_position_is_sought_withdraws_the_request_and_starts_no_route() =
        runTest {
            currentFix.answer = HERE
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installTile(TILE)
            routing.engine.outcome = fakeEngineTrack()
            val gate = currentFix.gateRequest()
            nowNanos = STALE_NANOS
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            viewModel.onAction(DestinationAction.DismissRoute)
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, currentFix.withdrawn)
            assertEquals(RouteStep.Idle, viewModel.uiState.value.route)
            assertEquals(emptyList(), routing.engine.requests)
        }

    @Test
    fun without_the_location_permission_the_route_says_it_needs_it() =
        runTest {
            // No grant, so no fix will ever come: waiting for one would not help.
            locationGranted = false
            val viewModel = subscribedViewModel(routingAvailable = true)

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, origin = null))

            assertEquals(RouteStep.Failed(RouteFailure.NO_LOCATION_PERMISSION), viewModel.uiState.value.route)
        }

    @Test
    fun without_the_location_permission_no_position_is_asked_for() =
        runTest {
            locationGranted = false
            val viewModel = subscribedViewModel(routingAvailable = true)

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, origin = null))

            assertEquals(0, currentFix.requests)
        }

    @Test
    fun a_found_route_becomes_the_active_route() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installTile(TILE)
            routing.engine.outcome = fakeEngineTrack()

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.Done, viewModel.uiState.value.route)
            assertEquals(fakeRoute(), activeRoute.active.value?.route)
        }

    @Test
    fun a_found_route_starts_a_session_with_the_destination_and_reference() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installTile(TILE)
            routing.engine.outcome = fakeEngineTrack()
            routing.engine.writesReference = true

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            val active = assertNotNull(activeRoute.active.value)
            val written = assertNotNull(
                routing.engine.requests
                    .single()
                    .referenceOut,
            )
            assertEquals(RoutePoint(DESTINATION.latitude, DESTINATION.longitude), active.destination)
            assertEquals(written, active.reference)
            assertEquals(RerouteStatus.NONE, active.reroute)
        }

    @Test
    fun a_found_route_deletes_the_references_of_earlier_routes() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installTile(TILE)
            routing.engine.outcome = fakeEngineTrack()
            val earlier = earlierReference()

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertFalse(earlier.exists())
        }

    @Test
    fun the_references_are_pruned_off_the_main_thread() =
        runTest {
            val io = HeldDispatcher().apply { hold = true }
            val viewModel = subscribedViewModel(routingAvailable = true, ioDispatcher = io)
            routing.installTile(TILE)
            routing.engine.outcome = fakeEngineTrack()
            val earlier = earlierReference()
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()
            val keptUntilTheIoRuns = earlier.exists()

            io.releaseNewestFirst()

            assertEquals(true to false, keptUntilTheIoRuns to earlier.exists())
        }

    // A reference an earlier route left, written long enough ago that a prune may delete it.
    private fun earlierReference(): File =
        File(routing.referenceDir.apply { mkdirs() }, "earlier.bin").apply {
            writeText("x")
            setLastModified(routing.nowEpochMs - 2 * REFERENCE_PRUNE_GRACE_MS)
        }

    @Test
    fun a_found_route_starts_the_finder_with_the_routes_origin() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installTile(TILE)
            routing.engine.outcome = fakeEngineTrack()

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            val session = assertNotNull(activeRoute.active.value).session
            assertEquals(listOf(session to RoutePoint(ORIGIN.point.latitude, ORIGIN.point.longitude)), candidateStarts)
        }

    @Test
    fun a_found_route_with_other_routes_on_opens_the_choice() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true, suggestOtherRoutes = { true })
            routing.installTile(TILE)
            routing.engine.outcome = fakeEngineTrack()

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(true to 1, activeRoute.active.value?.choosing to candidateStarts.size)
        }

    @Test
    fun a_found_route_with_other_routes_off_opens_no_choice_and_starts_no_search() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true, suggestOtherRoutes = { false })
            routing.installTile(TILE)
            routing.engine.outcome = fakeEngineTrack()

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(false to 0, activeRoute.active.value?.choosing to candidateStarts.size)
        }

    // The setting is read as the route is found, a suspension the route had not had there before: a dismiss that lands
    // in it loses the route, and with it the reference the engine wrote, which no session holds.
    @Test
    fun dismissing_while_the_setting_is_read_starts_no_route_and_deletes_its_reference() =
        runTest {
            val setting = CompletableDeferred<Boolean>()
            val viewModel = subscribedViewModel(routingAvailable = true, suggestOtherRoutes = { setting.await() })
            routing.installTile(TILE)
            routing.engine.outcome = fakeEngineTrack()
            routing.engine.writesReference = true
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()
            val writtenWhileRead = referenceFiles().size

            viewModel.onAction(DestinationAction.DismissRoute)
            setting.complete(true)
            advanceUntilIdle()

            assertEquals(1, writtenWhileRead)
            assertNull(activeRoute.active.value)
            assertEquals(emptyList(), referenceFiles())
        }

    private fun referenceFiles(): List<File> =
        routing.referenceDir
            .listFiles()
            .orEmpty()
            .toList()

    // Once as the run starts, and again right before its search: the old route's candidates hold the planner too.
    @Test
    fun a_new_route_stops_the_finder_before_searching() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installTile(TILE)
            routing.engine.outcome = fakeEngineTrack()

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(2, candidateCancels)
        }

    @Test
    fun a_route_run_cancels_a_running_reroute() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))

            assertEquals(1, rerouteCancels)
        }

    // --- Route conditions ----------------------------------------------------

    @Test
    fun toggling_a_chip_stores_the_conditions() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)

            viewModel.onAction(DestinationAction.SetAvoidTolls(on = true))
            viewModel.onAction(DestinationAction.SetAvoidMotorways(on = true))

            assertEquals(RouteConditions(avoidMotorways = true, avoidTolls = true), routeConditions.conditions.value)
            assertEquals(routeConditions.conditions.value, viewModel.uiState.value.conditions)
        }

    // A slow disk: the second tap comes before the first is written, and must not undo it.
    @Test
    fun two_chip_taps_back_to_back_both_land() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            val writes = routeConditions.holdWrites()

            viewModel.onAction(DestinationAction.SetAvoidTolls(on = true))
            viewModel.onAction(DestinationAction.SetAvoidMotorways(on = true))
            writes.complete(Unit)
            advanceUntilIdle()

            assertEquals(RouteConditions(avoidMotorways = true, avoidTolls = true), routeConditions.conditions.value)
        }

    // The chip is changed while the run waits on the routing data on the device.
    @Test
    fun a_route_run_passes_the_conditions_read_at_its_start() =
        runTest {
            val viewModel =
                subscribedViewModel(routingAvailable = true, conditions = RouteConditions(avoidTolls = true))
            routing.installTile(TILE)
            routing.engine.outcome = fakeEngineTrack()
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))

            viewModel.onAction(DestinationAction.SetAvoidTolls(on = false))
            advanceUntilIdle()

            assertEquals(
                "1",
                routing.engine.requests
                    .single()
                    .parameters["avoid_toll"],
            )
            assertEquals(RouteConditions(avoidTolls = true), activeRoute.active.value?.conditions)
        }

    @Test
    fun a_failed_route_with_a_condition_on_says_so() =
        runTest {
            val viewModel =
                subscribedViewModel(routingAvailable = true, conditions = RouteConditions(avoidTolls = true))
            routing.installTile(TILE)
            routing.engine.outcome = EngineOutcome.Error("no track found at pass=0")

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.Failed(RouteFailure.NO_ROUTE_UNDER_CONDITIONS), viewModel.uiState.value.route)
        }

    @Test
    fun a_failed_route_without_conditions_keeps_the_old_message() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installTile(TILE)
            routing.engine.outcome = EngineOutcome.Error("no track found at pass=0")

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.Failed(RouteFailure.NO_ROUTE), viewModel.uiState.value.route)
        }

    // A reroute of the old route may start while the data downloads; the search after it must not wait for one.
    @Test
    fun the_search_after_a_download_cancels_a_running_reroute() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.offerTile(TILE, sizeBytes = TILE_BYTES)
            routing.engine.outcome = fakeEngineTrack()
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()
            val gate = routing.feed.gateDownload()
            viewModel.onAction(DestinationAction.ConfirmDownload)
            advanceUntilIdle()
            val cancelsBeforeTheSearch = rerouteCancels

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(cancelsBeforeTheSearch + 1, rerouteCancels)
        }

    @Test
    fun a_route_runs_while_moving() =
        runTest {
            motion.value = VehicleMotion.MOVING
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installTile(TILE)
            routing.engine.outcome = fakeEngineTrack()

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.Done, viewModel.uiState.value.route)
        }

    @Test
    fun text_the_geocoder_finds_routes_to_its_position() =
        runTest {
            val locator = FakePlaceLocator(answers = mapOf("Imabari" to PlaceLookup.Found(34.07, 133.0)))
            val viewModel = subscribedViewModel(routingAvailable = true, placeLocator = locator)
            routing.installTile(TILE)
            routing.engine.outcome = fakeEngineTrack()

            viewModel.onAction(DestinationAction.RouteTo(PlaceTarget.Query("Imabari"), ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.Done, viewModel.uiState.value.route)
            assertEquals(
                RoutePoint(34.07, 133.0),
                routing.engine.requests
                    .single()
                    .destination,
            )
        }

    @Test
    fun text_the_geocoder_does_not_know_is_a_place_not_found() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)

            viewModel.onAction(DestinationAction.RouteTo(PlaceTarget.Query("Nowhere"), ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.Failed(RouteFailure.PLACE_NOT_FOUND), viewModel.uiState.value.route)
        }

    @Test
    fun a_geocoder_that_cannot_answer_is_a_failed_look_up() =
        runTest {
            val locator = FakePlaceLocator(answers = mapOf("Imabari" to PlaceLookup.Failed))
            val viewModel = subscribedViewModel(routingAvailable = true, placeLocator = locator)

            viewModel.onAction(DestinationAction.RouteTo(PlaceTarget.Query("Imabari"), ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.Failed(RouteFailure.LOOKUP_FAILED), viewModel.uiState.value.route)
        }

    @Test
    fun missing_routing_data_asks_first_with_its_size() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.offerTile(TILE, sizeBytes = TILE_BYTES)

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.ConfirmDownload(TILE_BYTES), viewModel.uiState.value.route)
            assertEquals(emptyList(), routing.feed.downloads)
        }

    @Test
    fun a_confirmed_download_reports_its_progress_then_routes() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.offerTile(TILE, sizeBytes = TILE_BYTES)
            routing.engine.outcome = fakeEngineTrack()
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()
            val gate = routing.feed.gateDownload()

            viewModel.onAction(DestinationAction.ConfirmDownload)
            advanceUntilIdle()
            // The fake transport reports halfway, then waits at the gate.
            assertEquals(RouteStep.Downloading(TILE_BYTES / 2, TILE_BYTES), viewModel.uiState.value.route)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(RouteStep.Done, viewModel.uiState.value.route)
            assertEquals(fakeRoute(), activeRoute.active.value?.route)
        }

    @Test
    fun a_server_that_cannot_size_the_data_fails_the_download() =
        runTest {
            // No answer for the tile: the fake transport reports it unavailable.
            val viewModel = subscribedViewModel(routingAvailable = true)

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.Failed(RouteFailure.DOWNLOAD_FAILED), viewModel.uiState.value.route)
        }

    @Test
    fun data_that_would_not_fit_fails_as_no_space() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.offerTile(TILE, sizeBytes = TILE_BYTES)
            routing.freeBytes = 0L
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            viewModel.onAction(DestinationAction.ConfirmDownload)
            advanceUntilIdle()

            assertEquals(RouteStep.Failed(RouteFailure.NO_SPACE), viewModel.uiState.value.route)
        }

    @Test
    fun a_point_where_the_server_has_no_data_has_no_road_data_without_asking() =
        runTest {
            // Open sea: the server has no tile, which weighs nothing, so no
            // confirmation comes first, and BRouter then finds no road there.
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.goneTile(TILE)
            routing.engine.outcome = EngineOutcome.Error("datafile $TILE.rd5 not found")

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.Failed(RouteFailure.NO_ROAD_DATA), viewModel.uiState.value.route)
        }

    // Without conditions, today's message.
    @Test
    fun a_search_out_of_time_is_timed_out() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installTile(TILE)
            routing.engine.outcome = EngineOutcome.Error("operation timeout after 60 seconds")

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.Failed(RouteFailure.TIMED_OUT), viewModel.uiState.value.route)
        }

    @Test
    fun a_search_out_of_time_with_a_condition_on_says_so() =
        runTest {
            val viewModel =
                subscribedViewModel(routingAvailable = true, conditions = RouteConditions(avoidMotorways = true))
            routing.installTile(TILE)
            routing.engine.outcome = EngineOutcome.Error("operation timeout after 60 seconds")

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.Failed(RouteFailure.TIMED_OUT_UNDER_CONDITIONS), viewModel.uiState.value.route)
        }

    @Test
    fun any_other_engine_failure_is_no_route() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installTile(TILE)
            routing.engine.outcome = EngineOutcome.Error("target island detected")

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.Failed(RouteFailure.NO_ROUTE), viewModel.uiState.value.route)
        }

    @Test
    fun a_version_mismatch_offers_the_data_again() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installTile(TILE)
            routing.engine.outcome =
                EngineOutcome.Error("lookup version mismatch (old rd5?) lookups.dat=11 $TILE.rd5=10")
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()
            assertEquals(RouteStep.Failed(RouteFailure.DATA_VERSION_MISMATCH), viewModel.uiState.value.route)

            routing.offerTile(TILE, sizeBytes = TILE_BYTES)
            viewModel.onAction(DestinationAction.DownloadAgain)
            advanceUntilIdle()

            assertEquals(RouteStep.ConfirmDownload(TILE_BYTES), viewModel.uiState.value.route)
            assertEquals(emptyList(), routing.tiles.installedTiles())
        }

    @Test
    fun dismissing_returns_to_the_entry_and_drops_the_pending_download() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.offerTile(TILE, sizeBytes = TILE_BYTES)
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            viewModel.onAction(DestinationAction.DismissRoute)
            viewModel.onAction(DestinationAction.ConfirmDownload)
            advanceUntilIdle()

            assertEquals(RouteStep.Idle, viewModel.uiState.value.route)
            assertEquals(emptyList(), routing.feed.downloads)
        }

    @Test
    fun dismissing_a_download_leaves_no_active_route() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.offerTile(TILE, sizeBytes = TILE_BYTES)
            routing.engine.outcome = fakeEngineTrack()
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()
            val gate = routing.feed.gateDownload()
            viewModel.onAction(DestinationAction.ConfirmDownload)
            advanceUntilIdle()
            assertEquals(RouteStep.Downloading(TILE_BYTES / 2, TILE_BYTES), viewModel.uiState.value.route)

            viewModel.onAction(DestinationAction.DismissRoute)
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(RouteStep.Idle, viewModel.uiState.value.route)
            assertNull(activeRoute.active.value?.route)
            assertEquals(emptyList(), routing.engine.requests)
        }

    @Test
    fun a_progress_report_after_a_dismiss_leaves_the_entry_in_place() =
        runTest {
            // A blocking read can report once more after the cancel; the
            // report must not bring the download back over the entry.
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.offerTile(TILE, sizeBytes = TILE_BYTES)
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()
            routing.feed.gateOutlivesCancel = true
            val gate = routing.feed.gateDownload()
            viewModel.onAction(DestinationAction.ConfirmDownload)
            advanceUntilIdle()

            viewModel.onAction(DestinationAction.DismissRoute)
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(RouteStep.Idle, viewModel.uiState.value.route)
        }

    @Test
    fun dismissing_a_search_leaves_no_active_route() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installTile(TILE)
            routing.engine.outcome = fakeEngineTrack()
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            // The planner's file work waits on the test scheduler, so the
            // search is under way until the test advances it.
            assertEquals(RouteStep.Computing, viewModel.uiState.value.route)

            viewModel.onAction(DestinationAction.DismissRoute)
            advanceUntilIdle()

            assertEquals(RouteStep.Idle, viewModel.uiState.value.route)
            assertNull(activeRoute.active.value?.route)
        }

    @Test
    fun a_second_route_replaces_the_one_under_way() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installTile(TILE)
            routing.engine.outcome = fakeEngineTrack()
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            assertEquals(RouteStep.Computing, viewModel.uiState.value.route)

            viewModel.onAction(DestinationAction.RouteTo(OTHER_DESTINATION, ORIGIN))
            advanceUntilIdle()

            // Only the second search ran: the first never reached the engine.
            assertEquals(listOf(OTHER_DESTINATION.toRoutePoint()), routing.engine.requests.map { it.destination })
            assertEquals(RouteStep.Done, viewModel.uiState.value.route)
        }

    // --- weekly check of the routing data ---------------------------------------

    @Test
    fun data_checked_within_the_week_routes_without_asking_the_server() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installCheckedTile(TILE, checkedAtEpochMs = routing.nowEpochMs - WEEK_MS + 1)
            routing.rebuildTile(TILE, sizeBytes = TILE_BYTES)
            routing.engine.outcome = fakeEngineTrack()

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.Done, viewModel.uiState.value.route)
            assertEquals(emptyList(), routing.feed.revalidatedUrls)
        }

    @Test
    fun data_a_week_old_is_checked_and_routes_when_unchanged() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installCheckedTile(TILE, checkedAtEpochMs = routing.nowEpochMs - WEEK_MS)
            routing.keepTile(TILE)
            routing.engine.outcome = fakeEngineTrack()

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.Done, viewModel.uiState.value.route)
            assertEquals(listOf(routing.tileUrl(TILE)), routing.feed.revalidatedUrls)
            assertEquals(emptyList(), routing.feed.downloads)
            assertEquals(
                routing.nowEpochMs,
                routing.tiles
                    .installedTiles()
                    .single()
                    .checkedAtEpochMs,
            )
        }

    @Test
    fun the_check_says_so_while_it_asks_the_server() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installCheckedTile(TILE, checkedAtEpochMs = routing.nowEpochMs - WEEK_MS)
            routing.engine.outcome = fakeEngineTrack()
            val gate = routing.feed.gateRevalidate()

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()
            assertEquals(RouteStep.CheckingData, viewModel.uiState.value.route)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(RouteStep.Done, viewModel.uiState.value.route)
        }

    @Test
    fun a_check_without_an_answer_routes_with_the_data_on_the_device() =
        runTest {
            // The fake server answers no check: offline, the installed tile routes.
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installCheckedTile(TILE, checkedAtEpochMs = routing.nowEpochMs - WEEK_MS)
            routing.engine.outcome = fakeEngineTrack()

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.Done, viewModel.uiState.value.route)
            assertEquals(emptyList(), routing.feed.downloads)
        }

    @Test
    fun rebuilt_data_asks_first_with_the_size_of_the_newer_file() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installCheckedTile(TILE, checkedAtEpochMs = routing.nowEpochMs - WEEK_MS)
            routing.rebuildTile(TILE, sizeBytes = TILE_BYTES)

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.ConfirmDownload(TILE_BYTES, RouteDownload.NEWER), viewModel.uiState.value.route)
            assertEquals(emptyList(), routing.feed.downloads)
            assertEquals(emptyList(), routing.engine.requests)
        }

    @Test
    fun confirmed_newer_data_replaces_the_tile_then_routes() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installCheckedTile(TILE, checkedAtEpochMs = routing.nowEpochMs - WEEK_MS)
            routing.rebuildTile(TILE, sizeBytes = TILE_BYTES)
            routing.engine.outcome = fakeEngineTrack()
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            viewModel.onAction(DestinationAction.ConfirmDownload)
            advanceUntilIdle()

            assertEquals(RouteStep.Done, viewModel.uiState.value.route)
            assertEquals(listOf(routing.tileUrl(TILE)), routing.feed.downloads.map { it.url })
            assertEquals(
                TILE_BYTES,
                routing.tiles
                    .installedTiles()
                    .single()
                    .sizeBytes,
            )
        }

    @Test
    fun rebuilt_data_and_missing_data_are_asked_for_together() =
        runTest {
            // Two tiles across a 5° edge: the western one is installed and
            // rebuilt since, the eastern one missing.
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installCheckedTile(TILE, checkedAtEpochMs = routing.nowEpochMs - WEEK_MS)
            routing.rebuildTile(TILE, sizeBytes = TILE_BYTES)
            routing.offerTile(EAST_TILE, sizeBytes = EAST_TILE_BYTES)

            viewModel.onAction(DestinationAction.RouteTo(EAST_DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(
                RouteStep.ConfirmDownload(TILE_BYTES + EAST_TILE_BYTES, RouteDownload.MISSING_AND_NEWER),
                viewModel.uiState.value.route,
            )
        }

    @Test
    fun cancelling_newer_data_downloads_nothing() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installCheckedTile(TILE, checkedAtEpochMs = routing.nowEpochMs - WEEK_MS)
            routing.rebuildTile(TILE, sizeBytes = TILE_BYTES)
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            viewModel.onAction(DestinationAction.DismissRoute)
            advanceUntilIdle()

            assertEquals(RouteStep.Idle, viewModel.uiState.value.route)
            assertEquals(emptyList(), routing.feed.downloads)
        }

    @Test
    fun stored_data_routes_at_once_when_the_driver_declines_newer_data() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installCheckedTile(TILE, checkedAtEpochMs = routing.nowEpochMs - WEEK_MS)
            routing.rebuildTile(TILE, sizeBytes = TILE_BYTES)
            routing.engine.outcome = fakeEngineTrack()
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            viewModel.onAction(DestinationAction.UseStoredData)
            advanceUntilIdle()

            assertEquals(RouteStep.Done, viewModel.uiState.value.route)
            assertEquals(fakeRoute(), activeRoute.active.value?.route)
            assertEquals(emptyList(), routing.feed.downloads)
            assertEquals(
                routing.nowEpochMs,
                routing.tiles
                    .installedTiles()
                    .single()
                    .checkedAtEpochMs,
            )
        }

    @Test
    fun declined_newer_data_is_not_asked_about_again_within_the_week() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installCheckedTile(TILE, checkedAtEpochMs = routing.nowEpochMs - WEEK_MS)
            routing.rebuildTile(TILE, sizeBytes = TILE_BYTES)
            routing.engine.outcome = fakeEngineTrack()
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()
            viewModel.onAction(DestinationAction.UseStoredData)
            advanceUntilIdle()
            viewModel.onAction(DestinationAction.DismissRoute)

            routing.nowEpochMs += WEEK_MS - 1
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.Done, viewModel.uiState.value.route)
            assertEquals(1, routing.feed.revalidatedUrls.size)
        }

    @Test
    fun declined_newer_data_is_asked_about_again_after_a_week() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installCheckedTile(TILE, checkedAtEpochMs = routing.nowEpochMs - WEEK_MS)
            routing.rebuildTile(TILE, sizeBytes = TILE_BYTES)
            routing.engine.outcome = fakeEngineTrack()
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()
            viewModel.onAction(DestinationAction.UseStoredData)
            advanceUntilIdle()
            viewModel.onAction(DestinationAction.DismissRoute)

            routing.nowEpochMs += WEEK_MS
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.ConfirmDownload(TILE_BYTES, RouteDownload.NEWER), viewModel.uiState.value.route)
        }

    @Test
    fun stored_data_is_no_way_out_when_the_route_needs_missing_data() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installCheckedTile(TILE, checkedAtEpochMs = routing.nowEpochMs - WEEK_MS)
            routing.rebuildTile(TILE, sizeBytes = TILE_BYTES)
            routing.offerTile(EAST_TILE, sizeBytes = EAST_TILE_BYTES)
            viewModel.onAction(DestinationAction.RouteTo(EAST_DESTINATION, ORIGIN))
            advanceUntilIdle()
            val offer = viewModel.uiState.value.route

            viewModel.onAction(DestinationAction.UseStoredData)
            advanceUntilIdle()

            assertEquals(offer, viewModel.uiState.value.route)
            assertEquals(emptyList(), routing.engine.requests)
        }

    @Test
    fun cancelling_missing_and_newer_data_downloads_nothing() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installCheckedTile(TILE, checkedAtEpochMs = routing.nowEpochMs - WEEK_MS)
            routing.rebuildTile(TILE, sizeBytes = TILE_BYTES)
            routing.offerTile(EAST_TILE, sizeBytes = EAST_TILE_BYTES)
            viewModel.onAction(DestinationAction.RouteTo(EAST_DESTINATION, ORIGIN))
            advanceUntilIdle()

            viewModel.onAction(DestinationAction.DismissRoute)
            advanceUntilIdle()

            assertEquals(RouteStep.Idle, viewModel.uiState.value.route)
            assertEquals(emptyList(), routing.feed.downloads)
        }

    @Test
    fun a_failed_update_routes_with_the_stored_data_and_counts_as_checked() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installCheckedTile(TILE, checkedAtEpochMs = routing.nowEpochMs - WEEK_MS)
            routing.rebuildTile(TILE, sizeBytes = TILE_BYTES)
            routing.feed.stopsByUrl[routing.tileUrl(TILE)] = FakeDownloadStop(5L, TransferResult.Interrupted)
            routing.engine.outcome = fakeEngineTrack()
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            viewModel.onAction(DestinationAction.ConfirmDownload)
            advanceUntilIdle()

            assertEquals(RouteStep.Done, viewModel.uiState.value.route)
            val tile = routing.tiles.installedTiles().single()
            assertEquals(1L, tile.sizeBytes, "the stored tile is the one routed with")
            assertEquals(routing.nowEpochMs, tile.checkedAtEpochMs)
        }

    @Test
    fun an_update_without_room_routes_with_the_stored_data() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installCheckedTile(TILE, checkedAtEpochMs = routing.nowEpochMs - WEEK_MS)
            routing.rebuildTile(TILE, sizeBytes = TILE_BYTES)
            routing.engine.outcome = fakeEngineTrack()
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()
            routing.freeBytes = 0L

            viewModel.onAction(DestinationAction.ConfirmDownload)
            advanceUntilIdle()

            assertEquals(RouteStep.Done, viewModel.uiState.value.route)
        }

    @Test
    fun with_missing_and_newer_data_a_failed_update_still_routes() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installCheckedTile(TILE, checkedAtEpochMs = routing.nowEpochMs - WEEK_MS)
            routing.rebuildTile(TILE, sizeBytes = TILE_BYTES)
            routing.offerTile(EAST_TILE, sizeBytes = EAST_TILE_BYTES)
            routing.feed.stopsByUrl[routing.tileUrl(TILE)] = FakeDownloadStop(5L, TransferResult.Interrupted)
            routing.engine.outcome = fakeEngineTrack()
            viewModel.onAction(DestinationAction.RouteTo(EAST_DESTINATION, ORIGIN))
            advanceUntilIdle()

            viewModel.onAction(DestinationAction.ConfirmDownload)
            advanceUntilIdle()

            assertEquals(RouteStep.Done, viewModel.uiState.value.route)
            assertEquals(listOf(TILE, EAST_TILE), routing.tiles.installedTiles().map { it.name })
        }

    @Test
    fun with_missing_and_newer_data_a_failed_missing_tile_fails_the_route() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installCheckedTile(TILE, checkedAtEpochMs = routing.nowEpochMs - WEEK_MS)
            routing.rebuildTile(TILE, sizeBytes = TILE_BYTES)
            routing.offerTile(EAST_TILE, sizeBytes = EAST_TILE_BYTES)
            routing.feed.stopsByUrl[routing.tileUrl(EAST_TILE)] = FakeDownloadStop(5L, TransferResult.Interrupted)
            viewModel.onAction(DestinationAction.RouteTo(EAST_DESTINATION, ORIGIN))
            advanceUntilIdle()

            viewModel.onAction(DestinationAction.ConfirmDownload)
            advanceUntilIdle()

            assertEquals(RouteStep.Failed(RouteFailure.DOWNLOAD_FAILED), viewModel.uiState.value.route)
        }

    @Test
    fun confirmed_missing_and_newer_data_fetches_both_then_routes() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installCheckedTile(TILE, checkedAtEpochMs = routing.nowEpochMs - WEEK_MS)
            routing.rebuildTile(TILE, sizeBytes = TILE_BYTES)
            routing.offerTile(EAST_TILE, sizeBytes = EAST_TILE_BYTES)
            routing.engine.outcome = fakeEngineTrack()
            viewModel.onAction(DestinationAction.RouteTo(EAST_DESTINATION, ORIGIN))
            advanceUntilIdle()

            viewModel.onAction(DestinationAction.ConfirmDownload)
            advanceUntilIdle()

            assertEquals(RouteStep.Done, viewModel.uiState.value.route)
            assertEquals(
                listOf(routing.tileUrl(EAST_TILE), routing.tileUrl(TILE)),
                routing.feed.downloads.map { it.url },
            )
        }

    // --- a point picked on the map ---------------------------------------------

    @Test
    fun a_picked_point_is_offered_and_not_saved() =
        runTest {
            val viewModel = subscribedViewModel()

            viewModel.onAction(DestinationAction.PickPoint(DESTINATION))

            assertEquals(DESTINATION, viewModel.uiState.value.pickedPoint)
            assertEquals(emptyList(), store.current)
        }

    @Test
    fun clearing_the_picked_point_gives_the_entry_back() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.PickPoint(DESTINATION))

            viewModel.onAction(DestinationAction.ClearPickedPoint)

            assertNull(viewModel.uiState.value.pickedPoint)
        }

    @Test
    fun saving_the_picked_point_labels_it_with_its_coordinates() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.PickPoint(DESTINATION))

            viewModel.onAction(DestinationAction.SavePickedPoint)

            assertEquals(listOf("34.07000, 133.00000" to DESTINATION), store.current.map { it.label to it.target })
        }

    @Test
    fun saving_the_picked_point_is_ignored_while_moving() =
        runTest {
            motion.value = VehicleMotion.MOVING
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.PickPoint(DESTINATION))

            viewModel.onAction(DestinationAction.SavePickedPoint)

            assertEquals(emptyList(), store.current)
        }

    @Test
    fun a_picked_point_routes_like_a_saved_point() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installTile(TILE)
            routing.engine.outcome = fakeEngineTrack()
            viewModel.onAction(DestinationAction.PickPoint(DESTINATION))

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()

            assertEquals(RouteStep.Done, viewModel.uiState.value.route)
            assertEquals(
                DESTINATION.toRoutePoint(),
                routing.engine.requests
                    .single()
                    .destination,
            )
        }

    // --- a place another app sent ----------------------------------------------

    @Test
    fun a_received_point_is_offered_with_its_name() =
        runTest {
            val viewModel = subscribedViewModel()

            viewModel.onAction(DestinationAction.Receive(ReceivedPlace.Found(DESTINATION, "Imabari Castle")))

            assertEquals(
                DESTINATION to "Imabari Castle",
                viewModel.uiState.value.let { it.pickedPoint to it.pickedLabel },
            )
        }

    @Test
    fun a_received_query_fills_the_search_field_even_while_moving() =
        runTest {
            motion.value = VehicleMotion.MOVING
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.PickPoint(DESTINATION))

            viewModel.onAction(DestinationAction.Receive(ReceivedPlace.Found(PlaceTarget.Query("Matsuyama Castle"))))

            assertEquals(
                "Matsuyama Castle" to null,
                viewModel.uiState.value.let { it.query to it.pickedPoint },
            )
        }

    @Test
    fun an_unreadable_place_shows_the_line_over_the_search() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.PickPoint(DESTINATION))

            viewModel.onAction(DestinationAction.Receive(ReceivedPlace.Unreadable))

            assertEquals(true to null, viewModel.uiState.value.let { it.receivedUnreadable to it.pickedPoint })
        }

    @Test
    fun an_unreadable_place_clears_the_search_before_it() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(
                DestinationAction.Receive(ReceivedPlace.Found(PlaceTarget.Query("Matsuyama Castle"), "")),
            )

            viewModel.onAction(DestinationAction.Receive(ReceivedPlace.Unreadable))

            assertEquals("" to true, viewModel.uiState.value.let { it.query to it.receivedUnreadable })
        }

    @Test
    fun a_second_place_replaces_the_first() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.Receive(ReceivedPlace.Unreadable))

            viewModel.onAction(DestinationAction.Receive(ReceivedPlace.Found(DESTINATION, "Imabari Castle")))

            assertEquals(
                Triple(false, DESTINATION, "Imabari Castle"),
                viewModel.uiState.value.let { Triple(it.receivedUnreadable, it.pickedPoint, it.pickedLabel) },
            )
        }

    @Test
    fun typing_puts_the_unreadable_line_away() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.Receive(ReceivedPlace.Unreadable))

            viewModel.onAction(DestinationAction.QueryChanged("Airport"))

            assertFalse(viewModel.uiState.value.receivedUnreadable)
        }

    @Test
    fun a_long_press_after_a_received_point_drops_its_name() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.Receive(ReceivedPlace.Found(DESTINATION, "Imabari Castle")))

            viewModel.onAction(DestinationAction.PickPoint(ORIGIN.point))

            assertEquals("", viewModel.uiState.value.pickedLabel)
        }

    @Test
    fun saving_a_received_point_labels_it_with_its_name() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.Receive(ReceivedPlace.Found(DESTINATION, "Imabari Castle")))

            viewModel.onAction(DestinationAction.SavePickedPoint)

            assertEquals(listOf("Imabari Castle" to DESTINATION), store.current.map { it.label to it.target })
        }

    // A route's status takes the panel's body over and names no destination: left running, a download prompt for the
    // old route would stand where the received place should, and Download would route to the old place.
    @Test
    fun a_place_received_at_a_download_prompt_stops_the_route_and_shows() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.offerTile(TILE, sizeBytes = TILE_BYTES)
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            advanceUntilIdle()
            assertEquals(RouteStep.ConfirmDownload(TILE_BYTES), viewModel.uiState.value.route)

            viewModel.onAction(DestinationAction.Receive(ReceivedPlace.Found(OTHER_DESTINATION, "Imabari Castle")))
            advanceUntilIdle()

            assertEquals(
                RouteStep.Idle to OTHER_DESTINATION,
                viewModel.uiState.value.let { it.route to it.pickedPoint },
            )
        }

    @Test
    fun a_place_received_during_a_search_stops_it_and_shows() =
        runTest {
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.installTile(TILE)
            routing.engine.outcome = fakeEngineTrack()
            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, ORIGIN))
            assertEquals(RouteStep.Computing, viewModel.uiState.value.route)

            viewModel.onAction(DestinationAction.Receive(ReceivedPlace.Found(OTHER_DESTINATION, "Imabari Castle")))
            advanceUntilIdle()

            assertEquals(
                Triple(RouteStep.Idle, OTHER_DESTINATION, null),
                viewModel.uiState.value.let { Triple(it.route, it.pickedPoint, activeRoute.active.value?.route) },
            )
        }

    @Test
    fun speaking_puts_the_unreadable_line_away() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.Receive(ReceivedPlace.Unreadable))

            viewModel.onAction(DestinationAction.StartListening)

            assertFalse(viewModel.uiState.value.receivedUnreadable)
        }

    @Test
    fun a_voice_result_puts_the_unreadable_line_away() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.Receive(ReceivedPlace.Unreadable))

            speech.mutableState.value = VoiceState.Result("Airport")

            assertEquals("Airport" to false, viewModel.uiState.value.let { it.query to it.receivedUnreadable })
        }

    @Test
    fun a_received_name_past_the_name_cap_is_cut_to_it() =
        runTest {
            val viewModel = subscribedViewModel()

            viewModel.onAction(DestinationAction.Receive(ReceivedPlace.Found(DESTINATION, "x".repeat(2_000))))

            assertEquals(MAX_PLACE_NAME_LENGTH, viewModel.uiState.value.pickedLabel.length)
        }

    @Test
    fun a_received_place_stops_the_microphone_so_no_transcript_overwrites_it() =
        runTest {
            val viewModel = subscribedViewModel()
            viewModel.onAction(DestinationAction.StartListening)

            viewModel.onAction(DestinationAction.Receive(ReceivedPlace.Found(PlaceTarget.Query("Matsuyama Castle"))))

            assertEquals(VoiceState.Idle, viewModel.uiState.value.voice)
        }

    private fun PlaceTarget.Point.toRoutePoint() = RoutePoint(latitude, longitude)

    private companion object {
        // A boot-clock reading well past zero, so an older fix stays positive.
        const val NOW_NANOS = 3_600_000_000_000L

        // Matsuyama to Imabari: both ends and the margin around them lie in one routing tile. The start is a fix taken
        // at NOW_NANOS, fresh unless a test moves the clock on.
        val ORIGIN = LastFix(PlaceTarget.Point(33.84, 132.77), elapsedRealtimeNanos = NOW_NANOS)
        val DESTINATION = PlaceTarget.Point(34.07, 133.0)

        // The first boot-clock reading at which ORIGIN is no longer fresh.
        const val STALE_NANOS = NOW_NANOS + LOCATION_STALE_THRESHOLD_MS * 1_000_000L + 1L

        // Where the device says it is when asked: a little off ORIGIN, in the same routing tile.
        val HERE = CurrentFix(latitude = 33.85, longitude = 132.78)

        // Another destination in the same routing tile.
        val OTHER_DESTINATION = PlaceTarget.Point(33.95, 132.9)
        const val TILE = "E130_N30"
        const val TILE_BYTES = 48_000_000L

        // A destination across the 135° meridian from ORIGIN, whose route reads TILE and the tile east of it.
        val EAST_DESTINATION = PlaceTarget.Point(34.07, 135.5)
        const val EAST_TILE = "E135_N30"
        const val EAST_TILE_BYTES = 31_000_000L

        const val WEEK_MS = ROUTING_TILE_CHECK_INTERVAL_MS
    }
}

package io.github.seijikohara.femto.ui.destination

import io.github.seijikohara.femto.data.geocoding.PlaceLookup
import io.github.seijikohara.femto.data.location.LOCATION_STALE_THRESHOLD_MS
import io.github.seijikohara.femto.data.location.VehicleMotion
import io.github.seijikohara.femto.data.places.PlaceTarget
import io.github.seijikohara.femto.data.places.SavedPlace
import io.github.seijikohara.femto.data.routing.ActiveRouteStore
import io.github.seijikohara.femto.data.routing.EngineOutcome
import io.github.seijikohara.femto.data.routing.RoutePoint
import io.github.seijikohara.femto.data.voice.VoiceState
import io.github.seijikohara.femto.testfixtures.FakePlaceLocator
import io.github.seijikohara.femto.testfixtures.FakeSavedPlacesStore
import io.github.seijikohara.femto.testfixtures.FakeSpeechInput
import io.github.seijikohara.femto.testfixtures.RoutingFixture
import io.github.seijikohara.femto.testfixtures.fakeEngineTrack
import io.github.seijikohara.femto.testfixtures.fakeRoute
import io.github.seijikohara.femto.testfixtures.fakeSavedPlace
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
import kotlin.test.assertEquals

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

    // The boot clock a route's start is judged by, and the location grant.
    private var nowNanos = NOW_NANOS
    private var locationGranted = true

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
            nowElapsedRealtimeNanos = { nowNanos },
            hasLocationPermission = { locationGranted },
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
    fun without_a_fix_the_route_says_it_starts_from_one() =
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
            // from elsewhere, which must not become the start.
            val viewModel = subscribedViewModel(routingAvailable = true)
            routing.offerTile(TILE, sizeBytes = TILE_BYTES)
            nowNanos = NOW_NANOS + LOCATION_STALE_THRESHOLD_MS * 1_000_000L + 1L

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
    fun without_the_location_permission_the_route_says_it_needs_it() =
        runTest {
            // No grant, so no fix will ever come: waiting for one would not help.
            locationGranted = false
            val viewModel = subscribedViewModel(routingAvailable = true)

            viewModel.onAction(DestinationAction.RouteTo(DESTINATION, origin = null))

            assertEquals(RouteStep.Failed(RouteFailure.NO_LOCATION_PERMISSION), viewModel.uiState.value.route)
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
            assertEquals(fakeRoute(), activeRoute.route.value)
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
            assertEquals(fakeRoute(), activeRoute.route.value)
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

    private companion object {
        // A boot-clock reading well past zero, so an older fix stays positive.
        const val NOW_NANOS = 3_600_000_000_000L

        // Matsuyama to Imabari: both ends and the margin around them lie in one routing tile. The start is a fix taken
        // at NOW_NANOS, fresh unless a test moves the clock on.
        val ORIGIN = LastFix(PlaceTarget.Point(33.84, 132.77), elapsedRealtimeNanos = NOW_NANOS)
        val DESTINATION = PlaceTarget.Point(34.07, 133.0)
        const val TILE = "E130_N30"
        const val TILE_BYTES = 48_000_000L
    }
}

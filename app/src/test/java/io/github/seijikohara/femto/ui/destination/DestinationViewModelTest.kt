package io.github.seijikohara.femto.ui.destination

import io.github.seijikohara.femto.data.location.VehicleMotion
import io.github.seijikohara.femto.data.places.PlaceTarget
import io.github.seijikohara.femto.data.places.SavedPlace
import io.github.seijikohara.femto.data.voice.VoiceState
import io.github.seijikohara.femto.testfixtures.FakeSavedPlacesStore
import io.github.seijikohara.femto.testfixtures.FakeSpeechInput
import io.github.seijikohara.femto.testfixtures.fakeSavedPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DestinationViewModelTest {
    private var store = FakeSavedPlacesStore()
    private val speech = FakeSpeechInput()
    private val motion = MutableStateFlow(VehicleMotion.PARKED)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // uiState is shared WhileUiSubscribed, and the gate reads its current
    // value, so every test holds a subscriber the way the panel does.
    private fun TestScope.subscribedViewModel(
        saved: List<SavedPlace> = emptyList(),
        motionFlow: Flow<VehicleMotion> = motion,
    ): DestinationViewModel =
        DestinationViewModel(FakeSavedPlacesStore(saved).also { store = it }, motionFlow, speech).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
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
}

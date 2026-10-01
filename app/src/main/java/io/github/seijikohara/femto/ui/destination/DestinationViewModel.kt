package io.github.seijikohara.femto.ui.destination

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.seijikohara.femto.data.common.WhileUiSubscribed
import io.github.seijikohara.femto.data.location.LocationGraph
import io.github.seijikohara.femto.data.location.VehicleMotion
import io.github.seijikohara.femto.data.places.PlaceTarget
import io.github.seijikohara.femto.data.places.SavedPlacesPreferences
import io.github.seijikohara.femto.data.places.SavedPlacesStore
import io.github.seijikohara.femto.data.voice.SpeechInput
import io.github.seijikohara.femto.data.voice.VoiceRecognizer
import io.github.seijikohara.femto.data.voice.VoiceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Drives the destination panel: the query (typed, or dictated through
 * [speech]), the saved places in [placesStore], and the motion gate from
 * [motionFlow] (see [DestinationUiState.typingAllowed]).
 *
 * The gate lives here rather than only in the UI, so a stale tap or a
 * keyboard that stays up as the vehicle pulls away cannot type, save or
 * delete: every edit that involves typing is dropped while moving. A voice
 * result fills the query in any motion state but never hands off by itself;
 * the user still taps Navigate. The hand-off is not an action here: the
 * panel raises it to the dashboard, whose ViewModel owns the launch events.
 */
internal class DestinationViewModel(
    private val placesStore: SavedPlacesStore,
    motionFlow: Flow<VehicleMotion>,
    private val speech: SpeechInput,
) : ViewModel() {
    private val query = MutableStateFlow("")

    val uiState: StateFlow<DestinationUiState> =
        combine(
            query,
            speech.state,
            placesStore.places,
            // Seeded so the panel never waits on a location stack that has not
            // spoken; UNKNOWN fails open (see DestinationUiState.typingAllowed).
            motionFlow.onStart { emit(VehicleMotion.UNKNOWN) }.distinctUntilChanged(),
        ) {
            query,
            voice,
            places,
            motion,
            ->
            DestinationUiState(query = query, voice = voice, places = places, motion = motion)
        }.stateIn(viewModelScope, WhileUiSubscribed, DestinationUiState.Initial)

    init {
        // A final transcript becomes the query, and the recognizer goes back to
        // Idle so the same result is not applied twice and the mic is ready
        // for another try. Partial transcripts only show in the voice line.
        viewModelScope.launch {
            speech.state.collect { state ->
                if (state is VoiceState.Result) {
                    query.value = state.text
                    speech.reset()
                }
            }
        }
    }

    fun onAction(action: DestinationAction) {
        val typingAllowed = uiState.value.typingAllowed
        when (action) {
            is DestinationAction.QueryChanged -> {
                if (typingAllowed) query.value = action.text
            }

            DestinationAction.StartListening -> {
                // Some engines silently ignore the next start after an error
                // unless the recognizer is cancelled first (VoiceRecognizer.reset).
                if (speech.state.value is VoiceState.Failed) speech.reset()
                speech.start()
            }

            DestinationAction.StopListening -> {
                speech.stop()
            }

            DestinationAction.CancelListening -> {
                // reset() cancels the engine; stop() would still deliver a
                // final result that could refill the query after a hand-off.
                speech.reset()
            }

            DestinationAction.SaveQuery -> {
                query.value
                    .trim()
                    .takeIf { typingAllowed && it.isNotEmpty() }
                    ?.let { text -> save(label = text, target = PlaceTarget.Query(text)) }
            }

            is DestinationAction.SaveCurrentLocation -> {
                if (typingAllowed) {
                    val label = action.address.trim().ifEmpty { action.point.coordinateLabel() }
                    save(label = label, target = action.point)
                }
            }

            is DestinationAction.DeletePlace -> {
                if (typingAllowed) viewModelScope.launch { placesStore.delete(action.id) }
            }

            DestinationAction.ClearQuery -> {
                query.value = ""
            }
        }
    }

    // viewModelScope outlives a panel collapsed right after the tap, so the
    // write always lands.
    private fun save(
        label: String,
        target: PlaceTarget,
    ) {
        viewModelScope.launch { placesStore.add(label, target) }
    }

    override fun onCleared() {
        speech.destroy()
    }
}

// Five decimals (about 1 m) names a place without an address; Locale.ROOT
// keeps the separator a dot in every locale, matching the hand-off URI.
private fun PlaceTarget.Point.coordinateLabel(): String = String.format(Locale.ROOT, "%.5f, %.5f", latitude, longitude)

/**
 * Wires the production store, the shared vehicle-motion verdict (the same
 * [LocationGraph] pipeline the dashboard and the updater read), and
 * the platform recognizer.
 */
internal val DestinationViewModelFactory =
    viewModelFactory {
        initializer {
            val application =
                checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
            DestinationViewModel(
                placesStore = SavedPlacesPreferences(application),
                motionFlow = LocationGraph.get(application).vehicleMotion(),
                speech = VoiceRecognizer(application),
            )
        }
    }

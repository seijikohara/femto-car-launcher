package io.github.seijikohara.femto.ui.destination

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.seijikohara.femto.data.common.WhileUiSubscribed
import io.github.seijikohara.femto.data.location.LocationGraph
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Drives the destination panel: the query (typed, or dictated through
 * [speech]), the saved places in [placesStore], and the motion gate from
 * [stationaryFlow].
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
    stationaryFlow: Flow<Boolean>,
    private val speech: SpeechInput,
) : ViewModel() {
    private val query = MutableStateFlow("")

    val uiState: StateFlow<DestinationUiState> =
        combine(query, speech.state, placesStore.places, stationaryFlow.distinctUntilChanged()) {
            query,
            voice,
            places,
            stationary,
            ->
            DestinationUiState(query = query, voice = voice, places = places, stationary = stationary)
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
        val stationary = uiState.value.stationary
        when (action) {
            is DestinationAction.QueryChanged -> {
                if (stationary) query.value = action.text
            }

            DestinationAction.StartListening -> {
                speech.start()
            }

            DestinationAction.StopListening -> {
                speech.stop()
            }

            DestinationAction.SaveQuery -> {
                query.value
                    .trim()
                    .takeIf { stationary && it.isNotEmpty() }
                    ?.let { text -> save(label = text, target = PlaceTarget.Query(text)) }
            }

            is DestinationAction.SaveCurrentLocation -> {
                if (stationary) {
                    val label =
                        query.value
                            .trim()
                            .ifEmpty { action.address.trim() }
                            .ifEmpty { action.point.coordinateLabel() }
                    save(label = label, target = action.point)
                }
            }

            is DestinationAction.DeletePlace -> {
                if (stationary) viewModelScope.launch { placesStore.delete(action.id) }
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
 * Wires the production store, the shared trip state's motion gate (the same
 * [LocationGraph] state the dashboard reads, so the two cannot disagree), and
 * the platform recognizer.
 */
internal val DestinationViewModelFactory =
    viewModelFactory {
        initializer {
            val application =
                checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
            DestinationViewModel(
                placesStore = SavedPlacesPreferences(application),
                stationaryFlow = LocationGraph.get(application).tripState.map { it.stationary },
                speech = VoiceRecognizer(application),
            )
        }
    }

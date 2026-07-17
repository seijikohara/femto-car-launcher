package io.github.seijikohara.femto.ui.home.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.seijikohara.femto.data.location.LocationGraph
import io.github.seijikohara.femto.data.location.TrackPointEntity
import io.github.seijikohara.femto.data.location.TripGeometry
import io.github.seijikohara.femto.data.location.TripStatePreferences
import io.github.seijikohara.femto.data.location.TripStats
import io.github.seijikohara.femto.data.location.TripSummaryRow
import io.github.seijikohara.femto.data.location.TripWireframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One trip in the flyover's selector. */
internal data class TripListItem(
    val tripId: Long,
    val startMs: Long,
    val endMs: Long,
    val pointCount: Int,
    val isCurrent: Boolean,
)

/**
 * The selected trip's render-ready wireframe plus its headline stats. Not a data
 * class — the [FloatArray] has identity equality, which is what recomposition
 * wants here (a new selection is a new array).
 */
internal class TripSelection(
    val tripId: Long,
    val wireframe: FloatArray,
    val stats: TripStats,
)

internal data class TripVizUiState(
    val loading: Boolean = true,
    val trips: List<TripListItem> = emptyList(),
    val selectedTripId: Long? = null,
    val selection: TripSelection? = null,
)

internal sealed interface TripVizAction {
    data class Select(
        val tripId: Long,
    ) : TripVizAction

    data object Refresh : TripVizAction
}

/**
 * Loads recorded trips and turns the selected one into a render-ready wireframe
 * for the flyover. Data only — the panel owns the playback clock and camera
 * state. All track reads are already never-crash wrapped in the repository; the
 * geometry build runs off the main thread.
 */
internal class TripVizViewModel(
    private val tripSummaries: suspend () -> List<TripSummaryRow>,
    private val pointsForTrip: suspend (Long) -> List<TrackPointEntity>,
    private val currentTripId: suspend () -> Long,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TripVizUiState())
    val uiState: StateFlow<TripVizUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun onAction(action: TripVizAction) {
        when (action) {
            is TripVizAction.Select -> select(action.tripId)
            TripVizAction.Refresh -> refresh()
        }
    }

    private fun refresh() =
        viewModelScope.launch {
            val current = currentTripId()
            val trips =
                tripSummaries()
                    // A trip needs at least two points to render; drop the rest so
                    // selecting one can never strand the panel with a null geometry.
                    .filter { it.pointCount >= 2 }
                    .map {
                        TripListItem(
                            tripId = it.tripId,
                            startMs = it.startMs,
                            endMs = it.endMs,
                            pointCount = it.pointCount,
                            isCurrent = it.tripId == current,
                        )
                    }
            _uiState.update { it.copy(loading = false, trips = trips) }
            // Open on the most recent trip so the panel is never empty when data exists.
            if (trips.isNotEmpty() && _uiState.value.selection == null) {
                select(trips.first().tripId)
            }
        }

    private fun select(tripId: Long) =
        viewModelScope.launch {
            _uiState.update { it.copy(selectedTripId = tripId) }
            val selection =
                withContext(Dispatchers.Default) {
                    TripGeometry.from(pointsForTrip(tripId))?.let { geometry ->
                        TripSelection(tripId, TripWireframe.build(geometry), geometry.stats)
                    }
                }
            // Ignore a stale load if the user moved on to another trip meanwhile.
            _uiState.update { state ->
                if (state.selectedTripId == tripId) state.copy(selection = selection) else state
            }
        }
}

/** Wires the process-singleton track log + trip-state store without an UNCHECKED_CAST factory. */
internal val TripVizViewModelFactory: ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
            val trackLog = LocationGraph.get(application).trackLog
            TripVizViewModel(
                tripSummaries = trackLog::tripSummaries,
                pointsForTrip = trackLog::pointsForTrip,
                currentTripId = { TripStatePreferences(application).read().tripId },
            )
        }
    }

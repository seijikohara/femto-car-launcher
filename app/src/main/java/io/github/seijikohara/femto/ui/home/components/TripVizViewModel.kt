package io.github.seijikohara.femto.ui.home.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.seijikohara.femto.data.location.LocationGraph
import io.github.seijikohara.femto.data.location.TrackPointEntity
import io.github.seijikohara.femto.data.location.TripGeometry
import io.github.seijikohara.femto.data.location.TripScenePalette
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

    /**
     * The rendered light/dark scene palette changed (theme toggle, or the system
     * flipping dark mode under [ThemeMode.SYSTEM][io.github.seijikohara.femto.data.display.ThemeMode]).
     * The selected trip's wireframe bakes theme colours, so it is rebuilt.
     */
    data class SetPalette(
        val palette: TripScenePalette,
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

    // The active scene palette and the selected trip's geometry are cached so a
    // palette change recolours the wireframe off the cached geometry — no DB
    // re-query, no re-projection. Touched only from viewModelScope (main).
    private var palette: TripScenePalette = TripScenePalette.Dark
    private var currentGeometry: TripGeometry? = null
    private var currentGeometryTripId: Long? = null

    init {
        refresh()
    }

    fun onAction(action: TripVizAction) {
        when (action) {
            is TripVizAction.Select -> select(action.tripId)
            is TripVizAction.SetPalette -> setPalette(action.palette)
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
            val activePalette = palette
            val geometry = withContext(Dispatchers.Default) { TripGeometry.from(pointsForTrip(tripId)) }
            currentGeometry = geometry
            currentGeometryTripId = tripId
            val selection = geometry?.let { buildSelection(tripId, it, activePalette) }
            // Ignore a stale load if the user moved on to another trip meanwhile.
            _uiState.update { state ->
                if (state.selectedTripId == tripId) state.copy(selection = selection) else state
            }
        }

    private fun setPalette(next: TripScenePalette) {
        if (next == palette) return
        palette = next
        val geometry = currentGeometry ?: return
        val tripId = currentGeometryTripId ?: return
        // Rebuild the wireframe with the new scene colours from the cached
        // geometry; keep the current frame until the recolour lands.
        viewModelScope.launch {
            val selection = buildSelection(tripId, geometry, next)
            _uiState.update { state ->
                if (state.selectedTripId == tripId) state.copy(selection = selection) else state
            }
        }
    }

    // Expand the geometry into a render-ready wireframe off the main thread. The
    // palette is captured on the caller (main) thread, so the Default-dispatched
    // build never races the mutable [palette] field.
    private suspend fun buildSelection(
        tripId: Long,
        geometry: TripGeometry,
        withPalette: TripScenePalette,
    ): TripSelection =
        withContext(Dispatchers.Default) {
            TripSelection(tripId, TripWireframe.build(geometry, withPalette), geometry.stats)
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

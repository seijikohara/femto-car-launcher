package io.github.seijikohara.femto.ui.destination

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.common.WhileUiSubscribed
import io.github.seijikohara.femto.data.display.DisplayPreferences
import io.github.seijikohara.femto.data.geocoding.PlaceLocator
import io.github.seijikohara.femto.data.geocoding.PlaceLookup
import io.github.seijikohara.femto.data.geocoding.PlatformPlaceLocator
import io.github.seijikohara.femto.data.location.LocationGraph
import io.github.seijikohara.femto.data.location.VehicleMotion
import io.github.seijikohara.femto.data.location.hasLocationPermission
import io.github.seijikohara.femto.data.location.isFresh
import io.github.seijikohara.femto.data.places.PlaceTarget
import io.github.seijikohara.femto.data.places.SavedPlacesPreferences
import io.github.seijikohara.femto.data.places.SavedPlacesStore
import io.github.seijikohara.femto.data.places.hasDestinationHandler
import io.github.seijikohara.femto.data.routing.ActiveRouteStore
import io.github.seijikohara.femto.data.routing.RoutePlanner
import io.github.seijikohara.femto.data.routing.RoutePoint
import io.github.seijikohara.femto.data.routing.RouteResult
import io.github.seijikohara.femto.data.routing.RoutingTileStore
import io.github.seijikohara.femto.data.routing.TileFetchResult
import io.github.seijikohara.femto.data.routing.routingAvailableFlow
import io.github.seijikohara.femto.data.routing.routingTilesAround
import io.github.seijikohara.femto.data.voice.SpeechInput
import io.github.seijikohara.femto.data.voice.VoiceRecognizer
import io.github.seijikohara.femto.data.voice.VoiceState
import kotlinx.coroutines.Job
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
 * Whether any app can take the hand-off does live here: each
 * [DestinationAction.CheckHandOff] asks [hasHandOffTarget] again.
 *
 * A route on the launcher's own map (issue #389 phase 3) runs here too, while
 * [routingAvailable] says so: text becomes a position through [placeLocator],
 * missing routing data is sized and, on the driver's word, fetched through
 * [tiles], [planner] computes the route, and a found route becomes the one in
 * [activeRoute] that the dashboard draws. One route runs at a time; a new one
 * or [DestinationAction.DismissRoute] cancels the one under way. A route starts
 * only from a fix still fresh at [nowElapsedRealtimeNanos]; without one it
 * says whether the fix or [hasLocationPermission] is missing.
 */
internal class DestinationViewModel(
    private val placesStore: SavedPlacesStore,
    motionFlow: Flow<VehicleMotion>,
    private val speech: SpeechInput,
    private val hasHandOffTarget: () -> Boolean,
    routingAvailable: Flow<Boolean>,
    private val placeLocator: PlaceLocator,
    private val planner: RoutePlanner,
    private val tiles: RoutingTileStore,
    private val activeRoute: ActiveRouteStore,
    private val nowElapsedRealtimeNanos: () -> Long,
    private val hasLocationPermission: () -> Boolean,
) : ViewModel() {
    private val query = MutableStateFlow("")

    // True until the first check: see DestinationUiState.handOffAvailable.
    private val handOffAvailable = MutableStateFlow(true)

    private val routeStep = MutableStateFlow<RouteStep>(RouteStep.Idle)
    private var routeJob: Job? = null

    // The two ends of the route under way and the tiles its download fetches,
    // kept for the steps that wait on the driver (a download to confirm, or to
    // redo after a version mismatch).
    private var routeEnds: RouteEnds? = null
    private var tilesToFetch: List<String> = emptyList()

    private val entryState: Flow<DestinationUiState> =
        combine(
            query,
            speech.state,
            placesStore.places,
            // Seeded so the panel never waits on a location stack that has not
            // spoken; UNKNOWN fails open (see DestinationUiState.typingAllowed).
            motionFlow.onStart { emit(VehicleMotion.UNKNOWN) }.distinctUntilChanged(),
            handOffAvailable,
        ) {
            query,
            voice,
            places,
            motion,
            handOff,
            ->
            DestinationUiState(
                query = query,
                voice = voice,
                places = places,
                motion = motion,
                handOffAvailable = handOff,
            )
        }

    val uiState: StateFlow<DestinationUiState> =
        combine(
            entryState,
            // Seeded off, so the entry never waits on the settings store.
            routingAvailable.onStart { emit(false) }.distinctUntilChanged(),
            routeStep,
        ) { entry, available, step ->
            entry.copy(routeAvailable = available, geocoderAvailable = placeLocator.available, route = step)
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
        val state = uiState.value
        val typingAllowed = state.typingAllowed
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

            DestinationAction.CheckHandOff -> {
                handOffAvailable.value = hasHandOffTarget()
            }

            is DestinationAction.RouteTo -> {
                if (state.canRoute(action.target)) startRoute(action.target, action.origin)
            }

            DestinationAction.ConfirmDownload -> {
                val confirm = routeStep.value as? RouteStep.ConfirmDownload
                val ends = routeEnds
                if (confirm != null && ends != null) runRoute { fetchThenRoute(ends, tilesToFetch, confirm.bytes) }
            }

            DestinationAction.DownloadAgain -> {
                val ends = routeEnds
                if (routeStep.value == RouteStep.Failed(RouteFailure.DATA_VERSION_MISMATCH) && ends != null) {
                    runRoute {
                        routingTilesAround(ends.origin, ends.destination).forEach { tiles.delete(it) }
                        route(ends)
                    }
                }
            }

            DestinationAction.DismissRoute -> {
                routeJob?.cancel()
                routeJob = null
                routeEnds = null
                tilesToFetch = emptyList()
                routeStep.value = RouteStep.Idle
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

    // The location flow replays its last fix after the receiver goes quiet (a
    // head unit started in an underground car park holds yesterday's), so only
    // a fresh fix is a start: an old one would route from elsewhere and offer
    // routing data for the wrong area.
    private fun startRoute(
        target: PlaceTarget,
        origin: LastFix?,
    ) {
        val start = origin?.takeIf { isFresh(it.elapsedRealtimeNanos, nowElapsedRealtimeNanos()) }
        if (start == null) {
            routeJob?.cancel()
            fail(if (hasLocationPermission()) RouteFailure.NO_FIX else RouteFailure.NO_LOCATION_PERMISSION)
            return
        }
        runRoute {
            destinationOrNull(target)?.let { destination ->
                RouteEnds(origin = start.point.toRoutePoint(), destination = destination)
                    .also { routeEnds = it }
                    .let { route(it) }
            }
        }
    }

    private fun runRoute(block: suspend () -> Unit) {
        routeJob?.cancel()
        routeJob = viewModelScope.launch { block() }
    }

    // A point routes as it is; text goes through the geocoder first. Null after
    // a failed look-up, whose reason is already on screen.
    private suspend fun destinationOrNull(target: PlaceTarget): RoutePoint? =
        when (target) {
            is PlaceTarget.Point -> {
                target.toRoutePoint()
            }

            is PlaceTarget.Query -> {
                routeStep.value = RouteStep.Locating
                when (val lookup = placeLocator.locate(target.text)) {
                    is PlaceLookup.Found -> RoutePoint(lookup.latitude, lookup.longitude)
                    PlaceLookup.NotFound -> null.also { fail(RouteFailure.PLACE_NOT_FOUND) }
                    PlaceLookup.Failed -> null.also { fail(RouteFailure.LOOKUP_FAILED) }
                }
            }
        }

    private suspend fun route(ends: RouteEnds) {
        routeStep.value = RouteStep.Computing
        when (val result = planner.route(ends.origin, ends.destination)) {
            is RouteResult.Found -> {
                activeRoute.set(result.route)
                routeStep.value = RouteStep.Done
            }

            is RouteResult.MissingTiles -> {
                offerDownload(ends, result.names)
            }

            RouteResult.NoRoadData -> {
                fail(RouteFailure.NO_ROAD_DATA)
            }

            RouteResult.DataVersionMismatch -> {
                fail(RouteFailure.DATA_VERSION_MISMATCH)
            }

            RouteResult.TimedOut -> {
                fail(RouteFailure.TIMED_OUT)
            }

            is RouteResult.Failed -> {
                fail(RouteFailure.NO_ROUTE)
            }
        }
    }

    // The driver sees the size before anything is fetched. Tiles the server
    // has no data for weigh nothing, and fetching them only records that, so a
    // download of nothing goes ahead without asking.
    private suspend fun offerDownload(
        ends: RouteEnds,
        names: List<String>,
    ) {
        tilesToFetch = names
        when (val bytes = tiles.downloadSizeBytes(names)) {
            null -> fail(RouteFailure.DOWNLOAD_FAILED)
            0L -> fetchThenRoute(ends, names, totalBytes = 0L)
            else -> routeStep.value = RouteStep.ConfirmDownload(bytes)
        }
    }

    // One tile at a time, as the plan promises brouter.de; the progress adds
    // each finished tile's bytes to the one under way.
    private suspend fun fetchThenRoute(
        ends: RouteEnds,
        names: List<String>,
        totalBytes: Long,
    ) {
        var finishedBytes = 0L
        routeStep.value = RouteStep.Downloading(receivedBytes = 0L, totalBytes = totalBytes)
        for (name in names) {
            var tileBytes = 0L
            val result =
                tiles.fetch(name) { received, _ ->
                    tileBytes = received
                    routeStep.value = RouteStep.Downloading(finishedBytes + received, totalBytes)
                }
            result.failureOrNull()?.let { return fail(it) }
            finishedBytes += tileBytes
        }
        route(ends)
    }

    private fun fail(reason: RouteFailure) {
        routeStep.value = RouteStep.Failed(reason)
    }

    override fun onCleared() {
        speech.destroy()
    }
}

private data class RouteEnds(
    val origin: RoutePoint,
    val destination: RoutePoint,
)

// Why a tile's fetch stops the route, or null when the route goes on: the tile
// is installed, or the server has none for it and the route does without.
private fun TileFetchResult.failureOrNull(): RouteFailure? =
    when (this) {
        TileFetchResult.INSTALLED, TileFetchResult.NO_DATA -> null
        TileFetchResult.NO_SPACE -> RouteFailure.NO_SPACE
        TileFetchResult.NETWORK_FAILED, TileFetchResult.STORAGE_FAILED -> RouteFailure.DOWNLOAD_FAILED
    }

private fun PlaceTarget.Point.toRoutePoint(): RoutePoint = RoutePoint(latitude = latitude, longitude = longitude)

// Five decimals (about 1 m) names a place without an address; Locale.ROOT
// keeps the separator a dot in every locale, matching the hand-off URI.
private fun PlaceTarget.Point.coordinateLabel(): String = String.format(Locale.ROOT, "%.5f, %.5f", latitude, longitude)

/**
 * Wires the production store, the shared vehicle-motion verdict (the same
 * [LocationGraph] pipeline the dashboard and the updater read), the
 * platform recognizer, the package manager's view of `geo:` handlers, and
 * the routing stack: the build's `routing_enabled` flag with the map backend,
 * the platform geocoder, the shared planner and tile store, the one active
 * route the dashboard reads, and the boot clock and location grant a route's
 * start is judged by.
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
                hasHandOffTarget = application::hasDestinationHandler,
                routingAvailable =
                    routingAvailableFlow(
                        enabled = application.resources.getBoolean(R.bool.routing_enabled),
                        settings = DisplayPreferences(application).settings,
                    ),
                placeLocator = PlatformPlaceLocator(application),
                planner = RoutePlanner.get(application),
                tiles = RoutingTileStore.get(application),
                activeRoute = ActiveRouteStore.shared,
                nowElapsedRealtimeNanos = SystemClock::elapsedRealtimeNanos,
                hasLocationPermission = application::hasLocationPermission,
            )
        }
    }

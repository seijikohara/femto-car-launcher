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
import io.github.seijikohara.femto.data.location.CurrentFixSource
import io.github.seijikohara.femto.data.location.LocationGraph
import io.github.seijikohara.femto.data.location.PlatformCurrentFixSource
import io.github.seijikohara.femto.data.location.VehicleMotion
import io.github.seijikohara.femto.data.location.hasLocationPermission
import io.github.seijikohara.femto.data.location.isFresh
import io.github.seijikohara.femto.data.places.PlaceTarget
import io.github.seijikohara.femto.data.places.SavedPlacesPreferences
import io.github.seijikohara.femto.data.places.SavedPlacesStore
import io.github.seijikohara.femto.data.places.hasDestinationHandler
import io.github.seijikohara.femto.data.routing.ActiveRouteStore
import io.github.seijikohara.femto.data.routing.Rerouter
import io.github.seijikohara.femto.data.routing.RoutePlanner
import io.github.seijikohara.femto.data.routing.RoutePoint
import io.github.seijikohara.femto.data.routing.RouteResult
import io.github.seijikohara.femto.data.routing.RoutingTileStore
import io.github.seijikohara.femto.data.routing.TileCheckResult
import io.github.seijikohara.femto.data.routing.TileFetchResult
import io.github.seijikohara.femto.data.routing.TileRefreshResult
import io.github.seijikohara.femto.data.routing.routingAvailableFlow
import io.github.seijikohara.femto.data.routing.routingTilesAround
import io.github.seijikohara.femto.data.routing.tilesDueForCheck
import io.github.seijikohara.femto.data.voice.SpeechInput
import io.github.seijikohara.femto.data.voice.VoiceRecognizer
import io.github.seijikohara.femto.data.voice.VoiceState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 * [tiles], [planner] computes the route, and a found route starts a new session
 * in [activeRoute], with its destination and the engine's reference, which the
 * dashboard draws and the rerouter routes again from. One route runs at a time;
 * a new one or [DestinationAction.DismissRoute] cancels the one under way, and
 * every route run stops a running reroute ([cancelReroute]) when it starts and
 * again right before each search, so it never waits for the planner behind
 * one. A route starts
 * from a fix still fresh at [nowElapsedRealtimeNanos], else from the position
 * [currentFix] finds on request; with neither it says whether the position or
 * [hasLocationPermission] is missing.
 *
 * Before a route is computed, the installed tiles it may read whose weekly
 * check is due at [nowEpochMs] are asked about with a conditional request
 * ([tilesDueForCheck]); a tile the server has rebuilt joins the download the
 * driver confirms, as a missing tile does. An update never blocks a route:
 * with only newer data on offer, the driver may route with the stored data
 * ([DestinationAction.UseStoredData]). Nothing is checked in the background.
 *
 * A point picked on the map ([DestinationAction.PickPoint]) stays here until
 * the panel lets go of it; it is saved only on the driver's word.
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
    private val currentFix: CurrentFixSource,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val cancelReroute: () -> Unit = {},
    // Where the panel's own file work runs, off the main thread; tests pin it to their scheduler.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
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
    private var pendingDownload = TileDownload()

    private val pickedPoint = MutableStateFlow<PlaceTarget.Point?>(null)

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
            pickedPoint,
        ) { entry, available, step, picked ->
            entry.copy(
                routeAvailable = available,
                geocoderAvailable = placeLocator.available,
                route = step,
                pickedPoint = picked,
            )
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

            is DestinationAction.PickPoint -> {
                pickedPoint.value = action.point
            }

            DestinationAction.ClearPickedPoint -> {
                pickedPoint.value = null
            }

            DestinationAction.SavePickedPoint -> {
                pickedPoint.value
                    ?.takeIf { typingAllowed }
                    ?.let { point -> save(label = point.coordinateLabel(), target = point) }
            }

            DestinationAction.UseStoredData -> {
                val confirm = routeStep.value as? RouteStep.ConfirmDownload
                val ends = routeEnds
                val declined = pendingDownload.refresh
                if (confirm?.kind == RouteDownload.NEWER && ends != null) {
                    runRoute {
                        declined.forEach { tiles.markChecked(it) }
                        route(ends)
                    }
                }
            }

            DestinationAction.ConfirmDownload -> {
                val confirm = routeStep.value as? RouteStep.ConfirmDownload
                val ends = routeEnds
                if (confirm != null && ends != null) runRoute { fetchThenRoute(ends, pendingDownload, confirm.bytes) }
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
                pendingDownload = TileDownload()
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

    private fun startRoute(
        target: PlaceTarget,
        origin: LastFix?,
    ) = runRoute {
        startOrNull(origin)?.let { start ->
            destinationOrNull(target)?.let { destination ->
                RouteEnds(origin = start, destination = destination)
                    .also { routeEnds = it }
                    .let { route(it) }
            }
        }
    }

    // The location flow replays its last fix after the receiver goes quiet (a
    // head unit started in an underground car park holds yesterday's), so only
    // a fresh fix is a start: an old one would route from elsewhere and offer
    // routing data for the wrong area. An old fix is no sign of a lost position,
    // though. The flow forwards nothing until the vehicle has moved the minimum
    // distance the driver set, so a parked vehicle, which is where a route gets
    // planned, holds an old fix for good, and a coarse-only grant, whose fixes
    // the platform slows down, holds one most of the time. So the route asks
    // for the current position in place of an old fix. Null when there is no
    // start, whose reason is already on screen; without the grant nothing is
    // asked, since no position can come.
    private suspend fun startOrNull(origin: LastFix?): RoutePoint? {
        val fresh = origin?.takeIf { isFresh(it.elapsedRealtimeNanos, nowElapsedRealtimeNanos()) }
        return when {
            fresh != null -> {
                fresh.point.toRoutePoint()
            }

            !hasLocationPermission() -> {
                null.also { fail(RouteFailure.NO_LOCATION_PERMISSION) }
            }

            else -> {
                routeStep.value = RouteStep.FindingPosition
                currentFix.currentFixOrNull()?.let { RoutePoint(latitude = it.latitude, longitude = it.longitude) }
                    ?: null.also { fail(RouteFailure.NO_FIX) }
            }
        }
    }

    private fun runRoute(block: suspend () -> Unit) {
        routeJob?.cancel()
        cancelReroute()
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

    // Computing from the start: the look at the tiles on the device is part of
    // it, and only a check that asks the server says so on its own.
    private suspend fun route(ends: RouteEnds) {
        routeStep.value = RouteStep.Computing
        val needed = routingTilesAround(ends.origin, ends.destination)
        val newer = newerTileSizes(needed)
        if (newer.isNotEmpty()) return offerDownload(ends, missing = tiles.missingTiles(needed), newer = newer)
        routeStep.value = RouteStep.Computing
        // Checking or downloading data takes time, during which a reroute of the old route may have started.
        cancelReroute()
        when (val result = planner.route(ends.origin, ends.destination)) {
            is RouteResult.Found -> {
                activeRoute.start(result.route, ends.destination, result.reference)
                routeStep.value = RouteStep.Done
                // The new session replaced every earlier route, whose references no reroute reads again.
                withContext(ioDispatcher) { planner.pruneReferences(setOfNotNull(result.reference)) }
            }

            is RouteResult.MissingTiles -> {
                offerDownload(ends, missing = result.names, newer = emptyMap())
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

    // The weekly check: the installed tiles among [needed] whose check is due,
    // asked about one at a time with a conditional request, which costs the
    // server nothing for a tile it has not rebuilt. A check without an answer
    // leaves the installed tile to route with. Returns the rebuilt tiles with
    // the sizes of their newer files, by name.
    private suspend fun newerTileSizes(needed: List<String>): Map<String, Long> {
        val due = tilesDueForCheck(needed, tiles.installedTiles(), nowEpochMs())
        if (due.isNotEmpty()) routeStep.value = RouteStep.CheckingData
        return due
            .mapNotNull { name -> (tiles.check(name) as? TileCheckResult.Changed)?.let { name to it.sizeBytes } }
            .toMap()
    }

    // The driver sees the size before anything is fetched: the missing tiles
    // as the server announces them, and the newer files of rebuilt ones. Tiles
    // the server has no data for weigh nothing, and fetching them only records
    // that, so a download of nothing goes ahead without asking.
    private suspend fun offerDownload(
        ends: RouteEnds,
        missing: List<String>,
        newer: Map<String, Long>,
    ) {
        val download = TileDownload(fetch = missing, refresh = newer.keys.toList())
        pendingDownload = download
        val missingBytes = tiles.downloadSizeBytes(missing) ?: return fail(RouteFailure.DOWNLOAD_FAILED)
        val bytes = missingBytes + newer.values.sum()
        if (bytes == 0L) {
            fetchThenRoute(ends, download, totalBytes = 0L)
        } else {
            routeStep.value = RouteStep.ConfirmDownload(bytes, downloadKind(missingBytes, newer.isNotEmpty()))
        }
    }

    // One tile at a time, as the plan promises brouter.de; the progress adds
    // each finished tile's bytes to the one under way.
    //
    // The transport reports from its own thread, and a blocking read can still
    // report after a cancel has put the entry back or started another route. A
    // report counts only while this route's job is active, and the job is read
    // inside the state's atomic update: a cancel always lands before the state
    // it writes, so one that slips between the read and the write fails the
    // update, whose retry then sees the job cancelled and drops the report.
    //
    // A rebuilt tile is replaced through the store's refresh, which asks once
    // more and keeps the installed file until the new one is whole. Newer data
    // never blocks a route: a replacement that fails leaves the installed tile
    // to route with, counted as checked so the next route does not ask at
    // once. Only a missing tile whose fetch fails stops the route.
    private suspend fun fetchThenRoute(
        ends: RouteEnds,
        download: TileDownload,
        totalBytes: Long,
    ) {
        val job = currentCoroutineContext().job
        var finishedBytes = 0L
        routeStep.value = RouteStep.Downloading(receivedBytes = 0L, totalBytes = totalBytes)
        for (name in download.fetch + download.refresh) {
            var tileBytes = 0L
            val onProgress = { received: Long, _: Long ->
                tileBytes = received
                routeStep.update { step ->
                    if (job.isActive) RouteStep.Downloading(finishedBytes + received, totalBytes) else step
                }
            }
            if (name in download.fetch) {
                tiles.fetch(name, onProgress).failureOrNull()?.let { return fail(it) }
            } else if (tiles.refresh(name, onProgress).failed()) {
                tiles.markChecked(name)
            }
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

// What a confirmed download fetches: missing tiles, and installed tiles the
// server has rebuilt.
private data class TileDownload(
    val fetch: List<String> = emptyList(),
    val refresh: List<String> = emptyList(),
)

// Why a tile's fetch stops the route, or null when the route goes on: the tile
// is installed, or the server has none for it and the route does without.
private fun TileFetchResult.failureOrNull(): RouteFailure? =
    when (this) {
        TileFetchResult.INSTALLED, TileFetchResult.NO_DATA -> null
        TileFetchResult.NO_SPACE -> RouteFailure.NO_SPACE
        TileFetchResult.NETWORK_FAILED, TileFetchResult.STORAGE_FAILED -> RouteFailure.DOWNLOAD_FAILED
    }

// Missing data the server sizes at nothing (tiles without data) needs no word,
// so only newer data left means an update the driver may decline.
private fun downloadKind(
    missingBytes: Long,
    hasNewer: Boolean,
): RouteDownload =
    when {
        !hasNewer -> RouteDownload.MISSING
        missingBytes == 0L -> RouteDownload.NEWER
        else -> RouteDownload.MISSING_AND_NEWER
    }

// Whether a rebuilt tile's replacement failed, leaving the installed tile in
// place: replaced, unchanged, or no longer due or installed is no failure.
private fun TileRefreshResult.failed(): Boolean =
    when (this) {
        TileRefreshResult.UPDATED,
        TileRefreshResult.UNCHANGED,
        TileRefreshResult.NOT_DUE,
        TileRefreshResult.NOT_INSTALLED,
        -> false

        TileRefreshResult.NETWORK_FAILED, TileRefreshResult.NO_SPACE, TileRefreshResult.STORAGE_FAILED -> true
    }

private fun PlaceTarget.Point.toRoutePoint(): RoutePoint = RoutePoint(latitude = latitude, longitude = longitude)

/**
 * Wires the production store, the shared vehicle-motion verdict (the same
 * [LocationGraph] pipeline the dashboard and the updater read), the
 * platform recognizer, the package manager's view of `geo:` handlers, and
 * the routing stack: the build's `routing_enabled` flag with the map backend,
 * the platform geocoder, the shared planner and tile store, the one active
 * route the dashboard reads, the boot clock and location grant a route's start
 * is judged by, the platform's answer to where the device is now, and the
 * shared rerouter a route run stops.
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
                currentFix = PlatformCurrentFixSource(application),
                cancelReroute = Rerouter.get(application)::cancel,
            )
        }
    }

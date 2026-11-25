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
import io.github.seijikohara.femto.data.places.ReceivedPlace
import io.github.seijikohara.femto.data.places.SavedPlacesPreferences
import io.github.seijikohara.femto.data.places.SavedPlacesStore
import io.github.seijikohara.femto.data.places.capPlaceName
import io.github.seijikohara.femto.data.places.hasDestinationHandler
import io.github.seijikohara.femto.data.routing.ActiveRouteStore
import io.github.seijikohara.femto.data.routing.CandidateFinder
import io.github.seijikohara.femto.data.routing.NavigationPreferences
import io.github.seijikohara.femto.data.routing.Rerouter
import io.github.seijikohara.femto.data.routing.RouteConditions
import io.github.seijikohara.femto.data.routing.RouteConditionsStore
import io.github.seijikohara.femto.data.routing.RoutePlanner
import io.github.seijikohara.femto.data.routing.RoutePoint
import io.github.seijikohara.femto.data.routing.RoutePreferences
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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
 * delete: every edit that involves typing is dropped while moving, and a rename
 * field open when the vehicle starts to move closes without saving. A voice
 * result fills the query in any motion state but never hands off by itself;
 * the user still taps Open in maps app. The hand-off is not an action here: the
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
 * one. A route avoids the roads [conditionsStore] holds as the run starts; a
 * chip changed while it runs applies to the next route, and the session keeps
 * the run's conditions for its reroutes. A route starts
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
 * the panel lets go of it; it is saved only on the driver's word. A place
 * another app sent ([DestinationAction.Receive]) lands in the same two slots a
 * driver fills: a point as the picked point, with the name it came with, and a
 * search text in the query.
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
    private val conditionsStore: RouteConditionsStore,
    private val nowElapsedRealtimeNanos: () -> Long,
    private val hasLocationPermission: () -> Boolean,
    private val currentFix: CurrentFixSource,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val cancelReroute: () -> Unit = {},
    // Where the panel's own file work runs, off the main thread; tests pin it to their scheduler.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    // The search for more routes once one is found (the CandidateFinder): started from the route's origin, and stopped
    // like a reroute before each search of the panel's own.
    private val startCandidates: (session: Long, origin: RoutePoint) -> Unit = { _, _ -> },
    private val cancelCandidates: () -> Unit = {},
    // The "Suggest other routes" setting, read as each route is found: off, the route starts with no choice and no
    // search for more.
    private val suggestOtherRoutes: suspend () -> Boolean = { true },
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

    private val pickedPoint = MutableStateFlow<PickedPoint?>(null)

    // Whether the last received place held nothing readable; see DestinationUiState.receivedUnreadable.
    private val receivedUnreadable = MutableStateFlow(false)

    // The name being typed for one saved place. Kept here, not in the row, so the typing gate judges it and a
    // rotation does not lose it.
    private val renaming = MutableStateFlow<RenameDraft?>(null)

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
            // Seeded with none for the same reason; the chips show only once routing is on.
            conditionsStore.conditions.onStart { emit(RouteConditions()) }.distinctUntilChanged(),
        ) { entry, available, step, picked, conditions ->
            entry.copy(
                routeAvailable = available,
                geocoderAvailable = placeLocator.available,
                route = step,
                pickedPoint = picked?.point,
                pickedLabel = picked?.label.orEmpty(),
                conditions = conditions,
            )
        }.combine(receivedUnreadable) { state, unreadable -> state.copy(receivedUnreadable = unreadable) }
            .combine(renaming) { state, draft -> state to draft }
            // The motion verdict reaches the VM only through this chain (collecting it elsewhere would keep the
            // location stack running with no panel open), so the rename that moving closes is closed here: the
            // draft is dropped for good, not only hidden until the next stop. The panel's own disposal clears it
            // when the panel closes while the chain is idle.
            .onEach { (state, draft) -> if (draft != null && !state.typingAllowed) renaming.value = null }
            .map { (state, draft) -> state.copy(renaming = draft.takeIf { state.typingAllowed }) }
            .stateIn(viewModelScope, WhileUiSubscribed, DestinationUiState.Initial)

    init {
        // A final transcript becomes the query, and the recognizer goes back to
        // Idle so the same result is not applied twice and the mic is ready
        // for another try. Partial transcripts only show in the voice line.
        viewModelScope.launch {
            speech.state.collect { state ->
                if (state is VoiceState.Result) {
                    query.value = state.text
                    receivedUnreadable.value = false
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
                if (typingAllowed) {
                    query.value = action.text
                    receivedUnreadable.value = false
                }
            }

            DestinationAction.StartListening -> {
                // Some engines silently ignore the next start after an error
                // unless the recognizer is cancelled first (VoiceRecognizer.reset).
                if (speech.state.value is VoiceState.Failed) speech.reset()
                // The driver is entering a place now; the line about the last share no longer applies.
                receivedUnreadable.value = false
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
                if (typingAllowed) {
                    if (renaming.value?.id == action.id) renaming.value = null
                    viewModelScope.launch { placesStore.delete(action.id) }
                }
            }

            is DestinationAction.StartRename -> {
                if (typingAllowed) {
                    state.places
                        .firstOrNull { it.id == action.id }
                        // Empty for an unnamed place, never its label: a label past the cap would be saved cut, as
                        // a name, by a Save with no edit. The panel's placeholder shows the label instead.
                        ?.let { place -> renaming.value = RenameDraft(place.id, place.name.orEmpty()) }
                }
            }

            is DestinationAction.RenameChanged -> {
                if (typingAllowed) renaming.update { draft -> draft?.copy(text = capPlaceName(action.text)) }
            }

            DestinationAction.SaveRename -> {
                // The store decides what is kept (trim, blank, equal to the label); a place deleted meanwhile stays
                // gone. viewModelScope outlives a panel collapsed right after the tap, so the write always lands.
                renaming.value
                    ?.takeIf { typingAllowed }
                    ?.let { draft -> viewModelScope.launch { placesStore.rename(draft.id, draft.text) } }
                renaming.value = null
            }

            DestinationAction.CancelRename -> {
                renaming.value = null
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
                pickedPoint.value = PickedPoint(action.point)
                receivedUnreadable.value = false
            }

            DestinationAction.ClearPickedPoint -> {
                pickedPoint.value = null
            }

            DestinationAction.SavePickedPoint -> {
                pickedPoint.value
                    ?.takeIf { typingAllowed }
                    ?.let { picked ->
                        save(label = picked.label.ifEmpty { picked.point.coordinateLabel() }, target = picked.point)
                    }
            }

            is DestinationAction.Receive -> {
                receive(action.place)
            }

            DestinationAction.ClearReceivedUnreadable -> {
                receivedUnreadable.value = false
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
                stopRoute()
            }

            is DestinationAction.SetAvoidMotorways -> {
                updateConditions { it.copy(avoidMotorways = action.on) }
            }

            is DestinationAction.SetAvoidTolls -> {
                updateConditions { it.copy(avoidTolls = action.on) }
            }
        }
    }

    // Not gated on motion: the driver chose the place in the other app, and the query field it fills still takes no
    // typing while moving. Two things stop first. A route under way, whose status would hide the place behind a step
    // that names no destination (a download prompt), so a tap meant for the new place would route to the old one. And
    // recognition, so a late transcript cannot overwrite the place.
    private fun receive(place: ReceivedPlace) {
        stopRoute()
        speech.reset()
        when (place) {
            is ReceivedPlace.Found -> {
                receivedUnreadable.value = false
                when (val target = place.target) {
                    // Cut to the name cap: the label names the pin and, saved, the place's row.
                    is PlaceTarget.Point -> {
                        pickedPoint.value = PickedPoint(target, capPlaceName(place.label))
                    }

                    is PlaceTarget.Query -> {
                        pickedPoint.value = null
                        query.value = target.text
                    }
                }
            }

            // The text of an earlier search goes too: left under the line, with its actions live, it would read
            // as the place that just arrived.
            ReceivedPlace.Unreadable -> {
                pickedPoint.value = null
                query.value = ""
                receivedUnreadable.value = true
            }
        }
    }

    // Stop the route under way, or put its message away, and give the panel its entry back.
    private fun stopRoute() {
        routeJob?.cancel()
        routeJob = null
        routeEnds = null
        pendingDownload = TileDownload()
        routeStep.value = RouteStep.Idle
    }

    // viewModelScope outlives a panel collapsed right after the tap, so the
    // write always lands.
    private fun save(
        label: String,
        target: PlaceTarget,
    ) {
        viewModelScope.launch { placesStore.add(label, target) }
    }

    // Each chip changes its own condition on the stored value, not on what the
    // panel showed: a second tap before the first is written must not undo it.
    private fun updateConditions(transform: (RouteConditions) -> RouteConditions) {
        viewModelScope.launch { conditionsStore.update(transform) }
    }

    // The conditions are read once, as the run starts, and travel with its two
    // ends through a download and a retry: a chip changed meanwhile is for the
    // next route.
    private fun startRoute(
        target: PlaceTarget,
        origin: LastFix?,
    ) = runRoute {
        val conditions = conditionsStore.conditions.first()
        startOrNull(origin)?.let { start ->
            destinationOrNull(target)?.let { destination ->
                RouteEnds(origin = start, destination = destination, conditions = conditions)
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

    // A reroute and a search for more candidates both hold the planner, and both belong to the route this run replaces.
    private fun stopBackgroundSearches() {
        cancelReroute()
        cancelCandidates()
    }

    private fun runRoute(block: suspend () -> Unit) {
        routeJob?.cancel()
        stopBackgroundSearches()
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
        stopBackgroundSearches()
        when (val result = planner.route(ends.origin, ends.destination, conditions = ends.conditions)) {
            is RouteResult.Found -> {
                val computingMs = planner.lastRunOrNull()?.computingMs ?: 0L
                val choosing = suggestOtherRoutesOrDelete(result)
                val session = activeRoute.start(
                    result.route,
                    ends.destination,
                    ends.conditions,
                    result.reference,
                    computingMs,
                    choosing = choosing,
                )
                routeStep.value = RouteStep.Done
                if (choosing) startCandidates(session, ends.origin)
                // The new session replaced every earlier route, whose references no reroute reads again. The new
                // candidates' files are younger than the prune's grace.
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
                fail(if (ends.conditions.isDefault) RouteFailure.TIMED_OUT else RouteFailure.TIMED_OUT_UNDER_CONDITIONS)
            }

            // With a condition on, the condition may be what stands between the two ends.
            is RouteResult.Failed -> {
                fail(if (ends.conditions.isDefault) RouteFailure.NO_ROUTE else RouteFailure.NO_ROUTE_UNDER_CONDITIONS)
            }
        }
    }

    // Read as the route is found rather than as the run starts, so a switch changed during the search counts. A run
    // cancelled during the read loses the route, and with it the reference the engine wrote for it, which no session
    // holds and so nothing else would delete.
    private suspend fun suggestOtherRoutesOrDelete(found: RouteResult.Found): Boolean =
        try {
            suggestOtherRoutes()
        } catch (e: CancellationException) {
            found.reference?.delete()
            throw e
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

// The point the panel offers in place of the text entry, and the name it came with ("" for a long press).
private data class PickedPoint(
    val point: PlaceTarget.Point,
    val label: String = "",
)

private data class RouteEnds(
    val origin: RoutePoint,
    val destination: RoutePoint,
    val conditions: RouteConditions,
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
 * is judged by, the platform's answer to where the device is now, the shared
 * rerouter a route run stops, the route conditions kept on the device, and
 * the "Suggest other routes" setting.
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
                conditionsStore = RoutePreferences(application),
                nowElapsedRealtimeNanos = SystemClock::elapsedRealtimeNanos,
                hasLocationPermission = application::hasLocationPermission,
                currentFix = PlatformCurrentFixSource(application),
                cancelReroute = Rerouter.get(application)::cancel,
                startCandidates = CandidateFinder.get(application)::start,
                cancelCandidates = CandidateFinder.get(application)::cancel,
                suggestOtherRoutes = { NavigationPreferences(application).settings.first().suggestOtherRoutes },
            )
        }
    }

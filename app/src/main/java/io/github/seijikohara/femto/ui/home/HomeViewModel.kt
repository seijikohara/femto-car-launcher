@file:OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest in activeRoute.

package io.github.seijikohara.femto.ui.home

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.location.Location
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.seijikohara.femto.BuildConfig
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.apps.AppsRepository
import io.github.seijikohara.femto.data.calendar.CalendarPreferences
import io.github.seijikohara.femto.data.calendar.CalendarRepository
import io.github.seijikohara.femto.data.calendar.CalendarSnapshot
import io.github.seijikohara.femto.data.clock.ClockRepository
import io.github.seijikohara.femto.data.common.WhileUiSubscribed
import io.github.seijikohara.femto.data.common.WhileUiSubscribedFresh
import io.github.seijikohara.femto.data.common.catchAsDefault
import io.github.seijikohara.femto.data.common.femtoUserAgent
import io.github.seijikohara.femto.data.display.DisplayPreferences
import io.github.seijikohara.femto.data.geocoding.NominatimApi
import io.github.seijikohara.femto.data.geocoding.NominatimReverseGeocoder
import io.github.seijikohara.femto.data.geocoding.PlatformReverseGeocoder
import io.github.seijikohara.femto.data.geocoding.ReverseGeocoder
import io.github.seijikohara.femto.data.geocoding.ReverseGeocoderRepository
import io.github.seijikohara.femto.data.geocoding.ShortAddress
import io.github.seijikohara.femto.data.location.LocationGraph
import io.github.seijikohara.femto.data.location.TripState
import io.github.seijikohara.femto.data.location.VehicleMotion
import io.github.seijikohara.femto.data.location.arrivedStale
import io.github.seijikohara.femto.data.location.isGpsFix
import io.github.seijikohara.femto.data.location.vehicleMotionFlow
import io.github.seijikohara.femto.data.location.withParkedDwell
import io.github.seijikohara.femto.data.music.AudioSpectrumRepository
import io.github.seijikohara.femto.data.music.LastPlayedKeeper
import io.github.seijikohara.femto.data.music.MediaResumer
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.MusicCommand
import io.github.seijikohara.femto.data.music.MusicSessionRepository
import io.github.seijikohara.femto.data.music.PlatformResumePort
import io.github.seijikohara.femto.data.music.ResumeOutcome
import io.github.seijikohara.femto.data.places.ReceivedPlace
import io.github.seijikohara.femto.data.places.ReceivedPlaceInbox
import io.github.seijikohara.femto.data.routing.ActiveRoute
import io.github.seijikohara.femto.data.routing.ActiveRouteStore
import io.github.seijikohara.femto.data.routing.FollowEvent
import io.github.seijikohara.femto.data.routing.NavigationPreferences
import io.github.seijikohara.femto.data.routing.NavigationSettings
import io.github.seijikohara.femto.data.routing.RerouteStatus
import io.github.seijikohara.femto.data.routing.Rerouter
import io.github.seijikohara.femto.data.routing.RouteFix
import io.github.seijikohara.femto.data.routing.RoutePersistence
import io.github.seijikohara.femto.data.routing.RoutePoint
import io.github.seijikohara.femto.data.routing.UpcomingTurn
import io.github.seijikohara.femto.data.routing.followFlow
import io.github.seijikohara.femto.data.routing.routingAvailableFlow
import io.github.seijikohara.femto.data.system.SystemStatus
import io.github.seijikohara.femto.data.system.SystemStatusRepository
import io.github.seijikohara.femto.data.update.UpdateManifest
import io.github.seijikohara.femto.data.update.UpdatePreferences
import io.github.seijikohara.femto.data.update.UpdateRepository
import io.github.seijikohara.femto.data.update.UpdateSettings
import io.github.seijikohara.femto.data.update.UpdateState
import io.github.seijikohara.femto.data.update.offersUpdate
import io.github.seijikohara.femto.data.update.promptedFor
import io.github.seijikohara.femto.data.update.skipped
import io.github.seijikohara.femto.data.weather.MetNorwayApi
import io.github.seijikohara.femto.data.weather.WeatherRepository
import io.github.seijikohara.femto.data.weather.WeatherSnapshot
import io.github.seijikohara.femto.ui.home.components.AppsBarShortcut
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

private const val TAG = "HomeViewModel"

// The connectivity reading the dashboard assumes before the first one arrives
// (see HomeViewModel.online).
private const val ASSUMED_ONLINE = true

internal class HomeViewModel(
    private val locationFlow: Flow<Location?>,
    private val addressFlow: Flow<ShortAddress?>,
    private val weatherFlow: Flow<WeatherSnapshot?>,
    private val musicStateFlow: Flow<MusicCardState>,
    private val calendarFlow: Flow<CalendarSnapshot?>,
    private val systemStatusFlow: Flow<SystemStatus>,
    private val tripStateFlow: Flow<TripState>,
    // Whether the default network has validated internet
    // (SystemStatusRepository.onlineFlow); backs [online]. Defaults to
    // always-online so previews and tests that do not exercise recovery are
    // unaffected.
    private val onlineFlow: Flow<Boolean> = flowOf(ASSUMED_ONLINE),
    // The updater's state; drives the dock's update badge and the update prompt.
    // Defaults to a build that never checks, so previews and tests that do not
    // exercise either are unaffected.
    private val updateStateFlow: Flow<UpdateState> = flowOf(UpdateState.Disabled),
    // The updater's store: the prompt reads which build it has asked about
    // (UpdateSettings.promptedVersionCode) and records each answer, and both
    // the prompt and the dock's dot leave a skipped build alone.
    private val updateSettingsFlow: Flow<UpdateSettings> = flowOf(UpdateSettings.Default),
    private val recordUpdatePrompted: suspend (Int) -> Unit = {},
    // The boot clock the motion gate judges a fix's age against; tests pin it,
    // because Robolectric's clock starts at zero.
    private val nowElapsedRealtimeNanos: () -> Long = SystemClock::elapsedRealtimeNanos,
    private val sendMusicCommand: (MusicCommand) -> Unit = {},
    private val resumeLastMusicSession: () -> Unit = {},
    // The resume chain for the track that played last (MediaResumer.resume),
    // true when it started playback. Run in viewModelScope so clearing the
    // dashboard cancels it and its browser connection goes with it.
    private val resumeLastPlayed: suspend (packageName: String) -> Boolean = { false },
    private val resetTrip: () -> Unit = {},
    private val resolveMusicSourceComponent: (String) -> ComponentName? = { null },
    private val spectrumEnabledFlow: Flow<Boolean> = flowOf(false),
    private val spectrumBandsFor: (Flow<Boolean>) -> Flow<FloatArray?> = { flowOf(null) },
    // The active route (ActiveRouteStore) and whether this build and map backend
    // offer routing (routingAvailableFlow). Default to none and off, so previews
    // and tests that do not exercise routing show none of it.
    private val activeRouteFlow: Flow<ActiveRoute?> = flowOf(null),
    routingAvailable: Flow<Boolean> = flowOf(false),
    private val clearRoute: () -> Unit = {},
    // Where the follow reports that the vehicle left the route of a session, and
    // that it is back on it (the Rerouter).
    onLeftRoute: (session: Long, fix: RouteFix) -> Unit = { _, _ -> },
    onBackOnRoute: (session: Long) -> Unit = {},
    // The route choice card's two actions on the store: a candidate becomes the
    // route followed, and the card closes keeping the route chosen.
    private val selectRouteCandidate: (session: Long, index: Int) -> Unit = { _, _ -> },
    private val closeRouteChoice: (session: Long) -> Unit = {},
    // Where the active route's progress is worked out; tests pin it to their
    // scheduler.
    routeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    // The time of each live GPS fix, taken as the trip meter takes fixes, for
    // the route kept across a restart (RoutePersistence): its last-driven
    // stamp and its check at the first fix, which also runs with no route on
    // the map. Defaults to nowhere, so previews and tests are unaffected.
    private val onGpsFix: (timeEpochMs: Long) -> Unit = {},
    // Where a place another app sent waits for the dashboard. Defaults to an
    // inbox of its own, so previews and tests receive nothing.
    private val receivedPlaces: ReceivedPlaceInbox = ReceivedPlaceInbox(),
    // The Navigation settings the dashboard reads (Settings → Navigation):
    // which time the speed readout's route row shows, whether the map draws
    // the vehicle on the route line while it drives along it, and what the
    // turn card shows. Defaults to the settings' defaults.
    navigationSettingsFlow: Flow<NavigationSettings> = flowOf(NavigationSettings.Default),
) : ViewModel() {
    // The builds the update prompt has been answered about in this process. The
    // prompt closes on the answer itself, not on the store's write: a slow disk
    // would hold it open after the tap, and a full or damaged one would never
    // close it (the updater keeps its daily gate in memory for the same
    // reason). The store carries the answers to later processes.
    private val answeredVersionCodes = MutableStateFlow(emptySet<Int>())

    // The app whose last played track a resume attempt is bringing back, or
    // null. One attempt at a time: a tap while one runs does nothing.
    private val resumingPackage = MutableStateFlow<String?>(null)

    // The music card's state as the dashboard last received it, mirrored as
    // it passes into uiState. A resume attempt waits on this alone for the
    // card to show the playback: a wait on uiState would subscribe to it and,
    // with the dashboard left, restart every dashboard source for the wait.
    private val cardMusic = MutableStateFlow(HomeUiState.Initial.musicState)

    // The motion verdict as each fix or trip update arrives (vehicleMotionFlow),
    // for the update dot and the route choice card; each reader collects it
    // afresh.
    private val vehicleMotion: Flow<VehicleMotion> =
        vehicleMotionFlow(locationFlow, tripStateFlow, nowElapsedRealtimeNanos)

    // The dock's update dot: an update is on offer and a live GPS fix shows the
    // vehicle parked (fail-closed; see VehicleMotion). The motion is judged as
    // each GPS fix or trip update arrives, never as other cards update, and a
    // parked verdict ages out once no fix follows (vehicleMotionFlow), so the
    // dot goes when the receiver goes quiet. Seeded with "no dot": the
    // updater resolves off the main thread when first collected
    // (UpdateRepository.observe), and the combine below emits only once every
    // source has, so an unseeded slot would hold the whole dashboard back.
    // A skipped build raises no dot; a broken store fails open, as the
    // prompt's read does.
    private val updateBadge: Flow<Boolean> =
        combine(
            updateStateFlow,
            updateSettingsFlow.catchAsDefault(TAG, "update badge settings", UpdateSettings.Default),
            vehicleMotion,
        ) { state, settings, motion -> state.offersUpdate(settings) && motion == VehicleMotion.PARKED }
            .onStart { emit(false) }
            .distinctUntilChanged()
            .catchAsDefault(TAG, "update badge", false)

    // Kotlin's typed combine overloads cover at most 5 flows. Stage the nine
    // sources through a typed intermediate (CoreSignals) so the compiler enforces
    // arity and per-slot types end-to-end: a future reorder fails to compile
    // instead of silently mismapping a positional values[i] cast.
    //
    // Each source is caught individually: an uncaught exception in any one flow
    // (e.g. a SecurityException when a permission is revoked between check and
    // register) would otherwise cancel the shared combine and crash the HOME
    // process. Catching per source degrades only that card to its initial value.
    //
    // The fixes reach onGpsFix from this collection, the dashboard's own: the
    // route keeper adds no location request and hears nothing once the
    // dashboard stops collecting.
    private val coreSignals: Flow<CoreSignals> =
        combine(
            locationFlow
                .onEach { fix ->
                    fix
                        ?.takeIf { it.isGpsFix() && !it.arrivedStale(nowElapsedRealtimeNanos()) }
                        ?.let { onGpsFix(it.time) }
                }.catchAsDefault(TAG, "location", HomeUiState.Initial.location),
            addressFlow.catchAsDefault(TAG, "address", HomeUiState.Initial.address),
            weatherFlow.catchAsDefault(TAG, "weather", HomeUiState.Initial.weather),
            // The card's last played track shows its attempt as resuming.
            musicStateFlow
                .catchAsDefault(TAG, "music", HomeUiState.Initial.musicState)
                .onEach { cardMusic.value = it }
                .combine(resumingPackage) { music, resuming -> music.resumingIn(resuming) },
            updateBadge,
        ) { location, address, weather, music, badge ->
            CoreSignals(location, address, weather, music, badge)
        }

    // The active route while routing is offered here, else none: a switch to
    // the Google backend hides it, and a switch back shows it again. Its
    // progress follows each fix along the line (Route.followFlow), a pass
    // over the route's points, so it runs here on the route and the fixes
    // alone, off the main thread, rather than in the combine below, which
    // every card's update re-runs. With no route it reads no fix and does no
    // work. Seeded with none, like the update badge, so the dashboard never
    // waits on the settings store.
    //
    // Each fix as the route follow reads it. Cheap GPS receivers leave the
    // speed unset while moving, so a GPS fix without one takes the trip's
    // position-derived speed, read as the fix arrives; a trip update alone
    // is no new fix. A network fix stays without a speed, so the off-route
    // watch leaves it out.
    private val routeFixes: Flow<RouteFix?> =
        combine(
            locationFlow.catchAsDefault(TAG, "route location", null),
            tripStateFlow.catchAsDefault(TAG, "route trip speed", HomeUiState.Initial.tripState),
        ) { location, trip -> location to trip.currentSpeedMs }
            .distinctUntilChanged { old, new -> old.first === new.first }
            .map { (location, tripSpeedMs) -> location?.toRouteFix(tripSpeedMs) }

    // The follow also watches for the vehicle leaving the route and reports it
    // with the route's session. It restarts on a new session or a new route (a
    // reroute's), never on a reroute status alone: a restart would put the
    // progress back to the start of the line and the off-route watch back to
    // unarmed. The status and the choice card join in beside it: selecting a
    // candidate changes the route, which restarts the follow on the new line.
    // The turns ahead and the off-route verdict ride along for the turn card,
    // which the settings shape below.
    private val activeRoute: Flow<FollowedRoute?> =
        combine(activeRouteFlow, routingAvailable) { active, available -> active?.takeIf { available } }
            .distinctUntilChangedBy { it?.session to it?.route }
            .flatMapLatest { active ->
                active?.let { shown ->
                    combine(
                        shown.route
                            .followFlow(routeFixes)
                            .onEach { follow ->
                                when (val event = follow.event) {
                                    is FollowEvent.LeftRoute -> onLeftRoute(shown.session, event.fix)
                                    FollowEvent.BackOnRoute -> onBackOnRoute(shown.session)
                                    null -> Unit
                                }
                            },
                        activeRouteFlow.map { it.sessionView(shown.session) }.distinctUntilChanged(),
                    ) { follow, view ->
                        FollowedRoute(
                            state =
                                ActiveRouteState(
                                    line = shown.route.points,
                                    progress = follow.progress,
                                    reroute = view.reroute,
                                    choice = view.choice,
                                    alternativeLines = view.alternativeLines,
                                    vehicleOnLine = follow.onLine,
                                ),
                            upcomingTurns = follow.upcomingTurns,
                            offRoute = follow.offRoute,
                            fixPlaced = follow.fixPlaced,
                        )
                    }
                } ?: flowOf(null)
            }.flowOn(routeDispatcher)
            .onStart { emit(null) }
            .distinctUntilChanged()
            .catchAsDefault(TAG, "route", null)

    // The choice card closes by itself once the vehicle has read MOVING for
    // ROUTE_CHOICE_DRIVING_CLOSE_MS without a break: by then the route being
    // followed is the one chosen, and the card would only sit on the map. Any
    // other verdict starts the count over; selecting a candidate does not, as
    // the count is keyed on the session alone. The motion is read only while a
    // card is open, and the count runs while the dashboard collects uiState.
    // Its one emission lets the combine below start without waiting on it.
    private val routeChoiceAutoClose: Flow<Unit> =
        activeRouteFlow
            .map { active -> active?.takeIf { it.choosing }?.session }
            .distinctUntilChanged()
            .flatMapLatest { session ->
                session?.let { open ->
                    vehicleMotion
                        .map { it == VehicleMotion.MOVING }
                        .distinctUntilChanged()
                        .transformLatest { driving ->
                            if (driving) {
                                delay(ROUTE_CHOICE_DRIVING_CLOSE_MS)
                                closeRouteChoice(open)
                            }
                            emit(Unit)
                        }
                } ?: flowOf(Unit)
            }.onStart { emit(Unit) }
            .distinctUntilChanged()
            .catchAsDefault(TAG, "route choice auto-close", Unit)

    // Seeded off like the route, so the dashboard never waits on the settings store.
    private val routingOffered: Flow<Boolean> =
        routingAvailable
            .onStart { emit(false) }
            .distinctUntilChanged()
            .catchAsDefault(TAG, "routing available", false)

    // Seeded with null, "not read yet", for the same reason; the dashboard
    // takes the stored settings as soon as they are read (withSettings).
    // Applied to the route below, after the follow and outside its restart
    // keys: switching a setting never restarts the follow, which would put
    // the progress back to the start of the line. A store that fails reads as
    // the defaults.
    private val navigationSettings: Flow<NavigationSettings?> =
        navigationSettingsFlow
            .map<NavigationSettings, NavigationSettings?> { it }
            .onStart { emit(null) }
            .distinctUntilChanged()
            .catchAsDefault(TAG, "navigation settings", NavigationSettings.Default)

    val uiState: StateFlow<HomeUiState> =
        combine(
            coreSignals,
            calendarFlow.catchAsDefault(TAG, "calendar", HomeUiState.Initial.calendar),
            systemStatusFlow.catchAsDefault(TAG, "system status", HomeUiState.Initial.systemStatus),
            tripStateFlow.catchAsDefault(TAG, "trip state", HomeUiState.Initial.tripState),
            combine(
                activeRoute,
                routingOffered,
                routeChoiceAutoClose,
                navigationSettings,
            ) { route, offered, _, settings -> route?.withSettings(settings) to offered },
        ) { core, calendar, systemStatus, tripState, (route, offered) ->
            HomeUiState(
                location = core.location,
                address = core.address,
                weather = core.weather,
                musicState = core.music,
                calendar = calendar,
                systemStatus = systemStatus,
                tripState = tripState,
                updateBadge = core.updateBadge,
                activeRoute = route,
                routingAvailable = offered,
            )
        }.stateIn(viewModelScope, WhileUiSubscribed, HomeUiState.Initial)

    /**
     * Whether the default network has validated internet, for the live map's
     * offline->online reload (WebMapView). Its own state, apart from [uiState]:
     * the combine emits only once every source has, so after a return the
     * reading would reach the map only when the slowest source spoke again,
     * seconds after the page the return reload built, which a late
     * offline->online edge then reloads a second time. Starts online, so a
     * dashboard that has not heard yet never shows the map a false
     * offline->online edge.
     */
    val online: StateFlow<Boolean> =
        onlineFlow
            .catchAsDefault(TAG, "connectivity", ASSUMED_ONLINE)
            .stateIn(viewModelScope, WhileUiSubscribed, ASSUMED_ONLINE)

    /**
     * The build the dashboard's update prompt asks about, or null: an offer
     * waiting for its first step (to download, or to install the verified
     * file), once a live GPS fix has shown the vehicle parked for
     * [UPDATE_PROMPT_PARKED_DWELL_MS] without a break (fail-closed; see
     * VehicleMotion), that the prompt has not asked about, the Updates
     * section has not shown (promptedFor, or an answer earlier in this
     * process), and the user has not skipped (skipped). A verdict that
     * leaves PARKED closes it at once, unrecorded, to ask again after the
     * next full dwell.
     *
     * Its own state, apart from [uiState], and shared with
     * [WhileUiSubscribedFresh]: a dashboard that comes back, even a second
     * later, starts from no prompt and judges the motion afresh, so leaving
     * the dashboard, or a rotation, starts the dwell over. Held in [uiState],
     * the prompt it left would show again until every dashboard source had
     * spoken, even while the vehicle moves.
     */
    val updatePrompt: StateFlow<UpdateManifest?> =
        combine(
            updateStateFlow,
            // Caught on its own, and failing open: a broken store may ask again
            // after a restart, while the answers kept in this process hold.
            updateSettingsFlow.catchAsDefault(TAG, "update settings", UpdateSettings.Default),
            answeredVersionCodes,
            vehicleMotionFlow(locationFlow, tripStateFlow, nowElapsedRealtimeNanos)
                .withParkedDwell(UPDATE_PROMPT_PARKED_DWELL_MS),
        ) { state, settings, answered, reading ->
            state.promptableOfferOrNull()?.takeIf { offer ->
                reading.parkedThroughDwell &&
                    offer.versionCode !in answered &&
                    !settings.promptedFor(offer.versionCode) &&
                    !settings.skipped(offer.versionCode)
            }
        }.distinctUntilChanged()
            .catchAsDefault(TAG, "update prompt", null)
            .stateIn(viewModelScope, WhileUiSubscribedFresh, null)

    // Spectrum levels for the music card's spectrum background, or null while
    // the visualization is off / unavailable. Kept OUT of HomeUiState: the
    // capture ticks at ~20 Hz and routing it through uiState would recompose
    // the whole dashboard per tick; as a separate stream only the spectrum
    // canvas observes it (side-channel precedent: events below). "Is playing"
    // derives from the shared uiState rather than a second collector on the
    // cold MusicSessionRepository flow, which would double-register its
    // MediaSessionManager listeners.
    val audioSpectrum: StateFlow<FloatArray?> =
        spectrumBandsFor(
            combine(spectrumEnabledFlow, uiState) { enabled, state ->
                enabled && (state.musicState as? MusicCardState.Playing)?.nowPlaying?.isPlaying == true
            }.distinctUntilChanged(),
        ).stateIn(viewModelScope, WhileUiSubscribed, null)

    /**
     * A place another app sent (the share sheet or a `geo:` link), waiting for
     * the destination panel, or null. A state rather than an [events] entry:
     * on a cold start through a share the place arrives before the dashboard
     * is composed, and an event with no collector would be lost. The panel
     * reports the place taken ([HomeAction.ReceivedPlaceHandled]), which
     * empties the inbox, so a recreated dashboard opens nothing again.
     */
    val receivedPlace: StateFlow<ReceivedPlace?> = receivedPlaces.pending

    // extraBufferCapacity = 1 lets a single tryEmit succeed without a live collector,
    // matching the semantics of a one-shot navigation request that is dropped if the
    // host is already detached (e.g. during configuration change).
    private val mutableEvents = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<HomeEvent> = mutableEvents.asSharedFlow()

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.OpenAppDrawer -> {
                // Opens the apps maximize panel, handled at the dashboard overlay
                // layer (DashboardContent intercepts this action before it reaches
                // here). Kept in the sealed action so the dock's APPS nav spec can
                // dispatch it; a no-op if it ever reaches the ViewModel.
            }

            is HomeAction.LaunchApp -> {
                mutableEvents.tryEmit(HomeEvent.LaunchComponent(action.componentName))
            }

            HomeAction.OpenMaps -> {
                // Carry the latest fix so the maps app opens at the user's
                // position; fall back to the category launcher (maps home) when
                // no location has arrived yet.
                mutableEvents.tryEmit(
                    uiState.value.location
                        ?.let { HomeEvent.LaunchGeo(it.latitude, it.longitude) }
                        ?: HomeEvent.LaunchAppCategory(Intent.CATEGORY_APP_MAPS),
                )
            }

            HomeAction.OpenDestinations -> {
                // Opens the destination panel at the dashboard overlay layer
                // (DashboardContent intercepts it, like OpenAppDrawer); a no-op
                // if it ever reaches the ViewModel.
            }

            is HomeAction.Navigate -> {
                mutableEvents.tryEmit(HomeEvent.LaunchDestination(action.target, action.label))
            }

            is HomeAction.ReceivedPlaceHandled -> {
                receivedPlaces.handled(action.place)
            }

            is HomeAction.Shortcut -> {
                mutableEvents.tryEmit(
                    when (action.target) {
                        AppsBarShortcut.Phone -> HomeEvent.LaunchDialer
                        AppsBarShortcut.Music -> HomeEvent.LaunchAppCategory(Intent.CATEGORY_APP_MUSIC)
                    },
                )
            }

            HomeAction.ConnectMusicPlayer -> {
                mutableEvents.tryEmit(HomeEvent.OpenNotificationListenerSettings)
            }

            is HomeAction.LaunchMusicSource -> {
                // Resolve to the source app's launcher activity and reuse the app
                // grid's launch path. A package with no launchable activity (a
                // background-only media service) resolves to null and no-ops —
                // logged so the deliberate dead tap stays diagnosable in the field.
                resolveMusicSourceComponent(action.packageName)
                    ?.let { mutableEvents.tryEmit(HomeEvent.LaunchComponent(it)) }
                    ?: Log.d(TAG, "no launcher activity for ${action.packageName}; ignoring tap")
            }

            is HomeAction.Music -> {
                sendMusicCommand(action.command)
            }

            HomeAction.PlayDefaultMusic -> {
                // Best-effort resume first, then unconditionally launch the
                // default music app: there is no callback confirming whether the
                // media key actually resumed a session, so the launch fallback
                // always fires too, guaranteeing the tap visibly responds.
                resumeLastMusicSession()
                mutableEvents.tryEmit(HomeEvent.LaunchAppCategory(Intent.CATEGORY_APP_MUSIC))
            }

            is HomeAction.ResumeLastPlayed -> {
                startResume(action.packageName)
            }

            HomeAction.OpenBrowser -> {
                mutableEvents.tryEmit(HomeEvent.LaunchAppCategory(Intent.CATEGORY_APP_BROWSER))
            }

            HomeAction.OpenCalendar -> {
                mutableEvents.tryEmit(HomeEvent.LaunchAppCategory(Intent.CATEGORY_APP_CALENDAR))
            }

            HomeAction.OpenWeather -> {
                // CATEGORY_APP_WEATHER ships exactly at the minSdk (API 33).
                // Devices without a weather app declaring the category no-op
                // gracefully via the host's tryStartActivity.
                mutableEvents.tryEmit(HomeEvent.LaunchAppCategory(Intent.CATEGORY_APP_WEATHER))
            }

            is HomeAction.AdjustMapZoom -> {
                mutableEvents.tryEmit(HomeEvent.AdjustMapZoom(action.delta))
            }

            HomeAction.ToggleMapNorthUp -> {
                mutableEvents.tryEmit(HomeEvent.ToggleMapNorthUp)
            }

            HomeAction.OpenSettings -> {
                mutableEvents.tryEmit(HomeEvent.OpenInAppSettings())
            }

            is HomeAction.UpdateLater -> {
                answerUpdatePrompt(action.versionCode)
            }

            is HomeAction.UpdateNow -> {
                answerUpdatePrompt(action.versionCode)
                mutableEvents.tryEmit(HomeEvent.OpenInAppSettings(startUpdate = true))
            }

            HomeAction.OpenLicenses -> {
                mutableEvents.tryEmit(HomeEvent.OpenLicenses)
            }

            HomeAction.OpenAssistant -> {
                mutableEvents.tryEmit(HomeEvent.OpenAssistant)
            }

            HomeAction.ResetTrip -> {
                resetTrip()
            }

            HomeAction.ClearRoute -> {
                clearRoute()
            }

            // The card on screen names its session, so a tap that lands after a
            // new route has started changes nothing of it.
            is HomeAction.SelectRouteCandidate -> {
                uiState.value.activeRoute
                    ?.choice
                    ?.let { selectRouteCandidate(it.session, action.index) }
            }

            HomeAction.CloseRouteChoice -> {
                uiState.value.activeRoute
                    ?.choice
                    ?.let { closeRouteChoice(it.session) }
            }

            is HomeAction.MoveDockNav -> {
                mutableEvents.tryEmit(HomeEvent.MoveDockNav(action.id, action.direction))
            }

            is HomeAction.SetDockNavOrder -> {
                mutableEvents.tryEmit(HomeEvent.SetDockNavOrder(action.order))
            }

            is HomeAction.HideDockNav -> {
                mutableEvents.tryEmit(HomeEvent.HideDockNav(action.id))
            }

            is HomeAction.MoveDockStatus -> {
                mutableEvents.tryEmit(HomeEvent.MoveDockStatus(action.id, action.direction))
            }

            is HomeAction.HideDockStatus -> {
                mutableEvents.tryEmit(HomeEvent.HideDockStatus(action.id))
            }

            HomeAction.ResetDock -> {
                mutableEvents.tryEmit(HomeEvent.ResetDock)
            }
        }
    }

    // Resume the track the card offers from [packageName], unless an attempt
    // already runs. The card must still offer that app: a tap that lands after
    // a live session took the card would otherwise wake a second app over it.
    // The flag clears however the attempt ends, cancellation included, so the
    // button is usable again whenever the card still offers the track.
    private fun startResume(packageName: String) {
        val offered = uiState.value.musicState.offeredPackageOrNull() == packageName
        if (offered && resumingPackage.compareAndSet(null, packageName)) {
            viewModelScope.launch {
                try {
                    if (resumeLastPlayed(packageName)) awaitCardLeaves(packageName)
                } finally {
                    resumingPackage.value = null
                }
            }
        }
    }

    // The attempt sees the app play before the card does: the card's state
    // takes its own way (the session watch, the store, back to the main
    // thread). Until the card stops offering the track the button stays
    // pending, so a tap in between cannot send prepare() and play() again to
    // an app already playing, which an older app answers by reloading the
    // track. Bounded, in case the card never hears of the playback, as when
    // the dashboard is no longer collecting: the wait reads the card's mirror
    // (cardMusic) and subscribes to nothing.
    private suspend fun awaitCardLeaves(packageName: String) {
        withTimeoutOrNull(RESUME_SETTLE_GRACE_MS) {
            cardMusic.first { it.offeredPackageOrNull() != packageName }
        }
    }

    // Either answer settles the prompt for build [versionCode]: it closes at
    // once, and never asks about the build again, in this process or a later one.
    private fun answerUpdatePrompt(versionCode: Int) {
        answeredVersionCodes.update { it + versionCode }
        viewModelScope.launch { recordUpdatePrompted(versionCode) }
    }
}

/**
 * How long, after an attempt started playback, the resume button stays pending at most while the card has not yet
 * switched from the last played track to the live session. The switch normally takes well under a second; internal
 * so tests probe both sides.
 */
internal const val RESUME_SETTLE_GRACE_MS = 3_000L

// The app whose last played track the card offers, or null.
private fun MusicCardState.offeredPackageOrNull(): String? = (this as? MusicCardState.LastPlayed)?.track?.packageName

// The card's last played track from [packageName], marked as resuming while an attempt for it runs; any other state
// passes unchanged.
private fun MusicCardState.resumingIn(packageName: String?): MusicCardState =
    (this as? MusicCardState.LastPlayed)
        ?.takeIf { it.track.packageName == packageName }
        ?.copy(resuming = true)
        ?: this

// Each reading only when the fix carries it: a missing speed must read as unknown, not as standing still. A GPS fix
// without one takes [tripSpeedMs], the trip's speed from the positions; a network fix's position is too coarse for it.
private fun Location.toRouteFix(tripSpeedMs: Double): RouteFix =
    RouteFix(
        point = RoutePoint(latitude = latitude, longitude = longitude),
        accuracyMeters = accuracy.takeIf { hasAccuracy() },
        speedMetersPerSecond = speed.takeIf { hasSpeed() } ?: tripSpeedMs.toFloat().takeIf { isGpsFix() },
        bearingDegrees = bearing.takeIf { hasBearing() },
        elapsedMs = elapsedRealtimeNanos / NANOS_PER_MILLI,
    )

private const val NANOS_PER_MILLI = 1_000_000L

// What the dashboard shows of route session [session] beside its line: where a
// reroute of it stands and, while the driver may still choose, the choice card
// and the routes not selected. Another session, or none, shows nothing.
private data class SessionView(
    val reroute: RerouteStatus = RerouteStatus.NONE,
    val choice: RouteChoiceState? = null,
    val alternativeLines: List<List<RoutePoint>> = emptyList(),
)

// The route as the follow and the session view give it, before the settings
// shape it: the dashboard's state, and the turns ahead and the off-route
// verdict the turn card is worked out from.
private data class FollowedRoute(
    val state: ActiveRouteState,
    val upcomingTurns: List<UpcomingTurn>?,
    val offRoute: Boolean,
    val fixPlaced: Boolean,
)

// The route row's time, the vehicle on the line only with its setting on, and
// the turn card as its three settings shape it, which hides while the choice
// card is open. Until the [stored] settings are read the row and the line take
// the defaults, and the card stays hidden, so a driver who switched it off
// never sees it flash.
private fun FollowedRoute.withSettings(stored: NavigationSettings?): ActiveRouteState =
    (stored ?: NavigationSettings.Default).let { settings ->
        state.copy(
            rowTime = settings.routeRowTime,
            vehicleOnLine = state.vehicleOnLine?.takeIf { settings.vehicleOnRouteLine },
            turnCard =
                turnCardStateOrNull(
                    upcomingTurns = upcomingTurns,
                    remainingMeters = state.progress.remainingMeters,
                    fixPlaced = fixPlaced,
                    offRoute = offRoute,
                    reroute = state.reroute,
                    choosing = state.choice != null,
                    settings =
                        TurnCardSettings(
                            show = stored != null && settings.turnCard,
                            showNextAfter = settings.turnCardNextAfter,
                            showStraight = settings.turnCardStraight,
                        ),
                ),
        )
    }

private fun ActiveRoute?.sessionView(session: Long): SessionView =
    this
        ?.takeIf { it.session == session }
        ?.let { active ->
            SessionView(
                reroute = active.reroute,
                choice =
                    active.takeIf { it.choosing }?.let {
                        RouteChoiceState(
                            session = session,
                            candidates =
                                it.candidates.map { candidate ->
                                    CandidateSummary(
                                        distanceMeters = candidate.route.distanceMeters,
                                        durationSeconds = candidate.route.durationSeconds,
                                    )
                                },
                            selected = it.selected,
                            finding = it.finding,
                            searchCompleted = it.searchCompleted,
                        )
                    },
                alternativeLines =
                    active.candidates
                        .takeIf { active.choosing }
                        .orEmpty()
                        .filterIndexed { index, _ -> index != active.selected }
                        .map { it.route.points },
            )
        } ?: SessionView()

/**
 * How long the vehicle must read MOVING without a break before the route
 * choice card closes by itself, keeping the route chosen. Internal so tests
 * probe both sides of it.
 */
internal const val ROUTE_CHOICE_DRIVING_CLOSE_MS = 30_000L

// File-private holder that groups the first five slots (four sources and the
// update badge) so the two-stage combine stays within Kotlin's typed
// (max-arity-5) combine overloads.
private data class CoreSignals(
    val location: Location?,
    val address: ShortAddress?,
    val weather: WeatherSnapshot?,
    val music: MusicCardState,
    val updateBadge: Boolean,
)

/**
 * How long the vehicle must read PARKED without a break before the
 * dashboard's update prompt asks. Most traffic-light stops are shorter, and a
 * car parked at its destination stays longer, so the prompt meets a driver who
 * has arrived rather than one about to pull away. The dock's dot does not wait.
 *
 * A known consequence: with the location "minimum distance" setting above 0,
 * fixes stop while the car stands still, PARKED ages out after
 * LOCATION_STALE_THRESHOLD_MS, and the prompt never reaches the dwell. The dot
 * (while fixes last) and Settings still offer the update. Internal so tests
 * probe both sides of the dwell.
 */
internal const val UPDATE_PROMPT_PARKED_DWELL_MS = 60_000L

// The offer the update prompt may ask about: one still waiting for the user's
// first step, to download it or to install the verified file. An update under
// way has already been acted on, and a failed one is Settings' to explain.
private fun UpdateState.promptableOfferOrNull(): UpdateManifest? =
    when (this) {
        is UpdateState.Available -> manifest
        is UpdateState.Ready -> manifest
        else -> null
    }

// Shared HTTP disk cache size. A forecast response is ~50 KB and Nominatim
// answers are tiny, so 5 MiB holds days of both with headroom.
private const val HTTP_CACHE_BYTES = 5L * 1024 * 1024

internal class HomeViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        // The location pipeline (GPS registration + trip accumulators) is an
        // app-scoped singleton so the dashboard and the background-ranging
        // foreground service share one registration and one running total.
        val locationGraph = LocationGraph.get(application)
        val locationFlow = locationGraph.locationFlow()
        val clock = ClockRepository(application)
        val clockFlow = clock.tickFlow()
        // Share one OkHttpClient across the weather and geocoding APIs so the
        // connection pool and dispatcher are reused instead of duplicated. The
        // disk cache turns on standard HTTP caching for both: api.met.no's terms
        // require honouring Expires and revalidating with If-Modified-Since, and
        // OkHttp enforces exactly that against the cache — including across
        // process restarts, so a boot-time relaunch reuses a still-fresh entry
        // instead of re-fetching.
        val httpClient =
            OkHttpClient
                .Builder()
                .cache(Cache(File(application.cacheDir, "http_cache"), HTTP_CACHE_BYTES))
                .build()
        // Default to the on-device platform geocoder: free, no ToS surface, and
        // degrades gracefully where no backend exists. A self-hosted
        // Nominatim-compatible host is opt-in via GEOCODER_BASE_URL (empty by
        // default — the public Nominatim endpoint is not ToS-compliant to ship).
        val reverseGeocoder: ReverseGeocoder =
            BuildConfig.GEOCODER_BASE_URL
                .takeIf { it.isNotBlank() }
                ?.let { baseUrl ->
                    NominatimReverseGeocoder(
                        NominatimApi(
                            client = httpClient,
                            baseUrl = baseUrl,
                            userAgent = femtoUserAgent,
                            // languageProvider defaults to the device locale, read per request.
                            apiKey = BuildConfig.GEOCODER_API_KEY.takeIf { it.isNotBlank() },
                        ),
                    )
                } ?: PlatformReverseGeocoder(application)
        val geocoder = ReverseGeocoderRepository(locationFlow, reverseGeocoder)
        val weatherApi =
            MetNorwayApi(
                client = httpClient,
                baseUrl = BuildConfig.WEATHER_BASE_URL,
                userAgent = femtoUserAgent,
            )
        val weather = WeatherRepository(weatherApi, locationFlow, clockFlow)
        // One keeper for the card's memory and the resume chain, so an app the
        // chain finds gone is forgotten in the store the card reads.
        val lastPlayed = LastPlayedKeeper.of(application)
        val music = MusicSessionRepository(application, lastPlayed)
        val resumer = MediaResumer(port = PlatformResumePort(application), forget = lastPlayed::forget)
        val audioSpectrum = AudioSpectrumRepository(application)
        val calendarPreferences = CalendarPreferences(application)
        val calendar = CalendarRepository(application, clockFlow, calendarPreferences.hiddenCalendarIds)
        val systemStatus = SystemStatusRepository(application, locationFlow)
        val apps = AppsRepository(application)
        val displayPreferences = DisplayPreferences(application)
        val updatePreferences = UpdatePreferences(application)
        val routingEnabled = application.resources.getBoolean(R.bool.routing_enabled)
        // Started here, beside the rerouter's wiring, once per process: the
        // route the last process left on the map comes back, or its files go,
        // off the main thread while the dashboard opens. A build without
        // routing only deletes what an earlier build left.
        val routeKeeper = RoutePersistence.get(application).apply { start(enabled = routingEnabled) }
        val navigationSettings = NavigationPreferences(application).settings

        @Suppress("UNCHECKED_CAST")
        return HomeViewModel(
            locationFlow = locationFlow,
            addressFlow = geocoder.addressFlow(),
            weatherFlow = weather.snapshotFlow(),
            musicStateFlow = music.stateFlow(),
            calendarFlow = calendar.snapshotFlow(),
            systemStatusFlow = systemStatus.statusFlow(),
            tripStateFlow = locationGraph.tripState,
            onlineFlow = systemStatus.onlineFlow(),
            // The first collection here is what starts the updater and its daily
            // check: the dashboard subscribes once onCreate has returned, and
            // observe() resolves the updater off the main thread, so neither the
            // cold start nor the first frame waits for it.
            updateStateFlow = UpdateRepository.observe(application) { it.state },
            updateSettingsFlow = updatePreferences.settings,
            recordUpdatePrompted = updatePreferences::recordPrompted,
            sendMusicCommand = music::send,
            resumeLastMusicSession = music::dispatchPlayMediaKey,
            resumeLastPlayed = { packageName -> resumer.resume(packageName) is ResumeOutcome.Started },
            resetTrip = locationGraph::resetTrip,
            resolveMusicSourceComponent = apps::launcherComponentFor,
            spectrumEnabledFlow =
                displayPreferences.settings
                    .map { it.musicSpectrum }
                    .distinctUntilChanged(),
            spectrumBandsFor = audioSpectrum::bandsFlow,
            activeRouteFlow = ActiveRouteStore.shared.active,
            routingAvailable =
                routingAvailableFlow(
                    enabled = routingEnabled,
                    settings = displayPreferences.settings,
                ),
            clearRoute = ActiveRouteStore.shared::clear,
            // Resolved at the first report, which only an active route can make,
            // so a build without routing never creates the rerouter or the
            // routing stack behind it.
            onLeftRoute = { session, fix -> Rerouter.get(application).onLeftRoute(session, fix) },
            onBackOnRoute = { session -> Rerouter.get(application).onBackOnRoute(session) },
            selectRouteCandidate = { session, index -> ActiveRouteStore.shared.select(session, index) },
            closeRouteChoice = { session -> ActiveRouteStore.shared.closeChoice(session) },
            onGpsFix = routeKeeper::onGpsFix,
            receivedPlaces = ReceivedPlaceInbox.shared,
            navigationSettingsFlow = navigationSettings,
        ) as T
    }
}

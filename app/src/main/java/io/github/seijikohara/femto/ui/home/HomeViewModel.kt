package io.github.seijikohara.femto.ui.home

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.seijikohara.femto.BuildConfig
import io.github.seijikohara.femto.data.apps.AppsRepository
import io.github.seijikohara.femto.data.calendar.CalendarPreferences
import io.github.seijikohara.femto.data.calendar.CalendarRepository
import io.github.seijikohara.femto.data.calendar.CalendarSnapshot
import io.github.seijikohara.femto.data.clock.ClockRepository
import io.github.seijikohara.femto.data.common.WhileUiSubscribed
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
import io.github.seijikohara.femto.data.location.vehicleMotion
import io.github.seijikohara.femto.data.music.AudioSpectrumRepository
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.MusicCommand
import io.github.seijikohara.femto.data.music.MusicSessionRepository
import io.github.seijikohara.femto.data.system.SystemStatus
import io.github.seijikohara.femto.data.system.SystemStatusRepository
import io.github.seijikohara.femto.data.update.UpdateRepository
import io.github.seijikohara.femto.data.update.UpdateState
import io.github.seijikohara.femto.data.weather.MetNorwayApi
import io.github.seijikohara.femto.data.weather.WeatherRepository
import io.github.seijikohara.femto.data.weather.WeatherSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

private const val TAG = "HomeViewModel"

internal class HomeViewModel(
    private val locationFlow: Flow<Location?>,
    private val addressFlow: Flow<ShortAddress?>,
    private val weatherFlow: Flow<WeatherSnapshot?>,
    private val musicStateFlow: Flow<MusicCardState>,
    private val calendarFlow: Flow<CalendarSnapshot?>,
    private val systemStatusFlow: Flow<SystemStatus>,
    private val tripStateFlow: Flow<TripState>,
    // Whether the device currently has validated internet; drives the live map's
    // offline->online reload (see WebMapView). Defaults to always-online so previews
    // and tests that do not exercise recovery are unaffected.
    private val onlineFlow: Flow<Boolean> = flowOf(true),
    // The updater's state; drives the dock's update badge. Defaults to a build that
    // never checks, so previews and tests that do not exercise the badge are unaffected.
    private val updateStateFlow: Flow<UpdateState> = flowOf(UpdateState.Disabled),
    private val sendMusicCommand: (MusicCommand) -> Unit = {},
    private val resumeLastMusicSession: () -> Unit = {},
    private val resetTrip: () -> Unit = {},
    private val resolveMusicSourceComponent: (String) -> ComponentName? = { null },
    private val spectrumEnabledFlow: Flow<Boolean> = flowOf(false),
    private val spectrumBandsFor: (Flow<Boolean>) -> Flow<FloatArray?> = { flowOf(null) },
) : ViewModel() {
    // Kotlin's typed combine overloads cover at most 5 flows. Stage the nine
    // sources through a typed intermediate (CoreSignals) so the compiler enforces
    // arity and per-slot types end-to-end: a future reorder fails to compile
    // instead of silently mismapping a positional values[i] cast.
    //
    // Each source is caught individually: an uncaught exception in any one flow
    // (e.g. a SecurityException when a permission is revoked between check and
    // register) would otherwise cancel the shared combine and crash the HOME
    // process. Catching per source degrades only that card to its initial value.
    private val coreSignals: Flow<CoreSignals> =
        combine(
            locationFlow.catchAsDefault("location", HomeUiState.Initial.location),
            addressFlow.catchAsDefault("address", HomeUiState.Initial.address),
            weatherFlow.catchAsDefault("weather", HomeUiState.Initial.weather),
            musicStateFlow.catchAsDefault("music", HomeUiState.Initial.musicState),
            // Seeded with "no offer": the updater resolves off the main thread when
            // first collected (UpdateRepository.observe), and the combine emits
            // only once every source has, so an unseeded slot would hold the
            // whole dashboard back until then.
            updateStateFlow
                .map { it.offersUpdate() }
                .distinctUntilChanged()
                .onStart { emit(false) }
                .catchAsDefault("update", false),
        ) { location, address, weather, music, updateOffered ->
            CoreSignals(location, address, weather, music, updateOffered)
        }

    val uiState: StateFlow<HomeUiState> =
        combine(
            coreSignals,
            calendarFlow.catchAsDefault("calendar", HomeUiState.Initial.calendar),
            systemStatusFlow.catchAsDefault("system status", HomeUiState.Initial.systemStatus),
            tripStateFlow.catchAsDefault("trip state", HomeUiState.Initial.tripState),
            onlineFlow.catchAsDefault("connectivity", HomeUiState.Initial.online),
        ) { core, calendar, systemStatus, tripState, online ->
            HomeUiState(
                location = core.location,
                address = core.address,
                weather = core.weather,
                musicState = core.music,
                calendar = calendar,
                systemStatus = systemStatus,
                tripState = tripState,
                online = online,
                updateBadge =
                    core.updateOffered && vehicleMotion(core.location, tripState) == VehicleMotion.PARKED,
            )
        }.stateIn(viewModelScope, WhileUiSubscribed, HomeUiState.Initial)

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

            is HomeAction.Shortcut -> {
                mutableEvents.tryEmit(HomeEvent.LaunchAppCategory(action.target.intentCategory))
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
                mutableEvents.tryEmit(HomeEvent.OpenInAppSettings)
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
}

// Replace a source failure with that source's neutral value so one broken
// repository degrades its own card instead of killing the launcher process.
// Cancellation is rethrown to keep structured concurrency intact. By design the
// failed source then COMPLETES for the rest of the current subscription epoch:
// its card stays at the neutral value until WhileUiSubscribed tears the chain
// down and a later subscriber re-collects the cold sources from scratch. No
// automatic retry within an epoch — a broken system service would turn a retry
// loop into a battery drain on the head unit.
private fun <T> Flow<T>.catchAsDefault(
    source: String,
    default: T,
): Flow<T> =
    catch { e ->
        if (e is CancellationException) throw e
        Log.e(TAG, "$source flow failed", e)
        emit(default)
    }

// File-private holder that groups the first five sources so the two-stage
// combine stays within Kotlin's typed (max-arity-5) combine overloads.
private data class CoreSignals(
    val location: Location?,
    val address: ShortAddress?,
    val weather: WeatherSnapshot?,
    val music: MusicCardState,
    val updateOffered: Boolean,
)

// Whether this state names a newer build the user has not installed yet:
// offered, on its way, verified, handed to the installer, or failed with the
// offer still known. The badge stays up across those steps instead of blinking
// off mid-way; a failure that lost the offer shows none, like every state
// without one.
private fun UpdateState.offersUpdate(): Boolean =
    when (this) {
        is UpdateState.Available, is UpdateState.Downloading, is UpdateState.Ready, is UpdateState.Installing -> true
        is UpdateState.Failed -> manifest != null
        UpdateState.Disabled, is UpdateState.Idle, UpdateState.Checking, UpdateState.UpToDate -> false
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
        val music = MusicSessionRepository(application)
        val audioSpectrum = AudioSpectrumRepository(application)
        val calendarPreferences = CalendarPreferences(application)
        val calendar = CalendarRepository(application, clockFlow, calendarPreferences.hiddenCalendarIds)
        val systemStatus = SystemStatusRepository(application, locationFlow)
        val apps = AppsRepository(application)
        val displayPreferences = DisplayPreferences(application)

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
            sendMusicCommand = music::send,
            resumeLastMusicSession = music::dispatchPlayMediaKey,
            resetTrip = locationGraph::resetTrip,
            resolveMusicSourceComponent = apps::launcherComponentFor,
            spectrumEnabledFlow =
                displayPreferences.settings
                    .map { it.musicSpectrum }
                    .distinctUntilChanged(),
            spectrumBandsFor = audioSpectrum::bandsFlow,
        ) as T
    }
}

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
import io.github.seijikohara.femto.data.AppsRepository
import io.github.seijikohara.femto.data.CalendarRepository
import io.github.seijikohara.femto.data.CalendarSnapshot
import io.github.seijikohara.femto.data.ClockRepository
import io.github.seijikohara.femto.data.ClockTick
import io.github.seijikohara.femto.data.LocationRepository
import io.github.seijikohara.femto.data.MusicCardState
import io.github.seijikohara.femto.data.MusicSessionRepository
import io.github.seijikohara.femto.data.NominatimApi
import io.github.seijikohara.femto.data.OpenMeteoApi
import io.github.seijikohara.femto.data.ReverseGeocoderRepository
import io.github.seijikohara.femto.data.ShortAddress
import io.github.seijikohara.femto.data.SystemStatus
import io.github.seijikohara.femto.data.SystemStatusRepository
import io.github.seijikohara.femto.data.TripRepository
import io.github.seijikohara.femto.data.TripState
import io.github.seijikohara.femto.data.WeatherRepository
import io.github.seijikohara.femto.data.WeatherSnapshot
import io.github.seijikohara.femto.ui.home.components.MusicCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import okhttp3.OkHttpClient
import java.util.Locale

private const val TAG = "HomeViewModel"

internal class HomeViewModel(
    private val clockFlow: Flow<ClockTick>,
    private val locationFlow: Flow<Location?>,
    private val addressFlow: Flow<ShortAddress?>,
    private val weatherFlow: Flow<WeatherSnapshot?>,
    private val musicStateFlow: Flow<MusicCardState>,
    private val calendarFlow: Flow<CalendarSnapshot?>,
    private val systemStatusFlow: Flow<SystemStatus>,
    private val tripStateFlow: Flow<TripState>,
    private val sendMusicCommand: (MusicCommand) -> Unit = {},
    private val resetTrip: () -> Unit = {},
    private val resolveMusicSourceComponent: (String) -> ComponentName? = { null },
) : ViewModel() {
    // Kotlin's typed combine overloads cover at most 5 flows. Stage the eight
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
            clockFlow.catchAsDefault("clock", HomeUiState.Initial.clock),
            locationFlow.catchAsDefault("location", HomeUiState.Initial.location),
            addressFlow.catchAsDefault("address", HomeUiState.Initial.address),
            weatherFlow.catchAsDefault("weather", HomeUiState.Initial.weather),
            musicStateFlow.catchAsDefault("music", HomeUiState.Initial.musicState),
        ) { clock, location, address, weather, music ->
            CoreSignals(clock, location, address, weather, music)
        }

    val uiState: StateFlow<HomeUiState> =
        combine(
            coreSignals,
            calendarFlow.catchAsDefault("calendar", HomeUiState.Initial.calendar),
            systemStatusFlow.catchAsDefault("system status", HomeUiState.Initial.systemStatus),
            tripStateFlow.catchAsDefault("trip state", HomeUiState.Initial.tripState),
        ) { core, calendar, systemStatus, tripState ->
            HomeUiState(
                clock = core.clock,
                location = core.location,
                address = core.address,
                weather = core.weather,
                musicState = core.music,
                calendar = calendar,
                systemStatus = systemStatus,
                tripState = tripState,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Initial)

    // extraBufferCapacity = 1 lets a single tryEmit succeed without a live collector,
    // matching the semantics of a one-shot navigation request that is dropped if the
    // host is already detached (e.g. during configuration change).
    private val mutableEvents = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<HomeEvent> = mutableEvents.asSharedFlow()

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.OpenAppDrawer -> {
                mutableEvents.tryEmit(HomeEvent.OpenDrawer)
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
                // background-only media service) resolves to null and no-ops.
                resolveMusicSourceComponent(action.packageName)
                    ?.let { mutableEvents.tryEmit(HomeEvent.LaunchComponent(it)) }
            }

            is HomeAction.Music -> {
                sendMusicCommand(action.command)
            }

            HomeAction.OpenBrowser -> {
                mutableEvents.tryEmit(HomeEvent.LaunchAppCategory(Intent.CATEGORY_APP_BROWSER))
            }

            HomeAction.OpenSettings -> {
                mutableEvents.tryEmit(HomeEvent.OpenInAppSettings)
            }

            HomeAction.OpenAssistant -> {
                mutableEvents.tryEmit(HomeEvent.OpenAssistantSheet)
            }

            HomeAction.ResetTrip -> {
                resetTrip()
            }
        }
    }
}

// Replace a source failure with that source's neutral value so one broken
// repository degrades its own card instead of killing the launcher process.
// Cancellation is rethrown to keep structured concurrency intact. By design the
// failed source then COMPLETES: its card stays at the neutral value until the
// process restarts. No automatic retry — a broken system service would turn a
// retry loop into a battery drain on the head unit.
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
    val clock: ClockTick,
    val location: Location?,
    val address: ShortAddress?,
    val weather: WeatherSnapshot?,
    val music: MusicCardState,
)

internal class HomeViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        val location = LocationRepository(application)
        val locationFlow = location.locationFlow()
        val clock = ClockRepository(application)
        val clockFlow = clock.tickFlow()
        // Share one OkHttpClient across the weather and geocoding APIs so the
        // connection pool and dispatcher are reused instead of duplicated.
        val httpClient = OkHttpClient()
        // Nominatim blocks stock HTTP User-Agents; identify the launcher with a
        // contact URL per the OSM usage policy.
        val userAgent =
            "FemtoCarLauncher/" + BuildConfig.VERSION_NAME +
                " (+https://github.com/seijikohara/femto-car-launcher)"
        val nominatimApi =
            NominatimApi(
                client = httpClient,
                baseUrl = BuildConfig.GEOCODER_BASE_URL,
                userAgent = userAgent,
                language = Locale.getDefault().language,
                apiKey = BuildConfig.GEOCODER_API_KEY.takeIf { it.isNotBlank() },
            )
        val geocoder = ReverseGeocoderRepository(locationFlow, nominatimApi)
        val weatherApi =
            OpenMeteoApi(
                client = httpClient,
                baseUrl = BuildConfig.WEATHER_BASE_URL,
                apiKey = BuildConfig.WEATHER_API_KEY.takeIf { it.isNotBlank() },
            )
        val weather = WeatherRepository(weatherApi, locationFlow, clockFlow)
        val music = MusicSessionRepository(application)
        val calendar = CalendarRepository(application, clockFlow)
        val systemStatus = SystemStatusRepository(application, locationFlow)
        val trip = TripRepository(locationFlow)
        val apps = AppsRepository(application)

        @Suppress("UNCHECKED_CAST")
        return HomeViewModel(
            clockFlow = clockFlow,
            locationFlow = locationFlow,
            addressFlow = geocoder.addressFlow(),
            weatherFlow = weather.snapshotFlow(),
            musicStateFlow = music.stateFlow(),
            calendarFlow = calendar.snapshotFlow(),
            systemStatusFlow = systemStatus.statusFlow(),
            tripStateFlow = trip.stateFlow(),
            sendMusicCommand = music::send,
            resetTrip = trip::reset,
            resolveMusicSourceComponent = apps::launcherComponentFor,
        ) as T
    }
}

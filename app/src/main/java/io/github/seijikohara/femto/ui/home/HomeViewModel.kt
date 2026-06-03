package io.github.seijikohara.femto.ui.home

import android.app.Application
import android.content.Intent
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.seijikohara.femto.BuildConfig
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import okhttp3.OkHttpClient
import java.util.Locale

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
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> =
        combine(
            clockFlow,
            locationFlow,
            addressFlow,
            weatherFlow,
            musicStateFlow,
            calendarFlow,
            systemStatusFlow,
            tripStateFlow,
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val clock = values[0] as ClockTick

            @Suppress("UNCHECKED_CAST")
            val location = values[1] as Location?

            @Suppress("UNCHECKED_CAST")
            val address = values[2] as ShortAddress?

            @Suppress("UNCHECKED_CAST")
            val weather = values[3] as WeatherSnapshot?

            @Suppress("UNCHECKED_CAST")
            val music = values[4] as MusicCardState

            @Suppress("UNCHECKED_CAST")
            val calendar = values[5] as CalendarSnapshot?

            @Suppress("UNCHECKED_CAST")
            val systemStatus = values[6] as SystemStatus

            @Suppress("UNCHECKED_CAST")
            val tripState = values[7] as TripState
            HomeUiState(
                clock = clock,
                location = location,
                address = address,
                weather = weather,
                musicState = music,
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

            is HomeAction.Music -> {
                sendMusicCommand(action.command)
            }

            HomeAction.OpenBrowser -> {
                mutableEvents.tryEmit(HomeEvent.LaunchAppCategory(Intent.CATEGORY_APP_BROWSER))
            }

            HomeAction.OpenSettings -> {
                mutableEvents.tryEmit(HomeEvent.OpenSystemSettings)
            }
        }
    }
}

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
                userAgent = userAgent,
                language = Locale.getDefault().language,
            )
        val geocoder = ReverseGeocoderRepository(locationFlow, nominatimApi)
        val weatherApi = OpenMeteoApi(client = httpClient)
        val weather = WeatherRepository(weatherApi, locationFlow)
        val music = MusicSessionRepository(application)
        val calendar = CalendarRepository(application, clockFlow)
        val systemStatus = SystemStatusRepository(application)
        val trip = TripRepository(locationFlow)

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
        ) as T
    }
}

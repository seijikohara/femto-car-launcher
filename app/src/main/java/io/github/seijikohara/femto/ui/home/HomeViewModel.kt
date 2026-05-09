package io.github.seijikohara.femto.ui.home

import android.app.Application
import android.content.Intent
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.seijikohara.femto.data.AppEntry
import io.github.seijikohara.femto.data.AppsRepository
import io.github.seijikohara.femto.data.ClockRepository
import io.github.seijikohara.femto.data.ClockTick
import io.github.seijikohara.femto.data.GmsAvailability
import io.github.seijikohara.femto.data.LocationRepository
import io.github.seijikohara.femto.data.MusicCardState
import io.github.seijikohara.femto.data.MusicSessionRepository
import io.github.seijikohara.femto.data.OpenMeteoApi
import io.github.seijikohara.femto.data.ReverseGeocoderRepository
import io.github.seijikohara.femto.data.ShortAddress
import io.github.seijikohara.femto.data.WeatherRepository
import io.github.seijikohara.femto.data.WeatherSnapshot
import io.github.seijikohara.femto.ui.home.components.MusicCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

internal class HomeViewModel(
    private val clockFlow: Flow<ClockTick>,
    private val locationFlow: Flow<Location?>,
    private val addressFlow: Flow<ShortAddress?>,
    private val weatherFlow: Flow<WeatherSnapshot?>,
    private val musicStateFlow: Flow<MusicCardState>,
    private val appsFlow: MutableStateFlow<List<AppEntry>>,
    private val isMapAvailable: () -> Boolean,
    private val sendMusicCommand: (MusicCommand) -> Unit = {},
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> =
        combine(
            clockFlow,
            locationFlow,
            addressFlow,
            weatherFlow,
            musicStateFlow,
            appsFlow,
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
            val apps = values[5] as List<AppEntry>
            HomeUiState(
                isLoading = apps.isEmpty(),
                apps = apps,
                clock = clock,
                location = location,
                address = address,
                weather = weather,
                musicState = music,
                mapAvailable = isMapAvailable(),
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
                mutableEvents.tryEmit(HomeEvent.LaunchAppCategory(Intent.CATEGORY_APP_MAPS))
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
        val geocoder = ReverseGeocoderRepository(application, locationFlow)
        val weatherApi = OpenMeteoApi(client = OkHttpClient())
        val weather = WeatherRepository(weatherApi, locationFlow)
        val music = MusicSessionRepository(application)
        val gms = GmsAvailability(application)
        val apps = MutableStateFlow<List<AppEntry>>(emptyList())
        val appsRepo = AppsRepository(application)

        @Suppress("UNCHECKED_CAST")
        return HomeViewModel(
            clockFlow = clock.tickFlow(),
            locationFlow = locationFlow,
            addressFlow = geocoder.addressFlow(),
            weatherFlow = weather.snapshotFlow(),
            musicStateFlow = music.stateFlow(),
            appsFlow = apps,
            isMapAvailable = { gms.isPresent() },
            sendMusicCommand = music::send,
        ).also { vm ->
            vm.viewModelScope.launch { apps.value = appsRepo.queryApps() }
        } as T
    }
}

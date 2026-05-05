package io.github.seijikohara.femto.ui.home

import android.app.Application
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
import io.github.seijikohara.femto.data.MusicSessionRepository
import io.github.seijikohara.femto.data.NowPlaying
import io.github.seijikohara.femto.data.OpenMeteoApi
import io.github.seijikohara.femto.data.ReverseGeocoderRepository
import io.github.seijikohara.femto.data.ShortAddress
import io.github.seijikohara.femto.data.WeatherRepository
import io.github.seijikohara.femto.data.WeatherSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

internal class HomeViewModel(
    private val clockFlow: Flow<ClockTick>,
    private val locationFlow: Flow<Location?>,
    private val addressFlow: Flow<ShortAddress?>,
    private val weatherFlow: Flow<WeatherSnapshot?>,
    private val nowPlayingFlow: Flow<NowPlaying?>,
    private val appsFlow: MutableStateFlow<List<AppEntry>>,
    private val isMapAvailable: () -> Boolean,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> =
        combine(
            clockFlow,
            locationFlow,
            addressFlow,
            weatherFlow,
            nowPlayingFlow,
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
            val music = values[4] as NowPlaying?

            @Suppress("UNCHECKED_CAST")
            val apps = values[5] as List<AppEntry>
            HomeUiState(
                isLoading = apps.isEmpty(),
                apps = apps,
                clock = clock,
                location = location,
                address = address,
                weather = weather,
                nowPlaying = music,
                mapAvailable = isMapAvailable(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Initial)

    fun onAction(action: HomeAction) {
        // Side-effect routing is wired by the host (MainActivity / HomeRoute) in Task 4.5+.
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
            nowPlayingFlow = music.nowPlayingFlow(),
            appsFlow = apps,
            isMapAvailable = { gms.isPresent() },
        ).also { vm ->
            vm.viewModelScope.launch { apps.value = appsRepo.queryApps() }
        } as T
    }
}

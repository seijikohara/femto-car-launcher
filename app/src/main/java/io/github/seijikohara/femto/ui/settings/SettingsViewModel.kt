package io.github.seijikohara.femto.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.seijikohara.femto.data.DisplayPreferences
import io.github.seijikohara.femto.data.DisplaySettingsStore
import io.github.seijikohara.femto.data.FontPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class SettingsViewModel(
    private val displayPreferences: DisplaySettingsStore,
    private val fontPreferences: FontPreferences,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> =
        combine(displayPreferences.settings, fontPreferences.fontTheme) { display, font ->
            SettingsUiState(
                themeMode = display.themeMode,
                speedUnit = display.speedUnit,
                temperatureUnit = display.temperatureUnit,
                clock = display.clock,
                fullscreen = display.fullscreen,
                mapFps = display.mapFps,
                mapBuildings3d = display.mapBuildings3d,
                mapStyle = display.mapStyle,
                mapTiltDeg = display.mapTiltDeg,
                mapZoom = display.mapZoom,
                mapRenderPercent = display.mapRenderPercent,
                showCalendar = display.showCalendar,
                showWeather = display.showWeather,
                showMusic = display.showMusic,
                fontTheme = font,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Initial)

    fun onAction(action: SettingsAction) {
        // Each branch is a single suspending write; launch once and dispatch.
        viewModelScope.launch {
            when (action) {
                is SettingsAction.SetThemeMode -> displayPreferences.setThemeMode(action.value)
                is SettingsAction.SetSpeedUnit -> displayPreferences.setSpeedUnit(action.value)
                is SettingsAction.SetTemperatureUnit -> displayPreferences.setTemperatureUnit(action.value)
                is SettingsAction.SetClock -> displayPreferences.setClock(action.value)
                is SettingsAction.SetFullscreen -> displayPreferences.setFullscreen(action.value)
                is SettingsAction.SetMapFps -> displayPreferences.setMapFps(action.value)
                is SettingsAction.SetMapBuildings3d -> displayPreferences.setMapBuildings3d(action.value)
                is SettingsAction.SetMapStyle -> displayPreferences.setMapStyle(action.value)
                is SettingsAction.SetMapTilt -> displayPreferences.setMapTilt(action.value)
                is SettingsAction.SetMapZoom -> displayPreferences.setMapZoom(action.value)
                is SettingsAction.SetMapRenderPercent -> displayPreferences.setMapRenderPercent(action.value)
                is SettingsAction.SetShowCalendar -> displayPreferences.setShowCalendar(action.value)
                is SettingsAction.SetShowWeather -> displayPreferences.setShowWeather(action.value)
                is SettingsAction.SetShowMusic -> displayPreferences.setShowMusic(action.value)
                is SettingsAction.SetFontTheme -> fontPreferences.setFontTheme(action.value)
            }
        }
    }
}

internal class SettingsViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        @Suppress("UNCHECKED_CAST")
        return SettingsViewModel(
            displayPreferences = DisplayPreferences(application),
            fontPreferences = FontPreferences(application),
        ) as T
    }
}

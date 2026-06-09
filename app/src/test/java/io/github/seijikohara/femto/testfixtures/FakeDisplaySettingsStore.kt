package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.AccentColor
import io.github.seijikohara.femto.data.ClockSetting
import io.github.seijikohara.femto.data.DisplaySettings
import io.github.seijikohara.femto.data.DisplaySettingsStore
import io.github.seijikohara.femto.data.FullscreenSetting
import io.github.seijikohara.femto.data.MapRenderMode
import io.github.seijikohara.femto.data.MapStyleSetting
import io.github.seijikohara.femto.data.SpeedUnitSetting
import io.github.seijikohara.femto.data.TemperatureUnitSetting
import io.github.seijikohara.femto.data.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [DisplaySettingsStore] for view-model tests: every setter mutates a
 * [MutableStateFlow] synchronously, so a test sees the write with no DataStore IO
 * and no cross-dispatcher timing. Defaults match [DisplaySettings.Default].
 */
internal class FakeDisplaySettingsStore(
    initial: DisplaySettings = DisplaySettings.Default,
) : DisplaySettingsStore {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<DisplaySettings> = state

    override suspend fun setThemeMode(value: ThemeMode) = state.update { it.copy(themeMode = value) }

    override suspend fun setAccentColor(value: AccentColor) = state.update { it.copy(accentColor = value) }

    override suspend fun setSpeedUnit(value: SpeedUnitSetting) = state.update { it.copy(speedUnit = value) }

    override suspend fun setTemperatureUnit(value: TemperatureUnitSetting) =
        state.update {
            it.copy(temperatureUnit = value)
        }

    override suspend fun setClock(value: ClockSetting) = state.update { it.copy(clock = value) }

    override suspend fun setShowClockSeconds(value: Boolean) = state.update { it.copy(showClockSeconds = value) }

    override suspend fun setFullscreen(value: FullscreenSetting) = state.update { it.copy(fullscreen = value) }

    override suspend fun setMapStyle(value: MapStyleSetting) = state.update { it.copy(mapStyle = value) }

    override suspend fun setMapTilt(value: Int) = state.update { it.copy(mapTiltDeg = value) }

    override suspend fun setMapZoom(value: Int) = state.update { it.copy(mapZoom = value) }

    override suspend fun setMapRenderPercent(value: Int) = state.update { it.copy(mapRenderPercent = value) }

    override suspend fun setMapRenderMode(value: MapRenderMode) = state.update { it.copy(mapRenderMode = value) }

    override suspend fun setMapLookAhead(value: Int) = state.update { it.copy(mapLookAheadM = value) }

    override suspend fun setMap3dBuildings(value: Boolean) = state.update { it.copy(map3dBuildings = value) }

    override suspend fun setMapTerrain(value: Boolean) = state.update { it.copy(mapTerrain = value) }

    override suspend fun setShowCalendar(value: Boolean) = state.update { it.copy(showCalendar = value) }

    override suspend fun setShowWeather(value: Boolean) = state.update { it.copy(showWeather = value) }

    override suspend fun setShowMusic(value: Boolean) = state.update { it.copy(showMusic = value) }
}

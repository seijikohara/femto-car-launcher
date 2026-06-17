package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.display.AccentColor
import io.github.seijikohara.femto.data.display.AssistantLaunchSetting
import io.github.seijikohara.femto.data.display.ClockSetting
import io.github.seijikohara.femto.data.display.DisplaySettings
import io.github.seijikohara.femto.data.display.DisplaySettingsStore
import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.display.FullscreenSetting
import io.github.seijikohara.femto.data.display.MAX_MAP_ZOOM
import io.github.seijikohara.femto.data.display.MIN_MAP_ZOOM
import io.github.seijikohara.femto.data.display.MapColorScheme
import io.github.seijikohara.femto.data.display.MapRenderMode
import io.github.seijikohara.femto.data.display.MapStyleSetting
import io.github.seijikohara.femto.data.display.OrientationSetting
import io.github.seijikohara.femto.data.display.SpeedUnitSetting
import io.github.seijikohara.femto.data.display.TemperatureUnitSetting
import io.github.seijikohara.femto.data.display.ThemeMode
import io.github.seijikohara.femto.data.display.ThemePreset
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

    override suspend fun applyThemePreset(preset: ThemePreset) =
        state.update {
            it.copy(
                accentColor = preset.accentColor,
                mapSchemeLight = preset.mapSchemeLight,
                mapSchemeDark = preset.mapSchemeDark,
            )
        }

    override suspend fun setSpeedUnit(value: SpeedUnitSetting) = state.update { it.copy(speedUnit = value) }

    override suspend fun setTemperatureUnit(value: TemperatureUnitSetting) =
        state.update {
            it.copy(temperatureUnit = value)
        }

    override suspend fun setClock(value: ClockSetting) = state.update { it.copy(clock = value) }

    override suspend fun setShowClockSeconds(value: Boolean) = state.update { it.copy(showClockSeconds = value) }

    override suspend fun setFullscreen(value: FullscreenSetting) = state.update { it.copy(fullscreen = value) }

    override suspend fun setDockPosition(value: DockPosition) = state.update { it.copy(dockPosition = value) }

    override suspend fun setOrientation(value: OrientationSetting) = state.update { it.copy(orientation = value) }

    override suspend fun setKeepScreenOn(value: Boolean) = state.update { it.copy(keepScreenOn = value) }

    override suspend fun setAssistantLaunch(value: AssistantLaunchSetting) =
        state.update {
            it.copy(assistantLaunch = value)
        }

    override suspend fun setMapStyle(value: MapStyleSetting) = state.update { it.copy(mapStyle = value) }

    override suspend fun setMapSchemeLight(value: MapColorScheme) = state.update { it.copy(mapSchemeLight = value) }

    override suspend fun setMapSchemeDark(value: MapColorScheme) = state.update { it.copy(mapSchemeDark = value) }

    override suspend fun setMapTilt(value: Int) = state.update { it.copy(mapTiltDeg = value) }

    override suspend fun setMapZoom(value: Int) = state.update { it.copy(mapZoom = value) }

    override suspend fun adjustMapZoom(delta: Int) =
        state.update {
            it.copy(mapZoom = (it.mapZoom + delta).coerceIn(MIN_MAP_ZOOM, MAX_MAP_ZOOM))
        }

    override suspend fun setMapNorthUp(value: Boolean) = state.update { it.copy(mapNorthUp = value) }

    override suspend fun toggleMapNorthUp() = state.update { it.copy(mapNorthUp = !it.mapNorthUp) }

    override suspend fun setMapRenderPercent(value: Int) = state.update { it.copy(mapRenderPercent = value) }

    override suspend fun setMapRenderMode(value: MapRenderMode) = state.update { it.copy(mapRenderMode = value) }

    override suspend fun setMapMarkerPos(value: Int) = state.update { it.copy(mapMarkerPos = value) }

    override suspend fun setMap3dBuildings(value: Boolean) = state.update { it.copy(map3dBuildings = value) }

    override suspend fun setMapTerrain(value: Boolean) = state.update { it.copy(mapTerrain = value) }

    override suspend fun setGlassBlurRadius(value: Int) = state.update { it.copy(glassBlurRadius = value) }

    override suspend fun setGlassTintScale(value: Int) = state.update { it.copy(glassTintScale = value) }

    override suspend fun setShowCalendar(value: Boolean) = state.update { it.copy(showCalendar = value) }

    override suspend fun setShowWeather(value: Boolean) = state.update { it.copy(showWeather = value) }

    override suspend fun setShowMusic(value: Boolean) = state.update { it.copy(showMusic = value) }

    override suspend fun setMusicSpectrum(value: Boolean) = state.update { it.copy(musicSpectrum = value) }

    override suspend fun resetToDefaults() = state.update { DisplaySettings.Default }
}

package io.github.seijikohara.femto.ui.settings

import io.github.seijikohara.femto.data.AccentColor
import io.github.seijikohara.femto.data.ClockSetting
import io.github.seijikohara.femto.data.DisplaySettings
import io.github.seijikohara.femto.data.FullscreenSetting
import io.github.seijikohara.femto.data.LocationQualitySetting
import io.github.seijikohara.femto.data.LocationSettings
import io.github.seijikohara.femto.data.MapColorScheme
import io.github.seijikohara.femto.data.MapRenderMode
import io.github.seijikohara.femto.data.MapStyleSetting
import io.github.seijikohara.femto.data.SpeedUnitSetting
import io.github.seijikohara.femto.data.TemperatureUnitSetting
import io.github.seijikohara.femto.data.ThemeMode

/** State for the in-app settings screen: the persisted display + font choices. */
internal data class SettingsUiState(
    val themeMode: ThemeMode,
    val accentColor: AccentColor,
    val speedUnit: SpeedUnitSetting,
    val temperatureUnit: TemperatureUnitSetting,
    val clock: ClockSetting,
    val showClockSeconds: Boolean,
    val fullscreen: FullscreenSetting,
    val keepScreenOn: Boolean,
    val mapStyle: MapStyleSetting,
    val mapSchemeLight: MapColorScheme,
    val mapSchemeDark: MapColorScheme,
    val mapTiltDeg: Int,
    val mapZoom: Int,
    val mapRenderPercent: Int,
    val mapRenderMode: MapRenderMode,
    val mapMarkerPos: Int,
    val map3dBuildings: Boolean,
    val mapTerrain: Boolean,
    val glassBlurRadius: Int,
    val glassTintScale: Int,
    val showCalendar: Boolean,
    val showWeather: Boolean,
    val showMusic: Boolean,
    // The chosen Google Fonts families per slot; null means the system font.
    val latinFont: String?,
    val cjkFont: String?,
    val locationQuality: LocationQualitySetting,
    val locationIntervalMillis: Long,
    val locationMinDistanceMeters: Int,
) {
    companion object {
        // Seeded from the persistence defaults so the default values live in one
        // place (DisplaySettings.Default + the system-font default for both slots).
        val Initial =
            SettingsUiState(
                themeMode = DisplaySettings.Default.themeMode,
                accentColor = DisplaySettings.Default.accentColor,
                speedUnit = DisplaySettings.Default.speedUnit,
                temperatureUnit = DisplaySettings.Default.temperatureUnit,
                clock = DisplaySettings.Default.clock,
                showClockSeconds = DisplaySettings.Default.showClockSeconds,
                fullscreen = DisplaySettings.Default.fullscreen,
                keepScreenOn = DisplaySettings.Default.keepScreenOn,
                mapStyle = DisplaySettings.Default.mapStyle,
                mapSchemeLight = DisplaySettings.Default.mapSchemeLight,
                mapSchemeDark = DisplaySettings.Default.mapSchemeDark,
                mapTiltDeg = DisplaySettings.Default.mapTiltDeg,
                mapZoom = DisplaySettings.Default.mapZoom,
                mapRenderPercent = DisplaySettings.Default.mapRenderPercent,
                mapRenderMode = DisplaySettings.Default.mapRenderMode,
                mapMarkerPos = DisplaySettings.Default.mapMarkerPos,
                map3dBuildings = DisplaySettings.Default.map3dBuildings,
                mapTerrain = DisplaySettings.Default.mapTerrain,
                glassBlurRadius = DisplaySettings.Default.glassBlurRadius,
                glassTintScale = DisplaySettings.Default.glassTintScale,
                showCalendar = DisplaySettings.Default.showCalendar,
                showWeather = DisplaySettings.Default.showWeather,
                showMusic = DisplaySettings.Default.showMusic,
                latinFont = null,
                cjkFont = null,
                locationQuality = LocationSettings.Default.quality,
                locationIntervalMillis = LocationSettings.Default.intervalMillis,
                locationMinDistanceMeters = LocationSettings.Default.minUpdateDistanceMeters,
            )
    }
}

/** Persisted-preference changes the settings screen reports up. */
internal sealed interface SettingsAction {
    data class SetThemeMode(
        val value: ThemeMode,
    ) : SettingsAction

    data class SetAccentColor(
        val value: AccentColor,
    ) : SettingsAction

    data class SetSpeedUnit(
        val value: SpeedUnitSetting,
    ) : SettingsAction

    data class SetTemperatureUnit(
        val value: TemperatureUnitSetting,
    ) : SettingsAction

    data class SetClock(
        val value: ClockSetting,
    ) : SettingsAction

    data class SetShowClockSeconds(
        val value: Boolean,
    ) : SettingsAction

    data class SetFullscreen(
        val value: FullscreenSetting,
    ) : SettingsAction

    data class SetKeepScreenOn(
        val value: Boolean,
    ) : SettingsAction

    data class SetMapStyle(
        val value: MapStyleSetting,
    ) : SettingsAction

    data class SetMapSchemeLight(
        val value: MapColorScheme,
    ) : SettingsAction

    data class SetMapSchemeDark(
        val value: MapColorScheme,
    ) : SettingsAction

    data class SetMapTilt(
        val value: Int,
    ) : SettingsAction

    data class SetMapZoom(
        val value: Int,
    ) : SettingsAction

    data class SetMapRenderPercent(
        val value: Int,
    ) : SettingsAction

    data class SetMapRenderMode(
        val value: MapRenderMode,
    ) : SettingsAction

    data class SetMapMarkerPos(
        val value: Int,
    ) : SettingsAction

    data class SetMap3dBuildings(
        val value: Boolean,
    ) : SettingsAction

    data class SetMapTerrain(
        val value: Boolean,
    ) : SettingsAction

    data class SetGlassBlurRadius(
        val value: Int,
    ) : SettingsAction

    data class SetGlassTintScale(
        val value: Int,
    ) : SettingsAction

    data class SetShowCalendar(
        val value: Boolean,
    ) : SettingsAction

    data class SetShowWeather(
        val value: Boolean,
    ) : SettingsAction

    data class SetShowMusic(
        val value: Boolean,
    ) : SettingsAction

    data class SetLocationQuality(
        val value: LocationQualitySetting,
    ) : SettingsAction

    data class SetLocationIntervalMillis(
        val value: Long,
    ) : SettingsAction

    data class SetLocationMinDistance(
        val value: Int,
    ) : SettingsAction

    /** Restore every display + font + location setting to its default value. */
    data object ResetToDefaults : SettingsAction
}

package io.github.seijikohara.femto.ui.settings

import io.github.seijikohara.femto.data.calendar.CalendarInfo
import io.github.seijikohara.femto.data.display.AccentColor
import io.github.seijikohara.femto.data.display.AssistantLaunchSetting
import io.github.seijikohara.femto.data.display.ClockSetting
import io.github.seijikohara.femto.data.display.DisplaySettings
import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.display.DriverSide
import io.github.seijikohara.femto.data.display.FullscreenSetting
import io.github.seijikohara.femto.data.display.GoogleMapType
import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.data.display.MapColorScheme
import io.github.seijikohara.femto.data.display.MapStyleSetting
import io.github.seijikohara.femto.data.display.MapboxStyle
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.data.display.OrientationSetting
import io.github.seijikohara.femto.data.display.SettingsSectionId
import io.github.seijikohara.femto.data.display.SpeedUnitSetting
import io.github.seijikohara.femto.data.display.TemperatureUnitSetting
import io.github.seijikohara.femto.data.display.ThemeMode
import io.github.seijikohara.femto.data.display.UiScale
import io.github.seijikohara.femto.data.location.LocationQualitySetting
import io.github.seijikohara.femto.data.location.LocationSettings

/** State for the in-app settings screen: the persisted display + font choices. */
internal data class SettingsUiState(
    val themeMode: ThemeMode,
    val accentColor: AccentColor,
    val uiScale: UiScale,
    val speedUnit: SpeedUnitSetting,
    val temperatureUnit: TemperatureUnitSetting,
    val clock: ClockSetting,
    val showClockSeconds: Boolean,
    val fullscreen: FullscreenSetting,
    val dockPosition: DockPosition,
    val driverSide: DriverSide,
    val motionTier: MotionTier,
    val orientation: OrientationSetting,
    val keepScreenOn: Boolean,
    val assistantLaunch: AssistantLaunchSetting,
    val mapStyle: MapStyleSetting,
    val mapSchemeLight: MapColorScheme,
    val mapSchemeDark: MapColorScheme,
    val mapTiltDeg: Int,
    val mapZoom: Int,
    val mapNorthUp: Boolean,
    val mapMarkerPos: Int,
    val map3dBuildings: Boolean,
    val mapTerrain: Boolean,
    val glassBlurRadius: Int,
    val glassTintScale: Int,
    val fontBaseSizeSp: Int,
    val fontWeightStep: Int,
    val fontLetterSpacingCentiEm: Int,
    val showCalendar: Boolean,
    val showWeather: Boolean,
    val showMusic: Boolean,
    val musicSpectrum: Boolean,
    val musicShowAlbum: Boolean,
    val musicShowArt: Boolean,
    // The chosen Google Fonts families per slot; null means the system font.
    val latinFont: String?,
    val cjkFont: String?,
    val locationQuality: LocationQualitySetting,
    val locationIntervalMillis: Long,
    val locationMinDistanceMeters: Int,
    val backgroundRangingEnabled: Boolean,
    val mapBackend: MapBackend = DisplaySettings.Default.mapBackend,
    val mapboxStyle: MapboxStyle = DisplaySettings.Default.mapboxStyle,
    val mapboxTraffic: Boolean = DisplaySettings.Default.mapboxTraffic,
    val mapboxAccessToken: String = "",
    val googleMapsApiKey: String = "",
    val googleMapsMapId: String = "",
    val googleMapsMapType: GoogleMapType = DisplaySettings.Default.googleMapsMapType,
    val googleMapsTraffic: Boolean = DisplaySettings.Default.googleMapsTraffic,
    val availableCalendars: List<CalendarInfo> = emptyList(),
    val hiddenCalendarIds: Set<Long> = emptySet(),
    // Defaults false so a not-yet-loaded selector never falsely claims "no
    // calendars found" (hiding the grant affordance) when access is actually
    // denied; CalendarCatalog emits the real value on subscription.
    val hasCalendarAccess: Boolean = false,
) {
    companion object {
        // Seeded from the persistence defaults so the default values live in one
        // place (DisplaySettings.Default + the system-font default for both slots).
        val Initial =
            SettingsUiState(
                themeMode = DisplaySettings.Default.themeMode,
                accentColor = DisplaySettings.Default.accentColor,
                uiScale = DisplaySettings.Default.uiScale,
                speedUnit = DisplaySettings.Default.speedUnit,
                temperatureUnit = DisplaySettings.Default.temperatureUnit,
                clock = DisplaySettings.Default.clock,
                showClockSeconds = DisplaySettings.Default.showClockSeconds,
                fullscreen = DisplaySettings.Default.fullscreen,
                dockPosition = DisplaySettings.Default.dockPosition,
                driverSide = DisplaySettings.Default.driverSide,
                motionTier = DisplaySettings.Default.motionTier,
                orientation = DisplaySettings.Default.orientation,
                keepScreenOn = DisplaySettings.Default.keepScreenOn,
                assistantLaunch = DisplaySettings.Default.assistantLaunch,
                mapStyle = DisplaySettings.Default.mapStyle,
                mapSchemeLight = DisplaySettings.Default.mapSchemeLight,
                mapSchemeDark = DisplaySettings.Default.mapSchemeDark,
                mapTiltDeg = DisplaySettings.Default.mapTiltDeg,
                mapZoom = DisplaySettings.Default.mapZoom,
                mapNorthUp = DisplaySettings.Default.mapNorthUp,
                mapMarkerPos = DisplaySettings.Default.mapMarkerPos,
                map3dBuildings = DisplaySettings.Default.map3dBuildings,
                mapTerrain = DisplaySettings.Default.mapTerrain,
                glassBlurRadius = DisplaySettings.Default.glassBlurRadius,
                glassTintScale = DisplaySettings.Default.glassTintScale,
                fontBaseSizeSp = DisplaySettings.Default.fontBaseSizeSp,
                fontWeightStep = DisplaySettings.Default.fontWeightStep,
                fontLetterSpacingCentiEm = DisplaySettings.Default.fontLetterSpacingCentiEm,
                showCalendar = DisplaySettings.Default.showCalendar,
                showWeather = DisplaySettings.Default.showWeather,
                showMusic = DisplaySettings.Default.showMusic,
                musicSpectrum = DisplaySettings.Default.musicSpectrum,
                musicShowAlbum = DisplaySettings.Default.musicShowAlbum,
                musicShowArt = DisplaySettings.Default.musicShowArt,
                latinFont = null,
                cjkFont = null,
                locationQuality = LocationSettings.Default.quality,
                locationIntervalMillis = LocationSettings.Default.intervalMillis,
                locationMinDistanceMeters = LocationSettings.Default.minUpdateDistanceMeters,
                backgroundRangingEnabled = LocationSettings.Default.backgroundRangingEnabled,
                mapBackend = DisplaySettings.Default.mapBackend,
                mapboxStyle = DisplaySettings.Default.mapboxStyle,
                mapboxTraffic = DisplaySettings.Default.mapboxTraffic,
                mapboxAccessToken = DisplaySettings.Default.mapboxAccessToken,
                googleMapsApiKey = DisplaySettings.Default.googleMapsApiKey,
                googleMapsMapId = DisplaySettings.Default.googleMapsMapId,
                googleMapsMapType = DisplaySettings.Default.googleMapsMapType,
                googleMapsTraffic = DisplaySettings.Default.googleMapsTraffic,
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

    data class SetUiScale(
        val value: UiScale,
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

    data class SetDockPosition(
        val value: DockPosition,
    ) : SettingsAction

    data class SetDriverSide(
        val value: DriverSide,
    ) : SettingsAction

    data class SetMotionTier(
        val value: MotionTier,
    ) : SettingsAction

    data class SetOrientation(
        val value: OrientationSetting,
    ) : SettingsAction

    data class SetKeepScreenOn(
        val value: Boolean,
    ) : SettingsAction

    data class SetAssistantLaunch(
        val value: AssistantLaunchSetting,
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

    data class SetMapNorthUp(
        val value: Boolean,
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

    data class SetFontBaseSizeSp(
        val value: Int,
    ) : SettingsAction

    data class SetFontWeightStep(
        val value: Int,
    ) : SettingsAction

    data class SetFontLetterSpacingCentiEm(
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

    data class SetMusicSpectrum(
        val value: Boolean,
    ) : SettingsAction

    data class SetMusicShowAlbum(
        val value: Boolean,
    ) : SettingsAction

    data class SetMusicShowArt(
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

    data class SetBackgroundRanging(
        val value: Boolean,
    ) : SettingsAction

    data class SetMapBackend(
        val value: MapBackend,
    ) : SettingsAction

    data class SetMapboxStyle(
        val value: MapboxStyle,
    ) : SettingsAction

    data class SetMapboxTraffic(
        val value: Boolean,
    ) : SettingsAction

    // Persist the token AND select the Mapbox backend atomically (one coroutine)
    // so the gate never briefly sees backend=MAPBOX with a blank token.
    data class SaveMapboxToken(
        val value: String,
    ) : SettingsAction

    data object ClearMapboxToken : SettingsAction

    // Persist the key AND select the Google Maps backend atomically (one coroutine)
    // so the gate never briefly sees backend=GOOGLEMAPS with a blank key.
    data class SaveGoogleMapsKey(
        val value: String,
    ) : SettingsAction

    data object ClearGoogleMapsKey : SettingsAction

    // Map ID is optional and does not switch the backend (a key already did
    // that); a non-empty value upgrades the map to a vector style. Blank is
    // valid, so this is a plain set — not the atomic key/backend pair above.
    data class SetGoogleMapsMapId(
        val value: String,
    ) : SettingsAction

    data object ClearGoogleMapsMapId : SettingsAction

    data class SetGoogleMapsMapType(
        val value: GoogleMapType,
    ) : SettingsAction

    data class SetGoogleMapsTraffic(
        val value: Boolean,
    ) : SettingsAction

    data class SetCalendarHidden(
        val id: Long,
        val hidden: Boolean,
    ) : SettingsAction

    /** Restore every display + font + location + calendar setting to its default value. */
    data object ResetToDefaults : SettingsAction

    /** Restore only [sectionId]'s own settings (and any store it owns) to their default value. */
    data class ResetSection(
        val sectionId: SettingsSectionId,
    ) : SettingsAction

    /**
     * Restore the dock's nav/status order and hidden sets to their defaults.
     * A standalone action rather than folded into [ResetSection]: the dock
     * lives in its own DockPreferences store, not DisplayPreferences, so no
     * [SettingsSectionId] owns it (mirrors the calendar hidden-ID set, which
     * IS folded into PANELS' section reset only because that store already
     * has a section home; the dock's Screen-category placement is a plain
     * settings row, not a whole-category reset).
     */
    data object ResetDock : SettingsAction
}

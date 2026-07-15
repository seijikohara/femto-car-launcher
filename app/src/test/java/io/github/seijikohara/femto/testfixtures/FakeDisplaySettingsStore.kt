package io.github.seijikohara.femto.testfixtures

import androidx.datastore.preferences.core.Preferences
import io.github.seijikohara.femto.data.display.AccentColor
import io.github.seijikohara.femto.data.display.AssistantLaunchSetting
import io.github.seijikohara.femto.data.display.ClockSetting
import io.github.seijikohara.femto.data.display.DisplayPreferences
import io.github.seijikohara.femto.data.display.DisplaySettings
import io.github.seijikohara.femto.data.display.DisplaySettingsStore
import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.display.DriverSide
import io.github.seijikohara.femto.data.display.FullscreenSetting
import io.github.seijikohara.femto.data.display.GoogleMapType
import io.github.seijikohara.femto.data.display.MAX_MAP_ZOOM
import io.github.seijikohara.femto.data.display.MIN_MAP_ZOOM
import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.data.display.MapColorScheme
import io.github.seijikohara.femto.data.display.MapStyleSetting
import io.github.seijikohara.femto.data.display.MapboxStyle
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.data.display.OrientationSetting
import io.github.seijikohara.femto.data.display.SpeedUnitSetting
import io.github.seijikohara.femto.data.display.TemperatureUnitSetting
import io.github.seijikohara.femto.data.display.ThemeMode
import io.github.seijikohara.femto.data.display.UiScale
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

    override suspend fun setUiScale(value: UiScale) = state.update { it.copy(uiScale = value) }

    override suspend fun setSpeedUnit(value: SpeedUnitSetting) = state.update { it.copy(speedUnit = value) }

    override suspend fun setTemperatureUnit(value: TemperatureUnitSetting) =
        state.update {
            it.copy(temperatureUnit = value)
        }

    override suspend fun setClock(value: ClockSetting) = state.update { it.copy(clock = value) }

    override suspend fun setShowClockSeconds(value: Boolean) = state.update { it.copy(showClockSeconds = value) }

    override suspend fun setFullscreen(value: FullscreenSetting) = state.update { it.copy(fullscreen = value) }

    override suspend fun setDockPosition(value: DockPosition) = state.update { it.copy(dockPosition = value) }

    override suspend fun setDriverSide(value: DriverSide) = state.update { it.copy(driverSide = value) }

    override suspend fun setMotionTier(value: MotionTier) = state.update { it.copy(motionTier = value) }

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

    override suspend fun setMapMarkerPos(value: Int) = state.update { it.copy(mapMarkerPos = value) }

    override suspend fun setMap3dBuildings(value: Boolean) = state.update { it.copy(map3dBuildings = value) }

    override suspend fun setMapTerrain(value: Boolean) = state.update { it.copy(mapTerrain = value) }

    override suspend fun setGlassBlurRadius(value: Int) = state.update { it.copy(glassBlurRadius = value) }

    override suspend fun setGlassTintScale(value: Int) = state.update { it.copy(glassTintScale = value) }

    override suspend fun setGlassShowBorder(value: Boolean) = state.update { it.copy(glassShowBorder = value) }

    override suspend fun setGlassShadowEnabled(value: Boolean) = state.update { it.copy(glassShadowEnabled = value) }

    override suspend fun setGlassShadowIntensity(value: Int) = state.update { it.copy(glassShadowIntensity = value) }

    override suspend fun setGlassShadowSizeDp(value: Int) = state.update { it.copy(glassShadowSizeDp = value) }

    override suspend fun setFontBaseSizeSp(value: Int) = state.update { it.copy(fontBaseSizeSp = value) }

    override suspend fun setFontWeightStep(value: Int) = state.update { it.copy(fontWeightStep = value) }

    override suspend fun setFontLetterSpacingCentiEm(value: Int) =
        state.update { it.copy(fontLetterSpacingCentiEm = value) }

    override suspend fun setShowCalendar(value: Boolean) = state.update { it.copy(showCalendar = value) }

    override suspend fun setShowWeather(value: Boolean) = state.update { it.copy(showWeather = value) }

    override suspend fun setShowMusic(value: Boolean) = state.update { it.copy(showMusic = value) }

    override suspend fun setMusicSpectrum(value: Boolean) = state.update { it.copy(musicSpectrum = value) }

    override suspend fun setMusicShowAlbum(value: Boolean) = state.update { it.copy(musicShowAlbum = value) }

    override suspend fun setMusicShowArt(value: Boolean) = state.update { it.copy(musicShowArt = value) }

    override suspend fun setMapBackend(value: MapBackend) = state.update { it.copy(mapBackend = value) }

    override suspend fun setMapboxStyle(value: MapboxStyle) = state.update { it.copy(mapboxStyle = value) }

    override suspend fun setMapboxTraffic(value: Boolean) = state.update { it.copy(mapboxTraffic = value) }

    override suspend fun setMapboxAccessToken(value: String) = state.update { it.copy(mapboxAccessToken = value) }

    override suspend fun setGoogleMapsApiKey(value: String) = state.update { it.copy(googleMapsApiKey = value) }

    override suspend fun setGoogleMapsMapId(value: String) = state.update { it.copy(googleMapsMapId = value) }

    override suspend fun setGoogleMapsMapType(value: GoogleMapType) =
        state.update { it.copy(googleMapsMapType = value) }

    override suspend fun setGoogleMapsTraffic(value: Boolean) = state.update { it.copy(googleMapsTraffic = value) }

    // Mirrors DisplayPreferences.resetKeys field-by-field: the real store simply
    // removes the DataStore key and lets its per-field read fallback (kept
    // identical to DisplaySettings.Default) do the work, but this in-memory fake
    // holds a fully-resolved DisplaySettings with no notion of an absent key, so
    // each key needs an explicit reset-to-Default mapping here.
    override suspend fun resetKeys(keys: Set<Preferences.Key<*>>) {
        val default = DisplaySettings.Default
        state.update { current -> keys.fold(current) { acc, key -> acc.resetField(key, default) } }
    }

    override suspend fun resetToDefaults() = state.update { DisplaySettings.Default }

    private fun DisplaySettings.resetField(
        key: Preferences.Key<*>,
        default: DisplaySettings,
    ): DisplaySettings =
        when (key) {
            DisplayPreferences.THEME_KEY -> {
                copy(themeMode = default.themeMode)
            }

            DisplayPreferences.ACCENT_KEY -> {
                copy(accentColor = default.accentColor)
            }

            DisplayPreferences.UI_SCALE_KEY -> {
                copy(uiScale = default.uiScale)
            }

            DisplayPreferences.SPEED_KEY -> {
                copy(speedUnit = default.speedUnit)
            }

            DisplayPreferences.TEMPERATURE_KEY -> {
                copy(temperatureUnit = default.temperatureUnit)
            }

            DisplayPreferences.CLOCK_KEY -> {
                copy(clock = default.clock)
            }

            DisplayPreferences.SHOW_CLOCK_SECONDS_KEY -> {
                copy(showClockSeconds = default.showClockSeconds)
            }

            DisplayPreferences.FULLSCREEN_KEY -> {
                copy(fullscreen = default.fullscreen)
            }

            DisplayPreferences.DOCK_POSITION_KEY -> {
                copy(dockPosition = default.dockPosition)
            }

            DisplayPreferences.DRIVER_SIDE_KEY -> {
                copy(driverSide = default.driverSide)
            }

            DisplayPreferences.MOTION_TIER_KEY -> {
                copy(motionTier = default.motionTier)
            }

            DisplayPreferences.ORIENTATION_KEY -> {
                copy(orientation = default.orientation)
            }

            DisplayPreferences.KEEP_SCREEN_ON_KEY -> {
                copy(keepScreenOn = default.keepScreenOn)
            }

            DisplayPreferences.ASSISTANT_LAUNCH_KEY -> {
                copy(assistantLaunch = default.assistantLaunch)
            }

            DisplayPreferences.MAP_STYLE_KEY -> {
                copy(mapStyle = default.mapStyle)
            }

            DisplayPreferences.MAP_SCHEME_LIGHT_KEY -> {
                copy(mapSchemeLight = default.mapSchemeLight)
            }

            DisplayPreferences.MAP_SCHEME_DARK_KEY -> {
                copy(mapSchemeDark = default.mapSchemeDark)
            }

            DisplayPreferences.MAP_TILT_KEY -> {
                copy(mapTiltDeg = default.mapTiltDeg)
            }

            DisplayPreferences.MAP_ZOOM_KEY -> {
                copy(mapZoom = default.mapZoom)
            }

            DisplayPreferences.MAP_NORTH_UP_KEY -> {
                copy(mapNorthUp = default.mapNorthUp)
            }

            DisplayPreferences.MAP_MARKER_POS_KEY -> {
                copy(mapMarkerPos = default.mapMarkerPos)
            }

            DisplayPreferences.MAP_3D_BUILDINGS_KEY -> {
                copy(map3dBuildings = default.map3dBuildings)
            }

            DisplayPreferences.MAP_TERRAIN_KEY -> {
                copy(mapTerrain = default.mapTerrain)
            }

            DisplayPreferences.GLASS_BLUR_KEY -> {
                copy(glassBlurRadius = default.glassBlurRadius)
            }

            DisplayPreferences.GLASS_TINT_KEY -> {
                copy(glassTintScale = default.glassTintScale)
            }

            DisplayPreferences.GLASS_SHOW_BORDER_KEY -> {
                copy(glassShowBorder = default.glassShowBorder)
            }

            DisplayPreferences.GLASS_SHADOW_ENABLED_KEY -> {
                copy(glassShadowEnabled = default.glassShadowEnabled)
            }

            DisplayPreferences.GLASS_SHADOW_INTENSITY_KEY -> {
                copy(glassShadowIntensity = default.glassShadowIntensity)
            }

            DisplayPreferences.GLASS_SHADOW_SIZE_KEY -> {
                copy(glassShadowSizeDp = default.glassShadowSizeDp)
            }

            DisplayPreferences.FONT_BASE_SIZE_KEY -> {
                copy(fontBaseSizeSp = default.fontBaseSizeSp)
            }

            DisplayPreferences.FONT_WEIGHT_STEP_KEY -> {
                copy(fontWeightStep = default.fontWeightStep)
            }

            DisplayPreferences.FONT_LETTER_SPACING_KEY -> {
                copy(fontLetterSpacingCentiEm = default.fontLetterSpacingCentiEm)
            }

            DisplayPreferences.SHOW_CALENDAR_KEY -> {
                copy(showCalendar = default.showCalendar)
            }

            DisplayPreferences.SHOW_WEATHER_KEY -> {
                copy(showWeather = default.showWeather)
            }

            DisplayPreferences.SHOW_MUSIC_KEY -> {
                copy(showMusic = default.showMusic)
            }

            DisplayPreferences.MUSIC_SPECTRUM_KEY -> {
                copy(musicSpectrum = default.musicSpectrum)
            }

            DisplayPreferences.MUSIC_SHOW_ALBUM_KEY -> {
                copy(musicShowAlbum = default.musicShowAlbum)
            }

            DisplayPreferences.MUSIC_SHOW_ART_KEY -> {
                copy(musicShowArt = default.musicShowArt)
            }

            DisplayPreferences.MAP_BACKEND_KEY -> {
                copy(mapBackend = default.mapBackend)
            }

            DisplayPreferences.MAPBOX_STYLE_KEY -> {
                copy(mapboxStyle = default.mapboxStyle)
            }

            DisplayPreferences.MAPBOX_TRAFFIC_KEY -> {
                copy(mapboxTraffic = default.mapboxTraffic)
            }

            DisplayPreferences.MAPBOX_ACCESS_TOKEN_KEY -> {
                copy(mapboxAccessToken = default.mapboxAccessToken)
            }

            DisplayPreferences.GOOGLE_MAPS_API_KEY_KEY -> {
                copy(googleMapsApiKey = default.googleMapsApiKey)
            }

            DisplayPreferences.GOOGLE_MAPS_MAP_ID_KEY -> {
                copy(googleMapsMapId = default.googleMapsMapId)
            }

            DisplayPreferences.GOOGLE_MAPS_MAP_TYPE_KEY -> {
                copy(googleMapsMapType = default.googleMapsMapType)
            }

            DisplayPreferences.GOOGLE_MAPS_TRAFFIC_KEY -> {
                copy(googleMapsTraffic = default.googleMapsTraffic)
            }

            else -> {
                this
            }
        }
}

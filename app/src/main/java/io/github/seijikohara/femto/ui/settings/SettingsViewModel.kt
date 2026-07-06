package io.github.seijikohara.femto.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.seijikohara.femto.data.calendar.CalendarCatalog
import io.github.seijikohara.femto.data.calendar.CalendarCatalogState
import io.github.seijikohara.femto.data.calendar.CalendarPreferences
import io.github.seijikohara.femto.data.calendar.CalendarPreferencesStore
import io.github.seijikohara.femto.data.common.WhileUiSubscribed
import io.github.seijikohara.femto.data.display.DisplayPreferences
import io.github.seijikohara.femto.data.display.DisplaySettingsStore
import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.data.fonts.FontPreferences
import io.github.seijikohara.femto.data.fonts.FontSelectionStore
import io.github.seijikohara.femto.data.location.LocationPreferences
import io.github.seijikohara.femto.data.location.LocationSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class SettingsViewModel(
    private val displayPreferences: DisplaySettingsStore,
    private val fontPreferences: FontSelectionStore,
    private val locationPreferences: LocationSettingsStore,
    private val calendarPreferences: CalendarPreferencesStore,
    availableCalendars: Flow<CalendarCatalogState>,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> =
        combine(
            displayPreferences.settings,
            fontPreferences.selection,
            locationPreferences.settings,
            calendarPreferences.hiddenCalendarIds,
            availableCalendars,
        ) { display, font, location, hiddenCalendars, catalog ->
            SettingsUiState(
                themeMode = display.themeMode,
                accentColor = display.accentColor,
                uiScale = display.uiScale,
                speedUnit = display.speedUnit,
                temperatureUnit = display.temperatureUnit,
                clock = display.clock,
                showClockSeconds = display.showClockSeconds,
                fullscreen = display.fullscreen,
                dockPosition = display.dockPosition,
                driverSide = display.driverSide,
                presetMode = display.presetMode,
                drivingThresholdKmh = display.drivingThresholdKmh,
                motionTier = display.motionTier,
                orientation = display.orientation,
                briefingShowEvent = display.briefingShowEvent,
                briefingShowWeather = display.briefingShowWeather,
                keepScreenOn = display.keepScreenOn,
                assistantLaunch = display.assistantLaunch,
                mapStyle = display.mapStyle,
                mapSchemeLight = display.mapSchemeLight,
                mapSchemeDark = display.mapSchemeDark,
                mapTiltDeg = display.mapTiltDeg,
                mapZoom = display.mapZoom,
                mapNorthUp = display.mapNorthUp,
                mapMarkerPos = display.mapMarkerPos,
                map3dBuildings = display.map3dBuildings,
                mapTerrain = display.mapTerrain,
                glassBlurRadius = display.glassBlurRadius,
                glassTintScale = display.glassTintScale,
                showCalendar = display.showCalendar,
                showWeather = display.showWeather,
                showMusic = display.showMusic,
                musicSpectrum = display.musicSpectrum,
                musicShowAlbum = display.musicShowAlbum,
                musicShowArt = display.musicShowArt,
                mapBackend = display.mapBackend,
                mapboxStyle = display.mapboxStyle,
                mapboxTraffic = display.mapboxTraffic,
                mapboxAccessToken = display.mapboxAccessToken,
                googleMapsApiKey = display.googleMapsApiKey,
                googleMapsMapId = display.googleMapsMapId,
                googleMapsMapType = display.googleMapsMapType,
                googleMapsTraffic = display.googleMapsTraffic,
                latinFont = font.latinFamily,
                cjkFont = font.cjkFamily,
                locationQuality = location.quality,
                locationIntervalMillis = location.intervalMillis,
                locationMinDistanceMeters = location.minUpdateDistanceMeters,
                backgroundRangingEnabled = location.backgroundRangingEnabled,
                availableCalendars = catalog.calendars,
                hiddenCalendarIds = hiddenCalendars,
                hasCalendarAccess = catalog.hasAccess,
            )
        }.stateIn(viewModelScope, WhileUiSubscribed, SettingsUiState.Initial)

    fun onAction(action: SettingsAction) {
        // Each branch is a single suspending write; launch once and dispatch.
        viewModelScope.launch {
            when (action) {
                is SettingsAction.SetThemeMode -> {
                    displayPreferences.setThemeMode(action.value)
                }

                is SettingsAction.SetAccentColor -> {
                    displayPreferences.setAccentColor(action.value)
                }

                is SettingsAction.SetSpeedUnit -> {
                    displayPreferences.setSpeedUnit(action.value)
                }

                is SettingsAction.SetTemperatureUnit -> {
                    displayPreferences.setTemperatureUnit(action.value)
                }

                is SettingsAction.SetClock -> {
                    displayPreferences.setClock(action.value)
                }

                is SettingsAction.SetShowClockSeconds -> {
                    displayPreferences.setShowClockSeconds(action.value)
                }

                is SettingsAction.SetFullscreen -> {
                    displayPreferences.setFullscreen(action.value)
                }

                is SettingsAction.SetDockPosition -> {
                    displayPreferences.setDockPosition(action.value)
                }

                is SettingsAction.SetDriverSide -> {
                    displayPreferences.setDriverSide(action.value)
                }

                is SettingsAction.SetPresetMode -> {
                    displayPreferences.setPresetMode(action.value)
                }

                is SettingsAction.SetDrivingThresholdKmh -> {
                    displayPreferences.setDrivingThresholdKmh(action.value)
                }

                is SettingsAction.SetMotionTier -> {
                    displayPreferences.setMotionTier(action.value)
                }

                is SettingsAction.SetOrientation -> {
                    displayPreferences.setOrientation(action.value)
                }

                is SettingsAction.SetBriefingShowEvent -> {
                    displayPreferences.setBriefingShowEvent(action.value)
                }

                is SettingsAction.SetBriefingShowWeather -> {
                    displayPreferences.setBriefingShowWeather(action.value)
                }

                is SettingsAction.SetUiScale -> {
                    displayPreferences.setUiScale(action.value)
                }

                is SettingsAction.SetKeepScreenOn -> {
                    displayPreferences.setKeepScreenOn(action.value)
                }

                is SettingsAction.SetAssistantLaunch -> {
                    displayPreferences.setAssistantLaunch(action.value)
                }

                is SettingsAction.SetMapStyle -> {
                    displayPreferences.setMapStyle(action.value)
                }

                is SettingsAction.SetMapSchemeLight -> {
                    displayPreferences.setMapSchemeLight(action.value)
                }

                is SettingsAction.SetMapSchemeDark -> {
                    displayPreferences.setMapSchemeDark(action.value)
                }

                is SettingsAction.SetMapTilt -> {
                    displayPreferences.setMapTilt(action.value)
                }

                is SettingsAction.SetMapZoom -> {
                    displayPreferences.setMapZoom(action.value)
                }

                is SettingsAction.SetMapNorthUp -> {
                    displayPreferences.setMapNorthUp(action.value)
                }

                is SettingsAction.SetMapMarkerPos -> {
                    displayPreferences.setMapMarkerPos(action.value)
                }

                is SettingsAction.SetMap3dBuildings -> {
                    displayPreferences.setMap3dBuildings(action.value)
                }

                is SettingsAction.SetMapTerrain -> {
                    displayPreferences.setMapTerrain(action.value)
                }

                is SettingsAction.SetGlassBlurRadius -> {
                    displayPreferences.setGlassBlurRadius(action.value)
                }

                is SettingsAction.SetGlassTintScale -> {
                    displayPreferences.setGlassTintScale(action.value)
                }

                is SettingsAction.SetShowCalendar -> {
                    displayPreferences.setShowCalendar(action.value)
                }

                is SettingsAction.SetShowWeather -> {
                    displayPreferences.setShowWeather(action.value)
                }

                is SettingsAction.SetShowMusic -> {
                    displayPreferences.setShowMusic(action.value)
                }

                is SettingsAction.SetMusicSpectrum -> {
                    displayPreferences.setMusicSpectrum(action.value)
                }

                is SettingsAction.SetMusicShowAlbum -> {
                    displayPreferences.setMusicShowAlbum(action.value)
                }

                is SettingsAction.SetMusicShowArt -> {
                    displayPreferences.setMusicShowArt(action.value)
                }

                is SettingsAction.SetLocationQuality -> {
                    locationPreferences.setQuality(action.value)
                }

                is SettingsAction.SetLocationIntervalMillis -> {
                    locationPreferences.setIntervalMillis(action.value)
                }

                is SettingsAction.SetLocationMinDistance -> {
                    locationPreferences.setMinUpdateDistanceMeters(action.value)
                }

                is SettingsAction.SetBackgroundRanging -> {
                    locationPreferences.setBackgroundRangingEnabled(action.value)
                }

                is SettingsAction.SetMapBackend -> {
                    displayPreferences.setMapBackend(action.value)
                }

                is SettingsAction.SetMapboxStyle -> {
                    displayPreferences.setMapboxStyle(action.value)
                }

                is SettingsAction.SetMapboxTraffic -> {
                    displayPreferences.setMapboxTraffic(action.value)
                }

                is SettingsAction.SaveMapboxToken -> {
                    // Token first, then backend, in this one coroutine: the gate
                    // never observes backend=MAPBOX with a blank token (no flicker).
                    displayPreferences.setMapboxAccessToken(action.value.trim())
                    displayPreferences.setMapBackend(MapBackend.MAPBOX)
                }

                SettingsAction.ClearMapboxToken -> {
                    displayPreferences.setMapboxAccessToken("")
                }

                is SettingsAction.SaveGoogleMapsKey -> {
                    displayPreferences.setGoogleMapsApiKey(action.value.trim())
                    displayPreferences.setMapBackend(MapBackend.GOOGLEMAPS)
                }

                SettingsAction.ClearGoogleMapsKey -> {
                    displayPreferences.setGoogleMapsApiKey("")
                }

                is SettingsAction.SetGoogleMapsMapId -> {
                    displayPreferences.setGoogleMapsMapId(action.value.trim())
                }

                SettingsAction.ClearGoogleMapsMapId -> {
                    displayPreferences.setGoogleMapsMapId("")
                }

                is SettingsAction.SetGoogleMapsMapType -> {
                    displayPreferences.setGoogleMapsMapType(action.value)
                }

                is SettingsAction.SetGoogleMapsTraffic -> {
                    displayPreferences.setGoogleMapsTraffic(action.value)
                }

                is SettingsAction.SetCalendarHidden -> {
                    calendarPreferences.setCalendarHidden(action.id, action.hidden)
                }

                is SettingsAction.ResetToDefaults -> {
                    displayPreferences.resetToDefaults()
                    locationPreferences.resetToDefaults()
                    fontPreferences.resetToDefaults()
                }
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
            locationPreferences = LocationPreferences(application),
            calendarPreferences = CalendarPreferences(application),
            availableCalendars = CalendarCatalog(application).availableCalendarsFlow(),
        ) as T
    }
}

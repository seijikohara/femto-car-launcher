package io.github.seijikohara.femto.ui.settings

import android.app.Application
import android.net.Uri
import android.util.Log
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
import io.github.seijikohara.femto.data.display.SettingsSectionId
import io.github.seijikohara.femto.data.dock.DockPreferences
import io.github.seijikohara.femto.data.dock.DockSettingsStore
import io.github.seijikohara.femto.data.fonts.FontPreferences
import io.github.seijikohara.femto.data.fonts.FontSelectionStore
import io.github.seijikohara.femto.data.location.LocationGraph
import io.github.seijikohara.femto.data.location.LocationPreferences
import io.github.seijikohara.femto.data.location.LocationSettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Narrow port for the track-log actions Settings drives. The factory binds it
 * to [io.github.seijikohara.femto.data.location.LocationGraph]'s recorder plus
 * the ContentResolver (opening the SAF document is a UI-side concern the data
 * layer must not know about); tests substitute an in-memory fake.
 */
internal interface TrackLogPort {
    /** Stream the track log as GPX into [uri]; null means the export failed. */
    suspend fun exportTo(uri: Uri): Long?

    suspend fun clearHistory(): Boolean
}

internal class SettingsViewModel(
    private val displayPreferences: DisplaySettingsStore,
    private val fontPreferences: FontSelectionStore,
    private val locationPreferences: LocationSettingsStore,
    private val calendarPreferences: CalendarPreferencesStore,
    private val dockPreferences: DockSettingsStore,
    private val trackLog: TrackLogPort,
    availableCalendars: Flow<CalendarCatalogState>,
) : ViewModel() {
    // VM-local export progress folded into the derived UiState below; every
    // other UiState field mirrors a persisted store.
    private val trackExportState = MutableStateFlow<TrackExportState>(TrackExportState.Idle)

    private val storeState: Flow<SettingsUiState> =
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
                motionTier = display.motionTier,
                orientation = display.orientation,
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
                glassShowBorder = display.glassShowBorder,
                glassShadowEnabled = display.glassShadowEnabled,
                glassShadowIntensity = display.glassShadowIntensity,
                glassShadowSizeDp = display.glassShadowSizeDp,
                fontBaseSizeSp = display.fontBaseSizeSp,
                fontWeightStep = display.fontWeightStep,
                fontLetterSpacingCentiEm = display.fontLetterSpacingCentiEm,
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
                latinFont = font.latin.displayNameOrNull,
                cjkFont = font.cjk.displayNameOrNull,
                locationQuality = location.quality,
                locationIntervalMillis = location.intervalMillis,
                locationMinDistanceMeters = location.minUpdateDistanceMeters,
                backgroundRangingEnabled = location.backgroundRangingEnabled,
                trackRecordingEnabled = location.trackRecordingEnabled,
                trackRetention = location.trackRetention,
                availableCalendars = catalog.calendars,
                hiddenCalendarIds = hiddenCalendars,
                hasCalendarAccess = catalog.hasAccess,
            )
        }

    val uiState: StateFlow<SettingsUiState> =
        combine(storeState, trackExportState) { state, export -> state.copy(trackExport = export) }
            .stateIn(viewModelScope, WhileUiSubscribed, SettingsUiState.Initial)

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

                is SettingsAction.SetMotionTier -> {
                    displayPreferences.setMotionTier(action.value)
                }

                is SettingsAction.SetOrientation -> {
                    displayPreferences.setOrientation(action.value)
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

                is SettingsAction.SetGlassShowBorder -> {
                    displayPreferences.setGlassShowBorder(action.value)
                }

                is SettingsAction.SetGlassShadowEnabled -> {
                    displayPreferences.setGlassShadowEnabled(action.value)
                }

                is SettingsAction.SetGlassShadowIntensity -> {
                    displayPreferences.setGlassShadowIntensity(action.value)
                }

                is SettingsAction.SetGlassShadowSizeDp -> {
                    displayPreferences.setGlassShadowSizeDp(action.value)
                }

                is SettingsAction.SetFontBaseSizeSp -> {
                    displayPreferences.setFontBaseSizeSp(action.value)
                }

                is SettingsAction.SetFontWeightStep -> {
                    displayPreferences.setFontWeightStep(action.value)
                }

                is SettingsAction.SetFontLetterSpacingCentiEm -> {
                    displayPreferences.setFontLetterSpacingCentiEm(action.value)
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

                is SettingsAction.SetTrackRecording -> {
                    locationPreferences.setTrackRecordingEnabled(action.value)
                }

                is SettingsAction.SetTrackRetention -> {
                    locationPreferences.setTrackRetention(action.value)
                }

                is SettingsAction.ExportTrackLog -> {
                    trackExportState.value = TrackExportState.Running
                    trackExportState.value =
                        trackLog.exportTo(action.uri)?.let { TrackExportState.Done(it) }
                            ?: TrackExportState.Failed
                }

                SettingsAction.ClearTrackHistory -> {
                    // Failure is already logged at the repository; settings writes
                    // degrade silently by the same editOrLog discipline. Clear a
                    // stale "Exported N points." so it can't misdescribe the now-
                    // empty history.
                    trackLog.clearHistory()
                    trackExportState.value = TrackExportState.Idle
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
                    calendarPreferences.resetToDefaults()
                }

                is SettingsAction.ResetSection -> {
                    displayPreferences.resetKeys(action.sectionId.displayKeys)
                    // The section's own DisplayPreferences keys are already cleared
                    // above; only add the other-store reset a section additionally owns.
                    when (action.sectionId) {
                        SettingsSectionId.APPEARANCE -> fontPreferences.resetToDefaults()

                        SettingsSectionId.LOCATION -> locationPreferences.resetToDefaults()

                        SettingsSectionId.PANELS -> calendarPreferences.resetToDefaults()

                        SettingsSectionId.SCREEN,
                        SettingsSectionId.UNITS,
                        SettingsSectionId.MAP,
                        -> Unit
                    }
                }

                SettingsAction.ResetDock -> {
                    dockPreferences.resetToDefaults()
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
            dockPreferences = DockPreferences(application),
            trackLog = trackLogPort(application),
            availableCalendars = CalendarCatalog(application).availableCalendarsFlow(),
        ) as T
    }

    // The one place UI meets the recorder: SAF document opening stays here so
    // the data layer never sees a Uri or ContentResolver.
    private fun trackLogPort(application: Application): TrackLogPort =
        object : TrackLogPort {
            private val trackLog get() = LocationGraph.get(application).trackLog

            // The whole pipeline runs off the main thread and under one
            // runCatching: openOutputStream is a Binder call into an arbitrary
            // DocumentsProvider (a cloud target can block), and use{}'s close()
            // can throw on a full/ejected disk — an escape from here would crash
            // the HOME app. "wt" truncates, so overwriting a longer previous
            // export can't leave stale bytes after </gpx>. CancellationException
            // is rethrown to keep structured concurrency intact.
            override suspend fun exportTo(uri: Uri): Long? =
                withContext(Dispatchers.IO) {
                    runCatching {
                        application.contentResolver
                            .openOutputStream(uri, "wt")
                            ?.use { output -> trackLog.exportGpx(output) }
                    }.getOrElse { e ->
                        if (e is CancellationException) throw e
                        Log.e(TAG, "track-log export failed", e)
                        null
                    }
                }

            override suspend fun clearHistory(): Boolean = trackLog.clearHistory()
        }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}

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
import io.github.seijikohara.femto.data.common.catchAsDefault
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
import io.github.seijikohara.femto.data.location.VehicleMotion
import io.github.seijikohara.femto.data.location.currentOrUnknown
import io.github.seijikohara.femto.data.update.UpdatePreferences
import io.github.seijikohara.femto.data.update.UpdateRepository
import io.github.seijikohara.femto.data.update.UpdateSettingsStore
import io.github.seijikohara.femto.data.update.UpdateState
import io.github.seijikohara.femto.data.update.offeredManifestOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "SettingsViewModel"

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

/**
 * Narrow port for the updater Settings drives: [UpdateRepository]'s state and
 * actions. The factory binds it to the app's updater; tests substitute an
 * in-memory fake, so every [UpdateState] can be staged directly.
 */
internal interface UpdaterPort {
    val state: Flow<UpdateState>

    /** The version this process was updated to, until [acknowledgeUpdatedTo]; null otherwise. */
    val updatedTo: Flow<String?>

    fun checkNow()

    fun download()

    fun install()

    /** Delete the staged download and return to the plain offer (UpdateRepository.discard). */
    fun discard()

    /** Skip the offered build (UpdateRepository.skip). */
    fun skip()

    fun acknowledgeUpdatedTo()
}

internal class SettingsViewModel(
    private val displayPreferences: DisplaySettingsStore,
    private val fontPreferences: FontSelectionStore,
    private val locationPreferences: LocationSettingsStore,
    private val calendarPreferences: CalendarPreferencesStore,
    private val dockPreferences: DockSettingsStore,
    private val trackLog: TrackLogPort,
    availableCalendars: Flow<CalendarCatalogState>,
    private val updater: UpdaterPort,
    private val updatePreferences: UpdateSettingsStore,
    // What the latest fix says about the vehicle, emitted on change
    // (vehicleMotionFlow); gates the install steps.
    private val motion: Flow<VehicleMotion>,
) : ViewModel() {
    // VM-local export progress folded into the derived UiState below; every
    // other UiState field mirrors a persisted store, the updater's state aside.
    private val trackExportState = MutableStateFlow<TrackExportState>(TrackExportState.Idle)

    // VM-local like the export progress: whether the last install tap came back
    // from the "Install unknown apps" access without it turned on. It stays set
    // until an install goes ahead, so the install row can say why nothing
    // happened.
    private val installGrantDeclined = MutableStateFlow(false)

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
                dockWidth = display.dockWidth,
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
                googleMapsApiKey = display.googleMapsApiKey,
                googleMapsMapId = display.googleMapsMapId,
                googleMapsRendering = display.googleMapsRendering,
                googleMapsMapType = display.googleMapsMapType,
                googleMapsTraffic = display.googleMapsTraffic,
                mapTileHost = display.mapTileHost,
                mapCustomStyleUrl = display.mapCustomStyleUrl,
                latinFont = font.latin.displayNameOrNull,
                cjkFont = font.cjk.displayNameOrNull,
                locationQuality = location.quality,
                locationIntervalMillis = location.intervalMillis,
                locationMinDistanceMeters = location.minUpdateDistanceMeters,
                backgroundRangingEnabled = location.backgroundRangingEnabled,
                tripAutoReset = location.tripAutoReset,
                trackRecordingEnabled = location.trackRecordingEnabled,
                trackRetention = location.trackRetention,
                availableCalendars = catalog.calendars,
                hiddenCalendarIds = hiddenCalendars,
                hasCalendarAccess = catalog.hasAccess,
            )
        }

    // The Screen switch's checked state, read off the dock store instead of a
    // boolean of its own: this is DockConfig.visibleStatus.isNotEmpty() expressed
    // on the two flows it derives from, so a per-indicator Hide from the dock's
    // long-press menu moves the switch too.
    private val dockStatusVisible: Flow<Boolean> =
        combine(dockPreferences.statusOrder, dockPreferences.statusHidden) { order, hidden ->
            order.any { it !in hidden }
        }

    // Only a fix that shows the vehicle moving holds the install steps: a phone
    // without the location grant never has a fix, and must still be able to
    // install an update it asked for. Seeded and caught like the dashboard's
    // slots: the updater resolves off the main thread when first collected, and
    // neither that wait nor a failure there may hold back or crash the rest of
    // Settings.
    private val updates: Flow<UpdatesUiState> =
        combine(
            updater.state,
            updater.updatedTo,
            updatePreferences.settings,
            motion,
            installGrantDeclined,
        ) { state, updatedTo, settings, currentMotion, grantDeclined ->
            updatesUiState(
                state = state,
                settings = settings,
                updatedTo = updatedTo,
                installBlocked = currentMotion == VehicleMotion.MOVING,
                installGrantDeclined = grantDeclined,
            )
        }.onStart { emit(UpdatesUiState.Initial) }
            .catchAsDefault(TAG, "updates", UpdatesUiState.Initial)

    // The one-tap update's "install when ready" mark, held as the coroutine that
    // acts on it (installOnceDownloaded). StartUpdate sets it; it is gone once it
    // has acted, and once the Updates section leaves the screen (UpdatesHidden),
    // so an install never starts by itself later or elsewhere. Once it has asked
    // for the install, the token of that request (oneTapToken) carries the same
    // guard to the install itself.
    private var installWhenReady: Job? = null

    // While the Updates section is on screen, every offer it shows counts as
    // seen (recordOffersSeen): UpdatesShown starts the record, UpdatesHidden
    // stops it taking new offers.
    private var offersSeen: Job? = null

    // The token of the one-tap update's install request while that install may
    // still go ahead (see SettingsAction.InstallOneTapUpdate), null otherwise:
    // UpdatesHidden voids it, and the install uses it up.
    private var oneTapToken: Int? = null
    private var lastOneTapToken = 0

    private val mutableInstallRequests = MutableSharedFlow<SettingsAction.InstallOneTapUpdate>(extraBufferCapacity = 1)

    /**
     * The one-tap update's install, for the screen to run the way a tap on the
     * install step runs it: through the "Install unknown apps" access, which
     * only the UI can open (rememberInstallGrantedActions), and back here with
     * the token it carries. The one-tap update makes one once its download is
     * verified, and only while the Updates section is on screen: collect it
     * only while the screen is started (InstallRequestsEffect).
     */
    val installRequests: SharedFlow<SettingsAction.InstallOneTapUpdate> = mutableInstallRequests.asSharedFlow()

    // Folded in here rather than into the store combine above, which already holds
    // kotlinx's five-flow typed overload.
    val uiState: StateFlow<SettingsUiState> =
        combine(storeState, trackExportState, dockStatusVisible, updates) { state, export, statusVisible, update ->
            state.copy(trackExport = export, dockStatusVisible = statusVisible, updates = update)
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

                is SettingsAction.SetDockWidth -> {
                    displayPreferences.setDockWidth(action.value)
                }

                is SettingsAction.SetDockStatusVisible -> {
                    dockPreferences.setStatusClusterVisible(action.value)
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

                is SettingsAction.SetTripAutoReset -> {
                    locationPreferences.setTripAutoReset(action.value)
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

                is SettingsAction.SetMapTileHost -> {
                    displayPreferences.setMapTileHost(action.value.trim())
                }

                SettingsAction.ClearMapTileHost -> {
                    displayPreferences.setMapTileHost("")
                }

                is SettingsAction.SetMapCustomStyleUrl -> {
                    displayPreferences.setMapCustomStyleUrl(action.value.trim())
                }

                SettingsAction.ClearMapCustomStyleUrl -> {
                    displayPreferences.setMapCustomStyleUrl("")
                }

                is SettingsAction.SetGoogleMapsRendering -> {
                    displayPreferences.setGoogleMapsRendering(action.value)
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

                SettingsAction.CheckForUpdates -> {
                    updater.checkNow()
                }

                SettingsAction.DownloadUpdate -> {
                    updater.download()
                }

                SettingsAction.StartUpdate -> {
                    // SettingsRoute sends this only once the access is on, so a
                    // decline no longer describes the update row.
                    installGrantDeclined.value = false
                    // The mark reads the updater's latest state, so no outcome of
                    // the download slips past it. A build already downloaded is not
                    // claimed again; the mark acts on it at once.
                    installWhenReady?.cancel()
                    installWhenReady = viewModelScope.launch { installOnceDownloaded() }
                    updater.download()
                }

                SettingsAction.UpdatesShown -> {
                    offersSeen?.cancel()
                    offersSeen = viewModelScope.launch { recordOffersSeen() }
                }

                SettingsAction.UpdatesHidden -> {
                    offersSeen?.cancel()
                    installWhenReady?.cancel()
                    oneTapToken = null
                }

                SettingsAction.InstallUpdate -> {
                    // SettingsRoute sends this only once the access is on, so a
                    // decline no longer describes the install row.
                    installGrantDeclined.value = false
                    // A local gate (AGENTS.md#driving-lockout): the system's install
                    // confirmation must not pop up over navigation while driving. The
                    // row already waits while moving; this read covers a tap that
                    // raced the car pulling away, and the install that resumes after
                    // the "Install unknown apps" grant screen. The updater gates the
                    // confirmation again when it arrives, reading the same source
                    // through the same function.
                    if (motion.currentOrUnknown() != VehicleMotion.MOVING) updater.install()
                }

                is SettingsAction.InstallOneTapUpdate -> {
                    // Sent only once the access is on, like InstallUpdate.
                    installGrantDeclined.value = false
                    // InstallUpdate's motion gate, then the token, read last: the
                    // section can leave while the motion is read.
                    if (motion.currentOrUnknown() != VehicleMotion.MOVING && oneTapToken == action.token) {
                        oneTapToken = null
                        updater.install()
                    }
                }

                // Neither puts anything on screen, so neither waits for parking.
                SettingsAction.DiscardUpdate -> {
                    updater.discard()
                }

                SettingsAction.SkipUpdate -> {
                    updater.skip()
                }

                SettingsAction.InstallGrantDeclined -> {
                    installGrantDeclined.value = true
                }

                is SettingsAction.SetUpdateAutoCheck -> {
                    updatePreferences.setAutoCheck(action.value)
                }

                SettingsAction.AcknowledgeUpdatedTo -> {
                    updater.acknowledgeUpdatedTo()
                }

                is SettingsAction.ResetToDefaults -> {
                    displayPreferences.resetToDefaults()
                    locationPreferences.resetToDefaults()
                    fontPreferences.resetToDefaults()
                    calendarPreferences.resetToDefaults()
                    updatePreferences.resetToDefaults()
                }

                is SettingsAction.ResetSection -> {
                    displayPreferences.resetKeys(action.sectionId.displayKeys)
                    // The section's own DisplayPreferences keys are already cleared
                    // above; only add the other-store reset a section additionally owns.
                    when (action.sectionId) {
                        SettingsSectionId.APPEARANCE -> fontPreferences.resetToDefaults()

                        SettingsSectionId.LOCATION -> locationPreferences.resetToDefaults()

                        SettingsSectionId.PANELS -> calendarPreferences.resetToDefaults()

                        SettingsSectionId.UPDATES -> updatePreferences.resetToDefaults()

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

    // Every offer the Updates section shows, from the state its "Available
    // version" row reads, is recorded as prompted: the dashboard's prompt never
    // asks about an update the user has already read about here. Leaving the
    // section cancels the collection, but never a write already under way: the
    // offer was on screen, and a DataStore write cancelled midway records
    // nothing.
    private suspend fun recordOffersSeen() =
        updater.state
            .catchAsDefault(TAG, "offers seen", UpdateState.Disabled)
            .mapNotNull { it.offeredManifestOrNull()?.versionCode }
            .distinctUntilChanged()
            .collect { withContext(NonCancellable) { updatePreferences.recordPrompted(it) } }

    // The one-tap update's second half: wait for the download it started to
    // settle. A verified file is then installed as a tap on the row installs it,
    // unless a fix shows the vehicle moving: then the chain stops, and the row
    // waits for a tap once parked, since a dialog that pops up later by itself
    // could meet the car pulling away (the updater holds its confirmation back
    // for the same reason). Any other outcome ends the chain too: a failed
    // download, or no newer build after all. A failing updater reads as no
    // download, like the rest of the section.
    //
    // The request waits for the screen to collect it: a sheet the dashboard's
    // prompt just opened on a verified build can settle before its collector
    // starts. It can wait only while the Updates section is on screen, which is
    // all the mark lives; the motion is read after the wait, just before asking.
    // The request carries a new token, which the install it comes back as needs.
    private suspend fun installOnceDownloaded() {
        val downloaded =
            updater.state
                .catchAsDefault(TAG, "one-tap update", UpdateState.Disabled)
                .firstOrNull { it !is UpdateState.Available && it !is UpdateState.Downloading } is UpdateState.Ready
        if (!downloaded) return
        mutableInstallRequests.subscriptionCount.first { it > 0 }
        if (motion.currentOrUnknown() != VehicleMotion.MOVING) {
            mutableInstallRequests.tryEmit(SettingsAction.InstallOneTapUpdate(newOneTapToken()))
        }
    }

    private fun newOneTapToken(): Int = (++lastOneTapToken).also { oneTapToken = it }
}

internal class SettingsViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        val locationGraph = LocationGraph.get(application)

        @Suppress("UNCHECKED_CAST")
        return SettingsViewModel(
            displayPreferences = DisplayPreferences(application),
            fontPreferences = FontPreferences(application),
            locationPreferences = LocationPreferences(application),
            calendarPreferences = CalendarPreferences(application),
            dockPreferences = DockPreferences(application),
            trackLog = trackLogPort(application),
            availableCalendars = CalendarCatalog(application).availableCalendarsFlow(),
            updater = updaterPort(application),
            updatePreferences = UpdatePreferences(application),
            // The dashboard's own location pipeline: one GPS registration shared
            // with the sheet's host, not a second one for this screen.
            motion = locationGraph.vehicleMotion(),
        ) as T
    }

    // Collected through UpdateRepository.observe, so even a sheet restored before
    // the dashboard's first subscription resolves the updater off the main
    // thread. The actions run on taps, long after it exists.
    private fun updaterPort(application: Application): UpdaterPort =
        object : UpdaterPort {
            private val repository get() = UpdateRepository.get(application)

            override val state: Flow<UpdateState> = UpdateRepository.observe(application) { it.state }

            override val updatedTo: Flow<String?> = UpdateRepository.observe(application) { it.updatedTo }

            override fun checkNow() = repository.checkNow()

            override fun download() = repository.download()

            override fun install() = repository.install()

            override fun discard() = repository.discard()

            override fun skip() = repository.skip()

            override fun acknowledgeUpdatedTo() = repository.acknowledgeUpdatedTo()
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
}

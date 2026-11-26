package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.calendar.CalendarCatalogState
import io.github.seijikohara.femto.data.calendar.CalendarPreferencesStore
import io.github.seijikohara.femto.data.display.DisplaySettingsStore
import io.github.seijikohara.femto.data.dock.DockSettingsStore
import io.github.seijikohara.femto.data.fonts.FontSelectionStore
import io.github.seijikohara.femto.data.location.LocationSettingsStore
import io.github.seijikohara.femto.data.location.VehicleMotion
import io.github.seijikohara.femto.data.routing.NavigationSettingsStore
import io.github.seijikohara.femto.data.update.UpdateSettingsStore
import io.github.seijikohara.femto.data.video.VideoSettingsStore
import io.github.seijikohara.femto.data.video.VideoSourceGrants
import io.github.seijikohara.femto.ui.settings.RoutingDataSummary
import io.github.seijikohara.femto.ui.settings.SettingsViewModel
import io.github.seijikohara.femto.ui.settings.TrackLogPort
import io.github.seijikohara.femto.ui.settings.UpdaterPort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Build a [SettingsViewModel] on in-memory fakes. Every collaborator defaults to a fresh fake, the calendar catalog to
 * granted access with no calendars, and the vehicle to parked; a test passes only the ones it reads or stages.
 */
internal fun fakeSettingsViewModel(
    displayPreferences: DisplaySettingsStore = FakeDisplaySettingsStore(),
    fontPreferences: FontSelectionStore = FakeFontSelectionStore(),
    locationPreferences: LocationSettingsStore = FakeLocationSettingsStore(),
    calendarPreferences: CalendarPreferencesStore = FakeCalendarPreferencesStore(),
    dockPreferences: DockSettingsStore = FakeDockSettingsStore(),
    trackLog: TrackLogPort = FakeTrackLogPort(),
    availableCalendars: CalendarCatalogState = CalendarCatalogState(hasAccess = true, calendars = emptyList()),
    updater: UpdaterPort = FakeUpdaterPort(),
    updatePreferences: UpdateSettingsStore = FakeUpdateSettingsStore(),
    motion: Flow<VehicleMotion> = flowOf(VehicleMotion.PARKED),
    videoPreferences: VideoSettingsStore = FakeVideoSettingsStore(),
    videoGrants: VideoSourceGrants = FakeVideoSourceGrants(),
    navigationPreferences: NavigationSettingsStore = FakeNavigationSettingsStore(),
    offlineRegionsAvailable: Boolean = false,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    routingData: Flow<RoutingDataSummary?> = flowOf(null),
): SettingsViewModel =
    SettingsViewModel(
        displayPreferences = displayPreferences,
        fontPreferences = fontPreferences,
        locationPreferences = locationPreferences,
        calendarPreferences = calendarPreferences,
        dockPreferences = dockPreferences,
        trackLog = trackLog,
        availableCalendars = flowOf(availableCalendars),
        updater = updater,
        updatePreferences = updatePreferences,
        motion = motion,
        videoPreferences = videoPreferences,
        videoGrants = videoGrants,
        navigationPreferences = navigationPreferences,
        offlineRegionsAvailable = offlineRegionsAvailable,
        ioDispatcher = ioDispatcher,
        routingData = routingData,
    )

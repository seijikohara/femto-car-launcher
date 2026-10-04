package io.github.seijikohara.femto.ui.home

import android.content.ComponentName
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import app.cash.turbine.test
import io.github.seijikohara.femto.data.common.UI_SUBSCRIPTION_GRACE_MS
import io.github.seijikohara.femto.data.dock.DockNavId
import io.github.seijikohara.femto.data.dock.DockStatusId
import io.github.seijikohara.femto.data.location.MIN_MOVING_SPEED_MS
import io.github.seijikohara.femto.data.location.TripState
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.MusicCommand
import io.github.seijikohara.femto.data.music.SPECTRUM_BAND_COUNT
import io.github.seijikohara.femto.data.update.UpdateFailure
import io.github.seijikohara.femto.data.update.UpdateManifest
import io.github.seijikohara.femto.data.update.UpdateSettings
import io.github.seijikohara.femto.data.update.UpdateState
import io.github.seijikohara.femto.data.weather.WeatherSnapshot
import io.github.seijikohara.femto.testfixtures.FakeUpdateSettingsStore
import io.github.seijikohara.femto.testfixtures.fakeAddress
import io.github.seijikohara.femto.testfixtures.fakeCalendarSnapshot
import io.github.seijikohara.femto.testfixtures.fakeLocation
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.testfixtures.fakeSystemStatus
import io.github.seijikohara.femto.testfixtures.fakeTripState
import io.github.seijikohara.femto.testfixtures.fakeUpdateManifest
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.home.components.AppsBarShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `combines all flows into one HomeUiState`() =
        runTest {
            // Each source carries a distinct, identity-checkable value so the
            // assertions below pin every field to its own flow. A future reorder
            // of the two-stage combine (or its CoreSignals holder) that swaps two
            // same-typed slots is caught here rather than slipping through.
            val location = fakeLocation()
            val address = fakeAddress()
            val weather = fakeWeatherSnapshot()
            val musicState = MusicCardState.Playing(fakeNowPlaying())
            val calendar = fakeCalendarSnapshot()
            val systemStatus = fakeSystemStatus()
            val tripState = fakeTripState()
            val viewModel =
                HomeViewModel(
                    locationFlow = flowOf(location),
                    addressFlow = flowOf(address),
                    weatherFlow = flowOf(weather),
                    musicStateFlow = flowOf(musicState),
                    calendarFlow = flowOf(calendar),
                    systemStatusFlow = flowOf(systemStatus),
                    tripStateFlow = flowOf(tripState),
                )
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(location, state.location)
                assertEquals(address, state.address)
                assertEquals(weather, state.weather)
                assertEquals(musicState, state.musicState)
                assertEquals(calendar, state.calendar)
                assertEquals(systemStatus, state.systemStatus)
                assertEquals(tripState, state.tripState)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // The map's reconnect must not wait for the rest of the dashboard: held back
    // until every source emits again after a return, it reaches the map after
    // the page the return reload built, which then reloads a second time.
    @Test
    fun `the online reading reaches the map while another dashboard source has not emitted`() =
        runTest {
            val viewModel =
                HomeViewModel(
                    locationFlow = flowOf(fakeLocation()),
                    addressFlow = flowOf(fakeAddress()),
                    weatherFlow = flowOf(fakeWeatherSnapshot()),
                    musicStateFlow = flowOf(MusicCardState.Playing(fakeNowPlaying())),
                    // A source still loading, like a slow calendar query.
                    calendarFlow = flow { awaitCancellation() },
                    systemStatusFlow = flowOf(fakeSystemStatus()),
                    tripStateFlow = flowOf(fakeTripState()),
                    onlineFlow = flowOf(false),
                )
            backgroundScope.launch { viewModel.uiState.collect {} }
            backgroundScope.launch { viewModel.online.collect {} }
            runCurrent()
            assertEquals(HomeUiState.Initial, viewModel.uiState.value, "the dashboard state still waits")
            assertFalse(viewModel.online.value)
        }

    @Test
    fun `a throwing source degrades only its own slot and never crashes the combine`() =
        runTest {
            // catchAsDefault must isolate a broken repository: a SecurityException
            // (e.g. a permission revoked between check and register) in one source
            // would otherwise cancel the shared combine and kill the HOME process.
            val weather = fakeWeatherSnapshot()
            val viewModel =
                HomeViewModel(
                    locationFlow = flowOf(fakeLocation()),
                    addressFlow = flowOf(fakeAddress()),
                    weatherFlow = flowOf(weather),
                    musicStateFlow = flowOf(MusicCardState.Playing(fakeNowPlaying())),
                    calendarFlow = flowOf(fakeCalendarSnapshot()),
                    systemStatusFlow = flow { throw SecurityException("system status source broke") },
                    tripStateFlow = flowOf(fakeTripState()),
                )
            viewModel.uiState.test {
                val state = awaitItem()
                // The broken system-status source holds its neutral Initial value...
                assertEquals(HomeUiState.Initial.systemStatus, state.systemStatus)
                // ...while every other card still shows its real value.
                assertEquals(weather, state.weather)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a source that fails after emitting falls back to its neutral value`() =
        runTest {
            // Documents the no-retry / no-hold-last-good policy: once a source that
            // had been working throws, its card resets to the neutral default and
            // stays there (until the process restarts), rather than freezing on the
            // last good value.
            val systemStatus = fakeSystemStatus()
            val viewModel =
                HomeViewModel(
                    locationFlow = flowOf(fakeLocation()),
                    addressFlow = flowOf(fakeAddress()),
                    weatherFlow = flowOf(fakeWeatherSnapshot()),
                    musicStateFlow = flowOf(MusicCardState.Playing(fakeNowPlaying())),
                    calendarFlow = flowOf(fakeCalendarSnapshot()),
                    systemStatusFlow =
                        flow {
                            emit(systemStatus)
                            throw IllegalStateException("system status source broke after emitting")
                        },
                    tripStateFlow = flowOf(fakeTripState()),
                )
            viewModel.uiState.test {
                val state = awaitItem()
                assertNotEquals(systemStatus, state.systemStatus)
                assertEquals(HomeUiState.Initial.systemStatus, state.systemStatus)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onAction OpenAppDrawer emits no event (handled at the dashboard overlay layer)`() =
        runTest {
            val viewModel = stubViewModel()
            viewModel.events.test {
                viewModel.onAction(HomeAction.OpenAppDrawer)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onAction LaunchApp emits LaunchComponent with the same component`() =
        runTest {
            val component = ComponentName("p", "c")
            stubViewModel().assertEvent(
                action = HomeAction.LaunchApp(component),
                expected = HomeEvent.LaunchComponent(component),
            )
        }

    @Test
    fun `onAction OpenMaps emits LaunchAppCategory APP_MAPS`() =
        runTest {
            stubViewModel().assertEvent(
                action = HomeAction.OpenMaps,
                expected = HomeEvent.LaunchAppCategory(Intent.CATEGORY_APP_MAPS),
            )
        }

    @Test
    fun `onAction OpenMaps emits LaunchGeo at the latest location when present`() =
        runTest {
            val location = fakeLocation()
            val viewModel =
                HomeViewModel(
                    locationFlow = flowOf(location),
                    addressFlow = flowOf(fakeAddress()),
                    weatherFlow = flowOf(fakeWeatherSnapshot()),
                    musicStateFlow = flowOf(MusicCardState.Playing(fakeNowPlaying())),
                    calendarFlow = flowOf(fakeCalendarSnapshot()),
                    systemStatusFlow = flowOf(fakeSystemStatus()),
                    tripStateFlow = flowOf(fakeTripState()),
                )
            // uiState uses WhileSubscribed, so collect it first to make the
            // StateFlow value live before onAction reads uiState.value.location.
            viewModel.uiState.test {
                assertNotNull(awaitItem().location)
                viewModel.events.test {
                    viewModel.onAction(HomeAction.OpenMaps)
                    assertEquals(
                        HomeEvent.LaunchGeo(location.latitude, location.longitude),
                        awaitItem(),
                    )
                    cancelAndIgnoreRemainingEvents()
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onAction Shortcut emits LaunchAppCategory carrying the shortcut category`() =
        runTest {
            stubViewModel().assertEvent(
                action = HomeAction.Shortcut(AppsBarShortcut.Music),
                expected = HomeEvent.LaunchAppCategory(AppsBarShortcut.Music.intentCategory),
            )
        }

    @Test
    fun `onAction OpenBrowser emits LaunchAppCategory APP_BROWSER`() =
        runTest {
            stubViewModel().assertEvent(
                action = HomeAction.OpenBrowser,
                expected = HomeEvent.LaunchAppCategory(Intent.CATEGORY_APP_BROWSER),
            )
        }

    @Test
    fun `onAction OpenCalendar emits LaunchAppCategory APP_CALENDAR`() =
        runTest {
            stubViewModel().assertEvent(
                action = HomeAction.OpenCalendar,
                expected = HomeEvent.LaunchAppCategory(Intent.CATEGORY_APP_CALENDAR),
            )
        }

    @Test
    fun `onAction OpenWeather emits LaunchAppCategory APP_WEATHER`() =
        runTest {
            stubViewModel().assertEvent(
                action = HomeAction.OpenWeather,
                expected = HomeEvent.LaunchAppCategory(Intent.CATEGORY_APP_WEATHER),
            )
        }

    @Test
    fun `onAction AdjustMapZoom emits the delta for the host to persist`() =
        runTest {
            stubViewModel().assertEvent(
                action = HomeAction.AdjustMapZoom(-1),
                expected = HomeEvent.AdjustMapZoom(-1),
            )
        }

    @Test
    fun `onAction ToggleMapNorthUp emits ToggleMapNorthUp`() =
        runTest {
            stubViewModel().assertEvent(
                action = HomeAction.ToggleMapNorthUp,
                expected = HomeEvent.ToggleMapNorthUp,
            )
        }

    @Test
    fun `onAction MoveDockNav emits MoveDockNav with the same id and direction`() =
        runTest {
            stubViewModel().assertEvent(
                action = HomeAction.MoveDockNav(DockNavId.MUSIC, -1),
                expected = HomeEvent.MoveDockNav(DockNavId.MUSIC, -1),
            )
        }

    @Test
    fun `onAction HideDockNav emits HideDockNav for the same id`() =
        runTest {
            stubViewModel().assertEvent(
                action = HomeAction.HideDockNav(DockNavId.MUSIC),
                expected = HomeEvent.HideDockNav(DockNavId.MUSIC),
            )
        }

    @Test
    fun `onAction MoveDockStatus emits MoveDockStatus with the same id and direction`() =
        runTest {
            stubViewModel().assertEvent(
                action = HomeAction.MoveDockStatus(DockStatusId.WIFI, 1),
                expected = HomeEvent.MoveDockStatus(DockStatusId.WIFI, 1),
            )
        }

    @Test
    fun `onAction HideDockStatus emits HideDockStatus for the same id`() =
        runTest {
            stubViewModel().assertEvent(
                action = HomeAction.HideDockStatus(DockStatusId.WIFI),
                expected = HomeEvent.HideDockStatus(DockStatusId.WIFI),
            )
        }

    @Test
    fun `onAction ResetDock emits ResetDock`() =
        runTest {
            stubViewModel().assertEvent(
                action = HomeAction.ResetDock,
                expected = HomeEvent.ResetDock,
            )
        }

    @Test
    fun `onAction OpenSettings emits OpenInAppSettings`() =
        runTest {
            stubViewModel().assertEvent(
                action = HomeAction.OpenSettings,
                expected = HomeEvent.OpenInAppSettings(),
            )
        }

    @Test
    fun `onAction OpenLicenses emits OpenLicenses`() =
        runTest {
            stubViewModel().assertEvent(
                action = HomeAction.OpenLicenses,
                expected = HomeEvent.OpenLicenses,
            )
        }

    @Test
    fun `onAction OpenAssistant emits OpenAssistant`() =
        runTest {
            stubViewModel().assertEvent(
                action = HomeAction.OpenAssistant,
                expected = HomeEvent.OpenAssistant,
            )
        }

    @Test
    fun `onAction ConnectMusicPlayer emits OpenNotificationListenerSettings`() =
        runTest {
            stubViewModel().assertEvent(
                action = HomeAction.ConnectMusicPlayer,
                expected = HomeEvent.OpenNotificationListenerSettings,
            )
        }

    @Test
    fun `onAction LaunchMusicSource emits LaunchComponent for the resolved package`() =
        runTest {
            val component = ComponentName("com.example.music", "com.example.music.MainActivity")
            stubViewModel(resolveMusicSourceComponent = { pkg -> component.takeIf { pkg == "com.example.music" } })
                .assertEvent(
                    action = HomeAction.LaunchMusicSource("com.example.music"),
                    expected = HomeEvent.LaunchComponent(component),
                )
        }

    @Test
    fun `onAction LaunchMusicSource emits no event when the package has no launcher activity`() =
        runTest {
            val viewModel = stubViewModel(resolveMusicSourceComponent = { null })
            viewModel.events.test {
                viewModel.onAction(HomeAction.LaunchMusicSource("com.example.headless"))
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onAction ResetTrip invokes resetTrip and emits no event`() =
        runTest {
            var resetCount = 0
            val viewModel = stubViewModel(resetTrip = { resetCount++ })
            viewModel.events.test {
                viewModel.onAction(HomeAction.ResetTrip)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, resetCount)
        }

    @Test
    fun `onAction Music forwards the command to sendMusicCommand and emits no event`() =
        runTest {
            val received = mutableListOf<MusicCommand>()
            val viewModel = stubViewModel(sendMusicCommand = { received += it })
            viewModel.events.test {
                viewModel.onAction(HomeAction.Music(MusicCommand.PlayPause))
                viewModel.onAction(HomeAction.Music(MusicCommand.SkipNext))
                viewModel.onAction(HomeAction.Music(MusicCommand.SkipPrevious))
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(
                listOf(MusicCommand.PlayPause, MusicCommand.SkipNext, MusicCommand.SkipPrevious),
                received,
            )
        }

    @Test
    fun `onAction PlayDefaultMusic resumes the last session and emits LaunchAppCategory APP_MUSIC`() =
        runTest {
            var resumeCount = 0
            val viewModel = stubViewModel(resumeLastMusicSession = { resumeCount++ })
            viewModel.events.test {
                viewModel.onAction(HomeAction.PlayDefaultMusic)
                assertEquals(HomeEvent.LaunchAppCategory(Intent.CATEGORY_APP_MUSIC), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            // Both the best-effort resume and the launch fallback fire on every
            // tap — there is no callback confirming whether the media key alone
            // resumed a session, so the launch always happens too.
            assertEquals(1, resumeCount)
        }

    @Test
    fun `audioSpectrum emits bands while the spectrum is enabled and music is playing`() =
        runTest {
            val bands = FloatArray(SPECTRUM_BAND_COUNT) { 0.5f }
            val viewModel =
                spectrumViewModel(
                    enabled = true,
                    musicState = MusicCardState.Playing(fakeNowPlaying(isPlaying = true)),
                    bands = bands,
                )
            viewModel.audioSpectrum.test {
                // The unconfined dispatcher may run the upstream before the
                // first collect, so the initial null is not always observed.
                assertEquals(bands, awaitItem() ?: awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `audioSpectrum stays null while the spectrum setting is off`() =
        runTest {
            val viewModel =
                spectrumViewModel(
                    enabled = false,
                    musicState = MusicCardState.Playing(fakeNowPlaying(isPlaying = true)),
                    bands = FloatArray(SPECTRUM_BAND_COUNT) { 0.5f },
                )
            viewModel.audioSpectrum.test {
                assertEquals(null, awaitItem())
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `audioSpectrum stays null while playback is paused`() =
        runTest {
            val viewModel =
                spectrumViewModel(
                    enabled = true,
                    musicState = MusicCardState.Playing(fakeNowPlaying(isPlaying = false)),
                    bands = FloatArray(SPECTRUM_BAND_COUNT) { 0.5f },
                )
            viewModel.audioSpectrum.test {
                assertEquals(null, awaitItem())
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the update badge shows for an update on offer while a live GPS fix shows the vehicle parked`() =
        runTest {
            val state = settledState(badgeViewModel(update = UpdateState.Available(UPDATE)))
            assertTrue(state.updateBadge)
        }

    @Test
    fun `the update badge stays hidden on a stale GPS fix`() =
        runTest {
            // The cached seed a subscription starts with: the trip speed reads zero
            // until live fixes set it, so the seed alone must not count as parked.
            val seed = liveGpsFix().apply { elapsedRealtimeNanos = BADGE_NOW - STALE_FIX_AGE_NANOS }
            val state = settledState(badgeViewModel(update = UpdateState.Available(UPDATE), location = seed))
            assertFalse(state.updateBadge)
        }

    @Test
    fun `the update badge stays hidden without a fix`() =
        runTest {
            // Before the first fix of a drive the trip speed reads zero, so a
            // stationary trip state alone must not count as parked.
            val state = settledState(badgeViewModel(update = UpdateState.Available(UPDATE), location = null))
            assertFalse(state.updateBadge)
        }

    @Test
    fun `the update badge stays hidden while a fix shows the vehicle moving`() =
        runTest {
            val moving = fakeTripState(currentSpeedMs = MIN_MOVING_SPEED_MS + 10.0)
            val state = settledState(badgeViewModel(update = UpdateState.Available(UPDATE), tripState = moving))
            assertFalse(state.updateBadge)
        }

    @Test
    fun `the update badge stays hidden for a skipped build`() =
        runTest {
            val skipped = UpdateSettings.Default.copy(skippedVersionCode = UPDATE.versionCode)
            val state = settledState(badgeViewModel(update = UpdateState.Available(UPDATE), settings = skipped))
            assertFalse(state.updateBadge)
        }

    @Test
    fun `the update badge shows for a build newer than the skipped one`() =
        runTest {
            val skipped = UpdateSettings.Default.copy(skippedVersionCode = UPDATE.versionCode - 1)
            val state = settledState(badgeViewModel(update = UpdateState.Available(UPDATE), settings = skipped))
            assertTrue(state.updateBadge)
        }

    @Test
    fun `the update prompt never asks about a skipped build`() =
        runTest {
            val store = FakeUpdateSettingsStore(UpdateSettings.Default.copy(skippedVersionCode = UPDATE.versionCode))
            val viewModel = promptViewModel(update = UpdateState.Available(UPDATE), store = store)
            assertNull(promptAfterDwell(viewModel))
        }

    @Test
    fun `the update badge stays hidden when the build is up to date`() =
        runTest {
            assertFalse(settledState(badgeViewModel(update = UpdateState.UpToDate)).updateBadge)
        }

    @Test
    fun `the update badge stays up once the update is downloaded and verified`() =
        runTest {
            val state = settledState(badgeViewModel(update = UpdateState.Ready(UPDATE, File("update.apk"))))
            assertTrue(state.updateBadge)
        }

    @Test
    fun `the update badge hides once a failure lost the offer`() =
        runTest {
            val lost = UpdateState.Failed(UpdateFailure.INSTALL_CONFLICT, manifest = null)
            assertFalse(settledState(badgeViewModel(update = lost)).updateBadge)
        }

    @Test
    fun `a failing updater costs only the badge`() =
        runTest {
            val weather = fakeWeatherSnapshot()
            val viewModel =
                HomeViewModel(
                    locationFlow = flowOf(fakeLocation()),
                    addressFlow = flowOf(fakeAddress()),
                    weatherFlow = flowOf(weather),
                    musicStateFlow = flowOf(MusicCardState.Playing(fakeNowPlaying())),
                    calendarFlow = flowOf(fakeCalendarSnapshot()),
                    systemStatusFlow = flowOf(fakeSystemStatus()),
                    tripStateFlow = flowOf(fakeTripState()),
                    updateStateFlow = flow { throw IllegalStateException("updater broke") },
                )
            val state = settledState(viewModel)
            assertFalse(state.updateBadge)
            assertEquals(weather, state.weather)
        }

    @Test
    fun `an updater that has not resolved yet holds nothing back`() =
        runTest {
            // The updater resolves off the main thread when first collected; until
            // it speaks, the badge slot's seed must keep the dashboard flowing.
            val weather = fakeWeatherSnapshot()
            val viewModel =
                HomeViewModel(
                    locationFlow = flowOf(fakeLocation()),
                    addressFlow = flowOf(fakeAddress()),
                    weatherFlow = flowOf(weather),
                    musicStateFlow = flowOf(MusicCardState.Playing(fakeNowPlaying())),
                    calendarFlow = flowOf(fakeCalendarSnapshot()),
                    systemStatusFlow = flowOf(fakeSystemStatus()),
                    tripStateFlow = flowOf(fakeTripState()),
                    updateStateFlow = flow { awaitCancellation() },
                )
            val state = settledState(viewModel)
            assertEquals(weather, state.weather)
            assertFalse(state.updateBadge)
        }

    // --- Update prompt -----------------------------------------------------------
    //
    // The prompt waits for UPDATE_PROMPT_PARKED_DWELL_MS of unbroken PARKED, so
    // these run on virtual time with a receiver fixing once a second (see
    // promptViewModel) and judge the prompt only once the dwell is over:
    // otherwise a test for any other gate would pass on the dwell alone.

    @Test
    fun `the update prompt asks about an available update once the vehicle has been parked for the dwell`() =
        runTest {
            assertEquals(UPDATE, promptAfterDwell(promptViewModel(update = UpdateState.Available(UPDATE))))
        }

    @Test
    fun `the update prompt waits out a stop a second shorter than the dwell`() =
        runTest {
            // Most traffic-light stops are shorter than the dwell.
            val viewModel = promptViewModel(update = UpdateState.Available(UPDATE))
            watch(viewModel)

            at(UPDATE_PROMPT_PARKED_DWELL_MS - 1_000)

            assertNull(viewModel.updatePrompt.value)
        }

    @Test
    fun `the dot shows at once while the prompt waits for the dwell`() =
        runTest {
            val viewModel = promptViewModel(update = UpdateState.Available(UPDATE))
            watch(viewModel)
            runCurrent()

            assertTrue(viewModel.uiState.value.updateBadge)
            assertNull(viewModel.updatePrompt.value)
        }

    @Test
    fun `a MOVING reading 30 s into the dwell starts the count over`() =
        runTest {
            val trip = MutableStateFlow(fakeTripState(currentSpeedMs = 0.0))
            val viewModel = promptViewModel(update = UpdateState.Available(UPDATE), tripState = trip)
            watch(viewModel)

            at(30_000)
            trip.value = fakeTripState(currentSpeedMs = MIN_MOVING_SPEED_MS + 10.0)
            at(31_000)
            trip.value = fakeTripState(currentSpeedMs = 0.0)
            at(UPDATE_PROMPT_PARKED_DWELL_MS)
            assertNull(viewModel.updatePrompt.value)

            at(31_000 + UPDATE_PROMPT_PARKED_DWELL_MS)
            assertEquals(UPDATE, viewModel.updatePrompt.value)
        }

    @Test
    fun `an UNKNOWN reading 30 s into the dwell starts the count over`() =
        runTest {
            // The receiver goes quiet after its fix at 20 s, so the parked verdict
            // ages out to UNKNOWN at 30 s; fixes resume at 31 s.
            val fixes = liveGpsFixes(silentSeconds = 21L..30L)
            val viewModel = promptViewModel(update = UpdateState.Available(UPDATE), fixes = fixes)
            watch(viewModel)

            at(UPDATE_PROMPT_PARKED_DWELL_MS)
            assertNull(viewModel.updatePrompt.value)

            at(31_000 + UPDATE_PROMPT_PARKED_DWELL_MS)
            assertEquals(UPDATE, viewModel.updatePrompt.value)
        }

    @Test
    fun `a prompt left on screen never comes back stale when the dashboard returns`() =
        runTest {
            // Weather that answers the first subscription only, the way a forecast
            // can take seconds after a drive: the dashboard's combined state waits
            // on it, so a prompt held there would show whatever it last was.
            var weatherSubscriptions = 0
            val weather =
                flow {
                    if (weatherSubscriptions++ == 0) emit(fakeWeatherSnapshot())
                    awaitCancellation()
                }
            val trip = MutableStateFlow(fakeTripState(currentSpeedMs = 0.0))
            val viewModel =
                promptViewModel(update = UpdateState.Available(UPDATE), tripState = trip, weather = weather)
            val onScreen = watch(viewModel)
            at(UPDATE_PROMPT_PARKED_DWELL_MS)
            assertEquals(UPDATE, viewModel.updatePrompt.value)

            // Another app takes the screen for longer than the subscription grace,
            // and the vehicle moves off meanwhile.
            onScreen.cancel()
            at(currentTime + UI_SUBSCRIPTION_GRACE_MS + 1_000)
            trip.value = fakeTripState(currentSpeedMs = MIN_MOVING_SPEED_MS + 10.0)
            watch(viewModel)
            runCurrent()

            assertNull(viewModel.updatePrompt.value)
        }

    @Test
    fun `a prompt left on screen is gone when the dashboard returns a second later, before any new fix`() =
        runTest {
            // The receiver goes quiet once the dashboard leaves at 60 s: the
            // vehicle can move off with no fix yet to say so.
            val fixes = liveGpsFixes(silentSeconds = 61L..Long.MAX_VALUE)
            val viewModel = promptViewModel(update = UpdateState.Available(UPDATE), fixes = fixes)
            val onScreen = watch(viewModel)
            at(UPDATE_PROMPT_PARKED_DWELL_MS)
            assertEquals(UPDATE, viewModel.updatePrompt.value)

            onScreen.cancel()
            at(UPDATE_PROMPT_PARKED_DWELL_MS + 1_000)
            watch(viewModel)
            runCurrent()

            assertNull(viewModel.updatePrompt.value)
        }

    @Test
    fun `the update prompt asks about a verified download too`() =
        runTest {
            val ready = UpdateState.Ready(UPDATE, File("update.apk"))
            assertEquals(UPDATE, promptAfterDwell(promptViewModel(update = ready)))
        }

    @Test
    fun `the update prompt leaves an update under way, a failed one and none at all to Settings`() =
        runTest {
            listOf(
                UpdateState.Downloading(UPDATE, fraction = 0.5f),
                UpdateState.Installing(UPDATE, sessionId = 7),
                UpdateState.Failed(UpdateFailure.NETWORK, UPDATE),
                UpdateState.UpToDate,
            ).forEach { state ->
                assertNull(promptAfterDwell(promptViewModel(update = state)), "prompt for $state")
            }
        }

    @Test
    fun `the update prompt waits while a fix shows the vehicle moving`() =
        runTest {
            val moving = fakeTripState(currentSpeedMs = MIN_MOVING_SPEED_MS + 10.0)
            val viewModel = promptViewModel(update = UpdateState.Available(UPDATE), tripState = flowOf(moving))
            assertNull(promptAfterDwell(viewModel))
        }

    @Test
    fun `the update prompt waits without a fix`() =
        runTest {
            // Fail-closed, like the dock's dot: no fix never counts as parked.
            val viewModel = promptViewModel(update = UpdateState.Available(UPDATE), fixes = flowOf(null))
            assertNull(promptAfterDwell(viewModel))
        }

    @Test
    fun `the update prompt waits on a stale GPS fix`() =
        runTest {
            val seed = liveGpsFix().apply { elapsedRealtimeNanos = BADGE_NOW - STALE_FIX_AGE_NANOS }
            val viewModel = promptViewModel(update = UpdateState.Available(UPDATE), fixes = flowOf(seed))
            assertNull(promptAfterDwell(viewModel))
        }

    @Test
    fun `a recorded version, asked about before or shown in Settings, is not asked about again`() =
        runTest {
            val store = FakeUpdateSettingsStore(UpdateSettings.Default.copy(promptedVersionCode = UPDATE.versionCode))
            val viewModel = promptViewModel(update = UpdateState.Available(UPDATE), store = store)
            assertNull(promptAfterDwell(viewModel))
        }

    @Test
    fun `a build newer than the recorded one is asked about`() =
        runTest {
            val store =
                FakeUpdateSettingsStore(UpdateSettings.Default.copy(promptedVersionCode = UPDATE.versionCode - 1))
            val viewModel = promptViewModel(update = UpdateState.Available(UPDATE), store = store)
            assertEquals(UPDATE, promptAfterDwell(viewModel))
        }

    @Test
    fun `Later records the version, closes the prompt and keeps the dot`() =
        runTest {
            val store = FakeUpdateSettingsStore()
            val viewModel = promptViewModel(update = UpdateState.Available(UPDATE), store = store)
            promptAfterDwell(viewModel)

            viewModel.onAction(HomeAction.UpdateLater(UPDATE.versionCode))
            runCurrent()

            assertEquals(UPDATE.versionCode, store.current.promptedVersionCode)
            assertNull(viewModel.updatePrompt.value)
            assertTrue(viewModel.uiState.value.updateBadge)
        }

    @Test
    fun `a failing update store fails open and still asks`() =
        runTest {
            // A broken store may ask again after a restart; within the process,
            // the answers kept in memory still hold.
            val broken = flow<UpdateSettings> { throw IllegalStateException("update store broke") }
            val viewModel = promptViewModel(update = UpdateState.Available(UPDATE), settings = broken)
            assertEquals(UPDATE, promptAfterDwell(viewModel))
        }

    @Test
    fun `an answer closes the prompt even when the store loses its record`() =
        runTest {
            // A full or damaged disk must not leave a dialog that no answer closes.
            val viewModel = promptViewModel(update = UpdateState.Available(UPDATE), record = {})
            promptAfterDwell(viewModel)

            viewModel.onAction(HomeAction.UpdateLater(UPDATE.versionCode))
            runCurrent()

            assertNull(viewModel.updatePrompt.value)
        }

    @Test
    fun `Update records the version and opens Settings to start the one-tap update`() =
        runTest {
            val store = FakeUpdateSettingsStore()
            val viewModel = promptViewModel(update = UpdateState.Available(UPDATE), store = store)
            viewModel.events.test {
                viewModel.onAction(HomeAction.UpdateNow(UPDATE.versionCode))
                assertEquals(HomeEvent.OpenInAppSettings(startUpdate = true), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            runCurrent()

            assertEquals(UPDATE.versionCode, store.current.promptedVersionCode)
        }

    @Test
    fun `the prompt closes unrecorded once the vehicle moves, and asks again after the next full dwell`() =
        runTest {
            val store = FakeUpdateSettingsStore()
            val trip = MutableStateFlow(fakeTripState(currentSpeedMs = 0.0))
            val viewModel = promptViewModel(update = UpdateState.Available(UPDATE), store = store, tripState = trip)
            assertEquals(UPDATE, promptAfterDwell(viewModel))

            trip.value = fakeTripState(currentSpeedMs = MIN_MOVING_SPEED_MS + 10.0)
            runCurrent()
            assertNull(viewModel.updatePrompt.value)
            assertNull(store.current.promptedVersionCode)

            trip.value = fakeTripState(currentSpeedMs = 0.0)
            runCurrent()
            assertNull(viewModel.updatePrompt.value)
            at(currentTime + UPDATE_PROMPT_PARKED_DWELL_MS)
            assertEquals(UPDATE, viewModel.updatePrompt.value)
        }

    // Subscribes (WhileUiSubscribed runs the combine only while collected) and
    // returns the state once every source has emitted. The clock stays put: a
    // parked verdict ages out after LOCATION_STALE_THRESHOLD_MS without a new
    // fix, and these sources emit one fix each.
    private fun TestScope.settledState(viewModel: HomeViewModel): HomeUiState {
        backgroundScope.launch { viewModel.uiState.collect { } }
        runCurrent()
        return viewModel.uiState.value
    }

    // Every source emits, so the combine settles. The motion inputs default to
    // parked with a live GPS fix, so each test moves exactly one badge input.
    // The clock is pinned: Robolectric's boot clock starts at zero.
    private fun badgeViewModel(
        update: UpdateState,
        location: Location? = liveGpsFix(),
        tripState: TripState = fakeTripState(currentSpeedMs = 0.0),
        settings: UpdateSettings = UpdateSettings.Default,
    ): HomeViewModel =
        HomeViewModel(
            locationFlow = flowOf(location),
            addressFlow = flowOf(fakeAddress()),
            weatherFlow = flowOf(fakeWeatherSnapshot()),
            musicStateFlow = flowOf(MusicCardState.Playing(fakeNowPlaying())),
            calendarFlow = flowOf(fakeCalendarSnapshot()),
            systemStatusFlow = flowOf(fakeSystemStatus()),
            tripStateFlow = flowOf(tripState),
            updateStateFlow = flowOf(update),
            updateSettingsFlow = flowOf(settings),
            nowElapsedRealtimeNanos = { BADGE_NOW },
        )

    // Subscribes the way the dashboard does, to its combined state and to the
    // update prompt; cancel the job to take the dashboard off screen.
    private fun TestScope.watch(viewModel: HomeViewModel): Job =
        backgroundScope.launch {
            launch { viewModel.uiState.collect { } }
            launch { viewModel.updatePrompt.collect { } }
        }

    // Watches, lets a full dwell pass on the virtual clock, and returns the
    // prompt then.
    private fun TestScope.promptAfterDwell(viewModel: HomeViewModel): UpdateManifest? {
        watch(viewModel)
        runCurrent()
        advanceTimeBy(UPDATE_PROMPT_PARKED_DWELL_MS)
        runCurrent()
        return viewModel.updatePrompt.value
    }

    // Runs the virtual clock to [timeMs] since the test started.
    private fun TestScope.at(timeMs: Long) {
        advanceTimeBy(timeMs - currentTime)
        runCurrent()
    }

    // The boot clock, read off the virtual one, so each fix is judged live.
    private fun TestScope.virtualNowNanos(): Long = BADGE_NOW + currentTime * 1_000_000

    // A GPS receiver fixing once a second on the virtual clock, silent in the
    // [silentSeconds] since the test started.
    private fun TestScope.liveGpsFixes(silentSeconds: LongRange = LongRange.EMPTY): Flow<Location?> =
        flow {
            while (true) {
                if (currentTime / 1_000 !in silentSeconds) {
                    emit(
                        fakeLocation(provider = LocationManager.GPS_PROVIDER, elapsedRealtimeNanos = virtualNowNanos()),
                    )
                }
                delay(1_000)
            }
        }

    // badgeViewModel's sources, plus the updater's own store (the record the
    // prompt reads and its answers write), on the virtual clock: [fixes]
    // defaults to a receiver fixing once a second, since a single fix ages out
    // long before the dwell ends.
    private fun TestScope.promptViewModel(
        update: UpdateState,
        fixes: Flow<Location?> = liveGpsFixes(),
        tripState: Flow<TripState> = flowOf(fakeTripState(currentSpeedMs = 0.0)),
        weather: Flow<WeatherSnapshot?> = flowOf(fakeWeatherSnapshot()),
        store: FakeUpdateSettingsStore = FakeUpdateSettingsStore(),
        settings: Flow<UpdateSettings> = store.settings,
        record: suspend (Int) -> Unit = store::recordPrompted,
    ): HomeViewModel =
        HomeViewModel(
            locationFlow = fixes,
            addressFlow = flowOf(fakeAddress()),
            weatherFlow = weather,
            musicStateFlow = flowOf(MusicCardState.Playing(fakeNowPlaying())),
            calendarFlow = flowOf(fakeCalendarSnapshot()),
            systemStatusFlow = flowOf(fakeSystemStatus()),
            tripStateFlow = tripState,
            updateStateFlow = flowOf(update),
            updateSettingsFlow = settings,
            recordUpdatePrompted = record,
            nowElapsedRealtimeNanos = { virtualNowNanos() },
        )

    private fun liveGpsFix(): Location =
        fakeLocation(provider = LocationManager.GPS_PROVIDER, elapsedRealtimeNanos = BADGE_NOW)

    /**
     * Build a view-model whose spectrum source maps the derived active gate
     * straight to [bands], so the assertions above pin the gating logic
     * (enabled AND Playing AND isPlaying) without a real Visualizer.
     */
    private fun spectrumViewModel(
        enabled: Boolean,
        musicState: MusicCardState,
        bands: FloatArray,
    ): HomeViewModel =
        // Every source must emit: the uiState combine (which the spectrum gate
        // derives its music state from) holds Initial until all sources have a
        // first value, and an emptyFlow source would keep the gate shut.
        HomeViewModel(
            locationFlow = flowOf(fakeLocation()),
            addressFlow = flowOf(fakeAddress()),
            weatherFlow = flowOf(fakeWeatherSnapshot()),
            musicStateFlow = flowOf(musicState),
            calendarFlow = flowOf(fakeCalendarSnapshot()),
            systemStatusFlow = flowOf(fakeSystemStatus()),
            tripStateFlow = flowOf(fakeTripState()),
            spectrumEnabledFlow = flowOf(enabled),
            spectrumBandsFor = { active -> active.map { if (it) bands else null } },
        )

    private suspend fun HomeViewModel.assertEvent(
        action: HomeAction,
        expected: HomeEvent,
    ) {
        events.test {
            onAction(action)
            assertEquals(expected, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun stubViewModel(
        sendMusicCommand: (MusicCommand) -> Unit = {},
        resumeLastMusicSession: () -> Unit = {},
        resetTrip: () -> Unit = {},
        resolveMusicSourceComponent: (String) -> ComponentName? = { null },
    ): HomeViewModel =
        HomeViewModel(
            locationFlow = emptyFlow(),
            addressFlow = emptyFlow(),
            weatherFlow = emptyFlow(),
            musicStateFlow = emptyFlow(),
            calendarFlow = emptyFlow(),
            systemStatusFlow = emptyFlow(),
            tripStateFlow = emptyFlow(),
            sendMusicCommand = sendMusicCommand,
            resumeLastMusicSession = resumeLastMusicSession,
            resetTrip = resetTrip,
            resolveMusicSourceComponent = resolveMusicSourceComponent,
        )

    private companion object {
        val UPDATE = fakeUpdateManifest(versionCode = 26092501)

        // An hour into the boot clock, so a fix can be dated before "now".
        const val BADGE_NOW = 3_600_000_000_000L

        // A minute old: a cached seed from before this subscription.
        const val STALE_FIX_AGE_NANOS = 60_000_000_000L
    }
}

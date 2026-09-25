package io.github.seijikohara.femto.ui.home

import android.content.ComponentName
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import app.cash.turbine.test
import io.github.seijikohara.femto.data.dock.DockNavId
import io.github.seijikohara.femto.data.dock.DockStatusId
import io.github.seijikohara.femto.data.location.MIN_MOVING_SPEED_MS
import io.github.seijikohara.femto.data.location.TripState
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.MusicCommand
import io.github.seijikohara.femto.data.music.SPECTRUM_BAND_COUNT
import io.github.seijikohara.femto.data.update.UpdateFailure
import io.github.seijikohara.femto.data.update.UpdateState
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
            // Distinct from HomeUiState.Initial.online (true) so the assertion pins
            // this field to its own flow rather than the default.
            val online = false
            val viewModel =
                HomeViewModel(
                    locationFlow = flowOf(location),
                    addressFlow = flowOf(address),
                    weatherFlow = flowOf(weather),
                    musicStateFlow = flowOf(musicState),
                    calendarFlow = flowOf(calendar),
                    systemStatusFlow = flowOf(systemStatus),
                    tripStateFlow = flowOf(tripState),
                    onlineFlow = flowOf(online),
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
                assertEquals(online, state.online)
                cancelAndIgnoreRemainingEvents()
            }
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
                expected = HomeEvent.OpenInAppSettings,
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

    // Subscribes (WhileUiSubscribed runs the combine only while collected) and
    // returns the state once every source has emitted.
    private fun TestScope.settledState(viewModel: HomeViewModel): HomeUiState {
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        return viewModel.uiState.value
    }

    // Every source emits, so the combine settles. The motion inputs default to
    // parked with a live GPS fix, so each test moves exactly one badge input.
    // The clock is pinned: Robolectric's boot clock starts at zero.
    private fun badgeViewModel(
        update: UpdateState,
        location: Location? = liveGpsFix(),
        tripState: TripState = fakeTripState(currentSpeedMs = 0.0),
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
            nowElapsedRealtimeNanos = { BADGE_NOW },
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

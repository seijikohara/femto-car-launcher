package io.github.seijikohara.femto.ui.home

import android.content.ComponentName
import android.content.Intent
import app.cash.turbine.test
import io.github.seijikohara.femto.data.clock.ClockTick
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.MusicCommand
import io.github.seijikohara.femto.data.music.SPECTRUM_BAND_COUNT
import io.github.seijikohara.femto.testfixtures.fakeAddress
import io.github.seijikohara.femto.testfixtures.fakeCalendarSnapshot
import io.github.seijikohara.femto.testfixtures.fakeLocation
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.testfixtures.fakeSystemStatus
import io.github.seijikohara.femto.testfixtures.fakeTripState
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.home.components.AppsBarShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
            val clock = ClockTick(LocalTime.of(14, 32), LocalDate.of(2026, 5, 1))
            val location = fakeLocation()
            val address = fakeAddress()
            val weather = fakeWeatherSnapshot()
            val musicState = MusicCardState.Playing(fakeNowPlaying())
            val calendar = fakeCalendarSnapshot()
            val systemStatus = fakeSystemStatus()
            val tripState = fakeTripState()
            val viewModel =
                HomeViewModel(
                    clockFlow = flowOf(clock),
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
                assertEquals(clock, state.clock)
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

    @Test
    fun `onAction OpenAppDrawer emits OpenDrawer event`() =
        runTest {
            stubViewModel().assertEvent(HomeAction.OpenAppDrawer, HomeEvent.OpenDrawer)
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
                    clockFlow = flowOf(ClockTick(LocalTime.of(14, 32), LocalDate.of(2026, 5, 1))),
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
    fun `onAction OpenSettings emits OpenInAppSettings`() =
        runTest {
            stubViewModel().assertEvent(
                action = HomeAction.OpenSettings,
                expected = HomeEvent.OpenInAppSettings,
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
            val component = ComponentName("com.spotify.music", "com.spotify.music.MainActivity")
            stubViewModel(resolveMusicSourceComponent = { pkg -> component.takeIf { pkg == "com.spotify.music" } })
                .assertEvent(
                    action = HomeAction.LaunchMusicSource("com.spotify.music"),
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
            clockFlow = flowOf(ClockTick(LocalTime.of(14, 32), LocalDate.of(2026, 5, 1))),
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
        resetTrip: () -> Unit = {},
        resolveMusicSourceComponent: (String) -> ComponentName? = { null },
    ): HomeViewModel =
        HomeViewModel(
            clockFlow = emptyFlow(),
            locationFlow = emptyFlow(),
            addressFlow = emptyFlow(),
            weatherFlow = emptyFlow(),
            musicStateFlow = emptyFlow(),
            calendarFlow = emptyFlow(),
            systemStatusFlow = emptyFlow(),
            tripStateFlow = emptyFlow(),
            sendMusicCommand = sendMusicCommand,
            resetTrip = resetTrip,
            resolveMusicSourceComponent = resolveMusicSourceComponent,
        )
}

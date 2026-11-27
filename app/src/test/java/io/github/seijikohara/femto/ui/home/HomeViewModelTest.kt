package io.github.seijikohara.femto.ui.home

import android.content.ComponentName
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import io.github.seijikohara.femto.data.common.UI_SUBSCRIPTION_GRACE_MS
import io.github.seijikohara.femto.data.dock.DockNavId
import io.github.seijikohara.femto.data.dock.DockStatusId
import io.github.seijikohara.femto.data.location.MAX_GAP_SECONDS
import io.github.seijikohara.femto.data.location.MIN_MOVING_SPEED_MS
import io.github.seijikohara.femto.data.location.TripState
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.MusicCommand
import io.github.seijikohara.femto.data.music.SPECTRUM_BAND_COUNT
import io.github.seijikohara.femto.data.places.PlaceTarget
import io.github.seijikohara.femto.data.places.ReceivedPlace
import io.github.seijikohara.femto.data.places.ReceivedPlaceInbox
import io.github.seijikohara.femto.data.routing.ActiveRoute
import io.github.seijikohara.femto.data.routing.NavigationSettings
import io.github.seijikohara.femto.data.routing.OnLinePoint
import io.github.seijikohara.femto.data.routing.RerouteStatus
import io.github.seijikohara.femto.data.routing.Route
import io.github.seijikohara.femto.data.routing.RouteCandidate
import io.github.seijikohara.femto.data.routing.RouteFix
import io.github.seijikohara.femto.data.routing.RoutePoint
import io.github.seijikohara.femto.data.routing.RouteProgress
import io.github.seijikohara.femto.data.routing.RouteRowTime
import io.github.seijikohara.femto.data.routing.RouteTurn
import io.github.seijikohara.femto.data.routing.TurnKind
import io.github.seijikohara.femto.data.update.UpdateFailure
import io.github.seijikohara.femto.data.update.UpdateManifest
import io.github.seijikohara.femto.data.update.UpdateSettings
import io.github.seijikohara.femto.data.update.UpdateState
import io.github.seijikohara.femto.data.weather.WeatherSnapshot
import io.github.seijikohara.femto.testfixtures.FAKE_MUSIC_PACKAGE
import io.github.seijikohara.femto.testfixtures.FakeUpdateSettingsStore
import io.github.seijikohara.femto.testfixtures.fakeAddress
import io.github.seijikohara.femto.testfixtures.fakeCalendarSnapshot
import io.github.seijikohara.femto.testfixtures.fakeEquatorRoute
import io.github.seijikohara.femto.testfixtures.fakeLastPlayedState
import io.github.seijikohara.femto.testfixtures.fakeLocation
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.testfixtures.fakeOutAndBackRoute
import io.github.seijikohara.femto.testfixtures.fakeParallelRoadsRoute
import io.github.seijikohara.femto.testfixtures.fakeRoute
import io.github.seijikohara.femto.testfixtures.fakeRouteFix
import io.github.seijikohara.femto.testfixtures.fakeSystemStatus
import io.github.seijikohara.femto.testfixtures.fakeTripState
import io.github.seijikohara.femto.testfixtures.fakeUpdateManifest
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import io.github.seijikohara.femto.ui.home.components.AppsBarShortcut
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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

    // The view model follows a route on Dispatchers.Default unless it is given another dispatcher. A worker thread of
    // that pool can still be handing a value to Dispatchers.Main when tearDown resets it, which the test dispatcher
    // reports as "Dispatchers.Main is used concurrently with setting it" in whichever test is running. So every view
    // model in this file follows the route on the test's own thread.
    private fun testRouteDispatcher(): CoroutineDispatcher = UnconfinedTestDispatcher()

    // A view model built here without it would bring the worker thread back, and fail a test only once in a long while.
    @Test
    fun `every view model in this file follows the route on a test dispatcher`() {
        val source = File("src/test/java/io/github/seijikohara/femto/ui/home/HomeViewModelTest.kt").readText()

        assertEquals(
            Regex("""\bHomeViewModel\(\n""").findAll(source).count(),
            Regex("""\brouteDispatcher\s=\s""").findAll(source).count(),
        )
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
                    routeDispatcher = testRouteDispatcher(),
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
                    routeDispatcher = testRouteDispatcher(),
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
                    routeDispatcher = testRouteDispatcher(),
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
                    routeDispatcher = testRouteDispatcher(),
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
                    routeDispatcher = testRouteDispatcher(),
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

    // The dashboard of a recreated activity (a rotation, or one the system
    // finished while the launcher was away) reads the same process inbox: once
    // the panel has taken the place, it must find nothing to open again.
    @Test
    fun `a received place reaches the dashboard once and not after a recreation`() {
        val inbox = ReceivedPlaceInbox()
        val place = ReceivedPlace.Found(PlaceTarget.Query("Matsuyama Castle"))
        inbox.offer(place)
        val first = stubViewModel(receivedPlaces = inbox)
        assertEquals(place, first.receivedPlace.value)

        first.onAction(HomeAction.ReceivedPlaceHandled(place))

        assertNull(stubViewModel(receivedPlaces = inbox).receivedPlace.value)
    }

    @Test
    fun `onAction Navigate with a typed query emits LaunchDestination for the query`() =
        runTest {
            stubViewModel().assertEvent(
                action = HomeAction.Navigate(PlaceTarget.Query("1st & Pike")),
                expected = HomeEvent.LaunchDestination(PlaceTarget.Query("1st & Pike"), label = ""),
            )
        }

    @Test
    fun `onAction Navigate with a saved place emits LaunchDestination carrying its label`() =
        runTest {
            val point = PlaceTarget.Point(35.681236, 139.767125)
            stubViewModel().assertEvent(
                action = HomeAction.Navigate(point, label = "Home"),
                expected = HomeEvent.LaunchDestination(point, label = "Home"),
            )
        }

    @Test
    fun `onAction Shortcut Music emits LaunchAppCategory APP_MUSIC`() =
        runTest {
            stubViewModel().assertEvent(
                action = HomeAction.Shortcut(AppsBarShortcut.Music),
                expected = HomeEvent.LaunchAppCategory(Intent.CATEGORY_APP_MUSIC),
            )
        }

    @Test
    fun `onAction Shortcut Phone emits LaunchDialer, not a contacts category`() =
        runTest {
            stubViewModel().assertEvent(
                action = HomeAction.Shortcut(AppsBarShortcut.Phone),
                expected = HomeEvent.LaunchDialer,
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

    // --- The last played track ---------------------------------------------------

    @Test
    fun `a tap on the last played track resumes it in its app`() =
        runTest {
            val resumed = mutableListOf<String>()
            val viewModel =
                resumeViewModel {
                    resumed += it
                    false
                }

            viewModel.onAction(HomeAction.ResumeLastPlayed(FAKE_MUSIC_PACKAGE))
            runCurrent()

            assertEquals(listOf(FAKE_MUSIC_PACKAGE), resumed)
        }

    @Test
    fun `a second tap while an attempt runs does nothing`() =
        runTest {
            var attempts = 0
            val viewModel =
                resumeViewModel {
                    attempts++
                    awaitCancellation()
                }

            repeat(2) { viewModel.onAction(HomeAction.ResumeLastPlayed(FAKE_MUSIC_PACKAGE)) }
            runCurrent()

            assertEquals(1, attempts)
        }

    @Test
    fun `the card shows a running attempt as resuming`() =
        runTest {
            val viewModel = resumeViewModel { awaitCancellation() }

            viewModel.onAction(HomeAction.ResumeLastPlayed(FAKE_MUSIC_PACKAGE))
            runCurrent()

            assertEquals(fakeLastPlayedState(resuming = true), viewModel.uiState.value.musicState)
        }

    @Test
    fun `the card is usable again once an attempt ends without playback`() =
        runTest {
            val attemptDone = CompletableDeferred<Boolean>()
            var attempts = 0
            val viewModel =
                resumeViewModel {
                    attempts++
                    attemptDone.await()
                }
            viewModel.onAction(HomeAction.ResumeLastPlayed(FAKE_MUSIC_PACKAGE))
            runCurrent()

            attemptDone.complete(false)
            runCurrent()
            assertEquals(fakeLastPlayedState(), viewModel.uiState.value.musicState)

            viewModel.onAction(HomeAction.ResumeLastPlayed(FAKE_MUSIC_PACKAGE))
            runCurrent()
            assertEquals(2, attempts)
        }

    @Test
    fun `after playback starts the button stays pending until the card leaves the track`() =
        runTest {
            // The attempt sees the app play before the card's own state arrives.
            val music = MutableStateFlow<MusicCardState>(fakeLastPlayedState())
            var attempts = 0
            val viewModel =
                resumeViewModel(musicStateFlow = music) {
                    attempts++
                    true
                }
            viewModel.onAction(HomeAction.ResumeLastPlayed(FAKE_MUSIC_PACKAGE))
            runCurrent()

            viewModel.onAction(HomeAction.ResumeLastPlayed(FAKE_MUSIC_PACKAGE))
            runCurrent()
            assertEquals(fakeLastPlayedState(resuming = true), viewModel.uiState.value.musicState)
            assertEquals(1, attempts)

            music.value = MusicCardState.Playing(fakeNowPlaying())
            runCurrent()
            // The live session ends and the track is offered again: the button is free.
            music.value = fakeLastPlayedState()
            runCurrent()
            assertEquals(fakeLastPlayedState(), viewModel.uiState.value.musicState)
        }

    @Test
    fun `a card that never hears of the playback frees the button after the grace`() =
        runTest {
            val viewModel = resumeViewModel { true }
            viewModel.onAction(HomeAction.ResumeLastPlayed(FAKE_MUSIC_PACKAGE))
            runCurrent()

            advanceTimeBy(RESUME_SETTLE_GRACE_MS - 1)
            runCurrent()
            assertEquals(fakeLastPlayedState(resuming = true), viewModel.uiState.value.musicState)

            advanceTimeBy(2)
            runCurrent()
            assertEquals(fakeLastPlayedState(), viewModel.uiState.value.musicState)
        }

    @Test
    fun `waiting for the card after playback starts subscribes to no dashboard source`() =
        runTest {
            // The driver taps, then leaves the dashboard while the app buffers;
            // the app plays once the dashboard's sources have stopped. Waiting
            // on uiState would subscribe to it and start every source again.
            var calendarCollections = 0
            val attemptPlays = CompletableDeferred<Boolean>()
            val viewModel =
                HomeViewModel(
                    locationFlow = flowOf(fakeLocation()),
                    addressFlow = flowOf(fakeAddress()),
                    weatherFlow = flowOf(fakeWeatherSnapshot()),
                    musicStateFlow = flowOf(fakeLastPlayedState()),
                    calendarFlow =
                        flow {
                            calendarCollections++
                            emit(fakeCalendarSnapshot())
                        },
                    systemStatusFlow = flowOf(fakeSystemStatus()),
                    tripStateFlow = flowOf(fakeTripState()),
                    resumeLastPlayed = { attemptPlays.await() },
                    routeDispatcher = testRouteDispatcher(),
                )
            val dashboard = backgroundScope.launch { viewModel.uiState.collect { } }
            runCurrent()
            viewModel.onAction(HomeAction.ResumeLastPlayed(FAKE_MUSIC_PACKAGE))
            runCurrent()

            dashboard.cancel()
            advanceTimeBy(UI_SUBSCRIPTION_GRACE_MS + 1)
            attemptPlays.complete(true)
            advanceTimeBy(RESUME_SETTLE_GRACE_MS + 1)
            runCurrent()

            assertEquals(1, calendarCollections)
        }

    @Test
    fun `a tap that lands after a live session took the card starts nothing`() =
        runTest {
            var attempts = 0
            val viewModel =
                resumeViewModel(musicStateFlow = flowOf(MusicCardState.Playing(fakeNowPlaying()))) {
                    attempts++
                    false
                }

            viewModel.onAction(HomeAction.ResumeLastPlayed(FAKE_MUSIC_PACKAGE))
            runCurrent()

            assertEquals(0, attempts)
        }

    @Test
    fun `a tap naming an app the card does not offer starts nothing`() =
        runTest {
            var attempts = 0
            val viewModel =
                resumeViewModel {
                    attempts++
                    false
                }

            viewModel.onAction(HomeAction.ResumeLastPlayed("com.example.other"))
            runCurrent()

            assertEquals(0, attempts)
        }

    @Test
    fun `clearing the dashboard cancels a running attempt`() =
        runTest {
            var cancelled = false
            val viewModel =
                resumeViewModel {
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled = true
                    }
                }
            viewModel.onAction(HomeAction.ResumeLastPlayed(FAKE_MUSIC_PACKAGE))
            runCurrent()

            // What onCleared does: the attempt runs in viewModelScope, which
            // closes with the ViewModel.
            viewModel.viewModelScope.cancel()
            runCurrent()

            assertTrue(cancelled)
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
                    routeDispatcher = testRouteDispatcher(),
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
                    routeDispatcher = testRouteDispatcher(),
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
            routeDispatcher = testRouteDispatcher(),
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
            routeDispatcher = testRouteDispatcher(),
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
            routeDispatcher = testRouteDispatcher(),
        )

    // Every source emits, so the card holds [musicState]; subscribed the way the
    // dashboard is, so the action reads the card on screen.
    private fun TestScope.resumeViewModel(
        musicStateFlow: Flow<MusicCardState> = flowOf(fakeLastPlayedState()),
        resume: suspend (String) -> Boolean,
    ): HomeViewModel =
        HomeViewModel(
            locationFlow = flowOf(fakeLocation()),
            addressFlow = flowOf(fakeAddress()),
            weatherFlow = flowOf(fakeWeatherSnapshot()),
            musicStateFlow = musicStateFlow,
            calendarFlow = flowOf(fakeCalendarSnapshot()),
            systemStatusFlow = flowOf(fakeSystemStatus()),
            tripStateFlow = flowOf(fakeTripState()),
            resumeLastPlayed = resume,
            routeDispatcher = testRouteDispatcher(),
        ).also { settledState(it) }

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

    // --- Active route (issue #389 phase 3) ------------------------------------

    @Test
    fun `an active route shows its line and what is left of it from the fix`() =
        runTest {
            val viewModel = routeViewModel(location = fakeLocation(latitude = 0.0, longitude = 0.1), available = true)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            val route = assertNotNull(viewModel.uiState.value.activeRoute)
            assertEquals(EQUATOR_ROUTE.points, route.line)
            assertEquals(11_000.0, route.progress.remainingMeters, 1.0)
            assertEquals(600.0, route.progress.remainingSeconds, 0.1)
        }

    @Test
    fun `an active route counts from its start until a fix arrives`() =
        runTest {
            val viewModel = routeViewModel(location = null, available = true)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            assertEquals(
                RouteProgress(22_000.0, 1_200.0),
                viewModel.uiState.value.activeRoute
                    ?.progress,
            )
        }

    @Test
    fun `no route shows where routing is not offered`() =
        runTest {
            // A stable build, or the Google backend: the route stays set but hidden.
            val viewModel = routeViewModel(location = fakeLocation(latitude = 0.0, longitude = 0.1), available = false)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            assertEquals(fakeAddress(), viewModel.uiState.value.address, "the dashboard state has arrived")
            assertNull(viewModel.uiState.value.activeRoute)
        }

    @Test
    fun `the dashboard knows where routing is offered, so a long press can pick a destination`() =
        runTest {
            val viewModel = routeViewModel(available = true)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            assertTrue(viewModel.uiState.value.routingAvailable)
        }

    @Test
    fun `where routing is not offered a long press picks nothing`() =
        runTest {
            val viewModel = routeViewModel(available = false)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            assertEquals(fakeAddress(), viewModel.uiState.value.address, "the dashboard state has arrived")
            assertFalse(viewModel.uiState.value.routingAvailable)
        }

    @Test
    fun `the progress carries each fix to the next, so a pass back along the road is not taken for this one`() =
        runTest {
            // On the way out, a fix nearer the way back: judged alone it would
            // leave 3.3 km where 18.9 km are left.
            val location = MutableStateFlow<Location?>(fakeLocation(latitude = 0.0, longitude = 0.029))
            val viewModel =
                routeViewModel(locationFlow = location, available = true, route = fakeOutAndBackRoute())
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            location.value = fakeLocation(latitude = 0.00015, longitude = 0.03)

            assertEquals(
                18_925.0,
                assertNotNull(viewModel.uiState.value.activeRoute).progress.remainingMeters,
                1.0,
            )
        }

    @Test
    fun `onAction ClearRoute clears the active route and emits no event`() =
        runTest {
            var clears = 0
            val viewModel = routeViewModel(location = null, available = true, clearRoute = { clears++ })
            viewModel.events.test {
                viewModel.onAction(HomeAction.ClearRoute)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, clears)
        }

    @Test
    fun `leaving the route reports the session and the fix`() =
        runTest {
            val location = MutableStateFlow<Location?>(null)
            val left = mutableListOf<Pair<Long, RouteFix>>()
            val viewModel =
                routeViewModel(
                    locationFlow = location,
                    available = true,
                    onLeftRoute = { session, fix -> left += session to fix },
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            leaveTheRoute(location)

            assertEquals(
                listOf(
                    SESSION to
                        fakeRouteFix(
                            RoutePoint(OFF_ROUTE_LATITUDE, ROUTE_LONGITUDE),
                            elapsedMs = 6_000L,
                            accuracyMeters = FIX_ACCURACY_M,
                            bearingDegrees = EAST,
                        ),
                ),
                left,
            )
        }

    @Test
    fun `back on the route is reported`() =
        runTest {
            val location = MutableStateFlow<Location?>(null)
            val back = mutableListOf<Long>()
            val viewModel = routeViewModel(locationFlow = location, available = true, onBackOnRoute = { back += it })
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            leaveTheRoute(location)

            // On the line again for two seconds.
            (7..9).forEach { second -> location.value = movingFix(latitude = 0.0, second = second) }

            assertEquals(listOf(SESSION), back)
        }

    @Test
    fun `a status change keeps the progress and shows the status`() =
        runTest {
            // Fixes that are not replayed: a follow that started over would see none and count from the start again.
            val location = MutableSharedFlow<Location?>()
            val active = MutableStateFlow<ActiveRoute?>(activeRouteOf(EQUATOR_ROUTE))
            val viewModel = routeViewModel(locationFlow = location, available = true, activeRouteFlow = active)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            location.emit(fakeLocation(latitude = 0.0, longitude = 0.1))

            active.value = active.value?.copy(reroute = RerouteStatus.REROUTING)

            val route = assertNotNull(viewModel.uiState.value.activeRoute)
            assertEquals(RerouteStatus.REROUTING, route.reroute)
            assertEquals(11_000.0, route.progress.remainingMeters, 1.0)
        }

    @Test
    fun `the route row shows the arrival time until the setting picks the time remaining`() =
        runTest {
            val settings = MutableStateFlow(NavigationSettings.Default)
            val viewModel = routeViewModel(available = true, navigationSettingsFlow = settings)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            assertEquals(
                RouteRowTime.ARRIVAL,
                viewModel.uiState.value.activeRoute
                    ?.rowTime,
            )

            settings.value = settings.value.copy(routeRowTime = RouteRowTime.REMAINING)

            assertEquals(
                RouteRowTime.REMAINING,
                viewModel.uiState.value.activeRoute
                    ?.rowTime,
            )
        }

    // Like the route itself, the setting is seeded, so a slow settings store never holds the dashboard back.
    @Test
    fun `a route row time not yet read shows the route with the arrival time`() =
        runTest {
            val viewModel = routeViewModel(available = true, navigationSettingsFlow = flow { awaitCancellation() })
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            assertEquals(RouteRowTime.ARRIVAL, assertNotNull(viewModel.uiState.value.activeRoute).rowTime)
        }

    // --- The vehicle on the line ------------------------------------------------

    @Test
    fun `the route carries the point of the line the map draws the vehicle at`() =
        runTest {
            val viewModel = routeViewModel(location = besideTheRoute(), available = true)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            assertOnTheLineHalfway(assertNotNull(viewModel.uiState.value.activeRoute).vehicleOnLine)
        }

    @Test
    fun `with the setting off the route carries no point, so the map draws the fix`() =
        runTest {
            val viewModel =
                routeViewModel(
                    location = besideTheRoute(),
                    available = true,
                    navigationSettingsFlow = flowOf(NavigationSettings.Default.copy(vehicleOnRouteLine = false)),
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            val route = assertNotNull(viewModel.uiState.value.activeRoute)
            assertEquals(11_000.0, route.progress.remainingMeters, 1.0, "the route follows the fix")
            assertNull(route.vehicleOnLine)
        }

    // A follow that started over would place no fix until the next one arrives: the progress back at the start, the
    // off-route watch unarmed, and no point on the line.
    @Test
    fun `switching the setting off and on again keeps the follow going`() =
        runTest {
            // Fixes that are not replayed: a follow that started over would see none.
            val location = MutableSharedFlow<Location?>()
            val settings = MutableStateFlow(NavigationSettings.Default)
            val viewModel = routeViewModel(locationFlow = location, available = true, navigationSettingsFlow = settings)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            location.emit(besideTheRoute())

            settings.value = settings.value.copy(vehicleOnRouteLine = false)
            assertNull(assertNotNull(viewModel.uiState.value.activeRoute).vehicleOnLine)
            settings.value = settings.value.copy(vehicleOnRouteLine = true)

            assertOnTheLineHalfway(assertNotNull(viewModel.uiState.value.activeRoute).vehicleOnLine)
        }

    // Cheap GPS receivers leave the speed unset while moving.
    @Test
    fun `a GPS fix without speed moves at the trip's speed, so leaving the route is noticed`() =
        runTest {
            val location = MutableStateFlow<Location?>(null)
            val left = mutableListOf<Pair<Long, RouteFix>>()
            val viewModel =
                routeViewModel(
                    locationFlow = location,
                    available = true,
                    tripState = fakeTripState(currentSpeedMs = TRIP_SPEED_MS),
                    onLeftRoute = { session, fix -> left += session to fix },
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            leaveTheRoute(location, hasSpeed = false)

            assertEquals(
                listOf(SESSION to TRIP_SPEED_MS.toFloat()),
                left.map { (session, fix) ->
                    session to
                        fix.speedMetersPerSecond
                },
            )
        }

    @Test
    fun `a network fix without speed is still left out of the watch`() =
        runTest {
            val location = MutableStateFlow<Location?>(null)
            val left = mutableListOf<Pair<Long, RouteFix>>()
            val viewModel =
                routeViewModel(
                    locationFlow = location,
                    available = true,
                    tripState = fakeTripState(currentSpeedMs = TRIP_SPEED_MS),
                    onLeftRoute = { session, fix -> left += session to fix },
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            leaveTheRoute(location, hasSpeed = false, provider = LocationManager.NETWORK_PROVIDER)

            assertEquals(emptyList(), left)
        }

    // --- The turn card (issue #389 phase 4) ------------------------------------

    @Test
    fun `a route with turn data shows the next turn and its distance on the card`() =
        runTest {
            val viewModel =
                routeViewModel(
                    location = fakeLocation(latitude = 0.0, longitude = ROUTE_LONGITUDE),
                    available = true,
                    route = TURNING_ROUTE,
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            val card = assertNotNull(assertNotNull(viewModel.uiState.value.activeRoute).turnCard)
            assertEquals(TurnManeuver.Turn(TurnKind.LEFT), card.maneuver)
            assertEquals(5_500.0, card.distanceMeters, 1.0)
        }

    // A route kept by a build before the turn data, or one the engine gave none for: no card, not a destination flag.
    @Test
    fun `a route without turn data shows no card at all`() =
        runTest {
            val viewModel =
                routeViewModel(
                    location = fakeLocation(latitude = 0.0, longitude = ROUTE_LONGITUDE),
                    available = true,
                    route = EQUATOR_ROUTE,
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            val route = assertNotNull(viewModel.uiState.value.activeRoute)
            assertEquals(16_500.0, route.progress.remainingMeters, 1.0, "the route follows the fix")
            assertNull(route.turnCard)
        }

    // A route restored after a restart counts from the start of its line until the first fix, which can lie far
    // behind the vehicle: the card waits for the fix.
    @Test
    fun `a restored route shows no card until the first fix, and the card with it`() =
        runTest {
            // A receiver that has not fixed yet: the dashboard has no position.
            val location = MutableStateFlow<Location?>(null)
            val viewModel = routeViewModel(locationFlow = location, available = true, route = TURNING_ROUTE)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            val beforeTheFix = assertNotNull(viewModel.uiState.value.activeRoute, "the route shows before the fix")

            location.value = beforeTheTurn()

            assertNull(beforeTheFix.turnCard)
            assertEquals(
                5_500.0,
                assertNotNull(
                    viewModel.uiState.value.activeRoute
                        ?.turnCard,
                ).distanceMeters,
                1.0,
            )
        }

    // A driver who switched the card off must not see it flash while the settings store is still being read.
    @Test
    fun `the card stays hidden until the stored settings are read`() =
        runTest {
            val settings = MutableSharedFlow<NavigationSettings>()
            val viewModel =
                routeViewModel(
                    location = beforeTheTurn(),
                    available = true,
                    route = TURNING_ROUTE,
                    navigationSettingsFlow = settings,
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            val notReadYet = assertNotNull(viewModel.uiState.value.activeRoute, "the route shows before the read")

            settings.emit(NavigationSettings.Default)

            assertNull(notReadYet.turnCard)
            assertNotNull(
                viewModel.uiState.value.activeRoute
                    ?.turnCard,
            )
        }

    @Test
    fun `the card shows only once the choice card closes`() =
        runTest {
            val active = MutableStateFlow<ActiveRoute?>(choosingRoute().copy(route = TURNING_ROUTE))
            val viewModel = routeViewModel(location = beforeTheTurn(), available = true, activeRouteFlow = active)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            val whileChoosing = viewModel.uiState.value.activeRoute

            active.value = active.value?.copy(choosing = false, finding = false)

            assertEquals(
                true to null,
                (whileChoosing?.choice != null) to whileChoosing?.turnCard,
                "the choice card shows alone",
            )
            assertEquals(
                TurnManeuver.Turn(TurnKind.LEFT),
                viewModel.uiState.value.activeRoute
                    ?.turnCard
                    ?.maneuver,
            )
        }

    // With "Reroute automatically" off nothing reroutes: the status stays NONE, and the follow alone hides the card.
    @Test
    fun `the card hides while the vehicle is off the route and comes back once it is on it again`() =
        runTest {
            val location = MutableStateFlow<Location?>(null)
            val viewModel = routeViewModel(locationFlow = location, available = true, route = TURNING_ROUTE)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            leaveTheRoute(location)
            val offTheRoute = viewModel.uiState.value.activeRoute

            (7..9).forEach { second -> location.value = movingFix(latitude = 0.0, second = second) }

            assertEquals(
                RerouteStatus.NONE to null,
                offTheRoute?.reroute to offTheRoute?.turnCard,
                "off the route, with no reroute",
            )
            assertNotNull(
                viewModel.uiState.value.activeRoute
                    ?.turnCard,
            )
        }

    @Test
    fun `while a reroute's status shows the card hides`() =
        runTest {
            val active = MutableStateFlow<ActiveRoute?>(activeRouteOf(TURNING_ROUTE))
            val viewModel = routeViewModel(location = beforeTheTurn(), available = true, activeRouteFlow = active)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            assertNotNull(
                viewModel.uiState.value.activeRoute
                    ?.turnCard,
                "the card shows before the reroute",
            )

            active.value = active.value?.copy(reroute = RerouteStatus.REROUTING)

            assertNull(
                viewModel.uiState.value.activeRoute
                    ?.turnCard,
            )
        }

    @Test
    fun `past the last turn the card shows the destination at the distance left, and 0 there`() =
        runTest {
            // A quarter of the way along the second leg, past the turn at its start.
            val location = MutableStateFlow<Location?>(fakeLocation(latitude = 0.0, longitude = 0.15))
            val viewModel = routeViewModel(locationFlow = location, available = true, route = TURNING_ROUTE)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            val pastTheTurn = assertNotNull(
                viewModel.uiState.value.activeRoute
                    ?.turnCard,
            )

            location.value = fakeLocation(latitude = 0.0, longitude = 0.2)

            val atTheEnd = assertNotNull(
                viewModel.uiState.value.activeRoute
                    ?.turnCard,
            )
            assertEquals(TurnManeuver.Arrive to TurnManeuver.Arrive, pastTheTurn.maneuver to atTheEnd.maneuver)
            assertEquals(5_500.0, pastTheTurn.distanceMeters, 1.0)
            assertEquals(0.0, atTheEnd.distanceMeters, 1.0)
        }

    @Test
    fun `with the turn card setting off no card shows`() =
        runTest {
            val viewModel =
                routeViewModel(
                    location = beforeTheTurn(),
                    available = true,
                    route = TURNING_ROUTE,
                    navigationSettingsFlow = flowOf(NavigationSettings.Default.copy(turnCard = false)),
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            assertEquals(
                true to null,
                (viewModel.uiState.value.activeRoute != null) to
                    viewModel.uiState.value.activeRoute
                        ?.turnCard,
            )
        }

    // "Continue straight" counts only with its switch on, and the turn after next shows only with its own.
    @Test
    fun `the turn card's two refinements reach the card`() =
        runTest {
            val settings = MutableStateFlow(NavigationSettings.Default)
            val viewModel =
                routeViewModel(
                    location = beforeTheTurn(),
                    available = true,
                    route = STRAIGHT_THEN_LEFT_ROUTE,
                    navigationSettingsFlow = settings,
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            val shown =
                listOf(
                    NavigationSettings.Default,
                    NavigationSettings.Default.copy(turnCardStraight = true),
                    NavigationSettings.Default.copy(turnCardStraight = true, turnCardNextAfter = false),
                ).map { value ->
                    settings.value = value
                    viewModel.uiState.value.activeRoute
                        ?.turnCard
                        ?.let { it.maneuver to it.then }
                }

            assertEquals(
                listOf(
                    TurnManeuver.Turn(TurnKind.LEFT) to null,
                    TurnManeuver.Turn(TurnKind.STRAIGHT) to TurnManeuver.Turn(TurnKind.LEFT),
                    TurnManeuver.Turn(TurnKind.STRAIGHT) to null,
                ),
                shown,
            )
        }

    // A follow that started over would see no fix until the next one arrives and count the turn from the start of the
    // line again, 11 km away.
    @Test
    fun `switching the turn card off and on again keeps the follow going`() =
        runTest {
            // Fixes that are not replayed: a follow that started over would see none.
            val location = MutableSharedFlow<Location?>()
            val settings = MutableStateFlow(NavigationSettings.Default)
            val viewModel =
                routeViewModel(
                    locationFlow = location,
                    available = true,
                    route = TURNING_ROUTE,
                    navigationSettingsFlow = settings,
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            location.emit(fakeLocation(latitude = 0.0, longitude = ROUTE_LONGITUDE))

            settings.value = settings.value.copy(turnCard = false)
            assertNull(
                viewModel.uiState.value.activeRoute
                    ?.turnCard,
            )
            settings.value = settings.value.copy(turnCard = true)

            assertEquals(
                5_500.0,
                assertNotNull(
                    viewModel.uiState.value.activeRoute
                        ?.turnCard,
                ).distanceMeters,
                1.0,
            )
        }

    // --- The spoken turn prompts -------------------------------------------------

    // The prompts lead by the fix's speed: at 10 m/s the final one is due within 60 m, and the left turn at the halfway
    // point of the route lies about 40 m ahead of this fix.
    // Settings that never complete, as the stored ones never do: a finished flow would end the dashboard's collection
    // and close the voice with it, which the tests below that watch the voice stay open would read as a close.
    private val stillOpenSettings = MutableStateFlow(NavigationSettings.Default)

    private fun insideTheFinalLead(): Location =
        fakeLocation(latitude = 0.0, longitude = NEAR_TURN_LONGITUDE, speedMps = 10f)

    @Test
    fun `a fix inside the final lead speaks the final prompt once and opens the engine`() =
        runTest {
            val voice = RecordedVoice()
            val viewModel =
                routeViewModel(
                    location = insideTheFinalLead(),
                    available = true,
                    route = TURNING_ROUTE,
                    navigationSettingsFlow = stillOpenSettings,
                    voice = voice,
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            assertEquals(
                Triple(listOf("FINAL" to "final at 40 m"), 1, 0),
                Triple(voice.spoken, voice.opens, voice.closes),
            )
        }

    @Test
    fun `with spoken prompts off nothing is spoken and the engine is neither opened nor closed`() =
        runTest {
            val voice = RecordedVoice()
            val viewModel =
                routeViewModel(
                    location = insideTheFinalLead(),
                    available = true,
                    route = TURNING_ROUTE,
                    navigationSettingsFlow = flowOf(NavigationSettings.Default.copy(spokenPrompts = false)),
                    voice = voice,
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            assertEquals<Any>(Triple(emptyList<Any>(), 0, 0), Triple(voice.spoken, voice.opens, voice.closes))
        }

    // The close belongs to the engine this view model opened: once, when voice stops being wanted, and never again
    // while it stays unwanted.
    @Test
    fun `switching spoken prompts or the turn card off while the engine is open closes it once`() =
        runTest {
            val settings = MutableStateFlow(NavigationSettings.Default)
            val voice = RecordedVoice()
            val viewModel =
                routeViewModel(
                    location = insideTheFinalLead(),
                    available = true,
                    route = TURNING_ROUTE,
                    navigationSettingsFlow = settings,
                    voice = voice,
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            val whileOn = Pair(voice.opens, voice.closes)

            settings.value = settings.value.copy(spokenPrompts = false)
            val afterTheFirstOff = voice.closes
            settings.value = settings.value.copy(turnCard = false)
            settings.value = settings.value.copy(turnCardNextAfter = false)

            assertEquals(Triple(Pair(1, 0), 1, 1), Triple(whileOn, afterTheFirstOff, voice.closes))
        }

    @Test
    fun `with the turn card off nothing is spoken`() =
        runTest {
            val voice = RecordedVoice()
            val viewModel =
                routeViewModel(
                    location = insideTheFinalLead(),
                    available = true,
                    route = TURNING_ROUTE,
                    navigationSettingsFlow = flowOf(NavigationSettings.Default.copy(turnCard = false)),
                    voice = voice,
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            assertEquals<Any>(Triple(emptyList<Any>(), 0, 0), Triple(voice.spoken, voice.opens, voice.closes))
        }

    @Test
    fun `nothing is spoken before the stored settings are read`() =
        runTest {
            val settings = MutableSharedFlow<NavigationSettings>()
            val voice = RecordedVoice()
            val viewModel =
                routeViewModel(
                    location = insideTheFinalLead(),
                    available = true,
                    route = TURNING_ROUTE,
                    navigationSettingsFlow = settings,
                    voice = voice,
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            val beforeTheRead = Pair(voice.spoken.toList(), voice.opens)

            settings.emit(NavigationSettings.Default)

            assertEquals(
                Pair(Pair(emptyList<Pair<String, String>>(), 0), listOf("FINAL" to "final at 40 m")),
                Pair(beforeTheRead, voice.spoken),
            )
        }

    @Test
    fun `the same fix delivered twice speaks once`() =
        runTest {
            val location = MutableSharedFlow<Location?>()
            val voice = RecordedVoice()
            val viewModel =
                routeViewModel(locationFlow = location, available = true, route = TURNING_ROUTE, voice = voice)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            location.emit(insideTheFinalLead())
            location.emit(insideTheFinalLead())

            assertEquals(1, voice.spoken.size)
        }

    @Test
    fun `nothing is spoken while the route choice card is open, and the prompt comes once it closes`() =
        runTest {
            val active = MutableStateFlow<ActiveRoute?>(choosingRoute().copy(route = TURNING_ROUTE))
            val voice = RecordedVoice()
            val viewModel =
                routeViewModel(
                    location = insideTheFinalLead(),
                    available = true,
                    activeRouteFlow = active,
                    voice = voice,
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            val whileChoosing = voice.spoken.toList()

            active.value = active.value?.copy(choosing = false, finding = false)

            assertEquals(emptyList<Pair<String, String>>() to 1, whileChoosing to voice.spoken.size)
        }

    @Test
    fun `leaving the dashboard closes the engine`() =
        runTest {
            val voice = RecordedVoice()
            val viewModel =
                routeViewModel(
                    location = insideTheFinalLead(),
                    available = true,
                    route = TURNING_ROUTE,
                    navigationSettingsFlow = stillOpenSettings,
                    voice = voice,
                )
            val collection =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            val whileShown = voice.closes

            collection.cancel()
            advanceUntilIdle()

            assertEquals(0 to 1, whileShown to voice.closes)
        }

    // A build without routing has no route to follow: its voice callbacks are never called, so the speaker is never
    // created.
    @Test
    fun `where routing is not offered the engine is neither opened nor closed`() =
        runTest {
            val voice = RecordedVoice()
            val viewModel =
                routeViewModel(
                    location = insideTheFinalLead(),
                    available = false,
                    route = TURNING_ROUTE,
                    voice = voice,
                )
            val collection =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            collection.cancel()
            advanceUntilIdle()

            assertEquals<Any>(Triple(0, 0, emptyList<Any>()), Triple(voice.opens, voice.closes, voice.spoken))
        }

    // The dashboard left for longer than the grace and reopened: the follow starts again on the same route, which must
    // not forget the turns it announced.
    @Test
    fun `the collection ending and starting again on the same route speaks no prompt twice`() =
        runTest {
            val voice = RecordedVoice()
            val viewModel =
                routeViewModel(
                    location = insideTheFinalLead(),
                    available = true,
                    route = TURNING_ROUTE,
                    navigationSettingsFlow = stillOpenSettings,
                    voice = voice,
                )
            val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            first.cancel()
            advanceUntilIdle()

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            // Opened again for the second collection, and the one prompt was spoken once.
            assertEquals(Pair(1, 2), Pair(voice.spoken.size, voice.opens))
        }

    @Test
    fun `a reroute, a new route of the same session, gets prompts of its own`() =
        runTest {
            val active = MutableStateFlow<ActiveRoute?>(activeRouteOf(TURNING_ROUTE))
            val voice = RecordedVoice()
            val viewModel =
                routeViewModel(
                    location = insideTheFinalLead(),
                    available = true,
                    activeRouteFlow = active,
                    navigationSettingsFlow = stillOpenSettings,
                    voice = voice,
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            val beforeTheReroute = voice.spoken.size

            active.value =
                active.value?.copy(route = TURNING_ROUTE.copy(distanceMeters = TURNING_ROUTE.distanceMeters + 1))

            assertEquals(Pair(1, 2), Pair(beforeTheReroute, voice.spoken.size))
        }

    @Test
    fun `nothing is spoken before the speed unit is read`() =
        runTest {
            val unit = MutableSharedFlow<SpeedUnit>()
            val voice = RecordedVoice()
            val viewModel =
                routeViewModel(
                    location = insideTheFinalLead(),
                    available = true,
                    route = TURNING_ROUTE,
                    navigationSettingsFlow = stillOpenSettings,
                    speedUnitFlow = unit,
                    voice = voice,
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            val beforeTheRead = Pair(voice.spoken.size, voice.opens)

            unit.emit(SpeedUnit.KILOMETERS_PER_HOUR)

            assertEquals(Pair(Pair(0, 0), 1), Pair(beforeTheRead, voice.spoken.size))
        }

    // The vehicle lies along the line within the final lead of the turn while the watch says it is off the route:
    // the card is hidden, and so is the voice, until it is back on it.
    @Test
    fun `nothing is spoken while the vehicle is off the route, and the prompt comes once it is back`() =
        runTest {
            val location = MutableStateFlow<Location?>(null)
            val voice = RecordedVoice()
            val viewModel =
                routeViewModel(
                    locationFlow = location,
                    available = true,
                    route = TURNING_ROUTE,
                    navigationSettingsFlow = stillOpenSettings,
                    voice = voice,
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            location.value = fixAt(ROUTE_LONGITUDE, latitude = 0.0, second = 0)
            (1..6).forEach { second -> location.value = fixAt(ROUTE_LONGITUDE, OFF_ROUTE_LATITUDE, second) }
            location.value = fixAt(NEAR_TURN_LONGITUDE, OFF_ROUTE_LATITUDE, second = 7)
            location.value = fixAt(NEAR_TURN_LONGITUDE, OFF_ROUTE_LATITUDE, second = 8)
            val whileOff = voice.spoken.toList()

            (9..12).forEach { second -> location.value = fixAt(NEAR_TURN_LONGITUDE, latitude = 0.0, second = second) }

            assertEquals(Pair(emptyList<Pair<String, String>>(), 1), Pair(whileOff, voice.spoken.size))
        }

    @Test
    fun `nothing is spoken while a reroute's status shows, and the prompt comes once it is gone`() =
        runTest {
            val active =
                MutableStateFlow<ActiveRoute?>(activeRouteOf(TURNING_ROUTE).copy(reroute = RerouteStatus.REROUTING))
            val voice = RecordedVoice()
            val viewModel =
                routeViewModel(
                    location = insideTheFinalLead(),
                    available = true,
                    activeRouteFlow = active,
                    navigationSettingsFlow = stillOpenSettings,
                    voice = voice,
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            val whileRerouting = voice.spoken.toList()

            active.value = active.value?.copy(reroute = RerouteStatus.NONE)

            assertEquals(Pair(emptyList<Pair<String, String>>(), 1), Pair(whileRerouting, voice.spoken.size))
        }

    // A fix at [longitude] and [latitude], [second] seconds into the boot clock, at 10 m/s heading east.
    private fun fixAt(
        longitude: Double,
        latitude: Double,
        second: Int,
    ): Location =
        fakeLocation(
            latitude = latitude,
            longitude = longitude,
            speedMps = 10f,
            elapsedRealtimeNanos = second * 1_000_000_000L,
            bearingDeg = EAST,
            accuracyM = FIX_ACCURACY_M,
            provider = TEST_PROVIDER,
        )

    // --- The turn card's control for the spoken prompts -----------------------------

    @Test
    fun `the card's spoken state follows the setting`() =
        runTest {
            val settings = MutableStateFlow(NavigationSettings.Default)
            val viewModel =
                routeViewModel(
                    location = beforeTheTurn(),
                    available = true,
                    route = TURNING_ROUTE,
                    navigationSettingsFlow = settings,
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            val shown = mutableListOf<Boolean?>()

            shown += viewModel.uiState.value.activeRoute
                ?.turnCard
                ?.spoken
            settings.value = settings.value.copy(spokenPrompts = false)
            shown += viewModel.uiState.value.activeRoute
                ?.turnCard
                ?.spoken

            assertEquals(listOf<Boolean?>(true, false), shown)
        }

    @Test
    fun `the card offers no control while the speaker is unavailable`() =
        runTest {
            val unavailable = MutableStateFlow(true)
            val viewModel =
                routeViewModel(
                    location = beforeTheTurn(),
                    available = true,
                    route = TURNING_ROUTE,
                    voiceUnavailableFlow = unavailable,
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            val whileUnavailable = viewModel.uiState.value.activeRoute
                ?.turnCard

            unavailable.value = false

            assertEquals(
                Pair(null, true),
                Pair(
                    whileUnavailable?.spoken,
                    viewModel.uiState.value.activeRoute
                        ?.turnCard
                        ?.spoken,
                ),
            )
        }

    // The speaker's status falls back to Closed with the dashboard left; the flow the card reads does not.
    @Test
    fun `the control stays off the card across a close and a reopen while the speaker cannot speak`() =
        runTest {
            val viewModel =
                routeViewModel(
                    location = beforeTheTurn(),
                    available = true,
                    route = TURNING_ROUTE,
                    voiceUnavailableFlow = MutableStateFlow(true),
                )
            val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            val before = viewModel.uiState.value.activeRoute
                ?.turnCard
            first.cancel()
            advanceUntilIdle()

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            assertEquals(
                Pair(null, null),
                Pair(
                    before?.spoken,
                    viewModel.uiState.value.activeRoute
                        ?.turnCard
                        ?.spoken,
                ),
            )
        }

    @Test
    fun `a tap on the card writes the opposite of the value it shows`() =
        runTest {
            val settings = MutableStateFlow(NavigationSettings.Default)
            val written = mutableListOf<Boolean>()
            val viewModel =
                routeViewModel(
                    location = beforeTheTurn(),
                    available = true,
                    route = TURNING_ROUTE,
                    navigationSettingsFlow = settings,
                    setSpokenPrompts = { value ->
                        written += value
                        settings.value = settings.value.copy(spokenPrompts = value)
                    },
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            viewModel.onAction(HomeAction.ToggleSpokenPrompts)
            advanceUntilIdle()
            viewModel.onAction(HomeAction.ToggleSpokenPrompts)
            advanceUntilIdle()

            assertEquals(listOf(false, true), written)
        }

    @Test
    fun `a tap does nothing where the card offers no control`() =
        runTest {
            val written = mutableListOf<Boolean>()
            val viewModel =
                routeViewModel(
                    location = beforeTheTurn(),
                    available = true,
                    route = TURNING_ROUTE,
                    voiceUnavailableFlow = flowOf(true),
                    setSpokenPrompts = { written += it },
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            viewModel.onAction(HomeAction.ToggleSpokenPrompts)
            advanceUntilIdle()

            assertEquals(emptyList(), written)
        }

    @Test
    fun `a tap does nothing with no card on the map`() =
        runTest {
            val written = mutableListOf<Boolean>()
            val viewModel =
                routeViewModel(available = true, route = EQUATOR_ROUTE, setSpokenPrompts = { written += it })
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            viewModel.onAction(HomeAction.ToggleSpokenPrompts)
            advanceUntilIdle()

            assertEquals(emptyList(), written)
        }

    // --- The route kept across a restart ---------------------------------------

    // The keeper's first-fix decision must run with nothing on the map too.
    @Test
    fun `each live GPS fix's time reaches the route keeper, with no route on the map`() =
        runTest {
            val times = mutableListOf<Long>()
            val viewModel =
                routeViewModel(
                    location =
                        fakeLocation(
                            provider = LocationManager.GPS_PROVIDER,
                            timeMs = GPS_TIME_MS,
                            elapsedRealtimeNanos = BADGE_NOW,
                        ),
                    available = true,
                    activeRouteFlow = flowOf(null),
                    onGpsFix = { times += it },
                    nowElapsedRealtimeNanos = { BADGE_NOW },
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            assertEquals(listOf(GPS_TIME_MS), times)
        }

    // As the trip meter takes fixes: a network position, or the cached seed a subscription replays, says nothing about
    // when the vehicle was last driven.
    @Test
    fun `a network fix and a stale seed never reach the route keeper`() =
        runTest {
            val location = MutableStateFlow<Location?>(null)
            val times = mutableListOf<Long>()
            val viewModel =
                routeViewModel(
                    locationFlow = location,
                    available = true,
                    onGpsFix = { times += it },
                    nowElapsedRealtimeNanos = { BADGE_NOW },
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            location.value =
                fakeLocation(
                    provider = LocationManager.NETWORK_PROVIDER,
                    timeMs = GPS_TIME_MS,
                    elapsedRealtimeNanos = BADGE_NOW,
                )
            location.value =
                fakeLocation(
                    provider = LocationManager.GPS_PROVIDER,
                    timeMs = GPS_TIME_MS + 1,
                    elapsedRealtimeNanos = BADGE_NOW - STALE_SEED_AGE_NANOS,
                )
            location.value =
                fakeLocation(
                    provider = LocationManager.GPS_PROVIDER,
                    timeMs = GPS_TIME_MS + 2,
                    elapsedRealtimeNanos = BADGE_NOW,
                )

            assertEquals(listOf(GPS_TIME_MS + 2), times)
        }

    // --- Route choice card (issue #389 phase 3) -------------------------------

    @Test
    fun `the choice shows the session's candidates and selection`() =
        runTest {
            val viewModel = routeViewModel(available = true, activeRouteFlow = flowOf(choosingRoute()))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            val route = assertNotNull(viewModel.uiState.value.activeRoute)
            assertEquals(
                RouteChoiceState(
                    session = SESSION,
                    candidates =
                        listOf(
                            CandidateSummary(distanceMeters = 22_000, durationSeconds = 1_200),
                            CandidateSummary(distanceMeters = 22_461, durationSeconds = 1_200),
                        ),
                    selected = 1,
                    finding = true,
                    searchCompleted = false,
                ),
                route.choice,
            )
            assertEquals(PARALLEL_ROUTE.points, route.line, "the selected candidate is the route followed")
            assertEquals(listOf(EQUATOR_ROUTE.points), route.alternativeLines, "the other candidate is drawn thin")
        }

    @Test
    fun `the card says no other route only after a search that ran to its end`() =
        runTest {
            val alone = choosingRoute().copy(route = EQUATOR_ROUTE, candidates = choosingRoute().candidates.take(1))
            val active = MutableStateFlow<ActiveRoute?>(alone.copy(selected = 0, finding = false))
            val viewModel = routeViewModel(available = true, activeRouteFlow = active)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            val afterAStop = viewModel.uiState.value.activeRoute
                ?.choice
                ?.noOtherRoute

            active.value = active.value?.copy(searchCompleted = true)

            assertEquals(
                false to true,
                afterAStop to
                    viewModel.uiState.value.activeRoute
                        ?.choice
                        ?.noOtherRoute,
            )
        }

    @Test
    fun `a closed choice shows no card and no other lines`() =
        runTest {
            val closed = choosingRoute().copy(choosing = false, finding = false)
            val viewModel = routeViewModel(available = true, activeRouteFlow = flowOf(closed))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            val route = assertNotNull(viewModel.uiState.value.activeRoute)
            assertEquals(null to emptyList(), route.choice to route.alternativeLines)
        }

    @Test
    fun `selecting a candidate dispatches to the store`() =
        runTest {
            val selections = mutableListOf<Pair<Long, Int>>()
            val viewModel =
                routeViewModel(
                    available = true,
                    activeRouteFlow = flowOf(choosingRoute()),
                    selectRouteCandidate = { session, index -> selections += session to index },
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            viewModel.onAction(HomeAction.SelectRouteCandidate(0))

            assertEquals(listOf(SESSION to 0), selections)
        }

    @Test
    fun `Done on the card dispatches the close to the store`() =
        runTest {
            val closes = mutableListOf<Long>()
            val viewModel =
                routeViewModel(
                    available = true,
                    activeRouteFlow = flowOf(choosingRoute()),
                    closeRouteChoice = { closes += it },
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            viewModel.onAction(HomeAction.CloseRouteChoice)

            assertEquals(listOf(SESSION), closes)
        }

    @Test
    fun `the choice closes after 30 s of driving`() =
        runTest {
            val closes = mutableListOf<Long>()
            val viewModel =
                routeViewModel(
                    location = liveGpsFix(),
                    available = true,
                    activeRouteFlow = flowOf(choosingRoute()),
                    tripStateFlow = flowOf(fakeTripState(currentSpeedMs = MIN_MOVING_SPEED_MS + 10.0)),
                    closeRouteChoice = { closes += it },
                )
            backgroundScope.launch { viewModel.uiState.collect {} }

            at(ROUTE_CHOICE_DRIVING_CLOSE_MS - 1)
            val beforeTheEnd = closes.toList()
            at(ROUTE_CHOICE_DRIVING_CLOSE_MS)

            assertEquals(emptyList<Long>() to listOf(SESSION), beforeTheEnd to closes)
        }

    @Test
    fun `a stop resets the driving count`() =
        runTest {
            val closes = mutableListOf<Long>()
            val trip = MutableStateFlow(fakeTripState(currentSpeedMs = MIN_MOVING_SPEED_MS + 10.0))
            val viewModel =
                routeViewModel(
                    location = liveGpsFix(),
                    available = true,
                    activeRouteFlow = flowOf(choosingRoute()),
                    tripStateFlow = trip,
                    closeRouteChoice = { closes += it },
                )
            backgroundScope.launch { viewModel.uiState.collect {} }
            at(20_000L)

            // A stop at a light, then driving on: the 30 s count starts again.
            trip.value = fakeTripState(currentSpeedMs = 0.0)
            at(25_000L)
            trip.value = fakeTripState(currentSpeedMs = MIN_MOVING_SPEED_MS + 10.0)
            at(25_000L + ROUTE_CHOICE_DRIVING_CLOSE_MS - 1)
            val beforeTheEnd = closes.toList()
            at(25_000L + ROUTE_CHOICE_DRIVING_CLOSE_MS)

            assertEquals(emptyList<Long>() to listOf(SESSION), beforeTheEnd to closes)
        }

    // The session while the driver chooses: the equator route found first, the parallel roads second and selected,
    // and a third being searched for.
    private fun choosingRoute(): ActiveRoute =
        ActiveRoute(
            session = SESSION,
            route = PARALLEL_ROUTE,
            destination = ROUTE_DESTINATION,
            reference = null,
            candidates =
                listOf(
                    RouteCandidate(EQUATOR_ROUTE, reference = null, computingMs = 500L),
                    RouteCandidate(PARALLEL_ROUTE, reference = null, computingMs = 900L),
                ),
            selected = 1,
            choosing = true,
            finding = true,
        )

    // On the equator route at 0 s, then about 110 m north of it, moving east, a fix a second until the watch decides
    // at 6 s that the vehicle has left the route.
    private fun leaveTheRoute(
        location: MutableStateFlow<Location?>,
        hasSpeed: Boolean = true,
        provider: String = TEST_PROVIDER,
    ) {
        location.value = movingFix(latitude = 0.0, second = 0, hasSpeed = hasSpeed, provider = provider)
        (1..6).forEach { second ->
            location.value = movingFix(OFF_ROUTE_LATITUDE, second, hasSpeed = hasSpeed, provider = provider)
        }
    }

    private fun movingFix(
        latitude: Double,
        second: Int,
        hasSpeed: Boolean = true,
        provider: String = TEST_PROVIDER,
    ): Location =
        fakeLocation(
            latitude = latitude,
            longitude = ROUTE_LONGITUDE,
            speedMps = 10f,
            elapsedRealtimeNanos = second * 1_000_000_000L,
            hasSpeed = hasSpeed,
            bearingDeg = EAST,
            accuracyM = FIX_ACCURACY_M,
            provider = provider,
        )

    // On the equator route a quarter of the way along it, 5.5 km before its halfway turn.
    private fun beforeTheTurn(): Location = fakeLocation(latitude = 0.0, longitude = ROUTE_LONGITUDE)

    // About 5.6 m north of the equator route, halfway along it.
    private fun besideTheRoute(): Location = fakeLocation(latitude = 0.00005, longitude = 0.1)

    // [drawn] is at the equator route's halfway point, to a micro-degree.
    private fun assertOnTheLineHalfway(drawn: OnLinePoint?) {
        assertNotNull(drawn)
        assertEquals(0.0, drawn.point.latitude, MICRODEGREE)
        assertEquals(0.1, drawn.point.longitude, MICRODEGREE)
    }

    private fun activeRouteOf(route: Route): ActiveRoute =
        ActiveRoute(SESSION, route, ROUTE_DESTINATION, reference = null)

    // The route's progress runs on the test scheduler, so a test reads it as
    // soon as its subscriber has.
    private fun TestScope.routeViewModel(
        location: Location? = null,
        available: Boolean,
        clearRoute: () -> Unit = {},
        locationFlow: Flow<Location?> = flowOf(location),
        route: Route = EQUATOR_ROUTE,
        activeRouteFlow: Flow<ActiveRoute?> = flowOf(activeRouteOf(route)),
        onLeftRoute: (Long, RouteFix) -> Unit = { _, _ -> },
        onBackOnRoute: (Long) -> Unit = {},
        tripState: TripState = fakeTripState(),
        tripStateFlow: Flow<TripState> = flowOf(tripState),
        selectRouteCandidate: (Long, Int) -> Unit = { _, _ -> },
        closeRouteChoice: (Long) -> Unit = {},
        onGpsFix: (Long) -> Unit = {},
        nowElapsedRealtimeNanos: () -> Long = SystemClock::elapsedRealtimeNanos,
        navigationSettingsFlow: Flow<NavigationSettings> = flowOf(NavigationSettings.Default),
        voice: RecordedVoice = RecordedVoice(),
        speedUnitFlow: Flow<SpeedUnit> = flowOf(SpeedUnit.KILOMETERS_PER_HOUR),
        voiceUnavailableFlow: Flow<Boolean> = flowOf(false),
        setSpokenPrompts: suspend (Boolean) -> Unit = {},
    ): HomeViewModel =
        HomeViewModel(
            locationFlow = locationFlow,
            addressFlow = flowOf(fakeAddress()),
            weatherFlow = flowOf(null),
            musicStateFlow = flowOf(MusicCardState.NeedsPermission),
            calendarFlow = flowOf(null),
            systemStatusFlow = flowOf(fakeSystemStatus()),
            tripStateFlow = tripStateFlow,
            activeRouteFlow = activeRouteFlow,
            routingAvailable = flowOf(available),
            clearRoute = clearRoute,
            onLeftRoute = onLeftRoute,
            onBackOnRoute = onBackOnRoute,
            selectRouteCandidate = selectRouteCandidate,
            closeRouteChoice = closeRouteChoice,
            routeDispatcher = UnconfinedTestDispatcher(testScheduler),
            onGpsFix = onGpsFix,
            nowElapsedRealtimeNanos = nowElapsedRealtimeNanos,
            navigationSettingsFlow = navigationSettingsFlow,
            speedUnitFlow = speedUnitFlow,
            voiceUnavailableFlow = voiceUnavailableFlow,
            setSpokenPrompts = setSpokenPrompts,
            // The stage names the prompt: the sentences are the text tests' business.
            turnPromptText = { prompt, _ -> prompt.stage.name },
            openTurnVoice = { voice.opens++ },
            speakTurnPrompt = { text, label -> voice.spoken += text to label },
            closeTurnVoice = { voice.closes++ },
        )

    // What the dashboard asked of the voice: the engine openings and closings, and each prompt with its label.
    private class RecordedVoice {
        var opens = 0
        var closes = 0
        val spoken = mutableListOf<Pair<String, String>>()
    }

    private fun stubViewModel(
        sendMusicCommand: (MusicCommand) -> Unit = {},
        resumeLastMusicSession: () -> Unit = {},
        resetTrip: () -> Unit = {},
        resolveMusicSourceComponent: (String) -> ComponentName? = { null },
        receivedPlaces: ReceivedPlaceInbox = ReceivedPlaceInbox(),
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
            receivedPlaces = receivedPlaces,
            routeDispatcher = testRouteDispatcher(),
        )

    private companion object {
        val UPDATE = fakeUpdateManifest(versionCode = 26092501)

        // The fix at 0.1° east is halfway along it.
        val EQUATOR_ROUTE = fakeEquatorRoute()

        // The second route on the choice card.
        val PARALLEL_ROUTE = fakeParallelRoadsRoute()

        // EQUATOR_ROUTE with turn data: a left turn at its halfway point.
        val TURNING_ROUTE =
            fakeRoute(
                points = EQUATOR_ROUTE.points,
                distanceMeters = EQUATOR_ROUTE.distanceMeters,
                durationSeconds = EQUATOR_ROUTE.durationSeconds,
                turns = listOf(RouteTurn(pointIndex = 1, kind = TurnKind.LEFT, exitNumber = 0, angleDegrees = -90)),
            )

        // Along the equator, straight on across a road at its halfway point and left about 55 m after it.
        val STRAIGHT_THEN_LEFT_ROUTE =
            fakeRoute(
                points = listOf(
                    RoutePoint(0.0, 0.0),
                    RoutePoint(0.0, 0.1),
                    RoutePoint(0.0, 0.1005),
                    RoutePoint(0.0, 0.2),
                ),
                distanceMeters = EQUATOR_ROUTE.distanceMeters,
                durationSeconds = EQUATOR_ROUTE.durationSeconds,
                turns =
                    listOf(
                        RouteTurn(pointIndex = 1, kind = TurnKind.STRAIGHT, exitNumber = 0, angleDegrees = 0),
                        RouteTurn(pointIndex = 2, kind = TurnKind.LEFT, exitNumber = 0, angleDegrees = -90),
                    ),
            )

        // The session the destination panel opened for the route under test, and where the route goes.
        const val SESSION = 7L
        val ROUTE_DESTINATION = RoutePoint(latitude = 0.0, longitude = 0.2)

        // A quarter of the way along the equator route, and about 110 m north of it.
        const val ROUTE_LONGITUDE = 0.05

        // About 40 m before the turn at the equator route's halfway point.
        const val NEAR_TURN_LONGITUDE = 0.0996403
        const val OFF_ROUTE_LATITUDE = 0.001
        const val EAST = 90f
        const val FIX_ACCURACY_M = 5f

        // The precision of an engine route's points.
        const val MICRODEGREE = 1e-6

        // A provider that counts as GPS (anything but the network provider), and the trip's speed from the positions.
        const val TEST_PROVIDER = "test"
        const val TRIP_SPEED_MS = 12.0

        // An hour into the boot clock, so a fix can be dated before "now".
        const val BADGE_NOW = 3_600_000_000_000L

        // A minute old: a cached seed from before this subscription.
        const val STALE_FIX_AGE_NANOS = 60_000_000_000L

        // Past the trip meter's gap window: a seed the trip meter, and so the route keeper, never takes.
        val STALE_SEED_AGE_NANOS = ((MAX_GAP_SECONDS + 1) * 1_000_000_000).toLong()

        // A GPS fix's own time: 2026-11-22T09:00:00Z.
        const val GPS_TIME_MS = 1_795_338_000_000L
    }
}

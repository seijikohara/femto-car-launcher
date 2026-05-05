package io.github.seijikohara.femto.ui.home

import android.graphics.Bitmap
import app.cash.turbine.test
import io.github.seijikohara.femto.data.AppEntry
import io.github.seijikohara.femto.data.ClockTick
import io.github.seijikohara.femto.testfixtures.fakeAddress
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.testfixtures.fakeWeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
            val placeholderIcon = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            val viewModel =
                HomeViewModel(
                    clockFlow = flowOf(ClockTick(LocalTime.of(14, 32), LocalDate.of(2026, 5, 1))),
                    locationFlow = flowOf(null),
                    addressFlow = flowOf(fakeAddress()),
                    weatherFlow = flowOf(fakeWeatherSnapshot()),
                    nowPlayingFlow = flowOf(fakeNowPlaying()),
                    appsFlow =
                        MutableStateFlow(
                            listOf(
                                AppEntry(
                                    componentName = android.content.ComponentName("p", "c"),
                                    label = "X",
                                    icon = placeholderIcon,
                                ),
                            ),
                        ),
                    isMapAvailable = { true },
                )
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(LocalTime.of(14, 32), state.clock.time)
                assertNotNull(state.address)
                assertNotNull(state.weather)
                assertNotNull(state.nowPlaying)
                assertTrue(state.mapAvailable)
                cancelAndIgnoreRemainingEvents()
            }
        }
}

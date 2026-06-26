package io.github.seijikohara.femto.ui.diagnostics

import app.cash.turbine.test
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.SpectrumDiagnosis
import io.github.seijikohara.femto.testfixtures.fakeDiagnosticsSnapshot
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import io.github.seijikohara.femto.testfixtures.fakePerformanceSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DiagnosticsViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh lands the snapshot and the spectrum probe in uiState`() =
        runTest {
            val snapshot = fakeDiagnosticsSnapshot()
            val viewModel =
                DiagnosticsViewModel(
                    collectSnapshot = { snapshot },
                    probeSpectrum = { SpectrumDiagnosis.SILENT },
                    musicStateFlow = flowOf(MusicCardState.Playing(fakeNowPlaying())),
                )
            viewModel.uiState.test {
                val state = awaitItem()
                assertFalse(state.isLoading)
                assertEquals(snapshot, state.snapshot)
                assertEquals(SpectrumDiagnosis.SILENT, state.spectrum)
                assertEquals(MusicCardState.Playing(fakeNowPlaying()), state.musicState)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `refresh lands the performance snapshot independently`() =
        runTest {
            val performance = fakePerformanceSnapshot()
            val viewModel =
                DiagnosticsViewModel(
                    collectSnapshot = { error("collector broke") },
                    probeSpectrum = { SpectrumDiagnosis.SILENT },
                    musicStateFlow = flowOf(MusicCardState.NoActiveSession),
                    collectPerformance = { performance },
                )
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(null, state.snapshot)
                assertEquals(performance, state.performance)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a failing snapshot degrades to null without hiding the probe`() =
        runTest {
            val viewModel =
                DiagnosticsViewModel(
                    collectSnapshot = { error("collector broke") },
                    probeSpectrum = { SpectrumDiagnosis.ENGINE_UNAVAILABLE },
                    musicStateFlow = flowOf(MusicCardState.NoActiveSession),
                )
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(null, state.snapshot)
                assertEquals(SpectrumDiagnosis.ENGINE_UNAVAILABLE, state.spectrum)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Refresh action re-runs the probes`() =
        runTest {
            var probes = 0
            val viewModel =
                DiagnosticsViewModel(
                    collectSnapshot = { fakeDiagnosticsSnapshot() },
                    probeSpectrum = {
                        probes++
                        SpectrumDiagnosis.ACTIVE
                    },
                    musicStateFlow = flowOf(MusicCardState.NoActiveSession),
                )
            viewModel.uiState.test {
                awaitItem()
                viewModel.onAction(DiagnosticsAction.Refresh)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(2, probes)
        }
}

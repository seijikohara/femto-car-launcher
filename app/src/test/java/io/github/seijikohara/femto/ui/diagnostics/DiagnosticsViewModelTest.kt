package io.github.seijikohara.femto.ui.diagnostics

import app.cash.turbine.test
import io.github.seijikohara.femto.data.diagnostics.DiagnosticFact
import io.github.seijikohara.femto.data.diagnostics.DiagnosticSection
import io.github.seijikohara.femto.data.diagnostics.FactValue
import io.github.seijikohara.femto.data.diagnostics.SectionCollector
import io.github.seijikohara.femto.data.diagnostics.SectionId
import io.github.seijikohara.femto.data.diagnostics.SectionPayload
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.testfixtures.fakeDiagnosticSections
import io.github.seijikohara.femto.testfixtures.fakeNowPlaying
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun `init refresh streams every section into uiState`() =
        runTest {
            val sections = fakeDiagnosticSections()
            val viewModel = viewModel(collectors = collectorsFor(sections))
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(SectionId.entries.toList(), state.sections.map { it.id })
                assertTrue(state.sections.none { it.payload == null })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a throwing collector degrades its own section to Unavailable and leaves siblings intact`() =
        runTest {
            val sections = fakeDiagnosticSections()
            val collectors =
                collectorsFor(sections).map { collector ->
                    if (collector.id == SectionId.STORAGE) {
                        SectionCollector(collector.id) { error("collector broke") }
                    } else {
                        collector
                    }
                }
            val viewModel = viewModel(collectors = collectors)
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(SectionPayload.Unavailable, state.sections.payloadOf(SectionId.STORAGE))
                assertEquals(
                    sections.first { it.id == SectionId.DEVICE }.payload,
                    state.sections.payloadOf(SectionId.DEVICE),
                )
                assertEquals(
                    sections.first { it.id == SectionId.LOGS }.payload,
                    state.sections.payloadOf(SectionId.LOGS),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Refresh action re-runs every collector`() =
        runTest {
            var runs = 0
            val collectors =
                collectorsFor(fakeDiagnosticSections()).map { collector ->
                    SectionCollector(collector.id) {
                        runs++
                        collector.collect()
                    }
                }
            val viewModel = viewModel(collectors = collectors)
            viewModel.uiState.test {
                awaitItem()
                viewModel.onAction(DiagnosticsAction.Refresh)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(2 * SectionId.entries.size, runs)
        }

    @Test
    fun `CopyReport hands the seam a report and pulses copyConfirmed across the confirmation window`() {
        // Standard dispatcher: the pulse assertion needs the 2 s delay to sit
        // on the virtual clock instead of resolving inline.
        Dispatchers.setMain(StandardTestDispatcher())
        runTest {
            var copied: String? = null
            val viewModel = viewModel(copyToClipboard = { copied = it })
            viewModel.uiState.test {
                runCurrent()
                assertFalse(expectMostRecentItem().copyConfirmed)
                viewModel.onAction(DiagnosticsAction.CopyReport)
                runCurrent()
                assertTrue(expectMostRecentItem().copyConfirmed)
                advanceTimeBy(COPY_CONFIRM_MS - 1)
                runCurrent()
                expectNoEvents()
                advanceTimeBy(1)
                runCurrent()
                assertFalse(awaitItem().copyConfirmed)
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(checkNotNull(copied).startsWith("# Femto Car Launcher diagnostics"))
        }
    }

    @Test
    fun `overlapping CopyReport actions do not truncate the confirmation pulse`() {
        // Standard dispatcher: same reasoning as the pulse test above — the
        // truncation bug only reproduces when both delays sit on the virtual clock.
        Dispatchers.setMain(StandardTestDispatcher())
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                runCurrent()
                assertFalse(expectMostRecentItem().copyConfirmed)

                viewModel.onAction(DiagnosticsAction.CopyReport)
                runCurrent()
                assertTrue(expectMostRecentItem().copyConfirmed)

                advanceTimeBy(1_900)
                runCurrent()
                expectNoEvents()

                // Second copy inside the first pulse's window: without job
                // cancellation, the first coroutine's trailing `= false`
                // still lands at its own t=2_000 and truncates this pulse.
                viewModel.onAction(DiagnosticsAction.CopyReport)
                runCurrent()
                expectNoEvents() // already confirmed; re-confirming is a no-op flip

                advanceTimeBy(1_999)
                runCurrent()
                // Proves the truncation is gone: the first coroutine's t=2_000
                // revert (well inside this window) would have emitted `false` here.
                expectNoEvents()

                advanceTimeBy(1)
                runCurrent()
                assertFalse(awaitItem().copyConfirmed)

                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `ToggleProblemsOnly flips the flag`() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                assertFalse(awaitItem().problemsOnly)
                viewModel.onAction(DiagnosticsAction.ToggleProblemsOnly)
                assertTrue(awaitItem().problemsOnly)
                viewModel.onAction(DiagnosticsAction.ToggleProblemsOnly)
                assertFalse(awaitItem().problemsOnly)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a live music emission updates the MUSIC Session fact without a refresh`() =
        runTest {
            val musicFlow = MutableStateFlow<MusicCardState>(MusicCardState.NoActiveSession)
            val viewModel = viewModel(musicStateFlow = musicFlow)
            viewModel.uiState.test {
                assertEquals(
                    DiagnosticFact("Session", FactValue.Text("no active session")),
                    awaitItem().sessionFact(),
                )
                musicFlow.value = MusicCardState.Playing(fakeNowPlaying())
                assertEquals(
                    DiagnosticFact("Session", FactValue.Text("com.example.music (playing)")),
                    awaitItem().sessionFact(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the benchmark stays hidden when no samples are available`() =
        runTest {
            val viewModel = viewModel(tileBenchmarkAvailable = { false })
            viewModel.uiState.test {
                val state = expectMostRecentItem()
                // Collected sections tell the combined state from the seed, whose benchmark is hidden too.
                assertTrue(state.sections.none { it.payload == null })
                assertEquals(TileBenchmarkUi.HIDDEN, state.tileBenchmark)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the benchmark is offered when samples are available`() =
        runTest {
            val viewModel = viewModel(tileBenchmarkAvailable = { true })
            viewModel.uiState.test {
                assertEquals(TileBenchmarkUi.IDLE, expectMostRecentItem().tileBenchmark)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `running the benchmark shows it running and then refreshes the sections`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            var providerCalls = 0
            val collectors = collectorsFor(fakeDiagnosticSections())
            val viewModel =
                viewModel(
                    collectorsProvider = { collectors.also { providerCalls++ } },
                    tileBenchmarkAvailable = { true },
                    runTileBenchmark = { gate.await() },
                )
            viewModel.uiState.test {
                assertEquals(TileBenchmarkUi.IDLE, expectMostRecentItem().tileBenchmark)
                viewModel.onAction(DiagnosticsAction.RunTileBenchmark)
                assertEquals(TileBenchmarkUi.RUNNING, expectMostRecentItem().tileBenchmark)
                gate.complete(Unit)
                assertEquals(TileBenchmarkUi.IDLE, expectMostRecentItem().tileBenchmark)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(2, providerCalls)
        }

    @Test
    fun `a second tap while the benchmark runs does nothing`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            var entered = 0
            val viewModel =
                viewModel(
                    tileBenchmarkAvailable = { true },
                    runTileBenchmark = {
                        entered++
                        gate.await()
                    },
                )
            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.onAction(DiagnosticsAction.RunTileBenchmark)
                viewModel.onAction(DiagnosticsAction.RunTileBenchmark)
                gate.complete(Unit)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, entered)
        }

    @Test
    fun `the action does nothing while the benchmark is hidden`() =
        runTest {
            var entered = 0
            val viewModel = viewModel(tileBenchmarkAvailable = { false }, runTileBenchmark = { entered++ })
            viewModel.uiState.test {
                viewModel.onAction(DiagnosticsAction.RunTileBenchmark)
                val state = expectMostRecentItem()
                assertTrue(state.sections.none { it.payload == null })
                assertEquals(TileBenchmarkUi.HIDDEN, state.tileBenchmark)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(0, entered)
        }

    @Test
    fun `a failing benchmark still returns to idle and refreshes`() =
        runTest {
            var providerCalls = 0
            val collectors = collectorsFor(fakeDiagnosticSections())
            val viewModel =
                viewModel(
                    collectorsProvider = { collectors.also { providerCalls++ } },
                    tileBenchmarkAvailable = { true },
                    runTileBenchmark = { throw IllegalStateException("benchmark broke") },
                )
            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.onAction(DiagnosticsAction.RunTileBenchmark)
                assertEquals(TileBenchmarkUi.IDLE, expectMostRecentItem().tileBenchmark)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(2, providerCalls)
        }

    private fun viewModel(
        collectors: List<SectionCollector> = collectorsFor(fakeDiagnosticSections()),
        collectorsProvider: () -> List<SectionCollector> = { collectors },
        musicStateFlow: Flow<MusicCardState> = flowOf(MusicCardState.NoActiveSession),
        copyToClipboard: suspend (String) -> Unit = {},
        tileBenchmarkAvailable: suspend () -> Boolean = { false },
        runTileBenchmark: suspend () -> Unit = {},
    ): DiagnosticsViewModel =
        DiagnosticsViewModel(
            // A lambda capturing one fixed list still returns the same
            // collector instances on every refresh — the freshness guarantee
            // matters only for the production registry's spectrum→logs gate.
            collectorsProvider = collectorsProvider,
            musicStateFlow = musicStateFlow,
            copyToClipboard = copyToClipboard,
            tileBenchmarkAvailable = tileBenchmarkAvailable,
            runTileBenchmark = runTileBenchmark,
        )

    private fun collectorsFor(sections: List<DiagnosticSection>): List<SectionCollector> =
        sections.map { section -> SectionCollector(section.id) { checkNotNull(section.payload) } }

    private fun List<DiagnosticSection>.payloadOf(id: SectionId): SectionPayload? = first { it.id == id }.payload

    private fun DiagnosticsUiState.sessionFact(): DiagnosticFact =
        (checkNotNull(sections.payloadOf(SectionId.MUSIC)) as SectionPayload.Facts).facts.first()
}

package io.github.seijikohara.femto.data.routing

import io.github.seijikohara.femto.testfixtures.fakeEquatorRoute
import io.github.seijikohara.femto.testfixtures.fakeRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CandidateFinderTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val store = ActiveRouteStore()
    private val planner = FakeAlternativePlanner()
    private val pruned = mutableListOf<Collection<File>>()
    private var nowMs = START_MS

    // The finder searches on the test's background scope, so each test steps it with runCurrent. The "Suggest other
    // routes" setting is on unless a test passes its own.
    private fun TestScope.finder(enabled: Flow<Boolean> = flowOf(true)): CandidateFinder =
        CandidateFinder(
            route = planner::route,
            store = store,
            scope = backgroundScope,
            pruneReferences = { pruned += it },
            nowMs = { nowMs },
            enabled = enabled,
        )

    private fun startSession(): Long = store.start(FIRST, DESTINATION, CONDITIONS, firstReference, computingMs = 500L)

    private val firstReference: File by lazy { tempFolder.newFile("route-0.bin") }

    @Test
    fun `finds alternatives 1 and 2 in order with the session's conditions`() =
        runTest {
            val finder = finder()
            val session = startSession()
            planner.answer(1, RouteResult.Found(SECOND, null))
            planner.answer(2, RouteResult.Found(THIRD, null))

            finder.start(session, ORIGIN)
            runCurrent()

            assertEquals(
                listOf(
                    AlternativeCall(ORIGIN, DESTINATION, CONDITIONS, 1),
                    AlternativeCall(ORIGIN, DESTINATION, CONDITIONS, 2),
                ),
                planner.calls,
            )
            assertEquals(
                listOf(FIRST, SECOND, THIRD),
                store.active.value
                    ?.candidates
                    ?.map { it.route },
            )
        }

    @Test
    fun `a search runs with the finding flag up and lowers it when done`() =
        runTest {
            val finder = finder()
            val session = startSession()
            finder.start(session, ORIGIN)
            runCurrent()
            val whileSearching = store.active.value?.finding

            planner.answer(1, RouteResult.Failed("no track found"))
            runCurrent()

            assertEquals(true to false, whileSearching to store.active.value?.finding)
        }

    @Test
    fun `drops a near-duplicate`() =
        runTest {
            val finder = finder()
            val session = startSession()
            val duplicateReference = tempFolder.newFile("route-1.bin")
            planner.answer(1, RouteResult.Found(FIRST, duplicateReference))
            planner.answer(2, RouteResult.Found(THIRD, null))

            finder.start(session, ORIGIN)
            runCurrent()

            assertEquals(
                listOf(FIRST, THIRD),
                store.active.value
                    ?.candidates
                    ?.map { it.route },
            )
            assertFalse(duplicateReference.exists())
        }

    @Test
    fun `a failure ends the search`() =
        runTest {
            val finder = finder()
            val session = startSession()
            planner.answer(1, RouteResult.TimedOut)

            finder.start(session, ORIGIN)
            runCurrent()

            assertEquals(listOf(1), planner.calls.map { it.alternativeIndex })
            assertEquals(
                listOf(FIRST),
                store.active.value
                    ?.candidates
                    ?.map { it.route },
            )
        }

    @Test
    fun `stops when the choice closes`() =
        runTest {
            val finder = finder()
            val session = startSession()
            finder.start(session, ORIGIN)
            runCurrent()

            store.closeChoice(session)
            runCurrent()

            assertTrue(planner.cancelled)
            assertEquals(CandidateSearchEnding.STOPPED, finder.lastSearch.value?.ending)
        }

    @Test
    fun `stops on a new session`() =
        runTest {
            val finder = finder()
            val session = startSession()
            finder.start(session, ORIGIN)
            runCurrent()

            store.start(SECOND, DESTINATION, CONDITIONS, reference = null)
            runCurrent()

            assertTrue(planner.cancelled)
            assertEquals(CandidateSearchEnding.STOPPED, finder.lastSearch.value?.ending)
        }

    @Test
    fun `cancel stops the search and lowers the finding flag`() =
        runTest {
            val finder = finder()
            val session = startSession()
            finder.start(session, ORIGIN)
            runCurrent()

            finder.cancel()
            runCurrent()

            assertEquals(true to false, planner.cancelled to store.active.value?.finding)
        }

    @Test
    fun `a late candidate never changes the selection`() =
        runTest {
            val finder = finder()
            val session = startSession()
            planner.answer(1, RouteResult.Found(SECOND, null))
            finder.start(session, ORIGIN)
            runCurrent()

            store.select(session, 1)
            planner.answer(2, RouteResult.Found(THIRD, null))
            runCurrent()

            val active = store.active.value
            assertEquals(SECOND to 1, active?.let { it.route to it.selected })
            assertEquals(listOf(FIRST, SECOND, THIRD), active?.candidates?.map { it.route })
        }

    // The choice closes in the very moment the planner answers, too late to stop the search.
    @Test
    fun `a candidate found as the choice closes is dropped with its reference`() =
        runTest {
            val finder = finder()
            val session = startSession()
            val lateReference = tempFolder.newFile("route-1.bin")
            planner.beforeAnswering = { store.closeChoice(session) }
            planner.answer(1, RouteResult.Found(SECOND, lateReference))

            finder.start(session, ORIGIN)
            runCurrent()

            assertEquals(
                listOf(FIRST),
                store.active.value
                    ?.candidates
                    ?.map { it.route },
            )
            assertFalse(lateReference.exists())
        }

    @Test
    fun `closing the choice deletes the references not chosen and keeps the chosen one`() =
        runTest {
            val finder = finder()
            val session = startSession()
            val secondReference = tempFolder.newFile("route-1.bin")
            val thirdReference = tempFolder.newFile("route-2.bin")
            planner.answer(1, RouteResult.Found(SECOND, secondReference))
            planner.answer(2, RouteResult.Found(THIRD, thirdReference))
            finder.start(session, ORIGIN)
            runCurrent()
            store.select(session, 1)

            store.closeChoice(session)
            runCurrent()

            assertEquals(
                listOf(false, true, false),
                listOf(firstReference, secondReference, thirdReference).map { it.exists() },
            )
            assertEquals(listOf<Collection<File>>(setOf(secondReference)), pruned)
        }

    @Test
    fun `the candidates' computing times and the search's ending are kept for the diagnostics`() =
        runTest {
            val finder = finder()
            val session = startSession()
            planner.computingMs = 1_100L
            planner.answer(1, RouteResult.Found(SECOND, null))
            planner.answer(2, RouteResult.Failed("no track found"))

            finder.start(session, ORIGIN)
            runCurrent()

            assertEquals(
                CandidateSearch(listOf(500L, 1_100L), CandidateSearchEnding.NO_MORE_ROUTES),
                finder.lastSearch.value,
            )
        }

    @Test
    fun `a search that finds every route ends all found`() =
        runTest {
            val finder = finder()
            val session = startSession()
            planner.answer(1, RouteResult.Found(SECOND, null))
            planner.answer(2, RouteResult.Found(THIRD, null))

            finder.start(session, ORIGIN)
            runCurrent()

            assertEquals(CandidateSearchEnding.ALL_FOUND, finder.lastSearch.value?.ending)
        }

    @Test
    fun `a search that runs to its end completes it in the session`() =
        runTest {
            val finder = finder()
            val session = startSession()
            planner.answer(1, RouteResult.Failed("no track found"))

            finder.start(session, ORIGIN)
            runCurrent()

            assertEquals(
                true to false,
                store.active.value?.let { it.searchCompleted to it.finding },
            )
        }

    // The panel's next route stops the search; when that route then fails, the first route's card stays open with
    // nothing found, and must not say there is no other route.
    @Test
    fun `a cancelled search never completes it, so the card never says no other route`() =
        runTest {
            val finder = finder()
            val session = startSession()
            finder.start(session, ORIGIN)
            runCurrent()

            finder.cancel()
            runCurrent()

            assertEquals(
                Triple(false, false, CandidateSearchEnding.STOPPED),
                Triple(
                    store.active.value?.searchCompleted,
                    store.active.value?.finding,
                    finder.lastSearch.value?.ending,
                ),
            )
        }

    @Test
    fun `a cancelled search still deletes the references not chosen once the choice closes`() =
        runTest {
            val finder = finder()
            val session = startSession()
            val secondReference = tempFolder.newFile("route-1.bin")
            planner.answer(1, RouteResult.Found(SECOND, secondReference))
            finder.start(session, ORIGIN)
            runCurrent()
            finder.cancel()
            runCurrent()

            store.closeChoice(session)
            runCurrent()

            assertEquals(listOf(true, false), listOf(firstReference, secondReference).map { it.exists() })
            assertEquals(listOf<Collection<File>>(setOf(firstReference)), pruned)
        }

    // --- The "Suggest other routes" setting ----------------------------------------------------------------------------

    // The driver had picked the second route; the search for the third still runs.
    @Test
    fun `switching other routes off mid-search stops it, closes the choice and keeps the selected route`() =
        runTest {
            val setting = MutableStateFlow(true)
            val finder = finder(enabled = setting)
            val session = startSession()
            val secondReference = tempFolder.newFile("route-1.bin")
            planner.answer(1, RouteResult.Found(SECOND, secondReference))
            finder.start(session, ORIGIN)
            runCurrent()
            store.select(session, 1)

            setting.value = false
            runCurrent()

            val active = store.active.value
            assertTrue(planner.cancelled)
            assertEquals(false to SECOND, active?.let { it.choosing to it.route })
            assertEquals(listOf(SECOND), active?.candidates?.map { it.route })
            assertEquals(listOf(false, true), listOf(firstReference, secondReference).map { it.exists() })
        }

    // The search ended with no other route, and the card waits for the driver.
    @Test
    fun `switching other routes off with no search running closes an open choice`() =
        runTest {
            val setting = MutableStateFlow(true)
            val finder = finder(enabled = setting)
            val session = startSession()
            planner.answer(1, RouteResult.Failed("no track found"))
            finder.start(session, ORIGIN)
            runCurrent()
            val openBefore = store.active.value?.choosing

            setting.value = false
            runCurrent()

            assertEquals(true to false, openBefore to store.active.value?.choosing)
        }

    // The panel read the setting as on just before the driver switched it off; the switch-off found no choice of the
    // new route open yet, so the search's own read is what closes it.
    @Test
    fun `a search started while other routes are off closes the choice and never calls the planner`() =
        runTest {
            val finder = finder(enabled = MutableStateFlow(false))
            runCurrent()
            val session = startSession()

            finder.start(session, ORIGIN)
            runCurrent()

            assertEquals(emptyList(), planner.calls)
            assertEquals(false, store.active.value?.choosing)
        }

    @Test
    fun `switched off a second time, a running search stops again`() =
        runTest {
            val setting = MutableStateFlow(true)
            val finder = finder(enabled = setting)
            runCurrent()
            setting.value = false
            runCurrent()
            setting.value = true
            runCurrent()
            val session = startSession()
            finder.start(session, ORIGIN)
            runCurrent()

            setting.value = false
            runCurrent()

            assertEquals(true to false, planner.cancelled to store.active.value?.choosing)
        }

    private data class AlternativeCall(
        val origin: RoutePoint,
        val destination: RoutePoint,
        val conditions: RouteConditions,
        val alternativeIndex: Int,
    )

    // The planner as the finder sees it: every call is recorded, takes [computingMs] on the test's clock, waits for
    // the answer given for its alternative, and runs [beforeAnswering] just before it returns, with no suspension in
    // between.
    private inner class FakeAlternativePlanner {
        val calls = mutableListOf<AlternativeCall>()
        var computingMs = 0L
        var cancelled = false
        var beforeAnswering: () -> Unit = {}
        private val answers = mutableMapOf<Int, CompletableDeferred<RouteResult>>()

        fun answer(
            alternativeIndex: Int,
            result: RouteResult,
        ) {
            answers.getOrPut(alternativeIndex) { CompletableDeferred() }.complete(result)
        }

        suspend fun route(
            origin: RoutePoint,
            destination: RoutePoint,
            conditions: RouteConditions,
            alternativeIndex: Int,
        ): RouteResult {
            calls += AlternativeCall(origin, destination, conditions, alternativeIndex)
            nowMs += computingMs
            return try {
                answers.getOrPut(alternativeIndex) { CompletableDeferred() }.await().also { beforeAnswering() }
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }
    }

    private companion object {
        const val START_MS = 1_000_000L
        val CONDITIONS = RouteConditions(avoidTolls = true)
        val ORIGIN = RoutePoint(latitude = 33.84, longitude = 132.77)
        val DESTINATION = RoutePoint(latitude = 34.07, longitude = 133.0)

        // Three routes with no point in common.
        val FIRST = fakeRoute()
        val SECOND = fakeEquatorRoute()
        val THIRD = fakeRoute(points = listOf(RoutePoint(1.0, 0.0), RoutePoint(1.0, 0.1)))
    }
}

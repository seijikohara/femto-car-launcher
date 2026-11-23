package io.github.seijikohara.femto.data.routing

import io.github.seijikohara.femto.testfixtures.fakeEquatorRoute
import io.github.seijikohara.femto.testfixtures.fakeRoute
import io.github.seijikohara.femto.testfixtures.fakeRouteFix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RerouterTest {
    private val store = ActiveRouteStore()
    private val planner = FakeReroutePlanner()
    private val pruned = mutableListOf<Collection<File>>()
    private var nowMs = START_MS

    // The rerouter runs its reroutes on the test's background scope, so each test steps them with runCurrent.
    private fun TestScope.rerouter(): Rerouter =
        Rerouter(
            route = planner::route,
            store = store,
            scope = backgroundScope,
            pruneReferences = { pruned += it },
            nowMs = { nowMs },
        )

    @Test
    fun `leaving the route routes again from the fix to the same destination with the reference and heading`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, REFERENCE)

            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            assertEquals(listOf(RerouteCall(LEFT_AT.point, DESTINATION, 92, REFERENCE)), planner.calls)
        }

    @Test
    fun `a found reroute replaces the route in the same session and clears the status`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, REFERENCE)
            planner.answer.complete(RouteResult.Found(REROUTED, NEW_REFERENCE))

            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            assertEquals(ActiveRoute(session, REROUTED, DESTINATION, NEW_REFERENCE), store.active.value)
            assertEquals(listOf<Collection<File>>(setOf(NEW_REFERENCE)), pruned)
        }

    // BRouter writes no reference for a route it merged onto the one it was given.
    @Test
    fun `a found reroute without a reference of its own keeps the session's reference for the next one`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, REFERENCE)
            planner.answer.complete(RouteResult.Found(REROUTED, reference = null))
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()
            val kept = store.active.value?.reference
            val prunedAfterTheFirst = pruned.toList()

            nowMs += REROUTE_MIN_INTERVAL_MS
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            assertEquals(REFERENCE, kept)
            assertEquals(listOf<Collection<File>>(setOf(REFERENCE)), prunedAfterTheFirst)
            assertEquals(listOf(REFERENCE, REFERENCE), planner.calls.map { it.reference })
        }

    @Test
    fun `the status says rerouting while a reroute runs`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, REFERENCE)

            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            assertEquals(RerouteStatus.REROUTING, store.active.value?.reroute)
        }

    @Test
    fun `missing tiles keep the old line and say no data`() =
        runTest {
            val session = rerouteAnswered(RouteResult.MissingTiles(listOf("E130_N30")))

            assertEquals(ActiveRoute(session, ROUTE, DESTINATION, REFERENCE, RerouteStatus.NO_DATA), store.active.value)
        }

    @Test
    fun `a destination without road data keeps the old line and says no data`() =
        runTest {
            rerouteAnswered(RouteResult.NoRoadData)

            assertEquals(RerouteStatus.NO_DATA, store.active.value?.reroute)
        }

    @Test
    fun `a failed reroute keeps the old line and says failed`() =
        runTest {
            val session = rerouteAnswered(RouteResult.Failed("no track found"))

            assertEquals(ActiveRoute(session, ROUTE, DESTINATION, REFERENCE, RerouteStatus.FAILED), store.active.value)
        }

    @Test
    fun `a timed out reroute says failed`() =
        runTest {
            rerouteAnswered(RouteResult.TimedOut)

            assertEquals(RerouteStatus.FAILED, store.active.value?.reroute)
        }

    @Test
    fun `a second LeftRoute while one runs is ignored`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, REFERENCE)
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            nowMs += REROUTE_MIN_INTERVAL_MS
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            assertEquals(1, planner.calls.size)
        }

    @Test
    fun `a LeftRoute within the minimum interval is ignored`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, REFERENCE)
            planner.answer.complete(RouteResult.Failed("no track found"))
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            nowMs += REROUTE_MIN_INTERVAL_MS - 1
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            assertEquals(1, planner.calls.size)
        }

    @Test
    fun `after the interval it routes again`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, REFERENCE)
            planner.answer.complete(RouteResult.Failed("no track found"))
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            nowMs += REROUTE_MIN_INTERVAL_MS
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            assertEquals(2, planner.calls.size)
        }

    @Test
    fun `a new session may reroute at once`() =
        runTest {
            val rerouter = rerouter()
            val first = store.start(ROUTE, DESTINATION, REFERENCE)
            planner.answer.complete(RouteResult.Failed("no track found"))
            rerouter.onLeftRoute(first, LEFT_AT)
            runCurrent()

            nowMs += 1_000L
            val second = store.start(REROUTED, DESTINATION, NEW_REFERENCE)
            rerouter.onLeftRoute(second, LEFT_AT)
            runCurrent()

            assertEquals(listOf(REFERENCE, NEW_REFERENCE), planner.calls.map { it.reference })
        }

    @Test
    fun `a reroute for an old session does nothing`() =
        runTest {
            val rerouter = rerouter()
            val old = store.start(ROUTE, DESTINATION, REFERENCE)
            val current = store.start(ROUTE, DESTINATION, REFERENCE)

            rerouter.onLeftRoute(old, LEFT_AT)
            runCurrent()

            assertEquals(emptyList(), planner.calls)
            assertEquals(ActiveRoute(current, ROUTE, DESTINATION, REFERENCE), store.active.value)
        }

    @Test
    fun `clearing the route cancels a running reroute and drops its result`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, REFERENCE)
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            store.clear()
            runCurrent()
            planner.answer.complete(RouteResult.Found(REROUTED, NEW_REFERENCE))
            runCurrent()

            assertTrue(planner.cancelled)
            assertNull(store.active.value)
        }

    @Test
    fun `a new route cancels a running reroute`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, REFERENCE)
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            val next = store.start(REROUTED, DESTINATION, NEW_REFERENCE)
            runCurrent()

            assertTrue(planner.cancelled)
            assertEquals(ActiveRoute(next, REROUTED, DESTINATION, NEW_REFERENCE), store.active.value)
        }

    @Test
    fun `cancel stops a running reroute`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, REFERENCE)
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            rerouter.cancel()
            runCurrent()

            assertTrue(planner.cancelled)
        }

    @Test
    fun `a cancelled reroute leaves the old line without a status`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, REFERENCE)
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            rerouter.cancel()
            runCurrent()

            assertEquals(ActiveRoute(session, ROUTE, DESTINATION, REFERENCE), store.active.value)
        }

    @Test
    fun `back on the route clears a failure status`() =
        runTest {
            val rerouter = rerouter()
            val session = rerouteAnswered(RouteResult.Failed("no track found"), rerouter)

            rerouter.onBackOnRoute(session)

            assertEquals(RerouteStatus.NONE, store.active.value?.reroute)
        }

    @Test
    fun `back on the route cancels a running reroute and clears its status`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, REFERENCE)
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            rerouter.onBackOnRoute(session)
            runCurrent()

            assertTrue(planner.cancelled)
            assertEquals(ActiveRoute(session, ROUTE, DESTINATION, REFERENCE), store.active.value)
        }

    // The engine answers in the same moment the vehicle rejoins the route, too late for the cancel to stop it.
    @Test
    fun `a failure that lands after back on the route does not show`() =
        runTest {
            val session = rerouteAnsweredAfterBackOnRoute(RouteResult.Failed("no track found"))

            assertEquals(ActiveRoute(session, ROUTE, DESTINATION, REFERENCE), store.active.value)
        }

    @Test
    fun `missing data that lands after back on the route does not show`() =
        runTest {
            val session = rerouteAnsweredAfterBackOnRoute(RouteResult.MissingTiles(listOf("E130_N30")))

            assertEquals(ActiveRoute(session, ROUTE, DESTINATION, REFERENCE), store.active.value)
        }

    @Test
    fun `no heading is passed for a fix without bearing or below the moving floor`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, REFERENCE)
            planner.answer.complete(RouteResult.Failed("no track found"))
            val withoutBearing = LEFT_AT.copy(bearingDegrees = null)
            val standing = LEFT_AT.copy(speedMetersPerSecond = 0.2f)

            rerouter.onLeftRoute(session, withoutBearing)
            runCurrent()
            nowMs += REROUTE_MIN_INTERVAL_MS
            rerouter.onLeftRoute(session, standing)
            runCurrent()

            assertEquals(listOf(null, null), planner.calls.map { it.headingDegrees })
        }

    @Test
    fun `stats count reroutes and record whether a reference was used`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, reference = null)
            planner.computingMs = 70L
            planner.answer.complete(RouteResult.Found(REROUTED, NEW_REFERENCE))
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()
            val first = rerouter.stats.value

            nowMs += REROUTE_MIN_INTERVAL_MS
            planner.answer = CompletableDeferred(RouteResult.TimedOut)
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            assertEquals(RerouteStats(1, RouteRun(RouteRunOutcome.FOUND, computingMs = 70L), false), first)
            assertEquals(
                RerouteStats(2, RouteRun(RouteRunOutcome.TIMED_OUT, computingMs = 70L), true),
                rerouter.stats.value,
            )
        }

    // A session whose one reroute through [rerouter] has answered [result]; returns the session.
    private fun TestScope.rerouteAnswered(
        result: RouteResult,
        rerouter: Rerouter = rerouter(),
    ): Long {
        val session = store.start(ROUTE, DESTINATION, REFERENCE)
        planner.answer.complete(result)
        rerouter.onLeftRoute(session, LEFT_AT)
        runCurrent()
        return session
    }

    // A session whose one reroute answers [result] only after the vehicle is back on the route; returns the session.
    private fun TestScope.rerouteAnsweredAfterBackOnRoute(result: RouteResult): Long {
        val rerouter = rerouter()
        val session = store.start(ROUTE, DESTINATION, REFERENCE)
        planner.beforeAnswering = { rerouter.onBackOnRoute(session) }
        planner.answer.complete(result)
        rerouter.onLeftRoute(session, LEFT_AT)
        runCurrent()
        return session
    }

    private data class RerouteCall(
        val origin: RoutePoint,
        val destination: RoutePoint,
        val headingDegrees: Int?,
        val reference: File?,
    )

    // The planner as the rerouter sees it: every call is recorded, takes [computingMs] on the test's clock, waits for
    // [answer], and runs [beforeAnswering] just before it returns the answer, with no suspension in between.
    private inner class FakeReroutePlanner {
        val calls = mutableListOf<RerouteCall>()
        var answer = CompletableDeferred<RouteResult>()
        var computingMs = 0L
        var cancelled = false
        var beforeAnswering: () -> Unit = {}

        suspend fun route(
            origin: RoutePoint,
            destination: RoutePoint,
            headingDegrees: Int?,
            reference: File?,
        ): RouteResult {
            calls += RerouteCall(origin, destination, headingDegrees, reference)
            nowMs += computingMs
            return try {
                answer.await().also { beforeAnswering() }
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }
    }

    private companion object {
        const val START_MS = 1_000_000L
        val ROUTE = fakeRoute()
        val REROUTED = fakeEquatorRoute()
        val DESTINATION = RoutePoint(latitude = 34.07, longitude = 133.0)
        val REFERENCE = File("route-1.bin")
        val NEW_REFERENCE = File("route-2.bin")

        // Moving east-north-east, 92.4° rounding to 92.
        val LEFT_AT = fakeRouteFix(RoutePoint(33.9, 132.8), elapsedMs = 60_000L, bearingDegrees = 92.4f)
    }
}

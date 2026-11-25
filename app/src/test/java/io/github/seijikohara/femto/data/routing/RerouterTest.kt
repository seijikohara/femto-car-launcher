package io.github.seijikohara.femto.data.routing

import io.github.seijikohara.femto.testfixtures.fakeEquatorRoute
import io.github.seijikohara.femto.testfixtures.fakeRoute
import io.github.seijikohara.femto.testfixtures.fakeRouteFix
import io.github.seijikohara.femto.testfixtures.withoutChoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RerouterTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val store = ActiveRouteStore()
    private val planner = FakeReroutePlanner()
    private val pruned = mutableListOf<Collection<File>>()
    private var nowMs = START_MS

    // Runs on every read of the rerouter's clock, so a test can act between two steps of a reroute.
    private var onClockRead: () -> Unit = {}

    // How often the rerouter has stopped the candidate search.
    private var candidateCancels = 0

    // The rerouter runs its reroutes on the test's background scope, so each test steps them with runCurrent. The
    // "Reroute automatically" setting is on unless a test passes its own.
    private fun TestScope.rerouter(enabled: Flow<Boolean> = flowOf(true)): Rerouter =
        Rerouter(
            route = planner::route,
            store = store,
            scope = backgroundScope,
            pruneReferences = { pruned += it },
            nowMs = {
                onClockRead()
                nowMs
            },
            cancelCandidates = { candidateCancels++ },
            enabled = enabled,
        )

    // The candidates were found from where the route started, so they no longer apply once the vehicle left it.
    @Test
    fun `leaving the route stops the candidate search and closes the choice`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)

            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            assertEquals(1 to false, candidateCancels to store.active.value?.choosing)
        }

    @Test
    fun `leaving the route routes again from the fix to the same destination with the reference and heading`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)

            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            assertEquals(listOf(RerouteCall(LEFT_AT.point, DESTINATION, 92, REFERENCE, NO_CONDITIONS)), planner.calls)
        }

    // The driver may have picked other conditions for the next route since this one was found.
    @Test
    fun `a reroute uses the session's conditions`() =
        runTest {
            val rerouter = rerouter()
            val conditions = RouteConditions(avoidMotorways = true, avoidTolls = true)
            val session = store.start(ROUTE, DESTINATION, conditions, REFERENCE)

            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            assertEquals(listOf(conditions), planner.calls.map { it.conditions })
        }

    @Test
    fun `a found reroute replaces the route in the same session and clears the status`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
            planner.answer.complete(RouteResult.Found(REROUTED, NEW_REFERENCE))

            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            assertEquals(
                ActiveRoute(session, REROUTED, DESTINATION, NEW_REFERENCE),
                store.active.value?.withoutChoice(),
            )
            assertEquals(listOf<Collection<File>>(setOf(NEW_REFERENCE)), pruned)
        }

    // The reference lived in the old process's cache, so the first reroute after a restart searches in full, and the
    // reference it writes serves the next one.
    @Test
    fun `the first reroute after a restore runs without a reference`() =
        runTest {
            val rerouter = rerouter()
            val session = assertNotNull(store.restore(ROUTE, DESTINATION, NO_CONDITIONS))
            planner.answer.complete(RouteResult.Found(REROUTED, NEW_REFERENCE))

            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            assertEquals(listOf<File?>(null), planner.calls.map { it.reference })
            assertEquals(
                ActiveRoute(session, REROUTED, DESTINATION, NEW_REFERENCE),
                store.active.value?.withoutChoice(),
            )
        }

    // BRouter writes no reference for a route it merged onto the one it was given.
    @Test
    fun `a found reroute without a reference of its own keeps the session's reference for the next one`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
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
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)

            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            assertEquals(RerouteStatus.REROUTING, store.active.value?.reroute)
        }

    @Test
    fun `missing tiles keep the old line and say no data`() =
        runTest {
            val session = rerouteAnswered(RouteResult.MissingTiles(listOf("E130_N30")))

            assertEquals(
                ActiveRoute(session, ROUTE, DESTINATION, REFERENCE, RerouteStatus.NO_DATA),
                store.active.value?.withoutChoice(),
            )
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

            assertEquals(
                ActiveRoute(session, ROUTE, DESTINATION, REFERENCE, RerouteStatus.FAILED),
                store.active.value?.withoutChoice(),
            )
        }

    @Test
    fun `a timed out reroute says failed`() =
        runTest {
            rerouteAnswered(RouteResult.TimedOut)

            assertEquals(RerouteStatus.FAILED, store.active.value?.reroute)
        }

    // The fake planner never answers, as a planner whose lock another search holds: the deadline counts the wait.
    @Test
    fun `a reroute that gets no answer within its deadline says failed`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            advanceTimeBy(REROUTE_TIME_LIMIT_MS)
            runCurrent()

            assertEquals(RerouteStatus.FAILED, store.active.value?.reroute)
            assertEquals(
                RouteRunOutcome.TIMED_OUT,
                rerouter.stats.value.last
                    ?.outcome,
            )
        }

    @Test
    fun `a second LeftRoute while one runs is ignored`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
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
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
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
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
            planner.answer.complete(RouteResult.Failed("no track found"))
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            nowMs += REROUTE_MIN_INTERVAL_MS
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            assertEquals(2, planner.calls.size)
        }

    @Test
    fun `a reroute that ran thirty seconds holds the next for thirty seconds after it ended`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
            planner.computingMs = REROUTE_MIN_INTERVAL_MS
            planner.answer.complete(RouteResult.Failed("no track found"))
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()
            planner.computingMs = 0L

            nowMs += REROUTE_MIN_INTERVAL_MS - 1
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()
            val callsWithinTheInterval = planner.calls.size
            nowMs += 1
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            assertEquals(1 to 2, callsWithinTheInterval to planner.calls.size)
        }

    @Test
    fun `after a reroute that back on the route cancelled, the next LeftRoute reroutes at once`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()
            rerouter.onBackOnRoute(session)
            runCurrent()

            nowMs += 1_000L
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            assertEquals(2, planner.calls.size)
        }

    @Test
    fun `a new session may reroute at once`() =
        runTest {
            val rerouter = rerouter()
            val first = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
            planner.answer.complete(RouteResult.Failed("no track found"))
            rerouter.onLeftRoute(first, LEFT_AT)
            runCurrent()

            nowMs += 1_000L
            val second = store.start(REROUTED, DESTINATION, NO_CONDITIONS, NEW_REFERENCE)
            rerouter.onLeftRoute(second, LEFT_AT)
            runCurrent()

            assertEquals(listOf(REFERENCE, NEW_REFERENCE), planner.calls.map { it.reference })
        }

    @Test
    fun `a reroute for an old session does nothing`() =
        runTest {
            val rerouter = rerouter()
            val old = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
            val current = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)

            rerouter.onLeftRoute(old, LEFT_AT)
            runCurrent()

            assertEquals(emptyList(), planner.calls)
            assertEquals(ActiveRoute(current, ROUTE, DESTINATION, REFERENCE), store.active.value?.withoutChoice())
        }

    @Test
    fun `clearing the route cancels a running reroute and drops its result`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
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
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            val next = store.start(REROUTED, DESTINATION, NO_CONDITIONS, NEW_REFERENCE)
            runCurrent()

            assertTrue(planner.cancelled)
            assertEquals(ActiveRoute(next, REROUTED, DESTINATION, NEW_REFERENCE), store.active.value?.withoutChoice())
        }

    @Test
    fun `cancel stops a running reroute`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            rerouter.cancel()
            runCurrent()

            assertTrue(planner.cancelled)
        }

    // The panel cancels the reroute after the planner has returned, before the result is put in place: the next read
    // of the clock (for the computing time) is the moment between.
    @Test
    fun `a found route cancelled before it is put in place deletes its reference`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
            val written = tempFolder.newFile("route-3.bin")
            planner.beforeAnswering = {
                onClockRead = {
                    onClockRead = {}
                    rerouter.cancel()
                }
            }
            planner.answer.complete(RouteResult.Found(REROUTED, written))

            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            assertFalse(written.exists())
            assertEquals(ActiveRoute(session, ROUTE, DESTINATION, REFERENCE), store.active.value?.withoutChoice())
        }

    // The engine returns a found route just as the panel cancels the reroute, too late for the cancel to stop it.
    @Test
    fun `a found route that arrives after the reroute was cancelled deletes its reference`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
            val written = tempFolder.newFile("route-3.bin")
            planner.beforeAnswering = { rerouter.cancel() }
            planner.answer.complete(RouteResult.Found(REROUTED, written))

            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            assertFalse(written.exists())
            assertEquals(ActiveRoute(session, ROUTE, DESTINATION, REFERENCE), store.active.value?.withoutChoice())
        }

    @Test
    fun `a cancelled reroute leaves the old line without a status`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            rerouter.cancel()
            runCurrent()

            assertEquals(ActiveRoute(session, ROUTE, DESTINATION, REFERENCE), store.active.value?.withoutChoice())
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
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            rerouter.onBackOnRoute(session)
            runCurrent()

            assertTrue(planner.cancelled)
            assertEquals(ActiveRoute(session, ROUTE, DESTINATION, REFERENCE), store.active.value?.withoutChoice())
        }

    // The engine answers in the same moment the vehicle rejoins the route, too late for the cancel to stop it.
    @Test
    fun `a failure that lands after back on the route does not show`() =
        runTest {
            val session = rerouteAnsweredAfterBackOnRoute(RouteResult.Failed("no track found"))

            assertEquals(ActiveRoute(session, ROUTE, DESTINATION, REFERENCE), store.active.value?.withoutChoice())
        }

    @Test
    fun `missing data that lands after back on the route does not show`() =
        runTest {
            val session = rerouteAnsweredAfterBackOnRoute(RouteResult.MissingTiles(listOf("E130_N30")))

            assertEquals(ActiveRoute(session, ROUTE, DESTINATION, REFERENCE), store.active.value?.withoutChoice())
        }

    @Test
    fun `no heading is passed for a fix without bearing or below the moving floor`() =
        runTest {
            val rerouter = rerouter()
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
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
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, reference = null)
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

    // --- The "Reroute automatically" setting ---------------------------------------------------------------------------

    // The rerouter may be created at the first report that the vehicle left a route, so that report comes before the
    // stored setting has been read: a default would reroute the first departure.
    @Test
    fun `with reroute off, the first departure after the start never reroutes`() =
        runTest {
            val setting = MutableSharedFlow<Boolean>(replay = 1)
            val rerouter = rerouter(enabled = setting)
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)

            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()
            setting.emit(false)
            runCurrent()

            assertEquals(emptyList(), planner.calls)
            assertEquals(
                Triple(RerouteStatus.NONE, true, 0),
                store.active.value?.let { Triple(it.reroute, it.choosing, candidateCancels) },
            )
        }

    @Test
    fun `switching reroute off stops a running reroute and clears its status`() =
        runTest {
            val setting = MutableStateFlow(true)
            val rerouter = rerouter(enabled = setting)
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            setting.value = false
            runCurrent()

            assertTrue(planner.cancelled)
            assertEquals(ActiveRoute(session, ROUTE, DESTINATION, REFERENCE), store.active.value?.withoutChoice())
        }

    @Test
    fun `switching reroute off clears no data`() =
        runTest {
            val setting = MutableStateFlow(true)
            rerouteAnswered(RouteResult.MissingTiles(listOf("E130_N30")), rerouter(enabled = setting))
            val before = store.active.value?.reroute

            setting.value = false
            runCurrent()

            assertEquals(RerouteStatus.NO_DATA to RerouteStatus.NONE, before to store.active.value?.reroute)
        }

    @Test
    fun `switching reroute off clears a failure`() =
        runTest {
            val setting = MutableStateFlow(true)
            rerouteAnswered(RouteResult.Failed("no track found"), rerouter(enabled = setting))
            val before = store.active.value?.reroute

            setting.value = false
            runCurrent()

            assertEquals(RerouteStatus.FAILED to RerouteStatus.NONE, before to store.active.value?.reroute)
        }

    // The departure the setting turned away did not reroute, so it holds back none after it.
    @Test
    fun `switched on again, the next departure reroutes`() =
        runTest {
            val setting = MutableStateFlow(false)
            val rerouter = rerouter(enabled = setting)
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()
            val callsWhileOff = planner.calls.size

            setting.value = true
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            assertEquals(0 to 1, callsWhileOff to planner.calls.size)
        }

    @Test
    fun `back on the route before the setting has been read starts nothing`() =
        runTest {
            val setting = MutableSharedFlow<Boolean>(replay = 1)
            val rerouter = rerouter(enabled = setting)
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            rerouter.onBackOnRoute(session)
            setting.emit(true)
            runCurrent()

            assertEquals(emptyList(), planner.calls)
            assertEquals(RerouteStatus.NONE, store.active.value?.reroute)
        }

    @Test
    fun `cancel before the setting has been read starts nothing`() =
        runTest {
            val setting = MutableSharedFlow<Boolean>(replay = 1)
            val rerouter = rerouter(enabled = setting)
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            rerouter.cancel()
            setting.emit(true)
            runCurrent()

            assertEquals(emptyList(), planner.calls)
            assertEquals(RerouteStatus.NONE, store.active.value?.reroute)
        }

    // first() checks for a cancel when it stops the flow it reads, so a cancel inside a well-behaved flow always ends
    // the read with a CancellationException. In the app only another thread lands a cancel between the read and the
    // lock; this flow stands in for that thread: it hands over the value, keeps first()'s stop from reaching it, and
    // then cancels.
    @Test
    fun `a cancel that lands between the setting's read and the lock starts nothing`() =
        runTest {
            lateinit var rerouter: Rerouter
            val setting =
                flow {
                    runCatching { emit(true) }
                    rerouter.cancel()
                }
            rerouter = rerouter(enabled = setting)
            // The switch-off collector reads the flow too; it runs out here, before a reroute it could cancel.
            runCurrent()
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)

            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            assertEquals(emptyList(), planner.calls)
            assertEquals(0 to true, candidateCancels to store.active.value?.choosing)
        }

    // The panel set a new route while the departure from the old one read the setting: the new route's search for
    // other routes is its own.
    @Test
    fun `a departure whose session ended while the setting was read leaves the new route's search alone`() =
        runTest {
            val setting = MutableSharedFlow<Boolean>(replay = 1)
            val rerouter = rerouter(enabled = setting)
            val left = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
            rerouter.onLeftRoute(left, LEFT_AT)
            runCurrent()

            store.start(REROUTED, DESTINATION, NO_CONDITIONS, NEW_REFERENCE)
            setting.emit(true)
            runCurrent()

            assertEquals(emptyList(), planner.calls)
            assertEquals(0 to true, candidateCancels to store.active.value?.choosing)
        }

    // The driver picked another of the offered routes while the departure read the setting. The departure belonged to
    // the route left, whose reference the choice's clean-up may delete; the route picked reports its own departure.
    @Test
    fun `a departure from a route the driver changed while the setting was read does not reroute`() =
        runTest {
            val setting = MutableSharedFlow<Boolean>(replay = 1)
            val rerouter = rerouter(enabled = setting)
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
            store.addCandidate(session, RouteCandidate(REROUTED, NEW_REFERENCE, computingMs = 900L))
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            store.select(session, 1)
            setting.emit(true)
            runCurrent()

            assertEquals(emptyList(), planner.calls)
            assertEquals(
                Triple(0, true, RerouteStatus.NONE),
                store.active.value?.let { Triple(candidateCancels, it.choosing, it.reroute) },
            )
        }

    // The route left is still on the map, with another reference by the time the setting has been read.
    @Test
    fun `a reroute uses the reference the route holds once the setting has been read`() =
        runTest {
            val setting = MutableSharedFlow<Boolean>(replay = 1)
            val rerouter = rerouter(enabled = setting)
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            store.replace(session, ROUTE, NEW_REFERENCE)
            setting.emit(true)
            runCurrent()

            assertEquals(listOf<File?>(NEW_REFERENCE), planner.calls.map { it.reference })
        }

    @Test
    fun `switched off a second time, a running reroute stops again`() =
        runTest {
            val setting = MutableStateFlow(true)
            val rerouter = rerouter(enabled = setting)
            val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
            runCurrent()
            setting.value = false
            runCurrent()
            setting.value = true
            runCurrent()
            rerouter.onLeftRoute(session, LEFT_AT)
            runCurrent()

            setting.value = false
            runCurrent()

            assertTrue(planner.cancelled)
            assertEquals(RerouteStatus.NONE, store.active.value?.reroute)
        }

    // A session whose one reroute through [rerouter] has answered [result]; returns the session.
    private fun TestScope.rerouteAnswered(
        result: RouteResult,
        rerouter: Rerouter = rerouter(),
    ): Long {
        val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
        planner.answer.complete(result)
        rerouter.onLeftRoute(session, LEFT_AT)
        runCurrent()
        return session
    }

    // A session whose one reroute answers [result] only after the vehicle is back on the route; returns the session.
    private fun TestScope.rerouteAnsweredAfterBackOnRoute(result: RouteResult): Long {
        val rerouter = rerouter()
        val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, REFERENCE)
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
        val conditions: RouteConditions,
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
            conditions: RouteConditions,
        ): RouteResult {
            calls += RerouteCall(origin, destination, headingDegrees, reference, conditions)
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
        val NO_CONDITIONS = RouteConditions()

        // Moving east-north-east, 92.4° rounding to 92.
        val LEFT_AT = fakeRouteFix(RoutePoint(33.9, 132.8), elapsedMs = 60_000L, bearingDegrees = 92.4f)
    }
}

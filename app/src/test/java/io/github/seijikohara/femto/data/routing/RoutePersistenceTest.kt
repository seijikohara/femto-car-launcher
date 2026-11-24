@file:OptIn(ExperimentalCoroutinesApi::class) // runCurrent steps the keeper's coroutine.

package io.github.seijikohara.femto.data.routing

import io.github.seijikohara.femto.data.common.writeAtomically
import io.github.seijikohara.femto.testfixtures.fakeEquatorRoute
import io.github.seijikohara.femto.testfixtures.fakeKeptRoute
import io.github.seijikohara.femto.testfixtures.fakeOutAndBackRoute
import io.github.seijikohara.femto.testfixtures.fakeParallelRoadsRoute
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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

class RoutePersistenceTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val store = ActiveRouteStore()

    private val dir: File get() = File(tempFolder.root, ACTIVE_ROUTE_DIR)

    // The files each write replaced, by name, in order.
    private val written = mutableListOf<String>()

    // Runs as each write starts, so a test can act while one is under way.
    private var beforeWrite: (File) -> Unit = {}

    // Whether the next writes fail, as on a full disk.
    private var failWrites = false

    private val files: RouteSnapshotFiles by lazy {
        RouteSnapshotFiles(dir) { target, bytes ->
            beforeWrite(target)
            !failWrites && writeAtomically(target, bytes).also { written += target.name }
        }
    }

    // The "Start a new trip automatically" time, as each decision reads it.
    private var parkedGapMs: suspend () -> Long? = { TWO_HOURS_MS }

    private var nowEpochMs = NOW_MS
    private var nowElapsedMs = BOOT_MS

    // The process's keeper, on the test's background scope: each test steps it with runCurrent.
    private fun TestScope.keeper(): RoutePersistence =
        RoutePersistence(
            store = store,
            files = files,
            parkedGapMs = { parkedGapMs() },
            scope = backgroundScope,
            nowEpochMs = { nowEpochMs },
            nowElapsedMs = { nowElapsedMs },
        )

    private fun TestScope.started(): RoutePersistence =
        keeper().also {
            it.start(enabled = true)
            runCurrent()
        }

    // What the last process left: its route and, unless null, when that route was last driven.
    private fun keptBefore(lastDrivenMs: Long?) {
        files.write(KEPT)
        lastDrivenMs?.let(files::writeStamp)
        written.clear()
    }

    private fun ActiveRouteStore.keptRouteOrNull(): KeptRoute? =
        active.value?.let { KeptRoute(it.route, it.destination, it.conditions) }

    private fun RoutePersistence.gpsFix(
        timeEpochMs: Long,
        scope: TestScope,
    ) {
        onGpsFix(timeEpochMs)
        scope.runCurrent()
    }

    // --- The decision at the start ------------------------------------------------------------------------------------

    @Test
    fun `a restart after a park shorter than the setting restores the route`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS - ONE_HOUR_MS)

            val keeper = started()

            assertEquals(KEPT, store.keptRouteOrNull())
            assertEquals(RouteRestoreDecision.RESTORED, keeper.decision.value)
        }

    @Test
    fun `a restart after a longer park deletes the files and restores nothing`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS - TWO_HOURS_MS)

            val keeper = started()

            assertNull(store.active.value)
            assertFalse(dir.exists())
            assertEquals(RouteRestoreDecision.PARKED_TOO_LONG, keeper.decision.value)
        }

    @Test
    fun `the setting Never restores however long the park`() =
        runTest {
            parkedGapMs = { null }
            keptBefore(lastDrivenMs = NOW_MS - 30 * ONE_DAY_MS)

            started()

            assertEquals(KEPT, store.keptRouteOrNull())
        }

    @Test
    fun `a restart without a kept route restores nothing`() =
        runTest {
            val keeper = started()

            assertNull(store.active.value)
            assertEquals(RouteRestoreDecision.NO_SNAPSHOT, keeper.decision.value)
        }

    @Test
    fun `an unreadable snapshot is deleted with its stamp and restores nothing`() =
        runTest {
            File(dir, "route.json").apply { parentFile?.mkdirs() }.writeText("{\"version\":1,\"line\":[")
            files.writeStamp(NOW_MS)

            val keeper = started()

            assertNull(store.active.value)
            assertFalse(dir.exists())
            assertEquals(RouteRestoreDecision.NO_SNAPSHOT, keeper.decision.value)
        }

    // --- Clock trouble ------------------------------------------------------------------------------------------------

    // A unit that boots with an unset clock: the wall clock reads years before the last drive.
    @Test
    fun `a clock behind the stamp waits for the first fix, then restores by GPS time`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS - ONE_HOUR_MS)
            nowEpochMs = UNSET_CLOCK_MS
            val keeper = started()
            val waiting = Triple(store.active.value, files.readOrNull(), keeper.decision.value)

            keeper.gpsFix(NOW_MS, this)

            assertEquals(Triple(null, KEPT, RouteRestoreDecision.WAITING_FOR_FIX), waiting)
            assertEquals(KEPT, store.keptRouteOrNull())
            assertEquals(RouteRestoreDecision.RESTORED, keeper.decision.value)
        }

    @Test
    fun `a clock behind the stamp waits for the first fix, then deletes by GPS time`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS - ONE_HOUR_MS)
            nowEpochMs = UNSET_CLOCK_MS
            val keeper = started()

            keeper.gpsFix(NOW_MS + ONE_HOUR_MS, this)

            assertNull(store.active.value)
            assertFalse(dir.exists())
            assertEquals(RouteRestoreDecision.PARKED_TOO_LONG, keeper.decision.value)
        }

    // A unit whose clock starts at its last saved time: the wall clock says a minute, GPS says three days.
    @Test
    fun `a restored route that the first fix shows too old is cleared`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS)
            nowEpochMs = NOW_MS + 60_000L
            val keeper = started()
            val restored = store.keptRouteOrNull()

            keeper.gpsFix(NOW_MS + 3 * ONE_DAY_MS, this)

            assertEquals(KEPT, restored)
            assertNull(store.active.value)
            assertFalse(dir.exists())
            assertEquals(RouteRestoreDecision.PARKED_TOO_LONG, keeper.decision.value)
        }

    @Test
    fun `a restored route that the first fix confirms stays and is stamped with the fix`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS - ONE_HOUR_MS)
            val keeper = started()

            keeper.gpsFix(NOW_MS + 60_000L, this)

            assertEquals(KEPT, store.keptRouteOrNull())
            assertEquals(NOW_MS + 60_000L, files.readStampOrNull())
        }

    // The wall clock agreed with the stamp at the start, so a fix far before the stamp has the odd clock: the route
    // restored on the wall clock's word stays.
    @Test
    fun `a first fix far before the stamp leaves a route restored on the wall clock`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS)
            nowEpochMs = NOW_MS + 60_000L
            val keeper = started()

            keeper.gpsFix(NOW_MS - CLOCK_ALLOWANCE_MS - 1, this)

            assertEquals(KEPT, store.keptRouteOrNull())
            assertEquals(RouteRestoreDecision.RESTORED, keeper.decision.value)
        }

    @Test
    fun `a snapshot without a stamp waits for the first fix, which deletes it`() =
        runTest {
            keptBefore(lastDrivenMs = null)
            val keeper = started()
            val waiting = keeper.decision.value

            keeper.gpsFix(NOW_MS, this)

            assertEquals(RouteRestoreDecision.WAITING_FOR_FIX, waiting)
            assertNull(store.active.value)
            assertFalse(dir.exists())
            assertEquals(RouteRestoreDecision.UNDATED, keeper.decision.value)
        }

    // A unit last driven with a good clock boots 38 hours later with its clock back at an old date, and its first fix
    // carries that clock too, as a bridged or mock provider's can: the fix reads years before the last drive.
    @Test
    fun `a first fix earlier than the stamp cannot measure the park, so nothing is restored`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS - 38 * ONE_HOUR_MS)
            nowEpochMs = UNSET_CLOCK_MS
            val keeper = started()

            keeper.gpsFix(UNSET_CLOCK_MS + 60_000L, this)

            assertNull(store.active.value)
            assertFalse(dir.exists())
            assertEquals(RouteRestoreDecision.FIX_BEFORE_LAST_DRIVE, keeper.decision.value)
        }

    // No time rule applies with "Never", here chosen while the route waited.
    @Test
    fun `with Never a first fix earlier than the stamp still restores`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS)
            nowEpochMs = UNSET_CLOCK_MS
            val keeper = started()
            parkedGapMs = { null }

            keeper.gpsFix(UNSET_CLOCK_MS + 60_000L, this)

            assertEquals(KEPT, store.keptRouteOrNull())
        }

    @Test
    fun `a first fix up to the clock allowance before the stamp restores`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS)
            nowEpochMs = UNSET_CLOCK_MS
            val keeper = started()

            keeper.gpsFix(NOW_MS - CLOCK_ALLOWANCE_MS, this)

            assertEquals(KEPT, store.keptRouteOrNull())
        }

    @Test
    fun `a first fix more than the clock allowance before the stamp restores nothing`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS)
            nowEpochMs = UNSET_CLOCK_MS
            val keeper = started()

            keeper.gpsFix(NOW_MS - CLOCK_ALLOWANCE_MS - 1, this)

            assertNull(store.active.value)
            assertFalse(dir.exists())
        }

    // A clock that runs a little behind GPS time, restarted within a minute of the last stamp: the route comes back at
    // once, and the first fix still checks the park.
    @Test
    fun `a wall clock up to the allowance behind the stamp restores and checks at the first fix`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS)
            nowEpochMs = NOW_MS - CLOCK_ALLOWANCE_MS
            val keeper = started()
            val restored = store.keptRouteOrNull()

            keeper.gpsFix(NOW_MS + 3 * ONE_DAY_MS, this)

            assertEquals(KEPT, restored)
            assertNull(store.active.value)
        }

    @Test
    fun `a wall clock more than the allowance behind the stamp waits for a fix`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS)
            nowEpochMs = NOW_MS - CLOCK_ALLOWANCE_MS - 1

            val keeper = started()

            assertNull(store.active.value)
            assertEquals(KEPT, files.readOrNull())
            assertEquals(RouteRestoreDecision.WAITING_FOR_FIX, keeper.decision.value)
        }

    // --- The start-up order -------------------------------------------------------------------------------------------

    // The settings store answers late, so the store's empty state reaches the keeper before its decision.
    @Test
    fun `the first null of a new process does not delete the snapshot`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS - ONE_HOUR_MS)
            val setting = CompletableDeferred<Long?>()
            parkedGapMs = { setting.await() }
            started()
            val beforeTheDecision = files.readOrNull() to files.readStampOrNull()

            setting.complete(TWO_HOURS_MS)
            runCurrent()

            assertEquals(KEPT to NOW_MS - ONE_HOUR_MS, beforeTheDecision)
            assertEquals(KEPT, store.keptRouteOrNull())
        }

    @Test
    fun `a restore never replaces an existing session`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS - ONE_HOUR_MS)
            val driven = store.start(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS, reference = null)

            val keeper = started()

            assertEquals(driven, store.active.value?.session)
            assertEquals(KeptRoute(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS), files.readOrNull())
            assertEquals(RouteRestoreDecision.REPLACED, keeper.decision.value)
        }

    @Test
    fun `a route set while the kept one waits for a fix replaces it, and the fix restores nothing`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS - ONE_HOUR_MS)
            nowEpochMs = UNSET_CLOCK_MS
            val keeper = started()
            val driven = store.start(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS, reference = null)
            runCurrent()

            keeper.gpsFix(NOW_MS, this)

            assertEquals(driven, store.active.value?.session)
            assertEquals(KeptRoute(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS), files.readOrNull())
            assertEquals(RouteRestoreDecision.REPLACED, keeper.decision.value)
        }

    @Test
    fun `a route set after a restore is not cleared by the first fix`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS)
            nowEpochMs = NOW_MS + 60_000L
            val keeper = started()
            val driven = store.start(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS, reference = null)
            runCurrent()

            keeper.gpsFix(NOW_MS + 3 * ONE_DAY_MS, this)

            assertEquals(driven, store.active.value?.session)
            assertEquals(KeptRoute(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS), files.readOrNull())
        }

    // The driver's route stands at the decision and is cleared before the keeper ever sees it: the clear, queued as the
    // setting is read, runs before the keeper starts to follow the store.
    @Test
    fun `a route set and cleared during the start's decision leaves no kept route`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS - ONE_HOUR_MS)
            store.start(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS, reference = null)
            parkedGapMs = {
                backgroundScope.launch { store.clear() }
                TWO_HOURS_MS
            }

            val keeper = started()

            assertNull(store.active.value)
            assertFalse(dir.exists())
            assertEquals(RouteRestoreDecision.REPLACED, keeper.decision.value)
        }

    // The keeper hears of the driver's route only once the clear, queued right after it, has run: its write finds the
    // route gone.
    @Test
    fun `a route set and cleared while the kept one waits for a fix ends the wait`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS - ONE_HOUR_MS)
            nowEpochMs = UNSET_CLOCK_MS
            val keeper = started()
            store.start(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS, reference = null)
            backgroundScope.launch { store.clear() }
            runCurrent()
            val afterTheClear = dir.exists() to keeper.decision.value

            keeper.gpsFix(NOW_MS, this)

            assertEquals(false to RouteRestoreDecision.REPLACED, afterTheClear)
            assertNull(store.active.value)
        }

    // The driver sets a route while the first fix's check waits for the setting: the check must end the restored
    // session only, never the route that replaced it.
    @Test
    fun `the first-fix check clears only the restored session, even one replaced while it runs`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS)
            nowEpochMs = NOW_MS + 60_000L
            val keeper = started()
            val setting = CompletableDeferred<Long?>()
            parkedGapMs = { setting.await() }
            keeper.gpsFix(NOW_MS + 3 * ONE_DAY_MS, this)
            val driven = store.start(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS, reference = null)
            runCurrent()

            setting.complete(TWO_HOURS_MS)
            runCurrent()

            assertEquals(driven, store.active.value?.session)
            assertEquals(KeptRoute(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS), files.readOrNull())
        }

    // --- The mirror ---------------------------------------------------------------------------------------------------

    @Test
    fun `a new route writes the snapshot`() =
        runTest {
            started()

            store.start(DRIVER_ROUTE, DRIVER_DESTINATION, AVOID_TOLLS, reference = null)
            runCurrent()

            assertEquals(KeptRoute(DRIVER_ROUTE, DRIVER_DESTINATION, AVOID_TOLLS), files.readOrNull())
        }

    // Not yet driven: the wall clock stands in until the first fix.
    @Test
    fun `a new route is stamped with the wall clock`() =
        runTest {
            started()

            store.start(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS, reference = null)
            runCurrent()

            assertEquals(NOW_MS, files.readStampOrNull())
        }

    @Test
    fun `a reroute and a candidate choice rewrite it`() =
        runTest {
            started()
            val session = store.start(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS, reference = null)
            store.addCandidate(session, RouteCandidate(CHOSEN_ROUTE, reference = null, computingMs = 900L))
            runCurrent()
            store.select(session, 1)
            runCurrent()
            val afterTheChoice = files.readOrNull()?.route

            store.replace(session, REROUTED, reference = null)
            runCurrent()

            assertEquals(CHOSEN_ROUTE to REROUTED, afterTheChoice to files.readOrNull()?.route)
        }

    // The wall clock moves on between the writes; only a new session takes it.
    @Test
    fun `a reroute and a candidate choice keep the stamp`() =
        runTest {
            started()
            val session = store.start(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS, reference = null)
            store.addCandidate(session, RouteCandidate(CHOSEN_ROUTE, reference = null, computingMs = 900L))
            runCurrent()
            nowEpochMs += TEN_MINUTES_MS

            store.select(session, 1)
            runCurrent()
            store.replace(session, REROUTED, reference = null)
            runCurrent()

            assertEquals(NOW_MS, files.readStampOrNull())
        }

    // The stamp is the time the kept route was last driven; a stamp from the wall clock would defeat the next check of
    // the park on a unit whose clock stopped.
    @Test
    fun `a reroute of a restored route keeps the stamp`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS - ONE_HOUR_MS)
            started()
            val restored = assertNotNull(store.active.value).session
            nowEpochMs += TEN_MINUTES_MS

            store.replace(restored, REROUTED, reference = null)
            runCurrent()

            assertEquals(REROUTED to NOW_MS - ONE_HOUR_MS, files.readOrNull()?.route to files.readStampOrNull())
        }

    // A write that failed deleted the files; the next one puts back the time the route was last driven, not the clock.
    @Test
    fun `a failed write keeps the restored route's last-driven time`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS - ONE_HOUR_MS)
            started()
            val restored = assertNotNull(store.active.value).session
            failWrites = true
            store.replace(restored, CHOSEN_ROUTE, reference = null)
            runCurrent()
            failWrites = false
            nowEpochMs += TEN_MINUTES_MS

            store.replace(restored, REROUTED, reference = null)
            runCurrent()

            assertEquals(REROUTED to NOW_MS - ONE_HOUR_MS, files.readOrNull()?.route to files.readStampOrNull())
        }

    @Test
    fun `status and choice flips do not rewrite it`() =
        runTest {
            started()
            val session = store.start(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS, reference = null)
            runCurrent()
            written.clear()

            store.setFinding(session, true)
            runCurrent()
            store.addCandidate(session, RouteCandidate(CHOSEN_ROUTE, reference = null, computingMs = 900L))
            runCurrent()
            store.completeSearch(session)
            runCurrent()
            store.setStatus(session, RerouteStatus.REROUTING)
            runCurrent()
            store.setStatus(session, RerouteStatus.FAILED)
            runCurrent()
            store.closeChoice(session)
            runCurrent()

            assertEquals(emptyList(), written)
        }

    @Test
    fun `clear deletes both files`() =
        runTest {
            started()
            store.start(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS, reference = null)
            runCurrent()
            val before = files.readOrNull() != null && files.readStampOrNull() != null

            store.clear()
            runCurrent()

            assertTrue(before)
            assertFalse(dir.exists())
        }

    // The driver taps × while the new route's snapshot is being written: the delete comes after the write, never
    // before it.
    @Test
    fun `a clear that lands during a write leaves no files`() =
        runTest {
            started()
            beforeWrite = { if (it.name == "route.json") store.clear() }

            store.start(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS, reference = null)
            runCurrent()

            assertNull(store.active.value)
            assertFalse(dir.exists())
        }

    // A snapshot that could not be replaced holds the line before the reroute: better none than an old one.
    @Test
    fun `a failed write leaves no snapshot`() =
        runTest {
            started()
            val session = store.start(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS, reference = null)
            runCurrent()
            failWrites = true

            store.replace(session, REROUTED, reference = null)
            runCurrent()

            assertFalse(dir.exists())
        }

    // The disk was full for a moment (a download running), and the driver stays on the new route for an hour, so the
    // store does not change again: the fixes retry the write, at the stamp's pace, with the session's own time.
    @Test
    fun `a fix after the interval writes the files again after a failed write, with the same last-driven time`() =
        runTest {
            val keeper = started()
            val session = store.start(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS, reference = null)
            runCurrent()
            keeper.gpsFix(NOW_MS + 1_000L, this)
            failWrites = true
            store.replace(session, REROUTED, reference = null)
            runCurrent()
            failWrites = false
            nowEpochMs += TEN_MINUTES_MS
            nowElapsedMs += LAST_DRIVEN_STAMP_INTERVAL_MS - 1
            keeper.gpsFix(NOW_MS + 2_000L, this)
            val withinTheInterval = dir.exists()

            nowElapsedMs += 1
            keeper.gpsFix(NOW_MS + 3_000L, this)

            assertFalse(withinTheInterval)
            assertEquals(REROUTED to NOW_MS + 1_000L, files.readOrNull()?.route to files.readStampOrNull())
        }

    // A fix that finds no route on the map, after the driver cleared the one whose write failed, writes nothing.
    @Test
    fun `no retry without a session`() =
        runTest {
            val keeper = started()
            val session = store.start(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS, reference = null)
            runCurrent()
            failWrites = true
            store.replace(session, REROUTED, reference = null)
            runCurrent()
            failWrites = false
            store.clear()
            runCurrent()
            written.clear()

            nowElapsedMs += LAST_DRIVEN_STAMP_INTERVAL_MS
            keeper.gpsFix(NOW_MS + 1_000L, this)

            assertEquals(emptyList(), written)
            assertFalse(dir.exists())
        }

    // While the kept route waits, a fix only decides: the route it restores is mirrored already, and one it drops
    // leaves nothing to write.
    @Test
    fun `no retry while waiting for a fix`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS - ONE_HOUR_MS)
            nowEpochMs = UNSET_CLOCK_MS
            val keeper = started()
            nowElapsedMs += LAST_DRIVEN_STAMP_INTERVAL_MS

            keeper.gpsFix(NOW_MS + ONE_DAY_MS, this)

            assertEquals(emptyList(), written)
            assertFalse(dir.exists())
        }

    @Test
    fun `the stamp follows the fixes at most once a minute and only while a route is active`() =
        runTest {
            val keeper = started()
            val stamps = mutableListOf<Long?>()
            val stampAfterFix = { timeEpochMs: Long ->
                keeper.gpsFix(timeEpochMs, this)
                stamps += files.readStampOrNull()
            }

            stampAfterFix(NOW_MS + 1_000L)
            store.start(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS, reference = null)
            runCurrent()
            stampAfterFix(NOW_MS + 2_000L)
            nowElapsedMs += LAST_DRIVEN_STAMP_INTERVAL_MS - 1
            stampAfterFix(NOW_MS + 3_000L)
            nowElapsedMs += 1
            stampAfterFix(NOW_MS + 4_000L)
            store.clear()
            runCurrent()
            stampAfterFix(NOW_MS + 5_000L)

            assertEquals(listOf(null, NOW_MS + 2_000L, NOW_MS + 2_000L, NOW_MS + 4_000L, null), stamps)
        }

    @Test
    fun `a stamp update never rewrites the route`() =
        runTest {
            val keeper = started()
            store.start(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS, reference = null)
            runCurrent()
            written.clear()

            keeper.gpsFix(NOW_MS + 1_000L, this)

            assertEquals(listOf("last-driven"), written)
        }

    // --- A build without routing --------------------------------------------------------------------------------------

    @Test
    fun `with routing disabled leftover files are deleted`() =
        runTest {
            keptBefore(lastDrivenMs = NOW_MS - ONE_HOUR_MS)

            keeper().start(enabled = false)
            runCurrent()

            assertNull(store.active.value)
            assertFalse(dir.exists())
        }

    @Test
    fun `with routing disabled a route writes nothing`() =
        runTest {
            val keeper = keeper()
            keeper.start(enabled = false)
            runCurrent()

            store.start(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS, reference = null)
            runCurrent()
            keeper.gpsFix(NOW_MS, this)

            assertFalse(dir.exists())
        }

    @Test
    fun `a second start does nothing`() =
        runTest {
            val keeper = started()
            store.start(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS, reference = null)
            runCurrent()

            keeper.start(enabled = false)
            runCurrent()

            assertEquals(KeptRoute(DRIVER_ROUTE, DRIVER_DESTINATION, NO_CONDITIONS), files.readOrNull())
        }

    private companion object {
        // 2026-11-22T09:00:00Z, and an hour into the boot clock.
        const val NOW_MS = 1_795_338_000_000L
        const val BOOT_MS = 3_600_000L

        // 2020-01-01T00:00:00Z: a unit that booted before its clock was set.
        const val UNSET_CLOCK_MS = 1_577_836_800_000L

        const val ONE_HOUR_MS = 60L * 60 * 1_000
        const val TWO_HOURS_MS = 2 * ONE_HOUR_MS
        const val ONE_DAY_MS = 24 * ONE_HOUR_MS
        const val TEN_MINUTES_MS = 10L * 60 * 1_000

        val NO_CONDITIONS = RouteConditions()
        val AVOID_TOLLS = RouteConditions(avoidTolls = true)

        // The route the last process kept.
        val KEPT = fakeKeptRoute()

        // A route the driver sets in this process, and the lines a choice and a reroute put in its place.
        val DRIVER_ROUTE = fakeEquatorRoute()
        val DRIVER_DESTINATION = RoutePoint(latitude = 0.0, longitude = 0.2)
        val CHOSEN_ROUTE = fakeParallelRoadsRoute()
        val REROUTED = fakeOutAndBackRoute()
    }
}

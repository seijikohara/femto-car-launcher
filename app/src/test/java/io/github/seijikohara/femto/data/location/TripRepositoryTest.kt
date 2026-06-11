package io.github.seijikohara.femto.data.location

import android.location.Location
import android.location.LocationManager
import app.cash.turbine.test
import io.github.seijikohara.femto.testfixtures.fakeLocation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Location is an Android type that Robolectric supplies; distanceTo() uses
// the WGS84 model, so assertions check inequalities / deltas rather than
// hardcoded metre counts.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TripRepositoryTest {
    @Test
    fun `accumulates distance over moving fixes`() =
        runTest {
            val flow =
                flowOf(
                    fakeLocation(latitude = ORIGIN_LAT, speedMps = 11f, elapsedRealtimeNanos = 0L),
                    fakeLocation(latitude = ORIGIN_LAT + STEP, speedMps = 11f, elapsedRealtimeNanos = tenSeconds(1)),
                    fakeLocation(
                        latitude = ORIGIN_LAT + 2 * STEP,
                        speedMps = 11f,
                        elapsedRealtimeNanos = tenSeconds(2),
                    ),
                )

            TripRepository(flow).stateFlow().test {
                skipItems(2) // initial snapshot + first fix (no previous yet)
                val afterTwoSteps = awaitItem()
                assertTrue(afterTwoSteps.distanceMeters > 0.0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `excludes stationary fixes below the moving-speed floor`() =
        runTest {
            // Same position, reported speed below MIN_MOVING_SPEED_MS. Stationary
            // fixes add no DISTANCE (asserted here); their time still counts toward
            // the average — see `average includes stopped time`.
            val flow =
                flowOf(
                    fakeLocation(speedMps = 0.1f, elapsedRealtimeNanos = 0L),
                    fakeLocation(speedMps = 0.1f, elapsedRealtimeNanos = tenSeconds(1)),
                )

            TripRepository(flow).stateFlow().test {
                skipItems(2) // initial snapshot + first fix
                assertEquals(0.0, awaitItem().distanceMeters, 0.0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `average includes stopped time`() =
        runTest {
            // Move for 10 s, then sit stopped for another 10 s at the same spot: the
            // stopped interval adds time but no distance, so the overall average must
            // drop below the moving-only average.
            val flow =
                flowOf(
                    fakeLocation(latitude = ORIGIN_LAT, speedMps = 11f, elapsedRealtimeNanos = 0L),
                    fakeLocation(latitude = ORIGIN_LAT + STEP, speedMps = 11f, elapsedRealtimeNanos = tenSeconds(1)),
                    fakeLocation(latitude = ORIGIN_LAT + STEP, speedMps = 0.1f, elapsedRealtimeNanos = tenSeconds(2)),
                )

            TripRepository(flow).stateFlow().test {
                skipItems(2) // initial snapshot + first fix (no previous yet)
                val moving = awaitItem() // after the second (moving) fix
                val afterStop = awaitItem() // after the stopped fix
                assertTrue(afterStop.avgSpeedMs > 0.0)
                assertTrue(afterStop.avgSpeedMs < moving.avgSpeedMs)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `drops pairs separated by a gap larger than the max window`() =
        runTest {
            // 61 s apart on the boot clock exceeds MAX_GAP_SECONDS (60 s).
            val flow =
                flowOf(
                    fakeLocation(latitude = ORIGIN_LAT, speedMps = 11f, elapsedRealtimeNanos = 0L),
                    fakeLocation(latitude = ORIGIN_LAT + STEP, speedMps = 11f, elapsedRealtimeNanos = 61_000_000_000L),
                )

            TripRepository(flow).stateFlow().test {
                skipItems(2) // initial snapshot + first fix
                assertEquals(0.0, awaitItem().distanceMeters, 0.0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `drops non-positive elapsed-time deltas`() =
        runTest {
            // Identical elapsedRealtimeNanos: delta is zero, below MIN_DELTA_SECONDS.
            val flow =
                flowOf(
                    fakeLocation(latitude = ORIGIN_LAT, speedMps = 11f, elapsedRealtimeNanos = 0L),
                    fakeLocation(latitude = ORIGIN_LAT + STEP, speedMps = 11f, elapsedRealtimeNanos = 0L),
                )

            TripRepository(flow).stateFlow().test {
                skipItems(2) // initial snapshot + first fix
                assertEquals(0.0, awaitItem().distanceMeters, 0.0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `accrues on speed-less fixes that are actually moving`() =
        runTest {
            // hasSpeed = false (Location.hasSpeed() == false) but the positions
            // differ over elapsed time, so the position-derived speed clears the
            // floor and distance accrues.
            val flow =
                flowOf(
                    fakeLocation(latitude = ORIGIN_LAT, hasSpeed = false, elapsedRealtimeNanos = 0L),
                    fakeLocation(latitude = ORIGIN_LAT + STEP, hasSpeed = false, elapsedRealtimeNanos = tenSeconds(1)),
                )

            TripRepository(flow).stateFlow().test {
                skipItems(2) // initial snapshot + first fix
                assertTrue(awaitItem().distanceMeters > 0.0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uses elapsed-realtime not wall clock for the delta`() =
        runTest {
            // timeMs jumps backward (a mid-trip NTP correction) while the boot
            // clock advances normally. If the delta used Location.time it would
            // be negative and accrual would be skipped; using elapsedRealtimeNanos
            // it stays positive and distance accrues.
            val flow =
                flowOf(
                    fakeLocation(
                        latitude = ORIGIN_LAT,
                        speedMps = 11f,
                        timeMs = 10_000_000L,
                        elapsedRealtimeNanos = 0L,
                    ),
                    fakeLocation(
                        latitude = ORIGIN_LAT + STEP,
                        speedMps = 11f,
                        timeMs = 5_000_000L,
                        elapsedRealtimeNanos = tenSeconds(1),
                    ),
                )

            TripRepository(flow).stateFlow().test {
                skipItems(2) // initial snapshot + first fix
                assertTrue(awaitItem().distanceMeters > 0.0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `persists distance across re-subscription`() =
        runTest {
            val source = MutableSharedFlow<Location?>(replay = 0)
            // Test dispatcher so the MutableSharedFlow emit/awaitItem handshake runs
            // on the virtual clock instead of racing Dispatchers.Default under load
            // (the source of an intermittent Turbine timeout).
            val repository = TripRepository(source, UnconfinedTestDispatcher(testScheduler))

            // First subscription: feed two moving fixes, capture the running total,
            // then cancel the collection (simulating a WhileSubscribed stop).
            var accumulated = 0.0
            repository.stateFlow().test {
                assertEquals(0.0, awaitItem().distanceMeters, 0.0) // initial snapshot
                source.emit(fakeLocation(latitude = ORIGIN_LAT, speedMps = 11f, elapsedRealtimeNanos = 0L))
                awaitItem()
                source.emit(
                    fakeLocation(latitude = ORIGIN_LAT + STEP, speedMps = 11f, elapsedRealtimeNanos = tenSeconds(1)),
                )
                accumulated = awaitItem().distanceMeters
                assertTrue(accumulated > 0.0)
                cancelAndIgnoreRemainingEvents()
            }

            // Second subscription: the first re-emission must carry the previously
            // accumulated total, not 0, and continue accruing from there.
            repository.stateFlow().test {
                val replayed = awaitItem()
                assertEquals(accumulated, replayed.distanceMeters, 0.0)
                source.emit(
                    fakeLocation(
                        latitude = ORIGIN_LAT + 2 * STEP,
                        speedMps = 11f,
                        elapsedRealtimeNanos = tenSeconds(2),
                    ),
                )
                assertTrue(awaitItem().distanceMeters > accumulated)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `publishes the effective speed as currentSpeedMs`() =
        runTest {
            val flow =
                flowOf(
                    fakeLocation(latitude = ORIGIN_LAT, speedMps = 9.5f, elapsedRealtimeNanos = 0L),
                    fakeLocation(latitude = ORIGIN_LAT + STEP, speedMps = 9.5f, elapsedRealtimeNanos = tenSeconds(1)),
                )

            TripRepository(flow).stateFlow().test {
                skipItems(2) // initial snapshot + first fix
                // Reported speed wins when the fix has one (hasSpeed default true).
                assertEquals(9.5, awaitItem().currentSpeedMs, 0.0001)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `rejects an impossible speed above the plausible ceiling`() =
        runTest {
            // A reported speed far beyond any vehicle (a bad chip report) must be
            // dropped, not published or accrued, so it cannot poison the totals.
            val flow =
                flowOf(
                    fakeLocation(latitude = ORIGIN_LAT, speedMps = 11f, elapsedRealtimeNanos = 0L),
                    fakeLocation(latitude = ORIGIN_LAT + STEP, speedMps = 5_000f, elapsedRealtimeNanos = tenSeconds(1)),
                )

            TripRepository(flow).stateFlow().test {
                skipItems(2) // initial snapshot + first fix
                // The first fix has no previous, so currentSpeedMs is never set
                // (stays 0.0); the second fix is over the ceiling and is dropped,
                // so both metrics remain zero.
                val afterSpike = awaitItem()
                assertEquals(0.0, afterSpike.distanceMeters, 0.0)
                assertEquals(0.0, afterSpike.currentSpeedMs, 0.0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `rejects an impossible speed from a sub-second gap between position fixes`() =
        runTest {
            // Two speed-less fixes far apart in position but ~20 ms apart on the
            // boot clock (the dual-provider interleave that produced the reported
            // tens-of-thousands km/h). The position-derived speed must be distrusted.
            val flow =
                flowOf(
                    fakeLocation(latitude = ORIGIN_LAT, hasSpeed = false, elapsedRealtimeNanos = 0L),
                    fakeLocation(latitude = ORIGIN_LAT + STEP, hasSpeed = false, elapsedRealtimeNanos = 20_000_000L),
                )

            TripRepository(flow).stateFlow().test {
                skipItems(2) // initial snapshot + first fix
                val afterGlitch = awaitItem()
                assertEquals(0.0, afterGlitch.distanceMeters, 0.0)
                assertEquals(0.0, afterGlitch.currentSpeedMs, 0.0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ignores network-provider fixes for trip distance`() =
        runTest {
            // A GPS pair accrues; a following NETWORK fix that jumps position must
            // be ignored — it must emit nothing and must not advance the GPS
            // anchor (network fixes are tower / Wi-Fi, not for trip math).
            val source = MutableSharedFlow<Location?>(replay = 0)
            val repository = TripRepository(source, UnconfinedTestDispatcher(testScheduler))

            repository.stateFlow().test {
                awaitItem() // initial snapshot
                source.emit(fakeLocation(latitude = ORIGIN_LAT, speedMps = 11f, elapsedRealtimeNanos = 0L))
                awaitItem() // first GPS fix, no previous
                source.emit(
                    fakeLocation(latitude = ORIGIN_LAT + STEP, speedMps = 11f, elapsedRealtimeNanos = tenSeconds(1)),
                )
                val afterGps = awaitItem()
                assertTrue(afterGps.distanceMeters > 0.0)

                // The far NETWORK jump is filtered out: no emission at all.
                source.emit(
                    fakeLocation(
                        latitude = ORIGIN_LAT + 10 * STEP,
                        speedMps = 11f,
                        elapsedRealtimeNanos = tenSeconds(2),
                        provider = LocationManager.NETWORK_PROVIDER,
                    ),
                )
                expectNoEvents()

                // The next GPS fix accrues a normal step from the last GPS anchor
                // (ORIGIN + STEP), not from the discarded network jump.
                source.emit(
                    fakeLocation(
                        latitude = ORIGIN_LAT + 2 * STEP,
                        speedMps = 11f,
                        elapsedRealtimeNanos = tenSeconds(3),
                    ),
                )
                assertTrue(awaitItem().distanceMeters > afterGps.distanceMeters)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `reset clears distance avg and current speed`() =
        runTest {
            val source = MutableSharedFlow<Location?>(replay = 0)
            // Test dispatcher so the non-suspending reset() and its emission are
            // observed deterministically under the virtual clock.
            val repository = TripRepository(source, UnconfinedTestDispatcher(testScheduler))

            repository.stateFlow().test {
                assertEquals(0.0, awaitItem().distanceMeters, 0.0) // initial snapshot
                source.emit(fakeLocation(latitude = ORIGIN_LAT, speedMps = 11f, elapsedRealtimeNanos = 0L))
                awaitItem() // first fix, no previous
                source.emit(
                    fakeLocation(latitude = ORIGIN_LAT + STEP, speedMps = 11f, elapsedRealtimeNanos = tenSeconds(1)),
                )
                assertTrue(awaitItem().distanceMeters > 0.0)

                repository.reset()

                val cleared = awaitItem()
                assertEquals(0.0, cleared.distanceMeters, 0.0)
                assertEquals(0.0, cleared.avgSpeedMs, 0.0)
                assertEquals(0.0, cleared.currentSpeedMs, 0.0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `accrual resumes after a reset`() =
        runTest {
            val source = MutableSharedFlow<Location?>(replay = 0)
            val repository = TripRepository(source, UnconfinedTestDispatcher(testScheduler))

            repository.stateFlow().test {
                awaitItem() // initial snapshot
                source.emit(fakeLocation(latitude = ORIGIN_LAT, speedMps = 11f, elapsedRealtimeNanos = 0L))
                awaitItem()
                source.emit(
                    fakeLocation(latitude = ORIGIN_LAT + STEP, speedMps = 11f, elapsedRealtimeNanos = tenSeconds(1)),
                )
                awaitItem()

                repository.reset()
                assertEquals(0.0, awaitItem().distanceMeters, 0.0)

                // The reset drops the anchor: the first post-reset fix re-anchors
                // (no accrual), the second accrues from there.
                source.emit(
                    fakeLocation(
                        latitude = ORIGIN_LAT + 2 * STEP,
                        speedMps = 11f,
                        elapsedRealtimeNanos = tenSeconds(3),
                    ),
                )
                assertEquals(0.0, awaitItem().distanceMeters, 0.0)
                source.emit(
                    fakeLocation(
                        latitude = ORIGIN_LAT + 3 * STEP,
                        speedMps = 11f,
                        elapsedRealtimeNanos = tenSeconds(4),
                    ),
                )
                assertTrue(awaitItem().distanceMeters > 0.0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `defers speed-less sub-floor fixes until the accumulated delta clears the floor`() =
        runTest {
            // Speed-less fixes 250 ms apart — a sub-second request interval on a
            // chip that never reports speed. Each individual delta is below the
            // trust floor, so the anchor must hold (distance stays 0 after the
            // second fix) until the accumulated delta reaches 0.5 s, then accrue
            // in one step. Advancing the anchor per fix would freeze distance
            // forever at this cadence.
            val flow =
                flowOf(
                    fakeLocation(latitude = ORIGIN_LAT, hasSpeed = false, elapsedRealtimeNanos = 0L),
                    fakeLocation(
                        latitude = ORIGIN_LAT + SMALL_STEP,
                        hasSpeed = false,
                        elapsedRealtimeNanos = quarterSecond(1),
                    ),
                    fakeLocation(
                        latitude = ORIGIN_LAT + 2 * SMALL_STEP,
                        hasSpeed = false,
                        elapsedRealtimeNanos = quarterSecond(2),
                    ),
                )

            TripRepository(flow).stateFlow().test {
                skipItems(2) // initial snapshot + first fix (anchor only)
                assertEquals(0.0, awaitItem().distanceMeters, 0.0) // deferred sub-floor fix
                assertTrue(awaitItem().distanceMeters > 0.0) // accumulated delta clears the floor
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `re-anchors on an implausible speed without accruing`() =
        runTest {
            // The implausible middle fix must not accrue, but it must advance the
            // anchor: the next normal fix then accrues one step from the glitch
            // position, not the ten-step span back to the origin.
            val flow =
                flowOf(
                    fakeLocation(latitude = ORIGIN_LAT, speedMps = 11f, elapsedRealtimeNanos = 0L),
                    fakeLocation(
                        latitude = ORIGIN_LAT + 10 * STEP,
                        speedMps = 5_000f,
                        elapsedRealtimeNanos = tenSeconds(1),
                    ),
                    fakeLocation(
                        latitude = ORIGIN_LAT + 11 * STEP,
                        speedMps = 11f,
                        elapsedRealtimeNanos = tenSeconds(2),
                    ),
                )

            TripRepository(flow).stateFlow().test {
                skipItems(2) // initial snapshot + first fix
                assertEquals(0.0, awaitItem().distanceMeters, 0.0) // implausible fix dropped
                val final = awaitItem()
                // One ~111 m step from the re-anchored position; the ~1.2 km span
                // from the origin would mean the glitch fix failed to re-anchor.
                assertTrue(final.distanceMeters > 0.0)
                assertTrue(final.distanceMeters < 300.0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private companion object {
        const val ORIGIN_LAT = 35.6580

        // ~0.001 deg latitude ≈ 111 m; over 10 s that is ~11 m/s, well above
        // the moving-speed floor.
        const val STEP = 0.001

        // ~5.5 m; over 250 ms a plausible vehicle step (~22 m/s once the
        // accumulated delta clears the trust floor).
        const val SMALL_STEP = STEP / 20

        // n * 10 s expressed in boot-clock nanoseconds.
        fun tenSeconds(n: Long): Long = n * 10_000_000_000L

        // n * 250 ms expressed in boot-clock nanoseconds.
        fun quarterSecond(n: Long): Long = n * 250_000_000L
    }
}

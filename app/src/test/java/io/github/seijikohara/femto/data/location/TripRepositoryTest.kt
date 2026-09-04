package io.github.seijikohara.femto.data.location

import android.location.Location
import android.location.LocationManager
import app.cash.turbine.test
import io.github.seijikohara.femto.testfixtures.FakeTripStateStore
import io.github.seijikohara.femto.testfixtures.fakeLocation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

            TripRepository(flow, FakeTripStateStore()).stateFlow().test {
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

            TripRepository(flow, FakeTripStateStore()).stateFlow().test {
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

            TripRepository(flow, FakeTripStateStore()).stateFlow().test {
                skipItems(2) // initial snapshot + first fix (no previous yet)
                val moving = awaitItem() // after the second (moving) fix
                val afterStop = awaitItem() // after the stopped fix
                assertTrue(afterStop.avgSpeedMs > 0.0)
                assertTrue(afterStop.avgSpeedMs < moving.avgSpeedMs)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `counts a parked gap toward the average time base`() =
        runTest {
            // Move for 10 s, stop, then a 120 s fix drought (distance-filtered
            // updates stop while parked). The gap began at a standstill, so it
            // must land in the average's time base: AVG drops sharply.
            val flow =
                flowOf(
                    fakeLocation(latitude = ORIGIN_LAT, speedMps = 11f, elapsedRealtimeNanos = 0L),
                    fakeLocation(latitude = ORIGIN_LAT + STEP, speedMps = 11f, elapsedRealtimeNanos = tenSeconds(1)),
                    fakeLocation(latitude = ORIGIN_LAT + STEP, speedMps = 0.1f, elapsedRealtimeNanos = tenSeconds(2)),
                    fakeLocation(
                        latitude = ORIGIN_LAT + STEP,
                        speedMps = 0.1f,
                        elapsedRealtimeNanos = tenSeconds(2) + 120_000_000_000L,
                    ),
                )

            TripRepository(flow, FakeTripStateStore()).stateFlow().test {
                skipItems(3) // initial snapshot + first fix + second (moving) fix
                val afterStop = awaitItem() // stopped fix: 20 s time base
                val afterGap = awaitItem() // parked drought: 140 s time base
                assertTrue(afterGap.avgSpeedMs > 0.0)
                // 20 s -> 140 s of elapsed time over the same distance.
                assertTrue(afterGap.avgSpeedMs < afterStop.avgSpeedMs / 5)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `skips a moving-onset gap from the average time base`() =
        runTest {
            // The anchor fix was moving (11 m/s) when the 61 s drought began —
            // a tunnel / backgrounded-mid-drive case. Neither time nor distance
            // may accrue across it, so the average is unchanged.
            val flow =
                flowOf(
                    fakeLocation(latitude = ORIGIN_LAT, speedMps = 11f, elapsedRealtimeNanos = 0L),
                    fakeLocation(latitude = ORIGIN_LAT + STEP, speedMps = 11f, elapsedRealtimeNanos = tenSeconds(1)),
                    fakeLocation(
                        latitude = ORIGIN_LAT + 2 * STEP,
                        speedMps = 11f,
                        elapsedRealtimeNanos = tenSeconds(1) + 61_000_000_000L,
                    ),
                )

            TripRepository(flow, FakeTripStateStore()).stateFlow().test {
                skipItems(2) // initial snapshot + first fix
                val beforeGap = awaitItem()
                val afterGap = awaitItem()
                assertEquals(beforeGap.avgSpeedMs, afterGap.avgSpeedMs, 1e-9)
                assertEquals(beforeGap.distanceMeters, afterGap.distanceMeters, 1e-9)
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

            TripRepository(flow, FakeTripStateStore()).stateFlow().test {
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

            TripRepository(flow, FakeTripStateStore()).stateFlow().test {
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

            TripRepository(flow, FakeTripStateStore()).stateFlow().test {
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

            TripRepository(flow, FakeTripStateStore()).stateFlow().test {
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
            val repository =
                TripRepository(source, FakeTripStateStore(), dispatcher = UnconfinedTestDispatcher(testScheduler))

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

            TripRepository(flow, FakeTripStateStore()).stateFlow().test {
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

            TripRepository(flow, FakeTripStateStore()).stateFlow().test {
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
    fun `rejects a negative reported speed`() =
        runTest {
            // The plausibility gate used to guard the high side only, so a
            // negative chip report was published verbatim as currentSpeedMs —
            // which also flips TripState.stationary and with it the
            // parked-only music marquee (issue #351).
            val flow =
                flowOf(
                    fakeLocation(latitude = ORIGIN_LAT, speedMps = 11f, elapsedRealtimeNanos = 0L),
                    fakeLocation(latitude = ORIGIN_LAT + STEP, speedMps = -12f, elapsedRealtimeNanos = tenSeconds(1)),
                )

            TripRepository(flow, FakeTripStateStore()).stateFlow().test {
                skipItems(2) // initial snapshot + first fix
                // currentSpeedMs is the falsifiable assertion: distanceMeters stays
                // zero either way, because -12.0 never clears MIN_MOVING_SPEED_MS.
                assertEquals(0.0, awaitItem().currentSpeedMs, 0.0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `rejects a NaN reported speed`() =
        runTest {
            // `speed > MAX_PLAUSIBLE_SPEED_MS` is false for NaN, so the old
            // one-sided gate let it through; a NaN current speed reaches
            // Float.roundToInt(), which throws rather than saturating.
            val flow =
                flowOf(
                    fakeLocation(latitude = ORIGIN_LAT, speedMps = 11f, elapsedRealtimeNanos = 0L),
                    fakeLocation(
                        latitude = ORIGIN_LAT + STEP,
                        speedMps = Float.NaN,
                        elapsedRealtimeNanos = tenSeconds(1),
                    ),
                )

            TripRepository(flow, FakeTripStateStore()).stateFlow().test {
                skipItems(2) // initial snapshot + first fix
                val afterNaN = awaitItem()
                assertEquals(0.0, afterNaN.currentSpeedMs, 0.0)
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

            TripRepository(flow, FakeTripStateStore()).stateFlow().test {
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
            val repository =
                TripRepository(source, FakeTripStateStore(), dispatcher = UnconfinedTestDispatcher(testScheduler))

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
            val repository =
                TripRepository(source, FakeTripStateStore(), dispatcher = UnconfinedTestDispatcher(testScheduler))

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
            val repository =
                TripRepository(source, FakeTripStateStore(), dispatcher = UnconfinedTestDispatcher(testScheduler))

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

            TripRepository(flow, FakeTripStateStore()).stateFlow().test {
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

            TripRepository(flow, FakeTripStateStore()).stateFlow().test {
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

    @Test
    fun `restores persisted totals and trip start before the first emission`() =
        runTest {
            val store =
                FakeTripStateStore(
                    PersistedTrip.Initial.copy(
                        totalMeters = 1_000.0,
                        totalSeconds = 100.0,
                        startedAtEpochMs = 42L,
                        tripId = 3L,
                    ),
                )

            TripRepository(flowOf(), store).stateFlow().test {
                val restored = awaitItem()
                assertEquals(1_000.0, restored.distanceMeters, 0.0)
                assertEquals(10.0, restored.avgSpeedMs, 1e-9)
                assertEquals(42L, restored.startedAtEpochMs)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `applies the persisted restore only once across re-subscription`() =
        runTest {
            val store =
                FakeTripStateStore(
                    PersistedTrip.Initial.copy(totalMeters = 500.0, totalSeconds = 50.0, startedAtEpochMs = 42L),
                )
            val repository = TripRepository(flowOf(), store)

            repository.stateFlow().test {
                assertEquals(500.0, awaitItem().distanceMeters, 0.0)
                cancelAndIgnoreRemainingEvents()
            }
            // A WhileSubscribed restart replays the running total; re-applying the
            // restore would double it.
            repository.stateFlow().test {
                assertEquals(500.0, awaitItem().distanceMeters, 0.0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `sets the trip start at the first accepted fix`() =
        runTest {
            val flow = flowOf(fakeLocation(timeMs = 123_456_789L, speedMps = 11f, elapsedRealtimeNanos = 0L))

            TripRepository(flow, FakeTripStateStore()).stateFlow().test {
                assertNull(awaitItem().startedAtEpochMs) // initial snapshot, nothing accepted yet
                assertEquals(123_456_789L, awaitItem().startedAtEpochMs)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `drops fixes that are already stale on arrival`() =
        runTest {
            // The boot clock sits at 200 s: a cached getLastKnownLocation seed
            // stamped at 0 s (hours old in production) must never enter the
            // chain, so the trip start comes from the first FRESH fix.
            val flow =
                flowOf(
                    fakeLocation(latitude = ORIGIN_LAT, timeMs = 1_000L, speedMps = 11f, elapsedRealtimeNanos = 0L),
                    fakeLocation(
                        latitude = ORIGIN_LAT + STEP,
                        timeMs = 2_000L,
                        speedMps = 11f,
                        elapsedRealtimeNanos = 199_500_000_000L,
                    ),
                )

            TripRepository(
                flow,
                FakeTripStateStore(),
                nowElapsedRealtimeNanos = { 200_000_000_000L },
            ).stateFlow().test {
                awaitItem() // initial snapshot
                assertEquals(2_000L, awaitItem().startedAtEpochMs)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `reset immediately persists zeroed totals and the next trip id`() =
        runTest {
            val source = MutableSharedFlow<Location?>(replay = 0)
            val store = FakeTripStateStore()
            val repository =
                TripRepository(
                    source,
                    store,
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            repository.stateFlow().test {
                awaitItem() // initial snapshot
                source.emit(fakeLocation(latitude = ORIGIN_LAT, speedMps = 11f, elapsedRealtimeNanos = 0L))
                awaitItem()
                source.emit(
                    fakeLocation(latitude = ORIGIN_LAT + STEP, speedMps = 11f, elapsedRealtimeNanos = tenSeconds(1)),
                )
                awaitItem()

                repository.reset()
                awaitItem()

                // The reset writes through synchronously on the accrual sequence.
                assertEquals(PersistedTrip.Initial.copy(tripId = 1L), store.stored)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `flushes the running totals when the subscription window closes`() =
        runTest {
            val source = MutableSharedFlow<Location?>(replay = 0)
            val store = FakeTripStateStore()
            val repository =
                TripRepository(
                    source,
                    store,
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            // Fixes 1 s apart stay inside the persist throttle, so only the
            // unsubscribe flush can write the accrued distance.
            var accumulated = 0.0
            repository.stateFlow().test {
                awaitItem()
                source.emit(fakeLocation(latitude = ORIGIN_LAT, speedMps = 11f, elapsedRealtimeNanos = 0L))
                awaitItem()
                source.emit(
                    fakeLocation(latitude = ORIGIN_LAT + STEP, speedMps = 11f, elapsedRealtimeNanos = 1_000_000_000L),
                )
                accumulated = awaitItem().distanceMeters
                cancelAndIgnoreRemainingEvents()
            }
            // Let the NonCancellable finally flush complete on the test scheduler.
            advanceUntilIdle()

            assertTrue(accumulated > 0.0)
            assertEquals(accumulated, store.stored.totalMeters, 0.0)
        }

    @Test
    fun `throttles periodic persistence to the minimum interval`() =
        runTest {
            val source = MutableSharedFlow<Location?>(replay = 0)
            val store = FakeTripStateStore()
            val repository =
                TripRepository(
                    source,
                    store,
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            repository.stateFlow().test {
                awaitItem() // initial snapshot
                source.emit(fakeLocation(latitude = ORIGIN_LAT, speedMps = 11f, elapsedRealtimeNanos = 0L))
                awaitItem()
                source.emit(
                    fakeLocation(latitude = ORIGIN_LAT + STEP, speedMps = 11f, elapsedRealtimeNanos = 1_000_000_000L),
                )
                awaitItem()
                source.emit(
                    fakeLocation(
                        latitude = ORIGIN_LAT + 2 * STEP,
                        speedMps = 11f,
                        elapsedRealtimeNanos = 2_000_000_000L,
                    ),
                )
                awaitItem()
                // Only the first fix writes; the 1 s / 2 s follow-ups are throttled.
                assertEquals(1, store.writes.size)

                source.emit(
                    fakeLocation(
                        latitude = ORIGIN_LAT + 3 * STEP,
                        speedMps = 11f,
                        elapsedRealtimeNanos = 7_000_000_000L,
                    ),
                )
                awaitItem()
                assertEquals(2, store.writes.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `tags tapped fixes with the reset-to-reset trip id`() =
        runTest {
            val source = MutableSharedFlow<Location?>(replay = 0)
            val tapped = mutableListOf<Long>()
            val repository =
                TripRepository(
                    source,
                    FakeTripStateStore(),
                    trackTap = TripFixTap { _, tripId -> tapped += tripId },
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            repository.stateFlow().test {
                awaitItem() // initial snapshot
                source.emit(fakeLocation(latitude = ORIGIN_LAT, speedMps = 11f, elapsedRealtimeNanos = 0L))
                awaitItem()
                repository.reset()
                awaitItem()
                source.emit(
                    fakeLocation(latitude = ORIGIN_LAT + STEP, speedMps = 11f, elapsedRealtimeNanos = tenSeconds(1)),
                )
                awaitItem()
                assertEquals(listOf(0L, 1L), tapped)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `does not tap fixes the trip math rejects as implausible teleports`() =
        runTest {
            val source = MutableSharedFlow<Location?>(replay = 0)
            val tappedPositions = mutableListOf<Double>()
            val repository =
                TripRepository(
                    source,
                    FakeTripStateStore(),
                    trackTap = TripFixTap { location, _ -> tappedPositions += location.latitude },
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            repository.stateFlow().test {
                awaitItem() // initial snapshot
                source.emit(fakeLocation(latitude = ORIGIN_LAT, speedMps = 11f, elapsedRealtimeNanos = 0L))
                awaitItem()
                // A ~5 km jump over 10 s ≈ 500 m/s — above MAX_PLAUSIBLE_SPEED_MS.
                // Trip math re-anchors without accruing; the recorder must skip it too.
                source.emit(
                    fakeLocation(
                        latitude = ORIGIN_LAT + 45 * STEP,
                        speedMps = 5_000f,
                        elapsedRealtimeNanos = tenSeconds(1),
                    ),
                )
                awaitItem()
                // The origin was tapped; the teleport was not.
                assertEquals(listOf(ORIGIN_LAT), tappedPositions)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `starts a new trip when a fix arrives after the parked gap`() =
        runTest {
            val source = MutableSharedFlow<Location?>(replay = 0)
            val repository =
                TripRepository(
                    source,
                    FakeTripStateStore(),
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                    autoReset = flowOf(TripAutoResetSetting.HOURS_2),
                )

            repository.stateFlow().test {
                awaitItem() // initial snapshot
                source.emit(
                    fakeLocation(latitude = ORIGIN_LAT, timeMs = NOON_MS, speedMps = 11f, elapsedRealtimeNanos = 0L),
                )
                awaitItem()
                source.emit(
                    fakeLocation(
                        latitude = ORIGIN_LAT + STEP,
                        timeMs = NOON_MS + 10_000L,
                        speedMps = 11f,
                        elapsedRealtimeNanos = tenSeconds(1),
                    ),
                )
                assertTrue(awaitItem().distanceMeters > 0.0)

                // Parked for the whole gap: the next fix opens a new trip (zero
                // totals, its own start time) instead of extending this one.
                val nextDrive = NOON_MS + 10_000L + parkedGapMs
                source.emit(
                    fakeLocation(
                        latitude = ORIGIN_LAT + 2 * STEP,
                        timeMs = nextDrive,
                        speedMps = 11f,
                        elapsedRealtimeNanos = tenSeconds(1) + millisToNanos(parkedGapMs),
                    ),
                )
                val fresh = awaitItem()
                assertEquals(0.0, fresh.distanceMeters, 0.0)
                assertEquals(nextDrive, fresh.startedAtEpochMs)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `persists the new trip id before tapping the first fix of an auto-started trip`() =
        runTest {
            val source = MutableSharedFlow<Location?>(replay = 0)
            val store = FakeTripStateStore()
            // Pair each tapped id with the id durable in the store at that moment.
            val tapped = mutableListOf<Pair<Long, Long>>()
            val repository =
                TripRepository(
                    source,
                    store,
                    trackTap = TripFixTap { _, tripId -> tapped += tripId to store.stored.tripId },
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                    autoReset = flowOf(TripAutoResetSetting.HOURS_2),
                )

            repository.stateFlow().test {
                awaitItem() // initial snapshot
                source.emit(
                    fakeLocation(latitude = ORIGIN_LAT, timeMs = NOON_MS, speedMps = 11f, elapsedRealtimeNanos = 0L),
                )
                awaitItem()
                source.emit(
                    fakeLocation(
                        latitude = ORIGIN_LAT + STEP,
                        timeMs = NOON_MS + parkedGapMs,
                        speedMps = 11f,
                        elapsedRealtimeNanos = millisToNanos(parkedGapMs),
                    ),
                )
                awaitItem()
                // The auto-started trip's id is written through before its first
                // point reaches the track log, exactly as a manual reset's is.
                assertEquals(listOf(0L to 0L, 1L to 1L), tapped)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `keeps the trip when the fix gap is shorter than the parked gap`() =
        runTest {
            val source = MutableSharedFlow<Location?>(replay = 0)
            val repository =
                TripRepository(
                    source,
                    FakeTripStateStore(),
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                    autoReset = flowOf(TripAutoResetSetting.HOURS_2),
                )

            repository.stateFlow().test {
                awaitItem() // initial snapshot
                source.emit(
                    fakeLocation(latitude = ORIGIN_LAT, timeMs = NOON_MS, speedMps = 11f, elapsedRealtimeNanos = 0L),
                )
                awaitItem()
                source.emit(
                    fakeLocation(
                        latitude = ORIGIN_LAT + STEP,
                        timeMs = NOON_MS + 10_000L,
                        speedMps = 11f,
                        elapsedRealtimeNanos = tenSeconds(1),
                    ),
                )
                val beforeStop = awaitItem()

                // A stop one second short of the gap (fuel, shopping) belongs to
                // the same trip: the start stands and nothing is re-zeroed.
                val shortStop = parkedGapMs - 1_000L
                source.emit(
                    fakeLocation(
                        latitude = ORIGIN_LAT + STEP,
                        timeMs = NOON_MS + 10_000L + shortStop,
                        speedMps = 11f,
                        elapsedRealtimeNanos = tenSeconds(1) + millisToNanos(shortStop),
                    ),
                )
                val afterStop = awaitItem()
                assertEquals(beforeStop.startedAtEpochMs, afterStop.startedAtEpochMs)
                assertEquals(beforeStop.distanceMeters, afterStop.distanceMeters, 0.0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `never starts a new trip on its own while auto-reset is off`() =
        runTest {
            val source = MutableSharedFlow<Location?>(replay = 0)
            val repository =
                TripRepository(
                    source,
                    FakeTripStateStore(),
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                    autoReset = flowOf(TripAutoResetSetting.OFF),
                )

            repository.stateFlow().test {
                awaitItem() // initial snapshot
                source.emit(
                    fakeLocation(latitude = ORIGIN_LAT, timeMs = NOON_MS, speedMps = 11f, elapsedRealtimeNanos = 0L),
                )
                val first = awaitItem()
                // Three days parked: the original reset-to-reset contract holds.
                val threeDays = 3 * 24 * 60 * 60_000L
                source.emit(
                    fakeLocation(
                        latitude = ORIGIN_LAT + STEP,
                        timeMs = NOON_MS + threeDays,
                        speedMps = 11f,
                        elapsedRealtimeNanos = millisToNanos(threeDays),
                    ),
                )
                assertEquals(first.startedAtEpochMs, awaitItem().startedAtEpochMs)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `starts a new trip on restore when the persisted trip is older than the parked gap`() =
        runTest {
            val store =
                FakeTripStateStore(
                    PersistedTrip.Initial.copy(
                        totalMeters = 1_000.0,
                        totalSeconds = 100.0,
                        startedAtEpochMs = NOON_MS - 600_000L,
                        lastFixEpochMs = NOON_MS,
                        tripId = 3L,
                    ),
                )

            TripRepository(
                flowOf(),
                store,
                autoReset = flowOf(TripAutoResetSetting.HOURS_2),
                nowEpochMs = { NOON_MS + parkedGapMs },
            ).stateFlow().test {
                // Zeroed before the first emission, so the dashboard never shows
                // the previous drive's totals while waiting for a fix, and the
                // bumped id is durable at once.
                val restored = awaitItem()
                assertEquals(0.0, restored.distanceMeters, 0.0)
                assertNull(restored.startedAtEpochMs)
                assertEquals(4L, store.stored.tripId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `keeps the persisted trip on restore when the clock reads before its last fix`() =
        runTest {
            // A dead RTC boots into the past: the span is negative, not a parked
            // gap, so the totals stand and the first live fix decides.
            val store =
                FakeTripStateStore(
                    PersistedTrip.Initial.copy(
                        totalMeters = 1_000.0,
                        totalSeconds = 100.0,
                        startedAtEpochMs = 42L,
                        lastFixEpochMs = NOON_MS,
                        tripId = 3L,
                    ),
                )

            TripRepository(
                flowOf(),
                store,
                autoReset = flowOf(TripAutoResetSetting.HOURS_2),
                nowEpochMs = { NOON_MS - 24 * 60 * 60_000L },
            ).stateFlow().test {
                val restored = awaitItem()
                assertEquals(1_000.0, restored.distanceMeters, 0.0)
                assertEquals(42L, restored.startedAtEpochMs)
                assertEquals(3L, store.stored.tripId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `applies an auto-reset setting change to the next fix`() =
        runTest {
            val source = MutableSharedFlow<Location?>(replay = 0)
            val setting = MutableStateFlow(TripAutoResetSetting.OFF)
            val repository =
                TripRepository(
                    source,
                    FakeTripStateStore(),
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                    autoReset = setting,
                )

            repository.stateFlow().test {
                awaitItem() // initial snapshot
                source.emit(
                    fakeLocation(latitude = ORIGIN_LAT, timeMs = NOON_MS, speedMps = 11f, elapsedRealtimeNanos = 0L),
                )
                val first = awaitItem()
                // Off: a fix a whole gap later still extends the trip.
                source.emit(
                    fakeLocation(
                        latitude = ORIGIN_LAT + STEP,
                        timeMs = NOON_MS + parkedGapMs,
                        speedMps = 11f,
                        elapsedRealtimeNanos = millisToNanos(parkedGapMs),
                    ),
                )
                assertEquals(first.startedAtEpochMs, awaitItem().startedAtEpochMs)

                // Switched on mid-flight: the next fix, another gap later, opens a
                // new trip without a re-subscription.
                setting.value = TripAutoResetSetting.HOURS_2
                val later = NOON_MS + 2 * parkedGapMs
                source.emit(
                    fakeLocation(
                        latitude = ORIGIN_LAT + 2 * STEP,
                        timeMs = later,
                        speedMps = 11f,
                        elapsedRealtimeNanos = millisToNanos(2 * parkedGapMs),
                    ),
                )
                assertEquals(later, awaitItem().startedAtEpochMs)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ignores the wall clock once this process has seen a fix`() =
        runTest {
            // A mid-trip clock correction larger than the gap, backward. The
            // boot-clock span (10 s) governs in-process, so the wall clock is
            // never consulted and the trip keeps accruing.
            val flow =
                flowOf(
                    fakeLocation(latitude = ORIGIN_LAT, timeMs = NOON_MS, speedMps = 11f, elapsedRealtimeNanos = 0L),
                    fakeLocation(
                        latitude = ORIGIN_LAT + STEP,
                        timeMs = NOON_MS - parkedGapMs - 1_000L,
                        speedMps = 11f,
                        elapsedRealtimeNanos = tenSeconds(1),
                    ),
                )

            TripRepository(
                flow,
                FakeTripStateStore(),
                autoReset = flowOf(TripAutoResetSetting.HOURS_2),
            ).stateFlow().test {
                awaitItem() // initial snapshot
                val first = awaitItem()
                val second = awaitItem()
                assertEquals(first.startedAtEpochMs, second.startedAtEpochMs)
                assertTrue(second.distanceMeters > 0.0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `starts a new trip at the first fix of a process that lands after the parked gap`() =
        runTest {
            // The window-open check saw no gap (the clock still reads the last
            // fix's time), so the first fix decides — on the wall clock, the only
            // stamp that spans the restart.
            val store =
                FakeTripStateStore(
                    PersistedTrip.Initial.copy(
                        totalMeters = 1_000.0,
                        totalSeconds = 100.0,
                        startedAtEpochMs = 42L,
                        lastFixEpochMs = NOON_MS,
                        tripId = 3L,
                    ),
                )
            val nextDrive = NOON_MS + parkedGapMs
            val flow =
                flowOf(
                    fakeLocation(latitude = ORIGIN_LAT, timeMs = nextDrive, speedMps = 11f, elapsedRealtimeNanos = 0L),
                )

            TripRepository(
                flow,
                store,
                autoReset = flowOf(TripAutoResetSetting.HOURS_2),
                nowEpochMs = { NOON_MS },
            ).stateFlow().test {
                assertEquals(1_000.0, awaitItem().distanceMeters, 0.0) // restored, no gap yet
                val fresh = awaitItem()
                assertEquals(0.0, fresh.distanceMeters, 0.0)
                assertEquals(nextDrive, fresh.startedAtEpochMs)
                assertEquals(4L, store.stored.tripId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `starts a new trip when the window reopens after the parked gap in the same process`() =
        runTest {
            // A head unit that slept through the gap: the process lived on, so the
            // boot clock carries the span and the replay on re-subscription already
            // shows the next drive's zeros — before any fix arrives.
            val source = MutableSharedFlow<Location?>(replay = 0)
            var bootNanos = 0L
            val repository =
                TripRepository(
                    source,
                    FakeTripStateStore(),
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                    nowElapsedRealtimeNanos = { bootNanos },
                    autoReset = flowOf(TripAutoResetSetting.HOURS_2),
                )

            repository.stateFlow().test {
                awaitItem() // initial snapshot
                source.emit(
                    fakeLocation(
                        latitude = ORIGIN_LAT,
                        timeMs = NOON_MS,
                        speedMps = 11f,
                        elapsedRealtimeNanos = tenSeconds(1),
                    ),
                )
                awaitItem()
                source.emit(
                    fakeLocation(
                        latitude = ORIGIN_LAT + STEP,
                        timeMs = NOON_MS + 10_000L,
                        speedMps = 11f,
                        elapsedRealtimeNanos = tenSeconds(2),
                    ),
                )
                assertTrue(awaitItem().distanceMeters > 0.0)
                cancelAndIgnoreRemainingEvents()
            }

            // The boot clock ran past the gap while nobody was subscribed.
            bootNanos = tenSeconds(2) + millisToNanos(parkedGapMs)
            repository.stateFlow().test {
                val replayed = awaitItem()
                assertEquals(0.0, replayed.distanceMeters, 0.0)
                assertNull(replayed.startedAtEpochMs)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `keeps the persisted trip when the first fix of a process predates its last fix`() =
        runTest {
            // Clock skew between the old and the new boot: a first fix stamped
            // before the persisted last fix is a negative span, not a parked gap.
            val store =
                FakeTripStateStore(
                    PersistedTrip.Initial.copy(
                        totalMeters = 1_000.0,
                        totalSeconds = 100.0,
                        startedAtEpochMs = 42L,
                        lastFixEpochMs = NOON_MS,
                        tripId = 3L,
                    ),
                )
            val flow =
                flowOf(
                    fakeLocation(
                        latitude = ORIGIN_LAT,
                        timeMs = NOON_MS - parkedGapMs - 1_000L,
                        speedMps = 11f,
                        elapsedRealtimeNanos = 0L,
                    ),
                )

            TripRepository(
                flow,
                store,
                autoReset = flowOf(TripAutoResetSetting.HOURS_2),
                nowEpochMs = { NOON_MS },
            ).stateFlow().test {
                awaitItem() // restored snapshot
                val afterFix = awaitItem()
                assertEquals(42L, afterFix.startedAtEpochMs)
                assertEquals(1_000.0, afterFix.distanceMeters, 0.0)
                assertEquals(3L, store.stored.tripId)
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

        // An arbitrary wall-clock instant for fixes whose Location.time matters.
        const val NOON_MS = 1_700_000_000_000L

        // The configured parked gap, read off the setting so the fixture can
        // never drift from the production threshold.
        val parkedGapMs: Long = checkNotNull(TripAutoResetSetting.HOURS_2.parkedGapMs)

        // n * 10 s expressed in boot-clock nanoseconds.
        fun tenSeconds(n: Long): Long = n * 10_000_000_000L

        // n * 250 ms expressed in boot-clock nanoseconds.
        fun quarterSecond(n: Long): Long = n * 250_000_000L

        fun millisToNanos(ms: Long): Long = ms * 1_000_000L
    }
}

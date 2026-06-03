package io.github.seijikohara.femto.data

import android.location.Location
import app.cash.turbine.test
import io.github.seijikohara.femto.testfixtures.fakeLocation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
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
            // Same position, reported speed below MIN_MOVING_SPEED_MS.
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
            val repository = TripRepository(source)

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

    private companion object {
        const val ORIGIN_LAT = 35.6580

        // ~0.001 deg latitude ≈ 111 m; over 10 s that is ~11 m/s, well above
        // the moving-speed floor.
        const val STEP = 0.001

        // n * 10 s expressed in boot-clock nanoseconds.
        fun tenSeconds(n: Long): Long = n * 10_000_000_000L
    }
}

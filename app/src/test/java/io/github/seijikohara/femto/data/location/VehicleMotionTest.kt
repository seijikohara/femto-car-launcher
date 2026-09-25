package io.github.seijikohara.femto.data.location

import android.location.Location
import android.location.LocationManager
import io.github.seijikohara.femto.testfixtures.fakeLocation
import io.github.seijikohara.femto.testfixtures.fakeTripState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

// Location is an Android type Robolectric supplies; the classification itself
// is pure, and the clock is always passed in (Robolectric's starts at zero).
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VehicleMotionTest {
    private val parked = fakeTripState(currentSpeedMs = 0.0)
    private val moving = fakeTripState(currentSpeedMs = MIN_MOVING_SPEED_MS)

    @Test
    fun `no fix is unknown even though the starting speed reads parked`() {
        // TripState.Initial is stationary: exactly the reading a fail-closed
        // gate must not take for "parked".
        assertEquals(VehicleMotion.UNKNOWN, vehicleMotion(location = null, tripState = TripState.Initial, NOW))
    }

    @Test
    fun `a live GPS fix below the moving floor is parked`() {
        assertEquals(VehicleMotion.PARKED, vehicleMotion(gpsFix(), parked, NOW))
    }

    @Test
    fun `a trip speed at the moving floor is moving`() {
        assertEquals(VehicleMotion.MOVING, vehicleMotion(gpsFix(), moving, NOW))
    }

    @Test
    fun `a trip speed at the moving floor is moving even on a stale fix`() {
        // A tunnel: the receiver went quiet mid-drive, and the last speed stands.
        assertEquals(VehicleMotion.MOVING, vehicleMotion(gpsFix(ageNanos = STALE_NANOS), moving, NOW))
    }

    @Test
    fun `a stale GPS fix, the cached seed a subscription starts with, is unknown`() {
        // The seed arrives before the trip math has any speed, so a stationary
        // trip state says nothing here.
        assertEquals(VehicleMotion.UNKNOWN, vehicleMotion(gpsFix(ageNanos = STALE_NANOS), parked, NOW))
    }

    @Test
    fun `a fix just past the freshness window is unknown`() {
        val justStale = LOCATION_STALE_THRESHOLD_MS * NANOS_PER_MS + 1
        assertEquals(VehicleMotion.UNKNOWN, vehicleMotion(gpsFix(ageNanos = justStale), parked, NOW))
    }

    @Test
    fun `a live NETWORK fix is unknown, since the trip math never sees it`() {
        // An "Approximate" grant: network fixes only, and a trip speed stuck at zero.
        val networkFix = fakeLocation(provider = LocationManager.NETWORK_PROVIDER, elapsedRealtimeNanos = NOW)
        assertEquals(VehicleMotion.UNKNOWN, vehicleMotion(networkFix, parked, NOW))
    }

    @Test
    fun `a live GPS fix that reports moving is moving before the trip speed catches up`() {
        // The first fix of a process only anchors the trip math, so the trip
        // speed still reads zero while the fix itself says otherwise.
        assertEquals(VehicleMotion.MOVING, vehicleMotion(gpsFix(speedMps = 20f), parked, NOW))
    }

    @Test
    fun `a stale fix that reported moving is unknown, not moving`() {
        // An old cached reading must not hold the install steps indefinitely.
        assertEquals(VehicleMotion.UNKNOWN, vehicleMotion(gpsFix(speedMps = 20f, ageNanos = STALE_NANOS), parked, NOW))
    }

    @Test
    fun `the flow judges each fix as it arrives and speaks only on change`() =
        runTest {
            val locations = MutableSharedFlow<Location?>()
            val trips = MutableStateFlow(parked)
            val verdicts = mutableListOf<VehicleMotion>()
            backgroundScope.launch { vehicleMotionFlow(locations, trips) { NOW }.toList(verdicts) }
            runCurrent()

            locations.emit(gpsFix(ageNanos = STALE_NANOS))
            runCurrent()
            locations.emit(gpsFix())
            runCurrent()
            locations.emit(gpsFix())
            runCurrent()

            assertEquals(listOf(VehicleMotion.UNKNOWN, VehicleMotion.PARKED), verdicts)
        }

    private fun gpsFix(
        speedMps: Float = 0f,
        ageNanos: Long = 0L,
    ): Location =
        fakeLocation(
            provider = LocationManager.GPS_PROVIDER,
            speedMps = speedMps,
            elapsedRealtimeNanos = NOW - ageNanos,
        )

    private companion object {
        const val NANOS_PER_MS = 1_000_000L

        // An hour into the boot clock, so a fix can be dated before "now".
        const val NOW = 3_600_000L * NANOS_PER_MS

        // A cached seed from well before this subscription.
        const val STALE_NANOS = 60_000L * NANOS_PER_MS
    }
}

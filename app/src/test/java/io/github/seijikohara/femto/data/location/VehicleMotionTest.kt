package io.github.seijikohara.femto.data.location

import io.github.seijikohara.femto.testfixtures.fakeLocation
import io.github.seijikohara.femto.testfixtures.fakeTripState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

// Location is an Android type Robolectric supplies; the classification itself is pure.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VehicleMotionTest {
    @Test
    fun `no fix is unknown even though the starting speed reads parked`() {
        // TripState.Initial is stationary: exactly the reading a fail-closed
        // gate must not take for "parked".
        assertEquals(VehicleMotion.UNKNOWN, vehicleMotion(location = null, tripState = TripState.Initial))
    }

    @Test
    fun `a fix below the moving floor is parked`() {
        assertEquals(VehicleMotion.PARKED, vehicleMotion(fakeLocation(), fakeTripState(currentSpeedMs = 0.0)))
    }

    @Test
    fun `a fix at the moving floor is moving`() {
        assertEquals(
            VehicleMotion.MOVING,
            vehicleMotion(fakeLocation(), fakeTripState(currentSpeedMs = MIN_MOVING_SPEED_MS)),
        )
    }
}

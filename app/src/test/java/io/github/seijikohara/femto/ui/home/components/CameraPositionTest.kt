package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.data.routing.OnLinePoint
import io.github.seijikohara.femto.data.routing.RoutePoint
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Where the map's camera puts the vehicle (issue #389 phase 4): on the route line while the dashboard hands one over,
 * and otherwise at the fix, exactly as before the vehicle was drawn on the line; and when the camera hears of it.
 */
class CameraPositionTest {
    @Test
    fun `the camera goes to the point on the line while there is one`() {
        assertEquals(ON_LINE, cameraPosition(fix = FIX, vehiclePosition = OnLinePoint(ON_LINE, fixElapsedMs = 1_000L)))
    }

    // No route, a route on the Google backend (routing is not offered there), the setting off, or a fix too far from
    // the line: the dashboard hands over no point, and the map gets the fix.
    @Test
    fun `without a point on the line the camera goes to the fix`() {
        assertEquals(FIX, cameraPosition(fix = FIX, vehiclePosition = null))
    }

    // Each fix must reach the page, or its fix clock stops and it greys the vehicle as if the signal were lost: past
    // the line's end, or outside a corner, several fixes in a row match the same point.
    @Test
    fun `two fixes matched to the same point of the line give the camera two keys`() {
        assertNotEquals(
            cameraKey(fix = FIX, vehiclePosition = OnLinePoint(ON_LINE, fixElapsedMs = 1_000L)),
            cameraKey(fix = NEXT_FIX, vehiclePosition = OnLinePoint(ON_LINE, fixElapsedMs = 2_000L)),
        )
    }

    @Test
    fun `without a point on the line the camera keys on the fix's position, as before`() {
        assertEquals(cameraKey(fix = FIX, vehiclePosition = null), cameraKey(fix = FIX.copy(), vehiclePosition = null))
        assertNotEquals(cameraKey(fix = FIX, vehiclePosition = null), cameraKey(fix = NEXT_FIX, vehiclePosition = null))
    }

    // The fix reaches the dashboard before the follow has placed it on the line. A push then would send the last
    // point again, and the page would ease into the same place twice a second.
    @Test
    fun `a fix that arrives while the last fix's point is still on the line keeps the key`() {
        val drawn = OnLinePoint(ON_LINE, fixElapsedMs = 1_000L)

        assertEquals(
            cameraKey(fix = FIX, vehiclePosition = drawn),
            cameraKey(fix = NEXT_FIX, vehiclePosition = drawn),
        )
    }

    private companion object {
        val FIX = RoutePoint(latitude = 35.6581, longitude = 139.7017)
        val NEXT_FIX = RoutePoint(latitude = 35.65811, longitude = 139.70171)
        val ON_LINE = RoutePoint(latitude = 35.65805, longitude = 139.70172)
    }
}

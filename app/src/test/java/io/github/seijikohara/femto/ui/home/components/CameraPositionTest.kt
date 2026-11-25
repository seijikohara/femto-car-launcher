package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.data.routing.RoutePoint
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Where the map's camera puts the vehicle (issue #389 phase 4): on the route line while the dashboard hands one over,
 * and otherwise at the fix, exactly as before the vehicle was drawn on the line.
 */
class CameraPositionTest {
    @Test
    fun `the camera goes to the point on the line while there is one`() {
        assertEquals(ON_LINE, cameraPosition(fix = FIX, vehiclePosition = ON_LINE))
    }

    // No route, a route on the Google backend (routing is not offered there), the setting off, or a fix too far from
    // the line: the dashboard hands over no point, and the map gets the fix.
    @Test
    fun `without a point on the line the camera goes to the fix`() {
        assertEquals(FIX, cameraPosition(fix = FIX, vehiclePosition = null))
    }

    private companion object {
        val FIX = RoutePoint(latitude = 35.6581, longitude = 139.7017)
        val ON_LINE = RoutePoint(latitude = 35.65805, longitude = 139.70172)
    }
}

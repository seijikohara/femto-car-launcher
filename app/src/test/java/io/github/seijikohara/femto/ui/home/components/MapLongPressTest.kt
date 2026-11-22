package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.data.routing.RoutePoint
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The host's reading of the OSM page's `longpress` detail (webmap/src/long-press.ts), and its tap or long press verdict. */
class MapLongPressTest {
    @Test
    fun `a latitude and longitude pair is the pressed point`() {
        assertEquals(RoutePoint(35.681236, 139.767125), longPressPointOrNull("35.681236,139.767125"))
    }

    @Test
    fun `a southern and western point keeps its signs`() {
        assertEquals(RoutePoint(-33.8688, -151.2093), longPressPointOrNull("-33.868800,-151.209300"))
    }

    @Test
    fun `the edges of the earth are points`() {
        assertEquals(RoutePoint(-90.0, 180.0), longPressPointOrNull("-90.000000,180.000000"))
    }

    @Test
    fun `a latitude beyond the pole is no point`() {
        assertNull(longPressPointOrNull("90.5,139.7"))
    }

    @Test
    fun `a longitude beyond the antimeridian is no point`() {
        assertNull(longPressPointOrNull("35.6,180.5"))
    }

    // --- tap or long press, in the main thread's order ---------------------------

    @Test
    fun `a long press that arrives while the finger is down takes the tap that ends the touch`() {
        val arbiter = MapPressArbiter()
        arbiter.onFingerDown()

        assertTrue(arbiter.claimLongPress())
        arbiter.onFingersUp()

        assertFalse(arbiter.allowsTap())
    }

    @Test
    fun `a release before the long press arrives is a tap, and the late long press is dropped`() {
        val arbiter = MapPressArbiter()
        arbiter.onFingerDown()
        arbiter.onFingersUp()

        assertTrue(arbiter.allowsTap())
        assertFalse(arbiter.claimLongPress())
    }

    @Test
    fun `a new touch after a long press taps again`() {
        val arbiter = MapPressArbiter()
        arbiter.onFingerDown()
        arbiter.claimLongPress()
        arbiter.onFingersUp()

        arbiter.onFingerDown()
        arbiter.onFingersUp()

        assertTrue(arbiter.allowsTap())
    }

    @Test
    fun `no long press counts before any touch`() {
        assertFalse(MapPressArbiter().claimLongPress())
    }

    @Test
    fun `a detail without two numbers is no point`() {
        listOf("", "35.6", "35.6,", ",139.7", "a,b", "35.6,139.7,0", "NaN,139.7", "35.6,Infinity").forEach { detail ->
            assertNull(longPressPointOrNull(detail), "detail \"$detail\"")
        }
    }
}

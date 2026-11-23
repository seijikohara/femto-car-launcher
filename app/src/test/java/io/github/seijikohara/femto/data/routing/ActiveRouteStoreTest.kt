package io.github.seijikohara.femto.data.routing

import io.github.seijikohara.femto.testfixtures.fakeEquatorRoute
import io.github.seijikohara.femto.testfixtures.fakeRoute
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActiveRouteStoreTest {
    private val store = ActiveRouteStore()

    @Test
    fun `start opens a new session each time`() {
        val first = store.start(ROUTE, DESTINATION, FIRST_REFERENCE)
        val second = store.start(ROUTE, DESTINATION, FIRST_REFERENCE)

        assertNotEquals(first, second)
        assertEquals(ActiveRoute(second, ROUTE, DESTINATION, FIRST_REFERENCE), store.active.value)
    }

    @Test
    fun `replace keeps the session and resets the status`() {
        val session = store.start(ROUTE, DESTINATION, FIRST_REFERENCE)
        store.setStatus(session, RerouteStatus.REROUTING)

        val replaced = store.replace(session, REROUTED, SECOND_REFERENCE)

        assertTrue(replaced)
        assertEquals(ActiveRoute(session, REROUTED, DESTINATION, SECOND_REFERENCE), store.active.value)
    }

    @Test
    fun `replace for an old session changes nothing`() {
        val old = store.start(ROUTE, DESTINATION, FIRST_REFERENCE)
        val current = store.start(ROUTE, DESTINATION, FIRST_REFERENCE)

        val replaced = store.replace(old, REROUTED, SECOND_REFERENCE)

        assertFalse(replaced)
        assertEquals(ActiveRoute(current, ROUTE, DESTINATION, FIRST_REFERENCE), store.active.value)
    }

    @Test
    fun `setStatus for an old session changes nothing`() {
        val old = store.start(ROUTE, DESTINATION, FIRST_REFERENCE)
        val current = store.start(ROUTE, DESTINATION, FIRST_REFERENCE)

        val set = store.setStatus(old, RerouteStatus.FAILED)

        assertFalse(set)
        assertEquals(ActiveRoute(current, ROUTE, DESTINATION, FIRST_REFERENCE), store.active.value)
    }

    @Test
    fun `clear ends the session so a late replace is dropped`() {
        val session = store.start(ROUTE, DESTINATION, FIRST_REFERENCE)
        store.clear()

        val replaced = store.replace(session, REROUTED, SECOND_REFERENCE)

        assertFalse(replaced)
        assertNull(store.active.value)
    }

    private companion object {
        val ROUTE = fakeRoute()
        val REROUTED = fakeEquatorRoute()
        val DESTINATION = RoutePoint(latitude = 34.07, longitude = 133.0)
        val FIRST_REFERENCE = File("route-1.bin")
        val SECOND_REFERENCE = File("route-2.bin")
    }
}

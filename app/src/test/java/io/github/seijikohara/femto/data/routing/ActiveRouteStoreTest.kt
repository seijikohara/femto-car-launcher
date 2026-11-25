package io.github.seijikohara.femto.data.routing

import io.github.seijikohara.femto.testfixtures.fakeEquatorRoute
import io.github.seijikohara.femto.testfixtures.fakeRoute
import io.github.seijikohara.femto.testfixtures.withoutChoice
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActiveRouteStoreTest {
    private val store = ActiveRouteStore()

    @Test
    fun `start opens a new session each time`() {
        val first = store.start(ROUTE, DESTINATION, NO_CONDITIONS, FIRST_REFERENCE)
        val second = store.start(ROUTE, DESTINATION, NO_CONDITIONS, FIRST_REFERENCE)

        assertNotEquals(first, second)
        assertEquals(ActiveRoute(second, ROUTE, DESTINATION, FIRST_REFERENCE), store.active.value?.withoutChoice())
    }

    @Test
    fun `replace keeps the session and resets the status`() {
        val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, FIRST_REFERENCE)
        store.setStatus(session, RerouteStatus.REROUTING)

        val replaced = store.replace(session, REROUTED, SECOND_REFERENCE)

        assertTrue(replaced)
        assertEquals(ActiveRoute(session, REROUTED, DESTINATION, SECOND_REFERENCE), store.active.value?.withoutChoice())
    }

    // --- The choice between routes --------------------------------------------

    @Test
    fun `start opens the choice with the first route`() {
        store.start(ROUTE, DESTINATION, NO_CONDITIONS, FIRST_REFERENCE, computingMs = 500L)

        val active = store.active.value
        assertEquals(listOf(RouteCandidate(ROUTE, FIRST_REFERENCE, 500L)), active?.candidates)
        assertEquals(Triple(0, true, false), active?.let { Triple(it.selected, it.choosing, it.finding) })
    }

    // "Suggest other routes" is off: the route stands alone, as a restored one does.
    @Test
    fun `a session started without a choice holds its one candidate and no open choice`() {
        store.start(ROUTE, DESTINATION, NO_CONDITIONS, FIRST_REFERENCE, computingMs = 500L, choosing = false)

        val active = store.active.value
        assertEquals(listOf(RouteCandidate(ROUTE, FIRST_REFERENCE, 500L)), active?.candidates)
        assertEquals(0 to false, active?.let { it.selected to it.choosing })
    }

    @Test
    fun `select switches the active route and reference`() {
        val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, FIRST_REFERENCE)
        store.addCandidate(session, RouteCandidate(REROUTED, SECOND_REFERENCE, 900L))
        store.setStatus(session, RerouteStatus.FAILED)

        val selected = store.select(session, 1)

        assertTrue(selected)
        assertEquals(
            ActiveRoute(session, REROUTED, DESTINATION, SECOND_REFERENCE),
            store.active.value
                ?.withoutChoice(),
        )
        assertEquals(1, store.active.value?.selected)
    }

    @Test
    fun `addCandidate for a stale session is dropped`() {
        val old = store.start(ROUTE, DESTINATION, NO_CONDITIONS, FIRST_REFERENCE)
        store.start(ROUTE, DESTINATION, NO_CONDITIONS, FIRST_REFERENCE)

        val added = store.addCandidate(old, RouteCandidate(REROUTED, SECOND_REFERENCE, 900L))

        assertFalse(added)
        assertEquals(
            1,
            store.active.value
                ?.candidates
                ?.size,
        )
    }

    @Test
    fun `addCandidate after the choice closed is dropped`() {
        val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, FIRST_REFERENCE)
        store.closeChoice(session)

        val added = store.addCandidate(session, RouteCandidate(REROUTED, SECOND_REFERENCE, 900L))

        assertFalse(added)
        assertEquals(listOf(RouteCandidate(ROUTE, FIRST_REFERENCE, 0L)), store.active.value?.candidates)
    }

    @Test
    fun `closeChoice keeps only the selected candidate`() {
        val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, FIRST_REFERENCE)
        store.addCandidate(session, RouteCandidate(REROUTED, SECOND_REFERENCE, 900L))
        store.select(session, 1)
        store.setFinding(session, true)

        store.closeChoice(session)

        val active = store.active.value
        assertEquals(listOf(RouteCandidate(REROUTED, SECOND_REFERENCE, 900L)), active?.candidates)
        assertEquals(Triple(0, false, false), active?.let { Triple(it.selected, it.choosing, it.finding) })
        assertEquals(REROUTED, active?.route)
    }

    @Test
    fun `completeSearch says the search ended and lowers the finding flag, which a stop never does`() {
        val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, FIRST_REFERENCE)
        store.setFinding(session, true)
        store.setFinding(session, false)
        val afterAStop = store.active.value?.searchCompleted
        store.setFinding(session, true)

        store.completeSearch(session)

        assertEquals(
            Triple(false, true, false),
            Triple(afterAStop, store.active.value?.searchCompleted, store.active.value?.finding),
        )
    }

    @Test
    fun `completeSearch after the choice closed is dropped`() {
        val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, FIRST_REFERENCE)
        store.closeChoice(session)

        assertFalse(store.completeSearch(session))
    }

    @Test
    fun `a reroute's replace closes the choice`() {
        val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, FIRST_REFERENCE)
        store.setFinding(session, true)

        store.replace(session, REROUTED, SECOND_REFERENCE)

        assertEquals(false to false, store.active.value?.let { it.choosing to it.finding })
    }

    @Test
    fun `a session keeps its conditions across replace`() {
        val conditions = RouteConditions(avoidTolls = true)
        val session = store.start(ROUTE, DESTINATION, conditions, FIRST_REFERENCE)

        store.replace(session, REROUTED, SECOND_REFERENCE)

        assertEquals(conditions, store.active.value?.conditions)
    }

    @Test
    fun `replace for an old session changes nothing`() {
        val old = store.start(ROUTE, DESTINATION, NO_CONDITIONS, FIRST_REFERENCE)
        val current = store.start(ROUTE, DESTINATION, NO_CONDITIONS, FIRST_REFERENCE)

        val replaced = store.replace(old, REROUTED, SECOND_REFERENCE)

        assertFalse(replaced)
        assertEquals(ActiveRoute(current, ROUTE, DESTINATION, FIRST_REFERENCE), store.active.value?.withoutChoice())
    }

    @Test
    fun `setStatus for an old session changes nothing`() {
        val old = store.start(ROUTE, DESTINATION, NO_CONDITIONS, FIRST_REFERENCE)
        val current = store.start(ROUTE, DESTINATION, NO_CONDITIONS, FIRST_REFERENCE)

        val set = store.setStatus(old, RerouteStatus.FAILED)

        assertFalse(set)
        assertEquals(ActiveRoute(current, ROUTE, DESTINATION, FIRST_REFERENCE), store.active.value?.withoutChoice())
    }

    @Test
    fun `clear ends the session so a late replace is dropped`() {
        val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, FIRST_REFERENCE)
        store.clear()

        val replaced = store.replace(session, REROUTED, SECOND_REFERENCE)

        assertFalse(replaced)
        assertNull(store.active.value)
    }

    // --- A route kept across a restart ---------------------------------------

    @Test
    fun `the restored route has one candidate, no choice, no reference`() {
        val conditions = RouteConditions(avoidMotorways = true)

        val session = assertNotNull(store.restore(ROUTE, DESTINATION, conditions))

        assertEquals(
            ActiveRoute(
                session = session,
                route = ROUTE,
                destination = DESTINATION,
                reference = null,
                conditions = conditions,
                candidates = listOf(RouteCandidate(ROUTE, reference = null, computingMs = 0L)),
            ),
            store.active.value,
        )
    }

    @Test
    fun `a restore takes its session from the store's counter`() {
        val started = store.start(ROUTE, DESTINATION, NO_CONDITIONS, FIRST_REFERENCE)
        store.clear()

        val restored = store.restore(ROUTE, DESTINATION, NO_CONDITIONS)

        assertTrue(restored != null && restored > started)
    }

    @Test
    fun `a restore never replaces an active session`() {
        val current = store.start(ROUTE, DESTINATION, NO_CONDITIONS, FIRST_REFERENCE)

        val restored = store.restore(REROUTED, DESTINATION, NO_CONDITIONS)

        assertNull(restored)
        assertEquals(ActiveRoute(current, ROUTE, DESTINATION, FIRST_REFERENCE), store.active.value?.withoutChoice())
    }

    @Test
    fun `clearing a session ends it`() {
        val session = store.start(ROUTE, DESTINATION, NO_CONDITIONS, FIRST_REFERENCE)

        val cleared = store.clear(session)

        assertTrue(cleared)
        assertNull(store.active.value)
    }

    @Test
    fun `clearing an old session leaves the current one`() {
        val old = store.start(ROUTE, DESTINATION, NO_CONDITIONS, FIRST_REFERENCE)
        val current = store.start(REROUTED, DESTINATION, NO_CONDITIONS, SECOND_REFERENCE)

        val cleared = store.clear(old)

        assertFalse(cleared)
        assertEquals(current, store.active.value?.session)
    }

    private companion object {
        val ROUTE = fakeRoute()
        val REROUTED = fakeEquatorRoute()
        val DESTINATION = RoutePoint(latitude = 34.07, longitude = 133.0)
        val FIRST_REFERENCE = File("route-1.bin")
        val SECOND_REFERENCE = File("route-2.bin")
        val NO_CONDITIONS = RouteConditions()
    }
}

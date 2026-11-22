package io.github.seijikohara.femto.data.routing

import io.github.seijikohara.femto.testfixtures.fakeEquatorRoute
import io.github.seijikohara.femto.testfixtures.fakeRoute
import org.junit.Test
import kotlin.test.assertEquals

class RouteProgressTest {
    private val route = fakeEquatorRoute()

    @Test
    fun `at the start the whole route is left`() {
        assertEquals(RouteProgress(22_000.0, 1_200.0), route.progressAt(RoutePoint(0.0, 0.0)))
    }

    @Test
    fun `halfway along half the distance and half the time are left`() {
        val progress = route.progressAt(RoutePoint(0.0, 0.1))

        assertEquals(11_000.0, progress.remainingMeters, TOLERANCE_METERS)
        assertEquals(600.0, progress.remainingSeconds, TOLERANCE_SECONDS)
    }

    @Test
    fun `a fix within a leg counts the part of the leg ahead`() {
        val progress = route.progressAt(RoutePoint(0.0, 0.15))

        assertEquals(5_500.0, progress.remainingMeters, TOLERANCE_METERS)
    }

    @Test
    fun `past the end nothing is left`() {
        assertEquals(RouteProgress(0.0, 0.0), route.progressAt(RoutePoint(0.0, 0.3)))
    }

    @Test
    fun `before the start the whole route is left`() {
        assertEquals(RouteProgress(22_000.0, 1_200.0), route.progressAt(RoutePoint(0.0, -0.1)))
    }

    @Test
    fun `a fix beside the line counts where it projects`() {
        // About 1.1 km north of the line's midpoint.
        val progress = route.progressAt(RoutePoint(0.01, 0.1))

        assertEquals(11_000.0, progress.remainingMeters, TOLERANCE_METERS)
    }

    @Test
    fun `where the line doubles back the earlier pass wins`() {
        val outAndBack =
            fakeRoute(points = listOf(RoutePoint(0.0, 0.0), RoutePoint(0.0, 0.1), RoutePoint(0.0, 0.0)))

        assertEquals(1.0, remainingShare(outAndBack.points, RoutePoint(0.0, 0.0)))
    }

    @Test
    fun `a line across the antimeridian is measured the short way`() {
        val line = listOf(RoutePoint(0.0, 179.9), RoutePoint(0.0, -179.9))

        assertEquals(0.5, remainingShare(line, RoutePoint(0.0, 180.0)), TOLERANCE_SHARE)
    }

    @Test
    fun `a line without length leaves nothing`() {
        val line = listOf(RoutePoint(1.0, 1.0), RoutePoint(1.0, 1.0))

        assertEquals(0.0, remainingShare(line, RoutePoint(1.0, 1.0)))
    }

    @Test
    fun `the full progress is the route's own distance and time`() {
        assertEquals(RouteProgress(22_000.0, 1_200.0), route.fullProgress())
    }

    private companion object {
        const val TOLERANCE_METERS = 1.0
        const val TOLERANCE_SECONDS = 0.1
        const val TOLERANCE_SHARE = 1e-9
    }
}

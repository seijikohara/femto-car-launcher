package io.github.seijikohara.femto.data.routing

import io.github.seijikohara.femto.testfixtures.fakeEquatorRoute
import io.github.seijikohara.femto.testfixtures.fakeOutAndBackRoute
import io.github.seijikohara.femto.testfixtures.fakeParallelRoadsRoute
import io.github.seijikohara.femto.testfixtures.fakeRoute
import io.github.seijikohara.femto.testfixtures.fakeRouteFix
import io.github.seijikohara.femto.testfixtures.fakeSameRoadOutAndBackRoute
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.math.PI
import kotlin.test.assertEquals

class RouteProgressTest {
    private val route = fakeEquatorRoute()
    private val line = RouteLine(route)

    // What is left from [position] for a fix with no history on the line.
    private fun RouteLine.progressFrom(
        position: RoutePoint,
        previous: LinePosition = LinePosition.Start,
    ): RouteProgress = progressAt(follow(position, previous))

    @Test
    fun `at the start the whole route is left`() {
        assertEquals(RouteProgress(22_000.0, 1_200.0), line.progressFrom(RoutePoint(0.0, 0.0)))
    }

    @Test
    fun `halfway along half the distance and half the time are left`() {
        val progress = line.progressFrom(RoutePoint(0.0, 0.1))

        assertEquals(11_000.0, progress.remainingMeters, TOLERANCE_METERS)
        assertEquals(600.0, progress.remainingSeconds, TOLERANCE_SECONDS)
    }

    @Test
    fun `a fix within a leg counts the part of the leg ahead`() {
        assertEquals(5_500.0, line.progressFrom(RoutePoint(0.0, 0.15)).remainingMeters, TOLERANCE_METERS)
    }

    @Test
    fun `past the end nothing is left`() {
        val progress = line.progressFrom(RoutePoint(0.0, 0.3))

        assertEquals(0.0, progress.remainingMeters, TOLERANCE_METERS)
        assertEquals(0.0, progress.remainingSeconds, TOLERANCE_SECONDS)
    }

    @Test
    fun `before the start the whole route is left`() {
        assertEquals(RouteProgress(22_000.0, 1_200.0), line.progressFrom(RoutePoint(0.0, -0.1)))
    }

    @Test
    fun `a fix beside the line counts where it projects`() {
        // About 1.1 km north of the line's midpoint.
        assertEquals(11_000.0, line.progressFrom(RoutePoint(0.01, 0.1)).remainingMeters, TOLERANCE_METERS)
    }

    @Test
    fun `where the line doubles back on itself the earlier pass wins`() {
        val sameRoad = RouteLine(fakeSameRoadOutAndBackRoute())

        assertEquals(0.0, sameRoad.follow(RoutePoint(0.0, 0.0), LinePosition.Start).travelledMeters)
    }

    @Test
    fun `a line across the antimeridian is measured the short way`() {
        val across =
            RouteLine(
                fakeRoute(points = listOf(RoutePoint(0.0, 179.9), RoutePoint(0.0, -179.9)), distanceMeters = 22_000),
            )

        assertEquals(11_000.0, across.progressFrom(RoutePoint(0.0, 180.0)).remainingMeters, TOLERANCE_METERS)
    }

    @Test
    fun `a line without length leaves nothing`() {
        val point = RouteLine(fakeRoute(points = listOf(RoutePoint(1.0, 1.0), RoutePoint(1.0, 1.0))))

        assertEquals(RouteProgress(0.0, 0.0), point.progressFrom(RoutePoint(1.0, 1.0)))
    }

    @Test
    fun `the start of the line is the route's own distance and time`() {
        assertEquals(RouteProgress(22_000.0, 1_200.0), line.progressAt(LinePosition.Start))
    }

    // --- Following the fixes along the line --------------------------------------

    @Test
    fun `on the way out a fix nearer the way back stays on the way out`() {
        // GPS noise puts the fix 5.6 m from the way back and 16.7 m from the
        // way out, 3.3 km from the start. The nearest point of the whole line
        // would leave 3.3 km where 18.9 km are left.
        val outAndBack = RouteLine(fakeOutAndBackRoute())
        val previous = outAndBack.follow(RoutePoint(0.0, 0.029), LinePosition.Start)

        val progress = outAndBack.progressFrom(RoutePoint(0.00015, 0.03), previous)

        assertEquals(OUT_AND_BACK_LEFT_AT_0_03_OUT, progress.remainingMeters, TOLERANCE_METERS)
    }

    @Test
    fun `at the start a fix nearer the way back counts on the way out`() {
        val outAndBack = RouteLine(fakeOutAndBackRoute())

        val progress = outAndBack.progressFrom(RoutePoint(0.00015, 0.03))

        assertEquals(OUT_AND_BACK_LEFT_AT_0_03_OUT, progress.remainingMeters, TOLERANCE_METERS)
    }

    @Test
    fun `on the way back a fix nearer the way out stays on the way back`() {
        val outAndBack = RouteLine(fakeOutAndBackRoute())
        val previous = outAndBack.follow(RoutePoint(0.0002, 0.031), MIDWAY)

        val progress = outAndBack.progressFrom(RoutePoint(0.00005, 0.03), previous)

        assertEquals(OUT_AND_BACK_LEFT_AT_0_03_BACK, progress.remainingMeters, TOLERANCE_METERS)
    }

    @Test
    fun `a fix a little behind the last one may count a little behind it`() {
        // GPS noise: 20 m back along the line, within the search's backtrack.
        val previous = line.follow(RoutePoint(0.0, 0.1), LinePosition.Start)

        val progress = line.progressFrom(RoutePoint(0.0, 0.1 - 20.0 / METERS_PER_DEGREE), previous)

        assertEquals(11_000.0 + 20.0 * EQUATOR_SCALE, progress.remainingMeters, TOLERANCE_METERS)
    }

    @Test
    fun `a fix off a detour counts where it projects on the whole line`() {
        // 1.1 km north of the line, far from everything ahead of the last fix.
        val previous = line.follow(RoutePoint(0.0, 0.05), LinePosition.Start)

        val progress = line.progressFrom(RoutePoint(0.01, 0.15), previous)

        assertEquals(5_500.0, progress.remainingMeters, TOLERANCE_METERS)
    }

    @Test
    fun `a fix back on the line after a detour counts where it rejoined`() {
        val previous = line.follow(RoutePoint(0.0, 0.05), LinePosition.Start)

        val progress = line.progressFrom(RoutePoint(0.0001, 0.15), previous)

        assertEquals(5_500.0, progress.remainingMeters, TOLERANCE_METERS)
    }

    @Test
    fun `after a detour on the way back along the same road the way back goes on`() {
        // Out and back on one carriageway, 5.5 km from the end: a fix 67 m off the road is as far from the way out
        // as from the way back. Taken for the way out, it would turn every fix after it into a step back along the
        // way out, and the distance left would grow all the way to the destination.
        val sameRoad = RouteLine(fakeSameRoadOutAndBackRoute())
        val beforeDetour = sameRoad.follow(RoutePoint(0.0, 0.05), MIDWAY)
        val onDetour = sameRoad.follow(RoutePoint(0.0006, 0.0499), beforeDetour)

        val rejoined = sameRoad.follow(RoutePoint(0.0, 0.0498), onDetour)

        assertEquals(0.0498 * METERS_PER_DEGREE, sameRoad.progressAt(rejoined).remainingMeters, TOLERANCE_METERS)
    }

    @Test
    fun `a fix off the same road counts on the pass the last fix was on`() {
        val sameRoad = RouteLine(fakeSameRoadOutAndBackRoute())
        val detour = RoutePoint(0.0006, 0.05)

        val wayOut = sameRoad.follow(detour, LinePosition(0.049 * METERS_PER_DEGREE))
        val wayBack = sameRoad.follow(detour, LinePosition(0.149 * METERS_PER_DEGREE))

        assertEquals(0.05 * METERS_PER_DEGREE, wayOut.travelledMeters, TOLERANCE_METERS)
        assertEquals(0.15 * METERS_PER_DEGREE, wayBack.travelledMeters, TOLERANCE_METERS)
    }

    @Test
    fun `a fix exactly as far from two passes counts on the one the last fix was on`() {
        // Midway between two parallel roads 222 m apart: 111 m from each, to the last bit.
        val parallel = RouteLine(fakeParallelRoadsRoute())
        val midway = RoutePoint(0.001, 0.05)

        val wayOut = parallel.follow(midway, LinePosition(0.049 * METERS_PER_DEGREE))
        val wayBack = parallel.follow(midway, LinePosition((0.1 + 0.002 + 0.049) * METERS_PER_DEGREE))

        assertEquals(0.05 * METERS_PER_DEGREE, wayOut.travelledMeters, TOLERANCE_METERS)
        assertEquals((0.1 + 0.002 + 0.05) * METERS_PER_DEGREE, wayBack.travelledMeters, TOLERANCE_METERS)
    }

    @Test
    fun `the follow flow starts with the whole route and carries each fix to the next`() =
        runTest {
            val outAndBack = fakeOutAndBackRoute()
            val fixes =
                flowOf(
                    null,
                    fakeRouteFix(RoutePoint(0.0, 0.029), elapsedMs = 0L),
                    fakeRouteFix(RoutePoint(0.00015, 0.03), elapsedMs = 1_000L),
                )

            val progress = outAndBack.followFlow(fixes).toList().map { it.progress }

            assertEquals(3, progress.size)
            assertEquals(RouteProgress(OUT_AND_BACK_METERS.toDouble(), OUT_AND_BACK_SECONDS.toDouble()), progress[0])
            assertEquals(OUT_AND_BACK_LEFT_AT_0_03_OUT, progress[2].remainingMeters, TOLERANCE_METERS)
        }

    // --- Leaving the line ---------------------------------------------------

    @Test
    fun `offLineMeters is zero on the line and the perpendicular distance beside it`() {
        val beside = RoutePoint(latitude = 100.0 / METERS_PER_DEGREE, longitude = 0.1)

        assertEquals(0.0, line.offLineMeters(RoutePoint(0.0, 0.1)), TOLERANCE_METERS)
        assertEquals(100.0, line.offLineMeters(beside), TOLERANCE_METERS)
    }

    @Test
    fun `followFlow emits LeftRoute from the fixes`() =
        runTest {
            // On the line at 0 s, then about 110 m north of it, moving, a fix a second for six seconds.
            val onLine = fakeRouteFix(RoutePoint(0.0, 0.05), elapsedMs = 0L)
            val offLine = (1..6).map { second -> fakeRouteFix(RoutePoint(0.001, 0.05), elapsedMs = second * 1_000L) }

            val events = route.followFlow((listOf(onLine) + offLine).asFlow()).toList().mapNotNull { it.event }

            assertEquals(listOf<FollowEvent>(FollowEvent.LeftRoute(offLine.last())), events)
        }

    private companion object {
        const val TOLERANCE_METERS = 1.0
        const val TOLERANCE_SECONDS = 0.1

        // A degree of the equator, in the metres RouteLine measures in.
        const val METERS_PER_DEGREE = 6_371_008.8 * PI / 180.0

        // The equator route's distance per metre of its line (22 km over 0.2°).
        const val EQUATOR_SCALE = 22_000.0 / (0.2 * METERS_PER_DEGREE)

        // fakeOutAndBackRoute's distance and time, and the distance left at 0.03° east on each pass.
        const val OUT_AND_BACK_METERS = 22_261
        const val OUT_AND_BACK_SECONDS = 1_200
        const val OUT_AND_BACK_LEFT_AT_0_03_OUT = OUT_AND_BACK_METERS - 0.03 * METERS_PER_DEGREE
        const val OUT_AND_BACK_LEFT_AT_0_03_BACK = 0.03 * METERS_PER_DEGREE

        // Halfway along fakeOutAndBackRoute and fakeSameRoadOutAndBackRoute: at the turn.
        val MIDWAY = LinePosition(0.1 * METERS_PER_DEGREE)
    }
}

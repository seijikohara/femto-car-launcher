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
import kotlin.math.IEEErem
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    // --- Points of the line --------------------------------------------------
    //
    // The expected points come from the projection's definition, not from pointAt: the plane is an affine map of the
    // degrees (longitude scaled by the cosine of the line's middle latitude, latitude by a degree's length), so a share
    // of a leg's length in the plane is the same share of the leg in degrees.

    @Test
    fun `pointAt gives the start, a point of the route, a place within a leg and the end of the line`() {
        assertPoint(RoutePoint(0.0, 0.0), line.pointAt(LinePosition.Start))
        assertPoint(RoutePoint(0.0, 0.1), line.pointAt(LinePosition(0.1 * METERS_PER_DEGREE)))
        assertPoint(RoutePoint(0.0, 0.15), line.pointAt(LinePosition(0.15 * METERS_PER_DEGREE)))
        assertPoint(RoutePoint(0.0, 0.2), line.pointAt(LinePosition(0.2 * METERS_PER_DEGREE)))
    }

    @Test
    fun `pointAt follows a leg north and a leg east away from the equator`() {
        // 0.2° north, then 0.3° east, around 10.1° north, where a degree of longitude is cos(10.1°) of one of latitude.
        val northThenEast =
            RouteLine(
                fakeRoute(points = listOf(RoutePoint(10.0, 20.0), RoutePoint(10.2, 20.0), RoutePoint(10.2, 20.3))),
            )
        val northLeg = 0.2 * METERS_PER_DEGREE
        val eastDegree = cos(10.1 * PI / 180.0) * METERS_PER_DEGREE

        assertPoint(RoutePoint(10.05, 20.0), northThenEast.pointAt(LinePosition(0.05 * METERS_PER_DEGREE)))
        assertPoint(RoutePoint(10.2, 20.0), northThenEast.pointAt(LinePosition(northLeg)))
        assertPoint(RoutePoint(10.2, 20.1), northThenEast.pointAt(LinePosition(northLeg + 0.1 * eastDegree)))
        assertPoint(RoutePoint(10.2, 20.3), northThenEast.pointAt(LinePosition(northLeg + 0.3 * eastDegree)))
    }

    @Test
    fun `pointAt on a line of two points gives the point at that share of it`() {
        // 0.1° north and 0.2° east, around 35.05° north.
        val diagonal = RouteLine(fakeRoute(points = listOf(RoutePoint(35.0, 139.0), RoutePoint(35.1, 139.2))))
        val length = hypot(0.2 * cos(35.05 * PI / 180.0) * METERS_PER_DEGREE, 0.1 * METERS_PER_DEGREE)

        assertPoint(RoutePoint(35.025, 139.05), diagonal.pointAt(LinePosition(0.25 * length)))
        assertPoint(RoutePoint(35.05, 139.1), diagonal.pointAt(LinePosition(0.5 * length)))
        assertPoint(RoutePoint(35.1, 139.2), diagonal.pointAt(LinePosition(length)))
    }

    @Test
    fun `pointAt on a line across the antimeridian gives longitudes from -180 to 180`() {
        val alongEquator = RouteLine(fakeRoute(points = listOf(RoutePoint(0.0, 179.9), RoutePoint(0.0, -179.9))))
        // 0.4° east and 0.2° north, around 16.8° south, from 179.8° east to 179.8° west.
        val diagonal = RouteLine(fakeRoute(points = listOf(RoutePoint(-16.9, 179.8), RoutePoint(-16.7, -179.8))))
        val diagonalLength = hypot(0.4 * cos(-16.8 * PI / 180.0) * METERS_PER_DEGREE, 0.2 * METERS_PER_DEGREE)

        val points =
            listOf(
                RoutePoint(0.0, 179.95) to alongEquator.pointAt(LinePosition(0.05 * METERS_PER_DEGREE)),
                RoutePoint(0.0, -179.95) to alongEquator.pointAt(LinePosition(0.15 * METERS_PER_DEGREE)),
                RoutePoint(0.0, -179.9) to alongEquator.pointAt(LinePosition(0.2 * METERS_PER_DEGREE)),
                RoutePoint(-16.85, 179.9) to diagonal.pointAt(LinePosition(0.25 * diagonalLength)),
                RoutePoint(-16.75, -179.9) to diagonal.pointAt(LinePosition(0.75 * diagonalLength)),
            )

        points.forEach { (expected, actual) ->
            assertTrue(actual.longitude in -180.0..180.0, "$actual")
            assertPoint(expected, actual)
        }
    }

    @Test
    fun `pointAt past either end gives that end`() {
        assertPoint(RoutePoint(0.0, 0.0), line.pointAt(LinePosition(-10.0)))
        assertPoint(RoutePoint(0.0, 0.2), line.pointAt(LinePosition(1.0 * METERS_PER_DEGREE)))
    }

    @Test
    fun `every position the follow finds lies on the line`() {
        // Fixes up to about 220 m from the line, on each of the lines above; the nearest point of the whole line to
        // each matched point is that point.
        val lines =
            listOf(
                fakeEquatorRoute(),
                fakeOutAndBackRoute(),
                fakeRoute(points = listOf(RoutePoint(10.0, 20.0), RoutePoint(10.2, 20.0), RoutePoint(10.2, 20.3))),
                fakeRoute(points = listOf(RoutePoint(35.0, 139.0), RoutePoint(35.1, 139.2))),
                fakeRoute(points = listOf(RoutePoint(-16.9, 179.8), RoutePoint(-16.7, -179.8))),
            )
        val random = Random(SEED)

        lines.forEach { route ->
            val line = RouteLine(route)
            route.points.zipWithNext().forEach { (from, to) ->
                repeat(FIXES_PER_LEG) {
                    val share = random.nextDouble()
                    val fix =
                        RoutePoint(
                            latitude = from.latitude + share * (to.latitude - from.latitude) + random.nearby(),
                            longitude =
                                from.longitude + share * shortWay(from.longitude, to.longitude) + random.nearby(),
                        )
                    val point = line.pointAt(line.follow(fix, LinePosition.Start))

                    assertTrue(point.longitude in -180.0..180.0, "$point")
                    assertEquals(0.0, line.offLineMeters(point), ON_LINE_TOLERANCE_METERS, "$fix on ${route.points}")
                }
            }
        }
    }

    @Test
    fun `distanceMeters is how far a fix lies from the point of the line at a position`() {
        val beside = RoutePoint(latitude = 10.0 / METERS_PER_DEGREE, longitude = 0.15)

        assertEquals(10.0, line.distanceMeters(LinePosition(0.15 * METERS_PER_DEGREE), beside), 1e-6)
    }

    @Test
    fun `travelledAt gives the length of line to each point`() {
        assertEquals(0.0, line.travelledAt(0).travelledMeters, 1e-6)
        assertEquals(0.1 * METERS_PER_DEGREE, line.travelledAt(1).travelledMeters, 1e-6)
        assertEquals(0.2 * METERS_PER_DEGREE, line.travelledAt(2).travelledMeters, 1e-6)
    }

    // --- Drawing the vehicle on the line ------------------------------------

    @Test
    fun `before the first fix nothing is drawn on the line`() =
        runTest {
            assertNull(
                route
                    .followFlow(flowOf(null))
                    .toList()
                    .single()
                    .onLine,
            )
        }

    @Test
    fun `a fix 10 m beside the line is drawn at the point of the line it was matched to`() =
        runTest {
            val onLine = followOne(route, besideTheLine(meters = 10.0, accuracyMeters = null)).onLine

            assertPoint(RoutePoint(0.0, 0.05), assertNotNull(onLine).point)
        }

    // A road beside the route: a good fix shows the vehicle where it is, not on a road it is not on.
    @Test
    fun `a fix on a parallel road 30 m away with an accuracy of 10 m is drawn where it is`() =
        runTest {
            assertNull(followOne(route, besideTheLine(meters = 30.0, accuracyMeters = 10f)).onLine)
        }

    @Test
    fun `a fix 30 m away with an accuracy of 40 m is drawn on the line`() =
        runTest {
            val onLine = followOne(route, besideTheLine(meters = 30.0, accuracyMeters = 40f)).onLine

            assertPoint(RoutePoint(0.0, 0.05), assertNotNull(onLine).point)
        }

    @Test
    fun `a fix 60 m away is drawn where it is, however poor its accuracy`() =
        runTest {
            assertNull(followOne(route, besideTheLine(meters = 60.0, accuracyMeters = 100f)).onLine)
        }

    @Test
    fun `a fix without an accuracy counts as an exact one`() =
        runTest {
            assertNull(followOne(route, besideTheLine(meters = 25.0, accuracyMeters = null)).onLine)
        }

    // Once on the line, the vehicle stays until its fix is 10 m beyond the gate it joined by: a fix at the gate's edge
    // must not throw the vehicle 20 m sideways and back every second.
    @Test
    fun `a vehicle drawn on the line stays on it while its fix is less than 10 m beyond the gate`() =
        runTest {
            val onLine = followAfterTheLine(besideTheLine(meters = 25.0, accuracyMeters = 10f, second = 1)).onLine

            assertPoint(RoutePoint(0.0, 0.05), assertNotNull(onLine).point)
        }

    @Test
    fun `a vehicle not drawn on the line does not join it beyond the gate`() =
        runTest {
            assertNull(followOne(route, besideTheLine(meters = 25.0, accuracyMeters = 10f)).onLine)
        }

    @Test
    fun `a vehicle drawn on the line leaves it more than 10 m beyond the gate`() =
        runTest {
            assertNull(followAfterTheLine(besideTheLine(meters = 31.0, accuracyMeters = 10f, second = 1)).onLine)
        }

    @Test
    fun `past 50 m the vehicle is drawn at the fix, whether it was on the line or not`() =
        runTest {
            // An accuracy of 45 m would put the gate at 45 m and the way off the line at 55 m.
            val fix = besideTheLine(meters = 52.0, accuracyMeters = 45f, second = 1)

            assertNull(followAfterTheLine(fix).onLine)
            assertNull(followOne(route, fix).onLine)
        }

    // The map hears from every fix drawn on the line through these: a fix that matches the point the last one did must
    // not read as no news, or the page greys the vehicle as if the signal were lost and its heading stops turning.
    @Test
    fun `fixes past the end of the line that all match its end each report their own stamp`() =
        runTest {
            // A second and a metre apart, 3 to 5 m east of the line's end.
            val fixes =
                (0..2).map { second ->
                    fakeRouteFix(
                        RoutePoint(0.0, 0.2 + (3.0 + second) / METERS_PER_DEGREE),
                        elapsedMs = second * 1_000L,
                    )
                }

            val drawn = route
                .followFlow(fixes.asFlow())
                .toList()
                .drop(1)
                .map { assertNotNull(it.onLine) }

            assertEquals(listOf(0L, 1_000L, 2_000L), drawn.map { it.fixElapsedMs })
            drawn.forEach { assertPoint(RoutePoint(0.0, 0.2), it.point) }
        }

    @Test
    fun `fixes on the outside of a corner that all match its point each report their own stamp`() =
        runTest {
            // North along the meridian to 0.1°, then east. North-west of the corner, every point of the line nearest a
            // fix is the corner itself.
            val corner = fakeRoute(points = listOf(RoutePoint(0.0, 0.0), RoutePoint(0.1, 0.0), RoutePoint(0.1, 0.1)))
            val fixes =
                (0..2).map { second ->
                    val offset = 0.00003 * (1 + second)
                    fakeRouteFix(RoutePoint(0.1 + offset, -offset), elapsedMs = second * 1_000L)
                }

            val drawn = corner
                .followFlow(fixes.asFlow())
                .toList()
                .drop(1)
                .map { assertNotNull(it.onLine) }

            assertEquals(listOf(0L, 1_000L, 2_000L), drawn.map { it.fixElapsedMs })
            drawn.forEach { assertPoint(RoutePoint(0.1, 0.0), it.point) }
        }

    // A route kept by an earlier build carries no turn data; the vehicle goes on the line all the same.
    @Test
    fun `a route without turn data still draws the vehicle on the line`() =
        runTest {
            val follow = followOne(route.copy(turns = null), besideTheLine(meters = 10.0, accuracyMeters = null))

            assertPoint(RoutePoint(0.0, 0.05), assertNotNull(follow.onLine).point)
        }

    // --- The turns ahead ----------------------------------------------------

    @Test
    fun `before the first fix the turns count from the start of the line`() =
        runTest {
            val turns = TURNING_ROUTE
                .followFlow(flowOf(null))
                .toList()
                .single()
                .upcomingTurns

            assertEquals(
                listOf(11_000.0, 22_000.0),
                assertNotNull(turns).map { it.distanceMeters.roundTo(MILLIMETERS) },
            )
        }

    @Test
    fun `the turns ahead shrink as the vehicle passes them, by their distance along the line`() =
        runTest {
            // A quarter of the way, then three quarters: the turn halfway is behind by then. The route's 22 km over its
            // line's 22.2 km scale each distance, as they scale the distance left.
            val fixes =
                flowOf(
                    fakeRouteFix(RoutePoint(0.0, 0.05), elapsedMs = 0L),
                    fakeRouteFix(RoutePoint(0.0, 0.15), elapsedMs = 1_000L),
                )

            val turns = TURNING_ROUTE
                .followFlow(fixes)
                .toList()
                .drop(1)
                .map { assertNotNull(it.upcomingTurns) }

            assertEquals(
                listOf(
                    listOf(HALFWAY_TURN to 5_500.0, END_TURN to 16_500.0),
                    listOf(END_TURN to 5_500.0),
                ),
                turns.map { ahead -> ahead.map { it.turn to it.distanceMeters.roundTo(MILLIMETERS) } },
            )
        }

    @Test
    fun `a turn at the end of the route is as far as the distance left`() =
        runTest {
            val follow = followOne(TURNING_ROUTE, fakeRouteFix(RoutePoint(0.0, 0.123), elapsedMs = 0L))

            assertEquals(follow.progress.remainingMeters, assertNotNull(follow.upcomingTurns).last().distanceMeters)
        }

    @Test
    fun `a turn stays ahead at no distance until the vehicle is 15 m past it`() =
        runTest {
            val past14 = followOne(TURNING_ROUTE, pastTheHalfwayTurn(meters = 14.0))
            val past16 = followOne(TURNING_ROUTE, pastTheHalfwayTurn(meters = 16.0))

            assertEquals(UpcomingTurn(HALFWAY_TURN, 0.0), assertNotNull(past14.upcomingTurns).first())
            assertEquals(listOf(END_TURN), assertNotNull(past16.upcomingTurns).map { it.turn })
        }

    // Stopped just past a turn, GPS noise moves the fix back within the follower's backtrack; the turn must not come
    // back, at no distance, as if the vehicle had yet to take it.
    @Test
    fun `a turn once passed stays passed while the fix drifts a little back`() =
        runTest {
            val fixes = flowOf(pastTheHalfwayTurn(meters = 16.0), pastTheHalfwayTurn(meters = 14.0, second = 1))

            val turns = TURNING_ROUTE
                .followFlow(fixes)
                .toList()
                .last()
                .upcomingTurns

            assertEquals(listOf(END_TURN), assertNotNull(turns).map { it.turn })
        }

    // Three quarters of the way, then back at a quarter: 5.5 km back, farther than the follower's backtrack, as only a
    // real jump back goes (a detour, a vehicle that turned round). The turns ahead start again from there.
    @Test
    fun `after a jump back farther than the backtrack the passed turns come back`() =
        runTest {
            val fixes =
                flowOf(
                    fakeRouteFix(RoutePoint(0.0, 0.15), elapsedMs = 0L),
                    fakeRouteFix(RoutePoint(0.0, 0.05), elapsedMs = 1_000L),
                )

            val turns = TURNING_ROUTE
                .followFlow(fixes)
                .toList()
                .last()
                .upcomingTurns

            assertEquals(
                listOf(HALFWAY_TURN to 5_500.0, END_TURN to 16_500.0),
                assertNotNull(turns).map { it.turn to it.distanceMeters.roundTo(MILLIMETERS) },
            )
        }

    @Test
    fun `a route without turn data has no turns ahead, and one without turns an empty list`() =
        runTest {
            val fix = fakeRouteFix(RoutePoint(0.0, 0.05), elapsedMs = 0L)

            assertNull(followOne(route.copy(turns = null), fix).upcomingTurns)
            assertEquals(emptyList(), followOne(route.copy(turns = emptyList()), fix).upcomingTurns)
        }

    // --- Off the route ------------------------------------------------------

    @Test
    fun `the vehicle is off the route from leaving it, through a repeat of it, until it is back`() =
        runTest {
            // Moving east at 10 m/s: on the line at 0 s, about 110 m north of it from 1 s to 11 s, on it again from
            // 12 s. The watch says the vehicle left at 6 s, says so again at 11 s, and has it back on at 14 s.
            val fixes =
                (0..14).map { second ->
                    fakeRouteFix(
                        RoutePoint(
                            latitude = if (second in 1..11) 0.001 else 0.0,
                            longitude = 0.05 + second * 10.0 / METERS_PER_DEGREE,
                        ),
                        elapsedMs = second * 1_000L,
                    )
                }

            val follows = route.followFlow(fixes.asFlow()).toList().drop(1)

            assertEquals(
                listOf(6 to true, 11 to true, 14 to false),
                follows.mapIndexedNotNull { second, follow ->
                    follow.event?.let { second to (it is FollowEvent.LeftRoute) }
                },
            )
            assertEquals((0..14).map { it in 6..13 }, follows.map { it.offRoute })
        }

    // What the follow reports at [fix], the one fix on [route].
    private suspend fun followOne(
        route: Route,
        fix: RouteFix,
    ): RouteFollow = route.followFlow(flowOf(fix)).toList().last()

    // What the follow reports at [fix] on the equator route, a second after a fix on the line drew the vehicle there.
    private suspend fun followAfterTheLine(fix: RouteFix): RouteFollow =
        route.followFlow(flowOf(besideTheLine(meters = 0.0, accuracyMeters = null), fix)).toList().last()

    // A fix [meters] north of the equator route a quarter of the way along it, taken at [second] on the boot clock.
    private fun besideTheLine(
        meters: Double,
        accuracyMeters: Float?,
        second: Int = 0,
    ): RouteFix =
        fakeRouteFix(
            RoutePoint(meters / METERS_PER_DEGREE, 0.05),
            elapsedMs = second * 1_000L,
            accuracyMeters = accuracyMeters,
        )

    // A fix on the line [meters] past the equator route's halfway point, taken at [second] on the boot clock.
    private fun pastTheHalfwayTurn(
        meters: Double,
        second: Int = 0,
    ): RouteFix = fakeRouteFix(RoutePoint(0.0, 0.1 + meters / METERS_PER_DEGREE), elapsedMs = second * 1_000L)

    private fun Double.roundTo(step: Double): Double = (this / step).roundToLong() * step

    // The two points within a micro-degree on each axis; longitudes compared the short way round.
    private fun assertPoint(
        expected: RoutePoint,
        actual: RoutePoint,
    ) {
        assertEquals(expected.latitude, actual.latitude, TOLERANCE_DEGREES, "latitude of $actual")
        assertEquals(0.0, shortWay(expected.longitude, actual.longitude), TOLERANCE_DEGREES, "longitude of $actual")
    }

    // How far east [to] lies from [from], from -180 to 180 degrees, without rounding away a small difference.
    private fun shortWay(
        from: Double,
        to: Double,
    ): Double = (to - from).IEEErem(360.0)

    // Up to 0.002° (about 220 m) either way.
    private fun Random.nearby(): Double = nextDouble(-0.002, 0.002)

    private companion object {
        const val TOLERANCE_METERS = 1.0
        const val TOLERANCE_SECONDS = 0.1

        // A micro-degree, the precision of an engine route's points.
        const val TOLERANCE_DEGREES = 1e-6

        // A point of the line lies on it to the last bits of the arithmetic.
        const val ON_LINE_TOLERANCE_METERS = 1e-6

        const val SEED = 389L
        const val FIXES_PER_LEG = 200

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

        // The equator route with a turn halfway, at its middle point, and one at its end.
        val HALFWAY_TURN = RouteTurn(pointIndex = 1, kind = TurnKind.LEFT, exitNumber = 0, angleDegrees = -90)
        val END_TURN = RouteTurn(pointIndex = 2, kind = TurnKind.RIGHT, exitNumber = 0, angleDegrees = 90)
        val TURNING_ROUTE = fakeEquatorRoute().copy(turns = listOf(HALFWAY_TURN, END_TURN))

        // Distances compared to the millimetre, past the arithmetic's last bits.
        const val MILLIMETERS = 0.001
    }
}

package io.github.seijikohara.femto.data.routing

import io.github.seijikohara.femto.testfixtures.fakeRouteFix
import org.junit.Test
import kotlin.test.assertEquals

class OffRouteWatchTest {
    @Test
    fun `a start away from the line does not count as leaving it`() {
        assertEquals(List(10) { null }, events(List(10) { Reading(offLineMeters = 120.0) }))
    }

    @Test
    fun `once on the line, five seconds off it while moving raise LeftRoute`() {
        // On the line at 0 s, off it from 1 s: held 4 s at 5 s, 5 s at 6 s.
        val events = events(listOf(ON_LINE) + List(6) { OFF_LINE })

        assertEquals(List(6) { null }, events.take(6))
        assertEquals(FollowEvent.LeftRoute(fixAt(6)), events[6])
    }

    @Test
    fun `the watch arms after thirty seconds of moving even off the line`() {
        val events = events(List(31) { Reading(offLineMeters = 120.0) })

        assertEquals(List(30) { null }, events.take(30))
        assertEquals(FollowEvent.LeftRoute(fixAt(30)), events[30])
    }

    @Test
    fun `a stop resets the hold`() {
        // Six seconds off the line in all, with a stop at 4 s.
        val readings = listOf(ON_LINE) + List(3) { OFF_LINE } + OFF_LINE.copy(speed = 0f) + List(3) { OFF_LINE }

        assertEquals(List(8) { null }, events(readings))
    }

    @Test
    fun `a poor fix widens the threshold`() {
        // 40 m of accuracy makes the threshold 80 m.
        val near = events(listOf(ON_LINE) + List(7) { Reading(offLineMeters = 70.0, accuracy = 40f) })
        val far = events(listOf(ON_LINE) + List(6) { Reading(offLineMeters = 90.0, accuracy = 40f) })

        assertEquals(List(8) { null }, near)
        assertEquals(FollowEvent.LeftRoute(fixAt(6, accuracy = 40f)), far.last())
    }

    // A car park beside the road: 120 m from the line is inside the 130 m that 65 m of accuracy allows.
    @Test
    fun `a poor fix near the line does not arm the watch`() {
        val parked = Reading(offLineMeters = 120.0, speed = 0f, accuracy = 65f)
        val readings = listOf(parked) + List(10) { Reading(offLineMeters = 110.0, accuracy = 5f) }

        assertEquals(List(11) { null }, events(readings))
    }

    @Test
    fun `a fix without speed never counts as moving`() {
        assertEquals(List(11) { null }, events(listOf(ON_LINE) + List(10) { OFF_LINE.copy(speed = null) }))
    }

    // The location stack interleaves network fixes, which carry no speed and a wide accuracy, with the GPS ones.
    @Test
    fun `a fix without speed between moving ones leaves the hold running`() {
        val networkFix = Reading(offLineMeters = 0.0, speed = null, accuracy = 500f)
        val readings = listOf(ON_LINE) + List(3) { OFF_LINE } + networkFix + List(2) { OFF_LINE }

        assertEquals(FollowEvent.LeftRoute(fixAt(6)), events(readings).last())
    }

    @Test
    fun `near the end of the line the watch stays quiet`() {
        val readings = listOf(ON_LINE) + List(10) { OFF_LINE.copy(remainingMeters = 250.0) }

        assertEquals(List(11) { null }, events(readings))
    }

    @Test
    fun `still off the route, it raises LeftRoute again after another hold`() {
        val events = events(listOf(ON_LINE) + List(11) { OFF_LINE })

        assertEquals(
            listOf(6 to FollowEvent.LeftRoute(fixAt(6)), 11 to FollowEvent.LeftRoute(fixAt(11))),
            events.withIndex().mapNotNull { (second, event) -> event?.let { second to it } },
        )
    }

    @Test
    fun `two seconds back on the line after LeftRoute raise BackOnRoute once`() {
        // Left at 6 s; on the line again from 7 s.
        val events = events(listOf(ON_LINE) + List(6) { OFF_LINE } + List(4) { ON_LINE })

        assertEquals(listOf(null, null, FollowEvent.BackOnRoute, null), events.drop(7))
    }

    @Test
    fun `a single fix back on the line raises no BackOnRoute`() {
        val events = events(listOf(ON_LINE) + List(6) { OFF_LINE } + ON_LINE + OFF_LINE)

        assertEquals(listOf(null, null), events.drop(7))
    }

    // 40 m of accuracy widens the threshold to 80 m; 70 m is inside it but outside the 50 m floor.
    @Test
    fun `poor fixes inside the widened threshold raise no BackOnRoute`() {
        val poor = Reading(offLineMeters = 70.0, accuracy = 40f)
        val events = events(listOf(ON_LINE) + List(6) { OFF_LINE } + List(3) { poor })

        assertEquals(listOf(null, null, null), events.drop(7))
    }

    // A tunnel, or the old fix a new subscription replays first: the hold counts from the first fix after the gap.
    @Test
    fun `a gap in the fixes restarts the hold`() {
        val seconds = listOf(0, 1, 2) + (20..25)
        val readings = seconds.map { second -> second to if (second == 0) ON_LINE else OFF_LINE }

        val events = timedEvents(readings)

        assertEquals(List(8) { null }, events.take(8))
        assertEquals(FollowEvent.LeftRoute(fixAt(25)), events.last())
    }

    // What the watch reads at one fix besides the fix's position, which it never reads.
    private data class Reading(
        val offLineMeters: Double,
        val speed: Float? = MOVING_SPEED,
        val accuracy: Float? = null,
        val remainingMeters: Double = FAR_FROM_THE_END_METERS,
    )

    // Each reading a second after the one before, from 0 s.
    private fun events(readings: List<Reading>): List<FollowEvent?> =
        timedEvents(readings.mapIndexed { second, reading -> second to reading })

    private fun timedEvents(readings: List<Pair<Int, Reading>>): List<FollowEvent?> =
        readings
            .runningFold(OffRouteWatch()) { watch, (second, reading) ->
                watch.advance(
                    fixAt(second, speed = reading.speed, accuracy = reading.accuracy),
                    offLineMeters = reading.offLineMeters,
                    remainingMeters = reading.remainingMeters,
                )
            }.drop(1)
            .map { it.event }

    private fun fixAt(
        second: Int,
        speed: Float? = MOVING_SPEED,
        accuracy: Float? = null,
    ): RouteFix =
        fakeRouteFix(POINT, elapsedMs = second * 1_000L, speedMetersPerSecond = speed, accuracyMeters = accuracy)

    private companion object {
        const val MOVING_SPEED = 10f
        const val FAR_FROM_THE_END_METERS = 10_000.0
        val POINT = RoutePoint(latitude = 0.0, longitude = 0.0)
        val ON_LINE = Reading(offLineMeters = 5.0)
        val OFF_LINE = Reading(offLineMeters = 60.0)
    }
}

package io.github.seijikohara.femto.ui.home

import io.github.seijikohara.femto.data.routing.RerouteStatus
import io.github.seijikohara.femto.data.routing.RouteTurn
import io.github.seijikohara.femto.data.routing.TurnKind
import io.github.seijikohara.femto.data.routing.UpcomingTurn
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the turn card shows, worked out from the follower's turns ahead, the reroute and choice state and the settings. */
class TurnCardStateTest {
    @Test
    fun `the first turn ahead is the manoeuvre, at its distance`() {
        assertEquals(
            TurnCardState(maneuver = TurnManeuver.Turn(TurnKind.LEFT), distanceMeters = 300.0),
            stateOrNull(listOf(upcoming(TurnKind.LEFT, 300.0), upcoming(TurnKind.RIGHT, 900.0))),
        )
    }

    @Test
    fun `the card is off when the setting is off`() {
        assertNull(stateOrNull(settings = TurnCardSettings(show = false)))
    }

    @Test
    fun `a route without turn data shows no card, not the destination`() {
        assertNull(stateOrNull(upcomingTurns = null))
    }

    // Before the first fix the turns count from the start of the line, which a restored route can lie far behind.
    @Test
    fun `the card hides until a fix has been placed on the line`() {
        assertNull(stateOrNull(fixPlaced = false))
    }

    @Test
    fun `the card hides while the vehicle is off the route`() {
        assertNull(stateOrNull(offRoute = true))
    }

    @Test
    fun `the card hides while a reroute's status shows`() {
        assertEquals(
            listOf(null, null, null),
            listOf(RerouteStatus.REROUTING, RerouteStatus.NO_DATA, RerouteStatus.FAILED).map {
                stateOrNull(reroute = it)
            },
        )
    }

    @Test
    fun `the card hides while the route choice card is open`() {
        assertNull(stateOrNull(choosing = true))
    }

    @Test
    fun `continue straight is dropped by default`() {
        assertEquals(
            TurnManeuver.Turn(TurnKind.RIGHT),
            stateOrNull(listOf(upcoming(TurnKind.STRAIGHT, 100.0), upcoming(TurnKind.RIGHT, 500.0)))?.maneuver,
        )
    }

    @Test
    fun `continue straight is kept when the setting asks for it`() {
        assertEquals(
            TurnManeuver.Turn(TurnKind.STRAIGHT),
            stateOrNull(
                listOf(upcoming(TurnKind.STRAIGHT, 100.0), upcoming(TurnKind.RIGHT, 500.0)),
                settings = TurnCardSettings(showStraight = true),
            )?.maneuver,
        )
    }

    @Test
    fun `off-route and beeline hints are never announced`() {
        assertEquals(
            TurnManeuver.Turn(TurnKind.LEFT),
            stateOrNull(
                listOf(
                    upcoming(TurnKind.OFF_ROUTE, 50.0),
                    upcoming(TurnKind.BEELINE, 80.0),
                    upcoming(TurnKind.LEFT, 400.0),
                ),
                settings = TurnCardSettings(showStraight = true),
            )?.maneuver,
        )
    }

    @Test
    fun `with no turn left the card shows the destination at the distance left`() {
        assertEquals(
            TurnCardState(maneuver = TurnManeuver.Arrive, distanceMeters = 1_250.0),
            stateOrNull(emptyList(), remainingMeters = 1_250.0),
        )
    }

    @Test
    fun `only dropped hints left counts as no turn left`() {
        assertEquals(
            TurnManeuver.Arrive,
            stateOrNull(listOf(upcoming(TurnKind.STRAIGHT, 100.0)), remainingMeters = 400.0)?.maneuver,
        )
    }

    @Test
    fun `a turn within the then distance of the first is the turn after next`() {
        assertEquals(
            TurnManeuver.Turn(TurnKind.RIGHT),
            stateOrNull(
                listOf(upcoming(TurnKind.LEFT, 300.0), upcoming(TurnKind.RIGHT, 300.0 + THEN_WITHIN_METERS)),
            )?.then,
        )
    }

    @Test
    fun `a turn beyond the then distance of the first is not shown`() {
        assertNull(
            stateOrNull(
                listOf(upcoming(TurnKind.LEFT, 300.0), upcoming(TurnKind.RIGHT, 301.0 + THEN_WITHIN_METERS)),
            )?.then,
        )
    }

    @Test
    fun `the turn after next is off when the setting is off`() {
        assertNull(
            stateOrNull(
                listOf(upcoming(TurnKind.LEFT, 300.0), upcoming(TurnKind.RIGHT, 320.0)),
                settings = TurnCardSettings(showNextAfter = false),
            )?.then,
        )
    }

    // A crossing whose two roads are offset: the engine reports a left and a right 20 m apart. One card with both.
    @Test
    fun `a staggered crossing gives one card with the turn after next`() {
        assertEquals(
            TurnCardState(
                maneuver = TurnManeuver.Turn(TurnKind.LEFT),
                distanceMeters = 180.0,
                then = TurnManeuver.Turn(TurnKind.RIGHT),
            ),
            stateOrNull(listOf(upcoming(TurnKind.LEFT, 180.0), upcoming(TurnKind.RIGHT, 200.0))),
        )
    }

    @Test
    fun `the turn after next skips a dropped hint`() {
        assertEquals(
            TurnManeuver.Turn(TurnKind.RIGHT),
            stateOrNull(
                listOf(
                    upcoming(TurnKind.LEFT, 180.0),
                    upcoming(TurnKind.STRAIGHT, 190.0),
                    upcoming(TurnKind.RIGHT, 200.0),
                ),
            )?.then,
        )
    }

    @Test
    fun `a roundabout keeps its exit number`() {
        assertEquals(
            TurnManeuver.Turn(TurnKind.ROUNDABOUT_COUNTERCLOCKWISE, exitNumber = 2),
            stateOrNull(listOf(upcoming(TurnKind.ROUNDABOUT_COUNTERCLOCKWISE, 200.0, exitNumber = 2)))?.maneuver,
        )
    }

    @Test
    fun `a plain U-turn takes its side from the angle's sign, zero counting as left`() {
        assertEquals(
            listOf(true, true, false),
            listOf(-170, 0, 175).map { angle ->
                (
                    stateOrNull(
                        listOf(upcoming(TurnKind.U_TURN, 100.0, angleDegrees = angle)),
                    )?.maneuver as? TurnManeuver.Turn
                )?.leftward
            },
        )
    }

    private fun stateOrNull(
        upcomingTurns: List<UpcomingTurn>? = listOf(upcoming(TurnKind.LEFT, 300.0)),
        remainingMeters: Double = 5_000.0,
        fixPlaced: Boolean = true,
        offRoute: Boolean = false,
        reroute: RerouteStatus = RerouteStatus.NONE,
        choosing: Boolean = false,
        settings: TurnCardSettings = TurnCardSettings(),
    ): TurnCardState? =
        turnCardStateOrNull(
            upcomingTurns = upcomingTurns,
            remainingMeters = remainingMeters,
            fixPlaced = fixPlaced,
            offRoute = offRoute,
            reroute = reroute,
            choosing = choosing,
            settings = settings,
        )

    private fun upcoming(
        kind: TurnKind,
        distanceMeters: Double,
        exitNumber: Int = 0,
        angleDegrees: Int = 0,
    ) = UpcomingTurn(
        turn = RouteTurn(pointIndex = 0, kind = kind, exitNumber = exitNumber, angleDegrees = angleDegrees),
        distanceMeters = distanceMeters,
    )
}

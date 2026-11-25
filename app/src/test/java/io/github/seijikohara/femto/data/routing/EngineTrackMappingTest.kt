package io.github.seijikohara.femto.data.routing

import io.github.seijikohara.femto.testfixtures.fakeEngineTrack
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** What `toRouteResult` makes of the turn hints on a found track; the track has three points. */
class EngineTrackMappingTest {
    @Test
    fun `rows map to turns with kinds, absolute exit numbers and angles`() {
        val turns =
            turnsOf(
                listOf(
                    EngineTurn(positionIndex = 0, command = 5, exitNumber = 0, angleDegrees = 84),
                    EngineTurn(positionIndex = 1, command = 14, exitNumber = -2, angleDegrees = 125),
                    EngineTurn(positionIndex = 2, command = 13, exitNumber = 3, angleDegrees = -236),
                ),
            )

        assertEquals(
            listOf(
                RouteTurn(pointIndex = 0, kind = TurnKind.RIGHT, exitNumber = 0, angleDegrees = 84),
                RouteTurn(pointIndex = 1, kind = TurnKind.ROUNDABOUT_CLOCKWISE, exitNumber = 2, angleDegrees = 125),
                RouteTurn(
                    pointIndex = 2,
                    kind = TurnKind.ROUNDABOUT_COUNTERCLOCKWISE,
                    exitNumber = 3,
                    angleDegrees = -236,
                ),
            ),
            turns,
        )
    }

    @Test
    fun `an unknown command makes the turns null and the route is still found`() {
        listOf(0, 19).forEach { command ->
            val result = fakeEngineTrack(turns = listOf(EngineTurn(1, command, 0, 0))).toRouteResult()

            assertNull(assertIs<RouteResult.Found>(result).route.turns)
        }
    }

    @Test
    fun `one unknown command among known ones makes the whole list null`() {
        assertNull(turnsOf(listOf(EngineTurn(0, 5, 0, 84), EngineTurn(1, 99, 0, 0))))
    }

    @Test
    fun `an index at the end of the positions makes the turns null`() {
        assertNull(turnsOf(listOf(EngineTurn(positionIndex = 3, command = 5, exitNumber = 0, angleDegrees = 84))))
    }

    @Test
    fun `turns the engine could not read stay null`() {
        assertNull(turnsOf(null))
    }

    @Test
    fun `no turn stays an empty list`() {
        assertEquals(emptyList(), turnsOf(emptyList()))
    }

    private fun turnsOf(rows: List<EngineTurn>?): List<RouteTurn>? =
        assertIs<RouteResult.Found>(fakeEngineTrack(turns = rows).toRouteResult()).route.turns
}

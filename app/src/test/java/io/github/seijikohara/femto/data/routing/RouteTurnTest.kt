package io.github.seijikohara.femto.data.routing

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RouteTurnTest {
    @Test
    fun `the kinds carry exactly the engine's command numbers 1 to 18`() {
        assertEquals((1..18).toList(), TurnKind.entries.map { it.code }.sorted())
    }

    @Test
    fun `every kind maps to its code and back`() {
        TurnKind.entries.forEach { kind -> assertEquals(kind, TurnKind.ofCodeOrNull(kind.code)) }
    }

    @Test
    fun `a command number outside 1 to 18 has no kind`() {
        assertNull(TurnKind.ofCodeOrNull(0))
        assertNull(TurnKind.ofCodeOrNull(19))
    }

    @Test
    fun `a list on the line is accepted at its first and last point`() {
        val turns = listOf(turnAt(0), turnAt(POINT_COUNT - 1))

        assertEquals(turns, turns.onLineOrNull(POINT_COUNT))
    }

    @Test
    fun `an empty list is accepted`() {
        assertEquals(emptyList(), emptyList<RouteTurn>().onLineOrNull(POINT_COUNT))
    }

    @Test
    fun `an index before the line is rejected`() {
        assertNull(listOf(turnAt(-1)).onLineOrNull(POINT_COUNT))
    }

    @Test
    fun `an index past the line is rejected`() {
        assertNull(listOf(turnAt(POINT_COUNT)).onLineOrNull(POINT_COUNT))
    }

    @Test
    fun `indexes that go backwards are rejected`() {
        assertNull(listOf(turnAt(4), turnAt(3)).onLineOrNull(POINT_COUNT))
    }

    @Test
    fun `two hints on one point are tolerated`() {
        assertNotNull(listOf(turnAt(4), turnAt(4)).onLineOrNull(POINT_COUNT))
    }

    private fun turnAt(pointIndex: Int) =
        RouteTurn(pointIndex = pointIndex, kind = TurnKind.RIGHT, exitNumber = 0, angleDegrees = 90)

    private companion object {
        const val POINT_COUNT = 10
    }
}

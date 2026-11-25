package io.github.seijikohara.femto.data.routing

import io.github.seijikohara.femto.testfixtures.brouterJson
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BRouterTurnsTest {
    @Test
    fun `reads the rows of a route through a left-hand roundabout`() {
        assertEquals(
            listOf(EngineTurn(5, 14, -2, 125), EngineTurn(36, 5, 0, 91)),
            brouterTurnsOrNull(brouterJson("brouter-left-hand.json")),
        )
    }

    @Test
    fun `reads the rows of a route through a right-hand roundabout`() {
        assertEquals(
            listOf(EngineTurn(1, 5, 0, 84), EngineTurn(4, 3, 0, -55), EngineTurn(16, 13, 3, -236)),
            brouterTurnsOrNull(brouterJson("brouter-right-hand.json")),
        )
    }

    // The engine leaves the key out of a route without a hint, which is no turn rather than unreadable hints.
    @Test
    fun `text without a voicehints key has no turn`() {
        assertEquals(emptyList(), brouterTurnsOrNull(brouterJson("brouter-no-turn.json")))
    }

    @Test
    fun `text that is not json cannot be read`() {
        assertNull(brouterTurnsOrNull("not json"))
    }

    @Test
    fun `an object without features cannot be read`() {
        assertNull(brouterTurnsOrNull("{}"))
    }

    @Test
    fun `an empty feature list cannot be read`() {
        assertNull(brouterTurnsOrNull("""{"features":[]}"""))
    }

    @Test
    fun `a row of four numbers cannot be read`() {
        assertNull(brouterTurnsOrNull(withRows("[1,5,0,63.0]")))
    }

    @Test
    fun `a row with a string in place of the command cannot be read`() {
        assertNull(brouterTurnsOrNull(withRows("""[1,"RIGHT",0,63.0,84]""")))
    }

    @Test
    fun `one unreadable row makes the whole list unreadable`() {
        assertNull(brouterTurnsOrNull(withRows("[1,5,0,63.0,84]", "[4,3,0,105.0]")))
    }

    @Test
    fun `a voicehints value that is not an array cannot be read`() {
        assertNull(brouterTurnsOrNull("""{"features":[{"properties":{"voicehints":"none"}}]}"""))
    }

    // Modes 4 and 9 append a string to each row.
    @Test
    fun `a row of six entries reads like a row of five`() {
        assertEquals(
            listOf(EngineTurn(1, 5, 0, 84)),
            brouterTurnsOrNull(withRows("""[1,5,0,63.0,84,"turn right"]""")),
        )
    }

    private fun withRows(vararg rows: String) =
        """{"features":[{"properties":{"voicehints":[${rows.joinToString(",")}]}}]}"""
}

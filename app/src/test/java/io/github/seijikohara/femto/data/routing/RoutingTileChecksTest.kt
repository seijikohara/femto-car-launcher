package io.github.seijikohara.femto.data.routing

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoutingTileChecksTest {
    @Test
    fun `a tile never checked is due`() {
        assertTrue(isCheckDue(checkedAtEpochMs = null, nowEpochMs = NOW))
    }

    @Test
    fun `a tile checked less than a week ago is not due`() {
        assertFalse(isCheckDue(checkedAtEpochMs = NOW - WEEK + 1, nowEpochMs = NOW))
    }

    @Test
    fun `a tile checked a week ago is due`() {
        assertTrue(isCheckDue(checkedAtEpochMs = NOW - WEEK, nowEpochMs = NOW))
    }

    @Test
    fun `a tile checked just now is not due`() {
        assertFalse(isCheckDue(checkedAtEpochMs = NOW, nowEpochMs = NOW))
    }

    @Test
    fun `a check dated after now is due, so a clock set back cannot hold a tile unchecked`() {
        assertTrue(isCheckDue(checkedAtEpochMs = NOW + 1, nowEpochMs = NOW))
    }

    @Test
    fun `only the installed tiles the route needs whose check is due are checked`() {
        val installed =
            listOf(
                tile("E130_N30", checkedAt = NOW - WEEK),
                tile("E135_N30", checkedAt = NOW - 1),
                tile("E140_N35", checkedAt = NOW - 2 * WEEK),
                tile("E125_N30", checkedAt = null),
            )

        val due = tilesDueForCheck(listOf("E125_N30", "E130_N30", "E135_N30", "E125_N25"), installed, NOW)

        assertEquals(listOf("E125_N30", "E130_N30"), due)
    }

    @Test
    fun `a route with nothing installed checks nothing`() {
        assertEquals(emptyList(), tilesDueForCheck(listOf("E130_N30"), emptyList(), NOW))
    }

    private fun tile(
        name: String,
        checkedAt: Long?,
    ) = InstalledRoutingTile(name = name, sizeBytes = 1L, dataDateEpochMs = null, checkedAtEpochMs = checkedAt)

    private companion object {
        const val NOW = 1_795_338_000_000L
        const val WEEK = ROUTING_TILE_CHECK_INTERVAL_MS
    }
}

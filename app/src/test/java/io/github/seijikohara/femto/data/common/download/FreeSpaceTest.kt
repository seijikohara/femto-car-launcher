package io.github.seijikohara.femto.data.common.download

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FreeSpaceTest {
    @Test
    fun `free space must cover the remaining bytes plus the margin`() {
        assertTrue(hasRoomFor(900_000_000L, 1_000_000_000L))
        assertFalse(hasRoomFor(900_000_001L, 1_000_000_000L))
        assertFalse(hasRoomFor(58_444_771_328L, 58_444_771_328L))
    }
}

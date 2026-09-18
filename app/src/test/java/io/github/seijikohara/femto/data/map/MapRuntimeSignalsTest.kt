package io.github.seijikohara.femto.data.map

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapRuntimeSignalsTest {
    @Test
    fun `parses the page's compact frame sample`() {
        assertEquals(
            MapRuntimeSignals.PageFrames(medianMs = 33, worstMs = 66, sampledFrames = 30, elapsedRealtimeMs = 42L),
            MapRuntimeSignals.pageFramesFrom("median=33,worst=66,samples=30", elapsedRealtimeMs = 42L),
        )
    }

    @Test
    fun `rejects a malformed or empty sample so it cannot replace a good one`() {
        assertNull(MapRuntimeSignals.pageFramesFrom("", 0L))
        assertNull(MapRuntimeSignals.pageFramesFrom("median=33,worst=66", 0L))
        assertNull(MapRuntimeSignals.pageFramesFrom("median=33,worst=66,samples=0", 0L))
        assertNull(MapRuntimeSignals.pageFramesFrom("median=x,worst=66,samples=30", 0L))
    }
}

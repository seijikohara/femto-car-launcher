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

    @Test
    fun `parses the page's lens report`() {
        assertEquals(
            MapRuntimeSignals.GoogleLens(measured = true, source = "webgl", fovyDeg = 27.3),
            MapRuntimeSignals.googleLensFrom("measured,source=webgl,fovy=27.3"),
        )
        assertEquals(
            MapRuntimeSignals.GoogleLens(measured = false, source = "canvas", fovyDeg = null),
            MapRuntimeSignals.googleLensFrom("unmeasured,source=canvas"),
        )
    }

    @Test
    fun `rejects a malformed lens report so it cannot replace a good one`() {
        assertNull(MapRuntimeSignals.googleLensFrom(""))
        assertNull(MapRuntimeSignals.googleLensFrom("measured,source=webgl"))
        assertNull(MapRuntimeSignals.googleLensFrom("measured,fovy=27.3"))
        assertNull(MapRuntimeSignals.googleLensFrom("maybe,source=webgl,fovy=27.3"))
    }
}

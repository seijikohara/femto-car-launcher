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
            MapRuntimeSignals.GoogleLens(MapRuntimeSignals.LensStatus.MEASURED, source = "webgl", fovyDeg = 27.3),
            MapRuntimeSignals.googleLensFrom("measured,source=webgl,fovy=27.3"),
        )
        assertEquals(
            MapRuntimeSignals.GoogleLens(MapRuntimeSignals.LensStatus.UNMEASURED, source = "canvas", fovyDeg = null),
            MapRuntimeSignals.googleLensFrom("unmeasured,source=canvas"),
        )
        assertEquals(
            MapRuntimeSignals.GoogleLens(MapRuntimeSignals.LensStatus.UNUSED, source = null, fovyDeg = null),
            MapRuntimeSignals.googleLensFrom("unused"),
        )
    }

    @Test
    fun `a new map page forgets the previous page's lens`() {
        // A rebuild to a raster map, or a page that never measures, must not
        // keep showing an earlier page's "measured".
        val page = MapRuntimeSignals.recordMapPageLoad()
        MapRuntimeSignals.recordGoogleLens("measured,source=webgl,fovy=27.3", page)
        MapRuntimeSignals.recordMapPageLoad()
        assertNull(MapRuntimeSignals.googleLensOrNull())
    }

    @Test
    fun `a lens report from a page that is no longer current is dropped`() {
        // The old WebView is destroyed only after the new page has loaded,
        // so its last report can arrive after the reset.
        val old = MapRuntimeSignals.recordMapPageLoad()
        val current = MapRuntimeSignals.recordMapPageLoad()
        MapRuntimeSignals.recordGoogleLens("measured,source=webgl,fovy=27.3", old)
        assertNull(MapRuntimeSignals.googleLensOrNull())
        MapRuntimeSignals.recordGoogleLens("unused", current)
        assertEquals(MapRuntimeSignals.LensStatus.UNUSED, MapRuntimeSignals.googleLensOrNull()?.status)
        // Even a stale report that lands after the current page's.
        MapRuntimeSignals.recordGoogleLens("measured,source=webgl,fovy=27.3", old)
        assertEquals(MapRuntimeSignals.LensStatus.UNUSED, MapRuntimeSignals.googleLensOrNull()?.status)
    }

    @Test
    fun `rejects a malformed lens report so it cannot replace a good one`() {
        assertNull(MapRuntimeSignals.googleLensFrom(""))
        assertNull(MapRuntimeSignals.googleLensFrom("measured,source=webgl"))
        assertNull(MapRuntimeSignals.googleLensFrom("measured,fovy=27.3"))
        assertNull(MapRuntimeSignals.googleLensFrom("maybe,source=webgl,fovy=27.3"))
    }
}

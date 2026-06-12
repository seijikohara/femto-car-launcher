package io.github.seijikohara.femto.data.system

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FrameStatsTest {
    @Test
    fun `reduces intervals to median worst and delayed share`() {
        // 8 smooth frames + 2 stalls: median stays at the vsync cadence,
        // worst surfaces the longest stall, 2/10 cross the delayed bar.
        val intervals = listOf(16L, 16L, 17L, 16L, 16L, 17L, 16L, 16L, 50L, 120L)
        val stats = computeFrameStats(intervals)
        assertEquals(10, stats?.sampledFrames)
        assertEquals(16L, stats?.medianMs)
        assertEquals(120L, stats?.worstMs)
        assertEquals(20, stats?.delayedPercent)
    }

    @Test
    fun `a fully smooth sample reports zero delayed`() {
        assertEquals(0, computeFrameStats(List(60) { 16L })?.delayedPercent)
    }

    @Test
    fun `an empty sample is null not a division by zero`() {
        assertNull(computeFrameStats(emptyList()))
    }
}

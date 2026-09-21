package io.github.seijikohara.femto.testfixtures

import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards [SelfMarkerAnchor] against drifting from its SSOT, `webmap/src/style.ts`. */
class SelfMarkerAnchorTest {
    private val tolerance = 1e-6f

    @Test
    fun maxMarkerDrop_reads_the_shared_json_constant() = assertEquals(0.32f, SelfMarkerAnchor.maxMarkerDrop, tolerance)

    @Test
    fun drop_scales_with_markerPos_inside_the_cap() =
        assertEquals(0.224f, SelfMarkerAnchor.drop(markerPos = 70, bottomSafeFraction = 0f), tolerance)

    @Test
    fun drop_reaches_the_cap_at_full_markerPos() =
        assertEquals(0.32f, SelfMarkerAnchor.drop(markerPos = 100, bottomSafeFraction = 0f), tolerance)

    @Test
    fun drop_shrinks_the_cap_by_the_bottom_safe_fraction() =
        assertEquals(0.14f, SelfMarkerAnchor.drop(markerPos = 70, bottomSafeFraction = 0.3f), tolerance)

    @Test
    fun drop_clamps_markerPos_above_100() =
        assertEquals(
            SelfMarkerAnchor.drop(markerPos = 100, bottomSafeFraction = 0f),
            SelfMarkerAnchor.drop(markerPos = 150, bottomSafeFraction = 0f),
            tolerance,
        )

    @Test
    fun drop_clamps_markerPos_below_0() =
        assertEquals(0f, SelfMarkerAnchor.drop(markerPos = -5, bottomSafeFraction = 0f), tolerance)

    @Test
    fun drop_clamps_a_negative_cap_to_zero() =
        assertEquals(0f, SelfMarkerAnchor.drop(markerPos = 70, bottomSafeFraction = 0.6f), tolerance)

    @Test
    fun xShift_shifts_left_for_a_right_safe_fraction() =
        assertEquals(-0.25f, SelfMarkerAnchor.xShift(leftSafeFraction = 0f, rightSafeFraction = 0.5f), tolerance)

    @Test
    fun xShift_caps_at_0_35() =
        assertEquals(0.35f, SelfMarkerAnchor.xShift(leftSafeFraction = 0.9f, rightSafeFraction = 0f), tolerance)

    @Test
    fun xShift_ignores_a_negative_safe_fraction() =
        assertEquals(0f, SelfMarkerAnchor.xShift(leftSafeFraction = -0.2f, rightSafeFraction = 0f), tolerance)
}

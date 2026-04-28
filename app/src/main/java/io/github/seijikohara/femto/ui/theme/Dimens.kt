package io.github.seijikohara.femto.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Automotive sizing constants that override M3 defaults for in-vehicle use.
 * The car-specific minimums are larger than M3's phone defaults because
 * drivers acquire targets at a glance, often with vibration.
 */
object FemtoDimens {
    /** Minimum tap target side length. M3 default is 48.dp. */
    val MinTouchTarget = 64.dp

    /** Minimum body text size for any driver-visible screen. */
    val MinBodyTextSize = 18.sp

    /** Outer padding for top-level screens. */
    val ScreenPadding = 24.dp

    /** Spacing between tiles in a launcher grid. */
    val GridGutter = 16.dp

    /** Default card elevation. Bold Minimal keeps surfaces flat. */
    val CardElevation = 0.dp
}

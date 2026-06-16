package io.github.seijikohara.femto.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Return a modal-sheet height as [fraction] of the current window height.
 *
 * Reads the window content size from [LocalWindowInfo] (`containerSize`, in
 * pixels) rather than the lint-flagged `LocalConfiguration.screenHeightDp`,
 * converting to [Dp] through the current density. The shared helper is the SSOT
 * for the launcher's bottom-sheet heights (drawer, settings, assistant,
 * diagnostics, licenses, font picker).
 */
@Composable
internal fun rememberSheetHeight(fraction: Float): Dp {
    val containerHeightPx = LocalWindowInfo.current.containerSize.height
    val density = LocalDensity.current
    return remember(containerHeightPx, fraction, density) {
        with(density) { (containerHeightPx * fraction).toDp() }
    }
}

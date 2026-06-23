package io.github.seijikohara.femto.ui.upsell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.seijikohara.femto.ui.common.ImmersiveSheetEffect
import io.github.seijikohara.femto.ui.common.rememberSheetHeight
import io.github.seijikohara.femto.ui.theme.FemtoDimens

/**
 * The upsell paywall as a Material 3 [ModalBottomSheet].
 * Mirrors [DiagnosticsSheet][io.github.seijikohara.femto.ui.diagnostics.DiagnosticsSheet]:
 * same height fraction, same immersive-flag wiring, starts fully expanded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpsellSheet(
    onDismiss: () -> Unit,
    fullscreen: Boolean,
    onLaunchPurchase: (offerToken: String) -> Unit,
    onPurchaseComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetHeight = rememberSheetHeight(FemtoDimens.DrawerSheetHeightFraction)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        ImmersiveSheetEffect(fullscreen)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(sheetHeight),
        ) {
            UpsellRoute(
                onPurchaseComplete = onPurchaseComplete,
                onLaunchPurchase = onLaunchPurchase,
            )
        }
    }
}

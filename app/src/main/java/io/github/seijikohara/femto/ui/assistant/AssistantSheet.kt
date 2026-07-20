package io.github.seijikohara.femto.ui.assistant

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.ui.common.ImmersiveSheetEffect
import io.github.seijikohara.femto.ui.common.rememberSheetHeight
import io.github.seijikohara.femto.ui.theme.FemtoDimens

/**
 * Assistant presented as a Material 3 [ModalBottomSheet] over the dashboard,
 * one of the launcher's bottom-sheet overlays (alongside settings).
 *
 * The launcher keeps the dashboard composed underneath, so the sheet reads as an
 * overlay (slide-up + scrim) the user dismisses by swiping down or tapping the
 * scrim. The sheet height keys off the viewport via
 * [FemtoDimens.DrawerSheetHeightFraction], never a specific device.
 * [AssistantRoute] supplies the content — the in-launcher voice surface plus the
 * system-intent delegation fallback.
 *
 * No standalone preview: a [ModalBottomSheet] renders in a popup window that
 * Compose previews do not capture, so [AssistantScreen]'s own @PreviewLightDark
 * covers the sheet content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AssistantSheet(
    onLaunchOption: (AssistantOption) -> Unit,
    onSubmitQuery: (String) -> Unit,
    onDismiss: () -> Unit,
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val sheetHeight = rememberSheetHeight(FemtoDimens.DrawerSheetHeightFraction)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        ImmersiveSheetEffect(fullscreen)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(sheetHeight),
        ) {
            AssistantRoute(
                onSubmitQuery = onSubmitQuery,
                onLaunchOption = onLaunchOption,
            )
        }
    }
}

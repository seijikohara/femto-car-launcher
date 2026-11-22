package io.github.seijikohara.femto.ui.routingdata

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.seijikohara.femto.ui.common.ImmersiveSheetEffect
import io.github.seijikohara.femto.ui.common.rememberModalSheetState
import io.github.seijikohara.femto.ui.common.rememberSheetHeight
import io.github.seijikohara.femto.ui.theme.FemtoDimens

/**
 * The Routing data sheet as a Material 3 [ModalBottomSheet] over settings, hosted like the Saved map regions sheet:
 * as tall as the settings sheet it opens over, and expanded from the start. Dismissing returns to settings.
 *
 * No standalone preview: a [ModalBottomSheet] renders in a popup window previews do not capture, so
 * [RoutingDataScreen]'s previews cover the content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutingDataSheet(
    onDismiss: () -> Unit,
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val sheetHeight = rememberSheetHeight(FemtoDimens.SettingsSheetHeightFraction)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalSheetState(skipPartiallyExpanded = true),
    ) {
        ImmersiveSheetEffect(fullscreen)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(sheetHeight),
        ) {
            RoutingDataRoute(onBack = onDismiss)
        }
    }
}

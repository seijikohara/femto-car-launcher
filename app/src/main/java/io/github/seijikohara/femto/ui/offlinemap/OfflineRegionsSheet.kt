package io.github.seijikohara.femto.ui.offlinemap

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
 * The Saved map regions sheet as a Material 3 [ModalBottomSheet] over settings, hosted like the font picker. As
 * tall as the settings sheet it opens over, and expanded from the start, so the region tree's long levels have
 * room. Dismissing returns to settings; a download keeps running.
 *
 * No standalone preview: a [ModalBottomSheet] renders in a popup window previews do not capture, so
 * [OfflineRegionsScreen]'s previews cover the content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OfflineRegionsSheet(
    onDismiss: () -> Unit,
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val sheetHeight = rememberSheetHeight(FemtoDimens.SettingsSheetHeightFraction)
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
            OfflineRegionsRoute(onBack = onDismiss)
        }
    }
}

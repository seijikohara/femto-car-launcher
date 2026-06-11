package io.github.seijikohara.femto.ui.fontpicker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.fonts.FontSlot
import io.github.seijikohara.femto.ui.theme.FemtoDimens

/**
 * The Google Fonts picker as a Material 3 [ModalBottomSheet] over settings.
 *
 * Taller than the standard sheet — a font browser benefits from the extra rows —
 * and starts expanded so the search field and the long list are reachable
 * without an initial drag. Dismissing returns to the settings sheet underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FontPickerSheet(
    slot: FontSlot,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetHeight = (LocalConfiguration.current.screenHeightDp * FemtoDimens.FontPickerSheetHeightFraction).dp
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(sheetHeight),
        ) {
            FontPickerRoute(slot = slot, onBack = onDismiss)
        }
    }
}

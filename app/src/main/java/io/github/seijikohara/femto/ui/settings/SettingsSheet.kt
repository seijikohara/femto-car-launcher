package io.github.seijikohara.femto.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.fonts.FontSlot
import io.github.seijikohara.femto.ui.common.ImmersiveSheetEffect
import io.github.seijikohara.femto.ui.common.rememberSheetHeight
import io.github.seijikohara.femto.ui.theme.FemtoDimens

/**
 * Settings presented as a Material 3 [ModalBottomSheet] over the dashboard: the
 * dashboard stays composed underneath, so settings read as an overlay the user
 * dismisses by swiping down, tapping the scrim, or the header back button —
 * rather than a full-screen swap. The sheet keys its height off the viewport via
 * [FemtoDimens.SettingsSheetHeightFraction] so the master-detail rail / list
 * and detail pane both have room to breathe. [SettingsRoute] supplies the
 * content inside the height-bounded [Box] so its category rail / list and
 * detail pane scroll independently within the sheet.
 *
 * No standalone preview: a [ModalBottomSheet] renders in a popup window Compose
 * previews do not capture, so [SettingsScreen]'s own @PreviewLightDark covers the
 * settings content.
 */
@Composable
internal fun SettingsSheet(
    onOpenNotificationAccess: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenFontPicker: (FontSlot) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenDocument: (SettingsDocument) -> Unit,
    onDismiss: () -> Unit,
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
    // Opened by the dashboard's update prompt: the sheet opens fully expanded
    // (see SettingsSheetFrame) and SettingsRoute starts the update.
    startUpdate: Boolean = false,
) = SettingsSheetFrame(
    onDismiss = onDismiss,
    fullscreen = fullscreen,
    modifier = modifier,
    openExpanded = startUpdate,
) {
    SettingsRoute(
        onBack = onDismiss,
        onOpenNotificationAccess = onOpenNotificationAccess,
        onOpenSystemSettings = onOpenSystemSettings,
        onOpenFontPicker = onOpenFontPicker,
        onOpenDiagnostics = onOpenDiagnostics,
        onOpenLicenses = onOpenLicenses,
        onOpenDocument = onOpenDocument,
        startUpdate = startUpdate,
    )
}

/**
 * The sheet [SettingsSheet] shows [content] in: full width, and as tall as
 * [FemtoDimens.SettingsSheetHeightFraction] of the window.
 *
 * [openExpanded] skips the partly expanded state the sheet otherwise opens
 * to. Partly expanded, the sheet shows only its top half on a landscape head
 * unit, so when the dashboard's update prompt opens the sheet to start an
 * update, the "Update to" row the user just triggered, and its progress,
 * would sit below the fold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheetFrame(
    onDismiss: () -> Unit,
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
    openExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    val sheetHeight = rememberSheetHeight(FemtoDimens.SettingsSheetHeightFraction)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = openExpanded),
        // Fill the head unit's full width. Material 3 caps a bottom sheet at 640dp
        // by default, which on a wide head unit would keep the settings content
        // below the master-detail breakpoint and render single-pane — defeating the
        // two-pane layout. A full-width sheet lets the wide layout engage.
        sheetMaxWidth = Dp.Unspecified,
    ) {
        ImmersiveSheetEffect(fullscreen)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(sheetHeight),
        ) {
            content()
        }
    }
}

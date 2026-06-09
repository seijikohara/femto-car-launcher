package io.github.seijikohara.femto.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.FontSlot
import io.github.seijikohara.femto.ui.theme.FemtoDimens

/**
 * Settings presented as a Material 3 [ModalBottomSheet] over the dashboard,
 * mirroring [io.github.seijikohara.femto.ui.drawer.AppDrawerSheet]: the dashboard
 * stays composed underneath, so settings read as an overlay the user dismisses by
 * swiping down, tapping the scrim, or the header back button — rather than a
 * full-screen swap. The sheet height is the same viewport fraction as the drawer so
 * the dashboard stays visible behind it. [SettingsRoute] supplies the content inside
 * the height-bounded [Box] so its sections scroll within the sheet.
 *
 * No standalone preview: a [ModalBottomSheet] renders in a popup window Compose
 * previews do not capture, so [SettingsScreen]'s own @PreviewLightDark covers the
 * settings content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(
    onOpenNotificationAccess: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenFontPicker: (FontSlot) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetHeight = (LocalConfiguration.current.screenHeightDp * FemtoDimens.DrawerSheetHeightFraction).dp
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(sheetHeight),
        ) {
            SettingsRoute(
                onBack = onDismiss,
                onOpenNotificationAccess = onOpenNotificationAccess,
                onOpenSystemSettings = onOpenSystemSettings,
                onOpenFontPicker = onOpenFontPicker,
            )
        }
    }
}

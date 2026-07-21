package io.github.seijikohara.femto.ui.drawer.components

import android.content.ComponentName
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pin
import com.composables.icons.lucide.PinOff
import com.composables.icons.lucide.Trash2
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.apps.AppEntry
import io.github.seijikohara.femto.ui.home.components.FemtoHorizontalDivider
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon

/**
 * Shared long-press menu for every app-tile surface — grid tile, list row,
 * recent tile, and pinned-dock tile — so one gesture yields one menu across
 * the whole drawer: optional surface-specific [leadingItems] first (the
 * dock's Move left / right), then Pin / Unpin, and the management pair —
 * App info, and Uninstall. Uninstall is hidden for system apps
 * ([AppEntry.isSystem]): the platform will not fully uninstall them, so
 * offering the row would only dead-end in the system dialog.
 */
@Composable
internal fun AppItemMenu(
    entry: AppEntry,
    isPinned: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onTogglePin: (ComponentName) -> Unit,
    onOpenAppInfo: (ComponentName) -> Unit,
    onRequestUninstall: (ComponentName) -> Unit,
    modifier: Modifier = Modifier,
    leadingItems: @Composable () -> Unit = {},
) = DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = modifier) {
    leadingItems()
    AppMenuItem(
        label = stringResource(if (isPinned) R.string.drawer_unpin else R.string.drawer_pin),
        icon = if (isPinned) Lucide.PinOff else Lucide.Pin,
        onClick = {
            onTogglePin(entry.componentName)
            onDismiss()
        },
    )
    // Seam between the drawer-local curation above and the system-UI
    // hand-offs below, mirroring the display-options menu's grouping.
    FemtoHorizontalDivider()
    AppMenuItem(
        label = stringResource(R.string.drawer_app_info),
        icon = Lucide.Info,
        onClick = {
            onOpenAppInfo(entry.componentName)
            onDismiss()
        },
    )
    if (!entry.isSystem) {
        AppMenuItem(
            label = stringResource(R.string.drawer_uninstall),
            icon = Lucide.Trash2,
            onClick = {
                onRequestUninstall(entry.componentName)
                onDismiss()
            },
        )
    }
}

/**
 * One menu row of the drawer's app menus, shared so every surface renders the
 * same recipe. M3's default menu-item height (48 dp) sits below the automotive
 * floor, hence the explicit min height.
 */
@Composable
internal fun AppMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = DropdownMenuItem(
    text = { Text(label) },
    modifier = modifier.sizeIn(minHeight = FemtoDimens.MinTouchTarget),
    leadingIcon = { FemtoIcon(imageVector = icon, contentDescription = null) },
    onClick = onClick,
)

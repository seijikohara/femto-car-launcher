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
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
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
 * Shared long-press menu for the drawer's app-tile surfaces — grid tile, list
 * row, and recent tile — so one gesture yields one menu across the drawer:
 * optional surface-specific [leadingItems] first, then the drawer-local
 * curation (Pin / Unpin and Hide / Unhide), and the management pair —
 * App info, and Uninstall. Uninstall is hidden for system apps
 * ([AppEntry.isSystem]): the platform will not fully uninstall them, so
 * offering the row would only dead-end in the system dialog. (The pinned dock
 * curates through its edit mode instead of this menu.)
 */
@Composable
internal fun AppItemMenu(
    entry: AppEntry,
    isPinned: Boolean,
    isHidden: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onTogglePin: (ComponentName) -> Unit,
    onToggleHide: (ComponentName) -> Unit,
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
    // Hide removes the app from the all-apps list (and Recent); Unhide restores
    // it. Independent of pinning — a pinned app can still be hidden from the
    // grid while staying in the dock.
    AppMenuItem(
        label = stringResource(if (isHidden) R.string.drawer_unhide else R.string.drawer_hide),
        icon = if (isHidden) Lucide.Eye else Lucide.EyeOff,
        onClick = {
            onToggleHide(entry.componentName)
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

package io.github.seijikohara.femto.ui.drawer.components

import android.content.ComponentName
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
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
 *
 * Each cluster is its own Expressive menu group. [leadingItems], when given,
 * renders as a group of its own ahead of them, and its items pick their own
 * [MenuDefaults.itemShape].
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
    leadingItems: (@Composable () -> Unit)? = null,
) = DropdownMenuPopup(
    expanded = expanded,
    onDismissRequest = onDismiss,
    // DropdownMenuPopup does not scroll on its own, unlike DropdownMenu; a
    // short head-unit window must still reach the last item.
    modifier = modifier.verticalScroll(rememberScrollState()),
) {
    val groupCount = if (leadingItems != null) 3 else 2
    val firstGroup = groupCount - 2
    leadingItems?.let { items ->
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = groupCount)) { items() }
        Spacer(Modifier.height(MenuDefaults.GroupSpacing))
    }
    DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = firstGroup, count = groupCount)) {
        AppMenuItem(
            label = stringResource(if (isPinned) R.string.drawer_unpin else R.string.drawer_pin),
            icon = if (isPinned) Lucide.PinOff else Lucide.Pin,
            shape = MenuDefaults.itemShape(index = 0, count = 2).shape,
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
            shape = MenuDefaults.itemShape(index = 1, count = 2).shape,
            onClick = {
                onToggleHide(entry.componentName)
                onDismiss()
            },
        )
    }
    // Seam between the drawer-local curation above and the system-UI
    // hand-offs below, mirroring the display-options menu's grouping.
    Spacer(Modifier.height(MenuDefaults.GroupSpacing))
    DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = firstGroup + 1, count = groupCount)) {
        val handOffCount = if (entry.isSystem) 1 else 2
        AppMenuItem(
            label = stringResource(R.string.drawer_app_info),
            icon = Lucide.Info,
            shape = MenuDefaults.itemShape(index = 0, count = handOffCount).shape,
            onClick = {
                onOpenAppInfo(entry.componentName)
                onDismiss()
            },
        )
        if (!entry.isSystem) {
            AppMenuItem(
                label = stringResource(R.string.drawer_uninstall),
                icon = Lucide.Trash2,
                shape = MenuDefaults.itemShape(index = 1, count = handOffCount).shape,
                onClick = {
                    onRequestUninstall(entry.componentName)
                    onDismiss()
                },
            )
        }
    }
}

/**
 * One menu row of the drawer's app menus, shared so every surface renders the
 * same recipe. [shape] is the item's place in its group
 * ([MenuDefaults.itemShape]). M3's default menu-item height sits below the
 * automotive floor, hence the explicit min height.
 */
@Composable
internal fun AppMenuItem(
    label: String,
    icon: ImageVector,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = DropdownMenuItem(
    onClick = onClick,
    text = { Text(label) },
    shape = shape,
    modifier = modifier.sizeIn(minHeight = FemtoDimens.MinTouchTarget),
    leadingIcon = { FemtoIcon(imageVector = icon, contentDescription = null) },
)

package io.github.seijikohara.femto.ui.home.components

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
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCcw
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon

/**
 * The status cluster's long-press edit menu ([StatusCluster], its only caller)
 * — same shape as
 * [io.github.seijikohara.femto.ui.drawer.components.PinnedDock]'s tile menu:
 * Move left / Move right (Move up / Move down when [vertical] — the two rail
 * dock positions reorder along the vertical axis, so a left/right label and
 * arrow would point the wrong way) reorder within the visible order, omitted
 * (not greyed) at the edge the direction cannot reach; Hide drops the
 * indicator, with no last-one guard — the cluster is read-only, so hiding every
 * indicator strands no action, and the Settings > Screen switch brings the
 * whole cluster back once nothing is left to long-press. The nav buttons keep
 * their floor and have their own affordance: a long press there opens the
 * reorder strip (DockNavEditStrip), whose remove badge writes through
 * [io.github.seijikohara.femto.data.dock.DockSettingsStore.toggleNavHidden] —
 * which refuses the last visible button, so the dock always keeps one
 * actionable id. Reset dock is always present — the same
 * [io.github.seijikohara.femto.data.dock.DockSettingsStore.resetToDefaults]
 * call Settings > Screen > Reset dock dispatches — so a user who hid
 * something (or forgot they reordered it) always has a one-tap way back,
 * without hunting through Settings.
 */
@Composable
internal fun DockEditMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onHide: () -> Unit,
    onResetDock: () -> Unit,
    // Rail docks (DockPosition.LEFT / RIGHT) stack the status cluster
    // vertically, so there onMoveLeft/onMoveRight (a -1/+1 reorder) move the
    // indicator up/down, not left/right.
    vertical: Boolean = false,
) = DropdownMenuPopup(
    expanded = expanded,
    onDismissRequest = onDismiss,
    // DropdownMenuPopup does not scroll on its own, unlike DropdownMenu; a
    // short head-unit window must still reach the last item.
    modifier = Modifier.verticalScroll(rememberScrollState()),
) {
    // One group, as before the Expressive menus: the items share no seam worth
    // splitting. The edge-dependent moves change the item count, and with it
    // which item takes the group's rounded ends.
    val hideIndex = listOf(canMoveLeft, canMoveRight).count { it }
    val count = hideIndex + 2
    DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
        if (canMoveLeft) {
            DockEditMenuItem(
                label = stringResource(if (vertical) R.string.dock_move_up else R.string.drawer_move_left),
                icon = if (vertical) Lucide.ArrowUp else Lucide.ArrowLeft,
                shape = MenuDefaults.itemShape(index = 0, count = count).shape,
                onClick = {
                    onMoveLeft()
                    onDismiss()
                },
            )
        }
        if (canMoveRight) {
            DockEditMenuItem(
                label = stringResource(if (vertical) R.string.dock_move_down else R.string.drawer_move_right),
                icon = if (vertical) Lucide.ArrowDown else Lucide.ArrowRight,
                shape = MenuDefaults.itemShape(index = if (canMoveLeft) 1 else 0, count = count).shape,
                onClick = {
                    onMoveRight()
                    onDismiss()
                },
            )
        }
        DockEditMenuItem(
            label = stringResource(R.string.dock_hide),
            icon = Lucide.EyeOff,
            shape = MenuDefaults.itemShape(index = hideIndex, count = count).shape,
            onClick = {
                onHide()
                onDismiss()
            },
        )
        DockEditMenuItem(
            label = stringResource(R.string.settings_reset_dock),
            icon = Lucide.RotateCcw,
            shape = MenuDefaults.itemShape(index = hideIndex + 1, count = count).shape,
            onClick = {
                onResetDock()
                onDismiss()
            },
        )
    }
}

// M3's default menu-item height sits below the automotive floor, hence the
// explicit min height.
@Composable
private fun DockEditMenuItem(
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

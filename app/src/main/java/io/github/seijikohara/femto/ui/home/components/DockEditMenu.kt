package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCcw
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon

/**
 * The dock's long-press edit menu, shared by a nav button ([DashboardDock])
 * and a status-cluster indicator ([StatusCluster]) — same shape as
 * [io.github.seijikohara.femto.ui.drawer.components.PinnedDock]'s tile menu:
 * Move left / Move right reorder within the visible order, omitted (not
 * greyed) at the edge the direction cannot reach; Hide drops the item,
 * omitted rather than disabled when it is the last visible one, so the dock
 * can never render empty. Reset dock is always present — the same
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
    canHide: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onHide: () -> Unit,
    onResetDock: () -> Unit,
) = DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
    if (canMoveLeft) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.drawer_move_left)) },
            // M3's default menu-item height (48 dp) sits below the automotive floor.
            modifier = Modifier.sizeIn(minHeight = FemtoDimens.MinTouchTarget),
            leadingIcon = { FemtoIcon(imageVector = Lucide.ArrowLeft, contentDescription = null) },
            onClick = {
                onMoveLeft()
                onDismiss()
            },
        )
    }
    if (canMoveRight) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.drawer_move_right)) },
            modifier = Modifier.sizeIn(minHeight = FemtoDimens.MinTouchTarget),
            leadingIcon = { FemtoIcon(imageVector = Lucide.ArrowRight, contentDescription = null) },
            onClick = {
                onMoveRight()
                onDismiss()
            },
        )
    }
    if (canHide) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.dock_hide)) },
            modifier = Modifier.sizeIn(minHeight = FemtoDimens.MinTouchTarget),
            leadingIcon = { FemtoIcon(imageVector = Lucide.EyeOff, contentDescription = null) },
            onClick = {
                onHide()
                onDismiss()
            },
        )
    }
    DropdownMenuItem(
        text = { Text(stringResource(R.string.settings_reset_dock)) },
        modifier = Modifier.sizeIn(minHeight = FemtoDimens.MinTouchTarget),
        leadingIcon = { FemtoIcon(imageVector = Lucide.RotateCcw, contentDescription = null) },
        onClick = {
            onResetDock()
            onDismiss()
        },
    )
}

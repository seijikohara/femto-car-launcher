package io.github.seijikohara.femto.ui.drawer

import android.content.ComponentName
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.seijikohara.femto.data.apps.DrawerIconSize
import io.github.seijikohara.femto.data.apps.DrawerLayout
import io.github.seijikohara.femto.data.apps.DrawerPreferences
import io.github.seijikohara.femto.ui.common.ImmersiveSheetEffect
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import kotlinx.coroutines.launch

/**
 * App drawer presented as a Material 3 [ModalBottomSheet] over the dashboard.
 *
 * The launcher keeps the dashboard composed underneath, so the drawer reads as an
 * overlay (slide-up + scrim) the user dismisses by swiping down or tapping the
 * scrim — rather than the previous full-screen swap. The sheet height is a
 * fraction of the available screen so the dashboard stays visible behind it; the
 * fraction keys off the viewport, never a specific device.
 *
 * This composable plays the Route role of `.claude/rules/compose.md` (the
 * AssistantSheet / FontPickerSheet precedent): it wires [AppDrawerViewModel],
 * which owns the app-query state. The activity-scoped view-model outlives sheet
 * dismissal, so a [AppDrawerAction.Refresh] is dispatched on every open to keep
 * the previous behavior of reflecting apps installed or removed since the last
 * open; the brief Loading is the accepted cost. Rendering is delegated to
 * [AppDrawerScreen], which fills the height-bounded [Box] so its grid scrolls
 * within the sheet.
 *
 * No standalone preview: a [ModalBottomSheet] renders in a popup window that
 * Compose previews do not capture, so [AppDrawerScreen]'s own @PreviewLightDark
 * covers the drawer content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppDrawerSheet(
    onLaunch: (ComponentName) -> Unit,
    onDismiss: () -> Unit,
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: AppDrawerViewModel = viewModel(factory = AppDrawerViewModelFactory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onAction(AppDrawerAction.Refresh) }

    // Drawer layout + pinned apps are persisted; collect them and write changes back.
    // Pins start as null (not the empty-list default) so the compact-vs-full
    // decision waits for the real persisted value — an empty-list initial would
    // flash the full drawer before snapping compact (the Default-initialised
    // replay trap).
    val drawerPreferences = remember { DrawerPreferences(context) }
    val layout by drawerPreferences.layout.collectAsStateWithLifecycle(initialValue = DrawerLayout.GRID)
    val iconSize by drawerPreferences.iconSize.collectAsStateWithLifecycle(initialValue = DrawerIconSize.MEDIUM)
    val pinnedOrNull by drawerPreferences.pinned.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    // The dock's apps button opens the quick pinned view first (when anything is
    // pinned); the All-apps row expands to the full drawer. State resets on every
    // open because the sheet recomposes from scratch.
    var expanded by remember { mutableStateOf(false) }

    val sheetHeight = (LocalConfiguration.current.screenHeightDp * FemtoDimens.DrawerSheetHeightFraction).dp
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        ImmersiveSheetEffect(fullscreen)
        val pinned = pinnedOrNull
        if (pinned != null) {
            val compact = !expanded && pinned.isNotEmpty()
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(if (compact) Modifier else Modifier.height(sheetHeight)),
            ) {
                AppDrawerScreen(
                    uiState = uiState,
                    layout = layout,
                    iconSize = iconSize,
                    pinned = pinned,
                    onLaunch = onLaunch,
                    onTogglePin = { component ->
                        scope.launch { drawerPreferences.togglePinned(component.flattenToString()) }
                    },
                    onToggleLayout = {
                        scope.launch {
                            drawerPreferences.setLayout(
                                if (layout == DrawerLayout.GRID) DrawerLayout.LIST else DrawerLayout.GRID,
                            )
                        }
                    },
                    onSelectIconSize = { size ->
                        scope.launch { drawerPreferences.setIconSize(size) }
                    },
                    onReorderPins = { order ->
                        scope.launch { drawerPreferences.setPinnedOrder(order) }
                    },
                    onRetry = { viewModel.onAction(AppDrawerAction.Refresh) },
                    compact = compact,
                    onExpand = { expanded = true },
                )
            }
        }
    }
}

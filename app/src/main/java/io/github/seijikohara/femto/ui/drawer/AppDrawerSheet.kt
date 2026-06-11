package io.github.seijikohara.femto.ui.drawer

import android.content.ComponentName
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.seijikohara.femto.data.apps.AppsRepository
import io.github.seijikohara.femto.data.apps.DrawerIconSize
import io.github.seijikohara.femto.data.apps.DrawerLayout
import io.github.seijikohara.femto.data.apps.DrawerPreferences
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "AppDrawerSheet"

/**
 * App drawer presented as a Material 3 [ModalBottomSheet] over the dashboard.
 *
 * The launcher keeps the dashboard composed underneath, so the drawer reads as an
 * overlay (slide-up + scrim) the user dismisses by swiping down or tapping the
 * scrim — rather than the previous full-screen swap. The sheet height is a
 * fraction of the available screen so the dashboard stays visible behind it; the
 * fraction keys off the viewport, never a specific device.
 *
 * The app query lives here (mirroring the previous VM-less route): it reloads on
 * each [retryKey] bump and flips back to Loading so a retry shows progress rather
 * than a stale error. It also re-runs every time the sheet opens (the composable
 * is created on demand), which intentionally reflects apps installed or removed
 * since the launcher started; the brief Loading is the accepted cost. Rendering is
 * delegated to [AppDrawerScreen], which fills the height-bounded [Box] so its grid
 * scrolls within the sheet.
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var uiState by remember { mutableStateOf<AppDrawerUiState>(AppDrawerUiState.Loading) }
    var retryKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(retryKey) {
        uiState = AppDrawerUiState.Loading
        runCatching { AppsRepository(context).queryApps() }
            .onSuccess { apps -> uiState = AppDrawerUiState.Content(apps) }
            .onFailure {
                // runCatching also traps the cancellation thrown when the sheet
                // is dismissed or retryKey restarts the effect; rethrow so
                // cancellation never renders as the Error state.
                if (it is CancellationException) throw it
                Log.e(TAG, "app query failed", it)
                uiState = AppDrawerUiState.Error
            }
    }

    // Drawer layout + pinned apps are persisted; collect them and write changes back.
    val drawerPreferences = remember { DrawerPreferences(context) }
    val layout by drawerPreferences.layout.collectAsStateWithLifecycle(initialValue = DrawerLayout.GRID)
    val iconSize by drawerPreferences.iconSize.collectAsStateWithLifecycle(initialValue = DrawerIconSize.MEDIUM)
    val pinned by drawerPreferences.pinned.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

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
                onRetry = { retryKey++ },
            )
        }
    }
}

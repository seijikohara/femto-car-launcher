package io.github.seijikohara.femto.ui.offlinemap

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Saved map regions entry point: binds [OfflineRegionsViewModel], whose factory is what first creates the
 * repository, and routes system back up the picker before it closes the sheet.
 */
@Composable
internal fun OfflineRegionsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: OfflineRegionsViewModel =
        viewModel(factory = OfflineRegionsViewModelFactory(context.applicationContext as Application))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    // Registered after the handler above, so it takes back first while the picker shows: up a level, then the list.
    BackHandler(enabled = uiState.picker != null) { viewModel.onAction(OfflineRegionsAction.Back) }
    // The ViewModel belongs to the activity and outlives the sheet; only the repository's state should, so the
    // picker, a pending offer and an open dialog end with the sheet.
    DisposableEffect(viewModel) {
        onDispose { viewModel.onAction(OfflineRegionsAction.Closed) }
    }
    OfflineRegionsScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

package io.github.seijikohara.femto.ui.routingdata

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Routing data entry point: binds [RoutingDataViewModel], whose factory is what first creates the tile store here. */
@Composable
internal fun RoutingDataRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: RoutingDataViewModel =
        viewModel(factory = RoutingDataViewModelFactory(context.applicationContext as Application))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    // The ViewModel belongs to the activity and outlives the sheet; an open confirmation ends with the sheet.
    DisposableEffect(viewModel) {
        onDispose { viewModel.onAction(RoutingDataAction.Closed) }
    }
    RoutingDataScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

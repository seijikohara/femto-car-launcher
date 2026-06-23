package io.github.seijikohara.femto.ui.diagnostics

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@Composable
internal fun DiagnosticsRoute(
    onBack: () -> Unit,
    // LaunchPurchase bubbles up to the Activity because BillingRepository.launchPurchase
    // needs a live Activity reference; neither the ViewModel nor the Route can provide one.
    onLaunchPurchase: (offerToken: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: DiagnosticsViewModel = viewModel(factory = DiagnosticsViewModelFactory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    DiagnosticsScreen(
        uiState = uiState,
        onAction = { action ->
            // Intercept LaunchPurchase so it never reaches the ViewModel
            // (the VM has no Activity). All other actions go to the ViewModel.
            when (action) {
                is DiagnosticsAction.LaunchPurchase -> onLaunchPurchase(action.offerToken)
                else -> viewModel.onAction(action)
            }
        },
        onBack = onBack,
        onCopyReport = {
            scope.launch {
                clipboard.setClipEntry(
                    ClipEntry(ClipData.newPlainText("Femto diagnostics", diagnosticsReport(uiState))),
                )
            }
        },
        modifier = modifier,
    )
}

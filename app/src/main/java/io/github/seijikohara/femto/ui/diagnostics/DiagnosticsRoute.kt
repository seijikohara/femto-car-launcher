package io.github.seijikohara.femto.ui.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal fun DiagnosticsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DiagnosticsViewModel = viewModel(factory = DiagnosticsViewModelFactory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DiagnosticsScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onBack = onBack,
        modifier = modifier,
    )
}

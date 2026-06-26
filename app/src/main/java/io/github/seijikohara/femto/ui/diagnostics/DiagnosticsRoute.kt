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
    modifier: Modifier = Modifier,
) {
    val viewModel: DiagnosticsViewModel = viewModel(factory = DiagnosticsViewModelFactory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    DiagnosticsScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
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

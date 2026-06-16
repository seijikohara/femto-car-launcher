package io.github.seijikohara.femto.ui.licenses

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal fun LicensesRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LicensesViewModel = viewModel(factory = LicensesViewModelFactory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LicensesScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onBack = onBack,
        modifier = modifier,
    )
}

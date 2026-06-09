package io.github.seijikohara.femto.ui.fontpicker

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.seijikohara.femto.data.FontSlot

/**
 * Font picker entry point: binds a [FontPickerViewModel] keyed to [slot] (so the
 * Latin and CJK slots keep independent instances) and forwards the choice to it.
 */
@Composable
internal fun FontPickerRoute(
    slot: FontSlot,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: FontPickerViewModel =
        viewModel(
            key = "font-picker-$slot",
            factory = FontPickerViewModelFactory(context.applicationContext as Application, slot),
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    FontPickerScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onBack = onBack,
        modifier = modifier,
    )
}

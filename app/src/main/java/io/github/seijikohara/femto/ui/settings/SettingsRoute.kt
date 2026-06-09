package io.github.seijikohara.femto.ui.settings

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
 * Settings entry point: binds [SettingsViewModel], collects its state, and
 * forwards persisted changes to the VM. Host-level navigation / system intents (back, the
 * notification-access screen, the OS settings root) flow up to [MainActivity] via
 * the callbacks so this route owns no Activity concerns.
 */
@Composable
internal fun SettingsRoute(
    onBack: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenFontPicker: (FontSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel =
        viewModel(factory = SettingsViewModelFactory(context.applicationContext as Application))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    SettingsScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onBack = onBack,
        onOpenNotificationAccess = onOpenNotificationAccess,
        onOpenSystemSettings = onOpenSystemSettings,
        onOpenFontPicker = onOpenFontPicker,
        modifier = modifier,
    )
}

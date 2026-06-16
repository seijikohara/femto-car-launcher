package io.github.seijikohara.femto.ui.settings

import android.Manifest
import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.seijikohara.femto.data.fonts.FontSlot
import io.github.seijikohara.femto.data.location.hasRecordAudioPermission

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
    onOpenDiagnostics: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel =
        viewModel(factory = SettingsViewModelFactory(context.applicationContext as Application))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    // The spectrum's Visualizer sits behind the RECORD_AUDIO runtime grant.
    // Prompt when the toggle turns on without it, and persist the setting in
    // the RESULT callback rather than alongside the launch: Settings is a
    // sheet over the live dashboard, so flipping the setting first would
    // activate the capture gate while the dialog is still up, fail the
    // permission check, and stay flat after the grant (the gate sees no
    // change to re-trigger on). The setting still persists whatever the
    // result — on denial the visualization degrades to flat (the
    // READ_CALENDAR / BLUETOOTH_CONNECT precedent: setting and grant stay
    // decoupled).
    val recordAudioLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { viewModel.onAction(SettingsAction.SetMusicSpectrum(true)) }
    val onAction: (SettingsAction) -> Unit = { action ->
        if (action is SettingsAction.SetMusicSpectrum && action.value && !context.hasRecordAudioPermission()) {
            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            viewModel.onAction(action)
        }
    }
    SettingsScreen(
        uiState = uiState,
        onAction = onAction,
        onBack = onBack,
        onOpenNotificationAccess = onOpenNotificationAccess,
        onOpenSystemSettings = onOpenSystemSettings,
        onOpenFontPicker = onOpenFontPicker,
        onOpenDiagnostics = onOpenDiagnostics,
        onOpenLicenses = onOpenLicenses,
        onOpenPrivacyPolicy = onOpenPrivacyPolicy,
        modifier = modifier,
    )
}

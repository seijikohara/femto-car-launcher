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
import io.github.seijikohara.femto.data.common.hasRecordAudioPermission
import io.github.seijikohara.femto.data.fonts.FontSlot

/**
 * Settings entry point: binds [SettingsViewModel], collects its state, and
 * forwards persisted changes to the VM. Host-level navigation / system intents (back, the
 * notification-access screen, the OS settings root) flow up to [MainActivity] via
 * the callbacks so this route owns no Activity concerns beyond the two round
 * trips whose results feed an action back into the VM: the RECORD_AUDIO prompt
 * and the "Install unknown apps" grant ([rememberInstallGrantedActions]), which
 * the install tap, the one-tap update and its install request all go through.
 */
@Composable
internal fun SettingsRoute(
    onBack: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenFontPicker: (FontSlot) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenDocument: (SettingsDocument) -> Unit,
    modifier: Modifier = Modifier,
    // Opened by the dashboard's update prompt: open on Updates and start the
    // one-tap update there.
    startUpdate: Boolean = false,
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
    val viewModelAction: (SettingsAction) -> Unit = { action ->
        when {
            action is SettingsAction.SetMusicSpectrum && action.value && !context.hasRecordAudioPermission() -> {
                recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }

            else -> {
                viewModel.onAction(action)
            }
        }
    }
    // Where the grant cannot be given here, the release page is the manual
    // path that is left.
    val onAction =
        rememberInstallGrantedActions(
            onAction = viewModelAction,
            onUnavailable = { onOpenDocument(SettingsDocument.RELEASE_PAGE) },
            startUpdate = startUpdate,
        )
    // The one-tap update installs its verified download exactly as the install
    // tap does, the "Install unknown apps" round trip included.
    InstallRequestsEffect(requests = viewModel.installRequests, onRequest = { onAction(SettingsAction.InstallUpdate) })
    SettingsScreen(
        uiState = uiState,
        onAction = onAction,
        onBack = onBack,
        onOpenNotificationAccess = onOpenNotificationAccess,
        onOpenSystemSettings = onOpenSystemSettings,
        onOpenFontPicker = onOpenFontPicker,
        onOpenDiagnostics = onOpenDiagnostics,
        onOpenLicenses = onOpenLicenses,
        onOpenDocument = onOpenDocument,
        modifier = modifier,
        initialCategory = SettingsCategoryId.UPDATES.takeIf { startUpdate },
    )
}

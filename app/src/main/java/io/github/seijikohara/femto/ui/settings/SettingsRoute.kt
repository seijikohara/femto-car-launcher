package io.github.seijikohara.femto.ui.settings

import android.Manifest
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.seijikohara.femto.data.common.hasRecordAudioPermission
import io.github.seijikohara.femto.data.fonts.FontSlot

private const val TAG = "SettingsRoute"

/**
 * Settings entry point: binds [SettingsViewModel], collects its state, and
 * forwards persisted changes to the VM. Host-level navigation / system intents (back, the
 * notification-access screen, the OS settings root) flow up to [MainActivity] via
 * the callbacks so this route owns no Activity concerns beyond the two round
 * trips whose results feed an action back into the VM: the RECORD_AUDIO prompt
 * and the "Install unknown apps" grant.
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
    // "Install unknown apps" is special access: without it the platform stops
    // the install at a dialog of its own. So Install sends the user to this
    // app's grant screen first and installs on the way back once the grant is
    // on. The result code is not relied on — the intent documents no output,
    // and OEM Settings builds differ — so the grant itself is read again.
    val installGrantLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { if (context.packageManager.canRequestPackageInstalls()) viewModel.onAction(SettingsAction.InstallUpdate) }
    val onAction: (SettingsAction) -> Unit = { action ->
        when {
            action is SettingsAction.SetMusicSpectrum && action.value && !context.hasRecordAudioPermission() -> {
                recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }

            action == SettingsAction.InstallUpdate && !context.packageManager.canRequestPackageInstalls() -> {
                // Where the grant cannot be given here, the release page is the
                // manual path that is left.
                if (!requestInstallGrant(context, installGrantLauncher)) onOpenDocument(SettingsDocument.RELEASE_PAGE)
            }

            else -> {
                viewModel.onAction(action)
            }
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
        onOpenDocument = onOpenDocument,
        modifier = modifier,
    )
}

// Opens this app's "Install unknown apps" screen. False when the grant cannot
// be given there: a device policy pins the toggle off, or no screen answers
// the intent (a locked-down head-unit ROM).
private fun requestInstallGrant(
    context: Context,
    launcher: ActivityResultLauncher<Intent>,
): Boolean =
    !context.installsBlockedByPolicy() &&
        try {
            launcher.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()),
            )
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no screen grants installs from this app", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "not permitted to open the install grant screen", e)
            false
        }

private fun Context.installsBlockedByPolicy(): Boolean =
    getSystemService<UserManager>()?.let { users ->
        users.hasUserRestriction(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES) ||
            users.hasUserRestriction(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)
    } == true

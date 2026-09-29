package io.github.seijikohara.femto.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.seijikohara.femto.data.common.hasInstallUnknownAppsAccess
import kotlinx.coroutines.flow.Flow

private const val TAG = "InstallGrant"

/**
 * A tap that needs Android's "Install unknown apps" access, routed through it
 * (Decision 11 of the in-app updater plan).
 *
 * - With the access on, [onGrant] runs at once.
 * - Without it, this app's access screen opens first. On the way back,
 *   [onGrant] runs if the access is now on, and [onDecline] runs if it is
 *   not. The access is read again rather than taken from the result code:
 *   the intent documents no output, and OEM Settings builds differ.
 * - Where the access cannot be given there, [onUnavailable] runs instead:
 *   a device policy pins it off, or no screen answers the intent on a
 *   locked-down ROM.
 *
 * Returns the action to run for the tap.
 */
@Composable
internal fun rememberInstallGrant(
    onGrant: () -> Unit,
    onDecline: () -> Unit,
    onUnavailable: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val latestOnGrant by rememberUpdatedState(onGrant)
    val latestOnDecline by rememberUpdatedState(onDecline)
    val latestOnUnavailable by rememberUpdatedState(onUnavailable)
    val grantLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (context.hasInstallUnknownAppsAccess()) latestOnGrant() else latestOnDecline()
        }
    return remember(context, grantLauncher) {
        {
            when {
                context.hasInstallUnknownAppsAccess() -> latestOnGrant()
                !requestInstallGrant(context, grantLauncher) -> latestOnUnavailable()
                else -> Unit
            }
        }
    }
}

/**
 * [onAction], with the two Updates actions that end in an install routed
 * through the "Install unknown apps" access first ([rememberInstallGrant]):
 * [SettingsAction.InstallUpdate], and [SettingsAction.StartUpdate], whose
 * download reaches [onAction] only once the access is on. The access is asked
 * for at the tap, never a minute later when the download lands, and a download
 * the install could not follow never starts. A declined access reaches
 * [onAction] as [SettingsAction.InstallGrantDeclined]; one that cannot be
 * given runs [onUnavailable]. With [startUpdate] (Settings opened by the
 * dashboard's update prompt), the one-tap update starts on entry the same way,
 * once per opening.
 */
@Composable
internal fun rememberInstallGrantedActions(
    onAction: (SettingsAction) -> Unit,
    onUnavailable: () -> Unit,
    startUpdate: Boolean = false,
): (SettingsAction) -> Unit {
    val latestOnAction by rememberUpdatedState(onAction)
    val install =
        rememberInstallGrant(
            onGrant = { latestOnAction(SettingsAction.InstallUpdate) },
            onDecline = { latestOnAction(SettingsAction.InstallGrantDeclined) },
            onUnavailable = onUnavailable,
        )
    val update =
        rememberInstallGrant(
            onGrant = { latestOnAction(SettingsAction.StartUpdate) },
            onDecline = { latestOnAction(SettingsAction.InstallGrantDeclined) },
            onUnavailable = onUnavailable,
        )
    LaunchedEffect(startUpdate) { if (startUpdate) update() }
    return remember(install, update) {
        { action ->
            when (action) {
                SettingsAction.InstallUpdate -> install()
                SettingsAction.StartUpdate -> update()
                else -> latestOnAction(action)
            }
        }
    }
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

/**
 * Runs [onRequest] for each of [requests] collected while this screen is
 * started: the one-tap update's install, started the way an install tap starts
 * it (route it through [rememberInstallGrantedActions]). The effect collects
 * only while started. The ViewModel holds a request until a collector arrives;
 * what keeps an install from starting later or elsewhere is the mark behind
 * the request, which ends when the Updates section stops (UpdatesHidden on
 * ON_STOP).
 */
@Composable
internal fun InstallRequestsEffect(
    requests: Flow<Unit>,
    onRequest: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnRequest by rememberUpdatedState(onRequest)
    LaunchedEffect(requests, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { requests.collect { latestOnRequest() } }
    }
}

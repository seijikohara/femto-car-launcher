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
 * An install tap, routed through Android's "Install unknown apps" access
 * (Decision 11 of the in-app updater plan).
 *
 * - With the access on, [onInstall] runs at once.
 * - Without it, this app's access screen opens first. On the way back,
 *   [onInstall] runs if the access is now on, and [onGrantDecline] runs if
 *   it is not. The access is read again rather than taken from the result
 *   code: the intent documents no output, and OEM Settings builds differ.
 * - Where the access cannot be given there, [onUnavailable] runs instead:
 *   a device policy pins it off, or no screen answers the intent on a
 *   locked-down ROM.
 *
 * Returns the action to run for the tap.
 */
@Composable
internal fun rememberInstallUpdate(
    onInstall: () -> Unit,
    onGrantDecline: () -> Unit,
    onUnavailable: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val latestOnInstall by rememberUpdatedState(onInstall)
    val latestOnGrantDecline by rememberUpdatedState(onGrantDecline)
    val latestOnUnavailable by rememberUpdatedState(onUnavailable)
    val grantLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (context.hasInstallUnknownAppsAccess()) latestOnInstall() else latestOnGrantDecline()
        }
    return remember(context, grantLauncher) {
        {
            when {
                context.hasInstallUnknownAppsAccess() -> latestOnInstall()
                !requestInstallGrant(context, grantLauncher) -> latestOnUnavailable()
                else -> Unit
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
 * Runs [onRequest] for each of [requests] that arrives while this screen is
 * started: the one-tap update's install, started the way an install tap starts
 * it (pass the action [rememberInstallUpdate] returns). A request that arrives
 * while the screen is stopped, behind another app say, is dropped rather than
 * kept, so an install never starts by itself later or elsewhere.
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

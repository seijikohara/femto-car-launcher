package io.github.seijikohara.femto.data.music

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The launcher's notification listener: the grant that lets it read and control media sessions is given to this
 * component by name, so its name and package must stay. It reads no notifications.
 */
internal class MusicSessionListenerService : NotificationListenerService() {
    // Turning access off unbinds the listener at once, while the dashboard may
    // never be shown again to notice; the last played track goes here.
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        val context = applicationContext
        listenerScope.launch {
            forgetLastPlayedWithoutAccess(
                store = LastPlayedPreferences(context),
                accessGranted = { listenerAccessGranted(context) },
            )
        }
    }
}

// The write outlives the callback, but not the process.
private val listenerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * How long after a disconnect that still reads "granted" the grant is read once more. The platform does not document
 * whether the disconnect comes before or after the grant is withdrawn; 2 s leaves a slow head unit time to finish
 * withdrawing it, and is short enough that the process is still there to clear the track. The dashboard clears it in
 * any case the next time it is shown.
 */
internal const val LISTENER_GRANT_RECHECK_MS = 2_000L

/**
 * Forget the last played track once notification access is off, as [accessGranted] reads it. The listener also
 * disconnects with access still on (the system unbinding it for its own reasons, or at shutdown), and the track must
 * survive that to be offered after a restart, so the grant decides, not the disconnect. A first read that still says
 * "granted" gets one more read after [LISTENER_GRANT_RECHECK_MS], once, not in a loop.
 */
internal suspend fun forgetLastPlayedWithoutAccess(
    store: LastPlayedStore,
    accessGranted: () -> Boolean,
) {
    if (!accessGranted()) {
        store.clear()
    } else {
        delay(LISTENER_GRANT_RECHECK_MS)
        if (!accessGranted()) store.clear()
    }
}

/**
 * Return whether the system's live list of approved listeners holds this one. Not the `enabled_notification_listeners`
 * setting the dashboard reads (hasNotificationListenerAccess): the system updates that setting and unbinds the listener
 * in no guaranteed order, so at the disconnect the setting can still list the launcher after access was revoked. A
 * failed read counts as granted: wrongly clearing during a shutdown would lose the track the feature exists to offer,
 * while access really revoked is still caught when the dashboard is next shown.
 */
private fun listenerAccessGranted(context: Context): Boolean =
    runCatching {
        checkNotNull(context.getSystemService<NotificationManager>())
            .isNotificationListenerAccessGranted(ComponentName(context, MusicSessionListenerService::class.java))
    }.getOrDefault(true)

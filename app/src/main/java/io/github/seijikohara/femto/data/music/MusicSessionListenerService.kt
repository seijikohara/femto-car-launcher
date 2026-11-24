package io.github.seijikohara.femto.data.music

import android.content.Context
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        listenerScope.launch { forgetLastPlayedWithoutAccess(applicationContext) }
    }
}

// The write outlives the callback, but not the process.
private val listenerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Forget the last played track once notification access is off. The listener also disconnects with access still on
 * (the system unbinding it for its own reasons), and the track must survive that to be offered after a restart, so
 * the grant decides, not the disconnect.
 */
internal suspend fun forgetLastPlayedWithoutAccess(
    context: Context,
    store: LastPlayedStore = LastPlayedPreferences(context),
) {
    if (!hasNotificationListenerAccess(context)) store.clear()
}

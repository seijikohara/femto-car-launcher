package io.github.seijikohara.femto.data.update

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.seijikohara.femto.BuildConfig
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.common.finishAsync
import io.github.seijikohara.femto.data.common.holdsHomeRole

private const val TAG = "PackageReplacedReceiver"

private const val CHANNEL_ID = "app_updates"

// A tag of its own keeps this notification apart from the app's other
// notifications, whatever their ids.
private const val NOTIFICATION_TAG = "update_installed"
private const val NOTIFICATION_ID = 1

/** What the launcher does once a new version of itself has replaced the old one; both steps can apply. */
internal data class AfterUpdate(
    /** Try to come back as the home screen; Android may refuse without an error. */
    val openLauncher: Boolean,
    /**
     * Post a notification whose tap opens the launcher. The launcher removes it
     * when it comes back on screen ([dismissUpdateNotification]).
     */
    val notify: Boolean,
)

/**
 * Android blocks activity starts from the background and lets the home app
 * through, but it recognises the home app by the home process that is running:
 * AOSP 13's `ActivityStarter#isHomeApp` compares the caller with
 * `mHomeProcess` before it looks up the default home activity. The install
 * killed this process, so when another launcher's home task took the screen
 * meanwhile, that launcher's process is the home process and the start is
 * refused without an error. The home app therefore tries, and also posts the
 * notification whenever it may, since a notification tap is a start Android
 * allows. Any other install cannot bring itself back, and only notifies.
 */
internal fun afterUpdateAction(
    holdsHomeRole: Boolean,
    mayNotify: Boolean,
): AfterUpdate = AfterUpdate(openLauncher = holdsHomeRole, notify = mayNotify)

/**
 * Remove the "Updated to …" notification. The launcher calls this each time
 * it comes to the foreground: once it is back on screen, by a tap, by Home or
 * by a relaunch Android allowed, the notification has nothing left to offer.
 */
internal fun Context.dismissUpdateNotification() =
    NotificationManagerCompat.from(this).cancel(NOTIFICATION_TAG, NOTIFICATION_ID)

/**
 * Brings the launcher back after an install replaced it: the install killed
 * the process that asked for it. Any replacement triggers it, a manual
 * sideload included, and each one is an update the user may want to open.
 * How it comes back is [afterUpdateAction]'s decision. Not exported: the
 * platform's own broadcast still reaches it.
 */
internal class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext
        finishAsync(TAG) { comeBack(app) }
    }
}

private fun comeBack(context: Context) {
    val mayNotify =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    val after = afterUpdateAction(context.holdsHomeRole(), mayNotify)
    if (after.notify) notifyUpdated(context)
    if (after.openLauncher) openHome(context)
}

// The HOME intent, limited to this package, rather than the launcher's own
// activity: the launcher comes back as the home screen, not as an ordinary
// app launch. A start Android refuses as a background start throws nothing
// (see afterUpdateAction); the catch covers only a start that fails outright.
private fun openHome(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure { Log.w(TAG, "reopening the launcher failed", it) }
}

@RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
private fun notifyUpdated(context: Context) {
    // The launcher's own entry point; null only if the manifest lost it.
    val open = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    val notifications = NotificationManagerCompat.from(context)
    notifications.createNotificationChannel(
        NotificationChannelCompat
            .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.notification_update_channel_name))
            .setDescription(context.getString(R.string.notification_update_channel_desc))
            .build(),
    )
    notifications.notify(
        NOTIFICATION_TAG,
        NOTIFICATION_ID,
        NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_update_installed)
            // The running build's own name: the repository's announcement of
            // the update is set asynchronously at start, so it is not read here.
            .setContentTitle(context.getString(R.string.notification_update_title, BuildConfig.VERSION_NAME))
            .setContentText(context.getString(R.string.notification_update_text))
            .setContentIntent(PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .build(),
    )
}

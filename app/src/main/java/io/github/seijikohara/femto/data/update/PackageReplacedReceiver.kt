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
import io.github.seijikohara.femto.data.common.holdsHomeRole

private const val TAG = "PackageReplacedReceiver"

private const val CHANNEL_ID = "app_updates"

// A tag of its own keeps this notification apart from the app's other
// notifications, whatever their ids.
private const val NOTIFICATION_TAG = "update_installed"
private const val NOTIFICATION_ID = 1

/** What the launcher does once a new version of itself has replaced the old one. */
internal enum class AfterUpdate {
    /** Come back on screen as the home app. */
    OPEN_LAUNCHER,

    /** Post a notification whose tap opens the launcher. */
    NOTIFY,

    /** Nothing: the user opens the launcher again when they want it. */
    NOTHING,
}

/**
 * The platform blocks activity starts from the background, and exempts the
 * home app; any other install cannot bring itself back. A notification tap is
 * a start the platform allows, so it serves the other installs, when they may
 * post notifications.
 */
internal fun afterUpdateAction(
    holdsHomeRole: Boolean,
    mayNotify: Boolean,
): AfterUpdate =
    when {
        holdsHomeRole -> AfterUpdate.OPEN_LAUNCHER
        mayNotify -> AfterUpdate.NOTIFY
        else -> AfterUpdate.NOTHING
    }

/**
 * Brings the launcher back after an install replaced it: the install killed
 * the process that asked for it. Any replacement triggers it, a manual
 * sideload included, and each one is an update the user may want to open.
 * Not exported: the platform's own broadcast still reaches it.
 */
internal class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val mayNotify =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        when (afterUpdateAction(context.holdsHomeRole(), mayNotify)) {
            AfterUpdate.OPEN_LAUNCHER -> openHome(context)
            AfterUpdate.NOTIFY -> notifyUpdated(context)
            AfterUpdate.NOTHING -> Unit
        }
    }
}

// The HOME intent, limited to this package, rather than the launcher's own
// activity: the launcher comes back as the home screen, not as an ordinary
// app launch.
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

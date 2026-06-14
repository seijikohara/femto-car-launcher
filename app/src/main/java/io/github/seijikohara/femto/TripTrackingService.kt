@file:OptIn(FlowPreview::class)

package io.github.seijikohara.femto

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.seijikohara.femto.data.display.DisplayPreferences
import io.github.seijikohara.femto.data.location.LocationGraph
import io.github.seijikohara.femto.data.location.TripState
import io.github.seijikohara.femto.ui.locale.resolved
import io.github.seijikohara.femto.ui.locale.speedUnitFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the trip distance / average accruing while the
 * launcher is backgrounded — typically because the driver has a navigation app
 * in front. It owns no location logic of its own: collecting [LocationGraph]'s
 * shared `tripState` keeps the one GPS registration and the one set of trip
 * accumulators hot, and the latest metrics drive an ongoing notification.
 *
 * The service lives at the app root (not under `data/`) because its notification
 * reuses the `ui/locale` speed/distance formatting SSOT, which `data/` may not
 * import.
 *
 * Lifecycle is owned by [MainActivity], which starts the service only while the
 * launcher is in the foreground (Android forbids starting a foreground service
 * from the background) and stops it when the user turns the toggle off. Because
 * the start always originates in the foreground, location access needs only the
 * "while in use" grant plus the `location` service type — never the heavily
 * gated `ACCESS_BACKGROUND_LOCATION`.
 */
internal class TripTrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // onStartCommand may fire again while already running (a redundant start from
    // MainActivity's lifecycle observer); promote and start collecting only once.
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (!started) {
            started = true
            // Promote to foreground immediately with a seed notification (the
            // collector refreshes it within the first sample window). The seed
            // unit follows the locale until the persisted choice loads.
            val seed = TripState.Initial.tripReadout(speedUnitFor())
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(seed),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
            collectTripMetrics()
        }
        // Do not let the OS resurrect a killed service in the background: that
        // would attempt a foreground-service start from the background and throw.
        // MainActivity restarts tracking on the next foreground instead.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun collectTripMetrics() {
        val tripState = LocationGraph.get(this).tripState
        val speedUnit =
            DisplayPreferences(this)
                .settings
                .map { it.speedUnit.resolved() }
                .distinctUntilChanged()
        scope.launch {
            // sample() caps notification churn at one update per second; the
            // location flow can land fixes up to ~4 Hz.
            combine(tripState, speedUnit) { trip, unit -> trip.tripReadout(unit) }
                .sample(NOTIFICATION_UPDATE_INTERVAL_MS)
                .collect { postNotification(buildNotification(it)) }
        }
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannelCompat
                .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.notification_trip_channel_name))
                .setDescription(getString(R.string.notification_trip_channel_desc))
                .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private fun buildNotification(readout: TripReadout): Notification =
        NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_trip_tracking)
            .setContentTitle(getString(R.string.notification_trip_title))
            .setContentText(
                getString(
                    R.string.notification_trip_text,
                    readout.speed,
                    readout.distance,
                    readout.averageSpeed,
                ),
            ).setContentIntent(launcherPendingIntent())
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    // Refresh the ongoing notification. The foreground promotion shows the seed
    // regardless; these updates need POST_NOTIFICATIONS (a runtime grant at the
    // minSdk-33 floor), so a denied grant silently skips the refresh while
    // tracking still runs.
    private fun postNotification(notification: Notification) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    private fun launcherPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TripTrackingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TripTrackingService::class.java))
        }

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "trip_tracking"

        // One notification refresh per second is plenty for a glance metric and
        // keeps the system from logging "notify too frequently" warnings.
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 1_000L
    }
}

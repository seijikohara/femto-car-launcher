package io.github.seijikohara.femto.data.system

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import io.github.seijikohara.femto.BuildConfig
import io.github.seijikohara.femto.data.location.hasBluetoothConnectPermission
import io.github.seijikohara.femto.data.location.hasCoarseLocationPermission
import io.github.seijikohara.femto.data.location.hasFineLocationPermission
import io.github.seijikohara.femto.data.location.hasReadCalendarPermission
import io.github.seijikohara.femto.data.location.hasReadPhoneStatePermission
import io.github.seijikohara.femto.data.location.hasRecordAudioPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "DiagnosticsRepo"

// Enough tail to cover the warnings of a feature being exercised right now
// without turning the report into a novel.
private const val MAX_LOG_LINES = 80

/** One runtime grant's state, keyed by the full permission constant. */
internal data class DiagnosticPermission(
    val permission: String,
    val granted: Boolean,
)

/** A point-in-time health capture of everything the dashboard depends on. */
internal data class DiagnosticsSnapshot(
    val appVersion: String,
    val deviceModel: String,
    val androidRelease: String,
    val sdkInt: Int,
    val permissions: List<DiagnosticPermission>,
    val notificationListenerEnabled: Boolean,
    val networkOnline: Boolean,
    val networkTransports: List<String>,
    val recentWarningLogs: List<String>,
)

/**
 * Collects the diagnostics snapshot for the in-app Diagnostics screen.
 *
 * Exists because the deployed head units are rarely adb-reachable: every
 * launcher feature degrades silently by design (missing grant, disabled
 * listener, unsupported HAL, rate-limited API), and without this surface
 * neither the user nor a remote debugging session can tell which gate is
 * closed. The recent-warnings tail relies on `logcat -d` returning the
 * calling app's own lines without `READ_LOGS` (uid-filtered since
 * Android 4.1); on builds that restrict even that, the section degrades to
 * empty with one WARN.
 */
internal class DiagnosticsRepository(
    private val context: Context,
) {
    suspend fun snapshot(): DiagnosticsSnapshot =
        withContext(Dispatchers.IO) {
            DiagnosticsSnapshot(
                appVersion = "${BuildConfig.VERSION_NAME} (${if (BuildConfig.DEBUG) "debug" else "release"})",
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                androidRelease = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
                permissions = permissionStates(),
                notificationListenerEnabled =
                    context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context),
                networkOnline =
                    networkCapabilities()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
                networkTransports = networkCapabilities()?.transportLabels().orEmpty(),
                recentWarningLogs = recentWarningLogsOrEmpty(),
            )
        }

    private fun permissionStates(): List<DiagnosticPermission> =
        listOf(
            DiagnosticPermission(Manifest.permission.ACCESS_FINE_LOCATION, context.hasFineLocationPermission()),
            DiagnosticPermission(Manifest.permission.ACCESS_COARSE_LOCATION, context.hasCoarseLocationPermission()),
            DiagnosticPermission(Manifest.permission.READ_CALENDAR, context.hasReadCalendarPermission()),
            DiagnosticPermission(Manifest.permission.READ_PHONE_STATE, context.hasReadPhoneStatePermission()),
            DiagnosticPermission(Manifest.permission.BLUETOOTH_CONNECT, context.hasBluetoothConnectPermission()),
            DiagnosticPermission(Manifest.permission.RECORD_AUDIO, context.hasRecordAudioPermission()),
        )

    private fun networkCapabilities(): NetworkCapabilities? =
        context.getSystemService<ConnectivityManager>()?.let { it.getNetworkCapabilities(it.activeNetwork) }

    private fun NetworkCapabilities.transportLabels(): List<String> =
        listOfNotNull(
            "Wi-Fi".takeIf { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) },
            "Cellular".takeIf { hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) },
            "Ethernet".takeIf { hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) },
            "Bluetooth".takeIf { hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) },
        )

    private fun recentWarningLogsOrEmpty(): List<String> =
        runCatching {
            ProcessBuilder("logcat", "-d", "-v", "time", "*:W")
                .redirectErrorStream(true)
                .start()
                .inputStream
                .bufferedReader()
                .useLines { lines -> lines.toList().takeLast(MAX_LOG_LINES) }
        }.onFailure { Log.w(TAG, "self logcat read failed; diagnostics omit the log tail", it) }
            .getOrDefault(emptyList())
}

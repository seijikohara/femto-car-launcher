// Deleted in the ViewModel/UI swap task.
//
// The v1 flat-snapshot pipeline, re-homed from data/system so the v1
// Diagnostics screen keeps compiling — and working — against the moved types
// until the section registry replaces it. Mechanisms that moved into the v2
// collectors (the log tail, the Choreographer frame sample) are delegated,
// not duplicated; only the doomed snapshot shapes and their assembly live here.
package io.github.seijikohara.femto.data.diagnostics

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.webkit.WebView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import io.github.seijikohara.femto.BuildConfig
import io.github.seijikohara.femto.data.display.DisplayPreferences
import io.github.seijikohara.femto.data.location.LocationPreferences
import io.github.seijikohara.femto.data.location.hasBluetoothConnectPermission
import io.github.seijikohara.femto.data.location.hasCoarseLocationPermission
import io.github.seijikohara.femto.data.location.hasFineLocationPermission
import io.github.seijikohara.femto.data.location.hasReadCalendarPermission
import io.github.seijikohara.femto.data.location.hasReadPhoneStatePermission
import io.github.seijikohara.femto.data.location.hasRecordAudioPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

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

/** One labelled value for the report's settings snapshot. */
internal data class DiagnosticEntry(
    val label: String,
    val value: String,
)

/** Point-in-time performance capture for the Diagnostics screen. */
internal data class PerformanceSnapshot(
    val thermal: ThermalLevel,
    // 0..1 fraction of the throttling threshold (1.0 = throttling now);
    // null when the platform cannot forecast (rate-limited or unsupported).
    val thermalHeadroom: Float?,
    val powerSaveMode: Boolean,
    val availMemMb: Int,
    val totalMemMb: Int,
    val lowMemory: Boolean,
    val appPssMb: Int,
    val javaHeapUsedMb: Int,
    val javaHeapMaxMb: Int,
    val nativeHeapMb: Int,
    val processUptimeMinutes: Long,
    val deviceUptimeMinutes: Long,
    val frameStats: FrameStats?,
    val webViewVersion: String?,
    val mapSettings: List<DiagnosticEntry>,
)

/** Collects the v1 flat diagnostics snapshot; the log tail delegates to [LogTailCollector]. */
internal class DiagnosticsRepository(
    private val context: Context,
) {
    suspend fun snapshot(): DiagnosticsSnapshot {
        val logTail = LogTailCollector(context).logTail()
        return withContext(Dispatchers.IO) {
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
                recentWarningLogs = logTail.lines,
            )
        }
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
}

/** Collects the v1 performance snapshot; the frame sample delegates to [sampleFrameStats]. */
internal class PerformanceProbe(
    private val context: Context,
) {
    suspend fun snapshot(): PerformanceSnapshot {
        // The v1 60 Hz-only delayed bar, preserved verbatim until this class
        // dies; the v2 collector derives it from the actual refresh rate.
        val frameStats = sampleFrameStats(DELAYED_FRAME_MS)
        return withContext(Dispatchers.IO) {
            val power = context.getSystemService<PowerManager>()
            val memory =
                ActivityManager.MemoryInfo().also {
                    context.getSystemService<ActivityManager>()?.getMemoryInfo(it)
                }
            val runtime = Runtime.getRuntime()
            PerformanceSnapshot(
                thermal = power?.currentThermalStatus.toThermalLevel(),
                thermalHeadroom =
                    power
                        ?.getThermalHeadroom(HEADROOM_FORECAST_SECONDS)
                        ?.takeIf { it.isFinite() },
                powerSaveMode = power?.isPowerSaveMode == true,
                availMemMb = (memory.availMem / BYTES_PER_MB).toInt(),
                totalMemMb = (memory.totalMem / BYTES_PER_MB).toInt(),
                lowMemory = memory.lowMemory,
                appPssMb = (Debug.getPss() / KB_PER_MB).toInt(),
                javaHeapUsedMb = ((runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB).toInt(),
                javaHeapMaxMb = (runtime.maxMemory() / BYTES_PER_MB).toInt(),
                nativeHeapMb = (Debug.getNativeHeapAllocatedSize() / BYTES_PER_MB).toInt(),
                processUptimeMinutes =
                    (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()) / MS_PER_MINUTE,
                deviceUptimeMinutes = SystemClock.elapsedRealtime() / MS_PER_MINUTE,
                frameStats = frameStats,
                webViewVersion =
                    WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" },
                mapSettings = mapSettingEntries(),
            )
        }
    }

    // Transient copy of the six shared wordings; their SSOT is now
    // SettingsFactsCollector, and this copy vanishes with the file.
    private suspend fun mapSettingEntries(): List<DiagnosticEntry> {
        val display = DisplayPreferences(context).settings.first()
        val location = LocationPreferences(context).settings.first()
        return listOf(
            DiagnosticEntry("Map backend", display.mapBackend.name),
            DiagnosticEntry("3D buildings / terrain", "${display.map3dBuildings} / ${display.mapTerrain}"),
            DiagnosticEntry("Map zoom / tilt", "z${display.mapZoom} / ${display.mapTiltDeg}°"),
            DiagnosticEntry("Location quality", location.quality.name),
            DiagnosticEntry("Location interval", "${location.intervalMillis} ms"),
            DiagnosticEntry("Location min distance", "${location.minUpdateDistanceMeters} m"),
        )
    }
}

// One missed 60 Hz vsync (>2 frame periods) marks the interval as delayed.
private const val DELAYED_FRAME_MS = 32L

// getThermalHeadroom forecast window; 10 s is the documented typical use.
private const val HEADROOM_FORECAST_SECONDS = 10

private const val BYTES_PER_MB = 1024L * 1024L
private const val KB_PER_MB = 1024L
private const val MS_PER_MINUTE = 60_000L

package io.github.seijikohara.femto.data.system

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.view.Choreographer
import android.webkit.WebView
import androidx.core.content.getSystemService
import io.github.seijikohara.femto.data.display.DisplayPreferences
import io.github.seijikohara.femto.data.location.LocationPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * How a thermal level reads on the diagnostics surface. Mirrors
 * [PowerManager]'s `THERMAL_STATUS_*` ladder; [UNKNOWN] covers a missing
 * service. [isThrottling] marks the levels where the platform is already
 * slowing the device — the first suspect when "everything got sluggish".
 */
internal enum class ThermalLevel(
    val isThrottling: Boolean,
) {
    NONE(false),
    LIGHT(false),
    MODERATE(true),
    SEVERE(true),
    CRITICAL(true),
    EMERGENCY(true),
    SHUTDOWN(true),
    UNKNOWN(false),
}

/** UI-thread frame-delivery stats from a short [Choreographer] sample. */
internal data class FrameStats(
    val sampledFrames: Int,
    val medianMs: Long,
    val worstMs: Long,
    val delayedPercent: Int,
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

/**
 * Collects the performance snapshot: thermal state, memory pressure, process /
 * device uptime, a short UI-thread frame sample, the WebView provider version,
 * and the active map/location settings. Exists for the same reason as
 * [DiagnosticsRepository]: deployed head units are rarely adb-reachable, and
 * "the launcher feels sluggish" needs numbers — is the device throttling, out
 * of memory, long-uptime, or configured into a heavy map — before any code
 * path is worth suspecting.
 */
internal class PerformanceProbe(
    private val context: Context,
) {
    suspend fun snapshot(): PerformanceSnapshot {
        // The frame sample must run where the Choreographer of the UI thread
        // lives; everything else is plain reads moved off the main thread.
        val frameStats = sampleFrameStats()
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

    // Capture vsync-callback intervals on the main thread for a short window.
    // postFrameCallback re-arms every vsync whether or not content changed, so
    // a stretched interval means the MAIN THREAD was busy past its deadline —
    // the direct cause of "taps feel laggy" — independent of GPU load.
    private suspend fun sampleFrameStats(): FrameStats? =
        withContext(Dispatchers.Main) {
            val intervals = mutableListOf<Long>()
            // The Choreographer goes quiet when the display sleeps or the host
            // is backgrounded; without a ceiling the whole snapshot() — and the
            // Refresh button — would hang on it. On timeout the partial sample
            // still reduces to stats (an empty one to null).
            withTimeoutOrNull(FRAME_SAMPLE_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val choreographer = Choreographer.getInstance()
                    val callback =
                        object : Choreographer.FrameCallback {
                            var previousNanos = 0L

                            override fun doFrame(frameTimeNanos: Long) {
                                if (previousNanos != 0L) {
                                    intervals += (frameTimeNanos - previousNanos) / NANOS_PER_MS
                                }
                                previousNanos = frameTimeNanos
                                if (intervals.size < FRAME_SAMPLE_COUNT) {
                                    choreographer.postFrameCallback(this)
                                } else {
                                    continuation.resume(Unit)
                                }
                            }
                        }
                    choreographer.postFrameCallback(callback)
                    continuation.invokeOnCancellation { choreographer.removeFrameCallback(callback) }
                }
            }
            computeFrameStats(intervals)
        }

    // Labels stay English on the SCREEN as well as in the report: the entry
    // list is a debug artifact shared verbatim with the unlocalized Markdown
    // report, where stable machine-greppable wording is the contract.
    private suspend fun mapSettingEntries(): List<DiagnosticEntry> {
        val display = DisplayPreferences(context).settings.first()
        val location = LocationPreferences(context).settings.first()
        return listOf(
            DiagnosticEntry("Map render mode", display.mapRenderMode.name),
            DiagnosticEntry("3D buildings / terrain", "${display.map3dBuildings} / ${display.mapTerrain}"),
            DiagnosticEntry("Map zoom / tilt", "z${display.mapZoom} / ${display.mapTiltDeg}°"),
            DiagnosticEntry("Snapshot render percent", "${display.mapRenderPercent}%"),
            DiagnosticEntry("Location quality", location.quality.name),
            DiagnosticEntry("Location interval", "${location.intervalMillis} ms"),
            DiagnosticEntry("Location min distance", "${location.minUpdateDistanceMeters} m"),
        )
    }
}

private fun Int?.toThermalLevel(): ThermalLevel =
    when (this) {
        PowerManager.THERMAL_STATUS_NONE -> ThermalLevel.NONE
        PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.LIGHT
        PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
        PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
        PowerManager.THERMAL_STATUS_CRITICAL -> ThermalLevel.CRITICAL
        PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalLevel.EMERGENCY
        PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalLevel.SHUTDOWN
        else -> ThermalLevel.UNKNOWN
    }

/**
 * Reduce sampled vsync intervals to [FrameStats]; null when the sample is
 * empty (the Choreographer never delivered — e.g. the probe ran while the
 * host was backgrounded). Pure, so the maths is pinned by JVM tests.
 */
internal fun computeFrameStats(intervalsMs: List<Long>): FrameStats? {
    if (intervalsMs.isEmpty()) return null
    val sorted = intervalsMs.sorted()
    return FrameStats(
        sampledFrames = intervalsMs.size,
        medianMs = sorted[sorted.size / 2],
        worstMs = sorted.last(),
        delayedPercent = intervalsMs.count { it > DELAYED_FRAME_MS } * 100 / intervalsMs.size,
    )
}

// ~2 s of vsync callbacks at 60 Hz; long enough to catch periodic stalls,
// short enough that Refresh stays snappy. The timeout bounds a stalled or
// silent Choreographer while leaving generous room for a janky device to
// finish the full sample.
private const val FRAME_SAMPLE_COUNT = 120
private const val FRAME_SAMPLE_TIMEOUT_MS = 10_000L

// One missed 60 Hz vsync (>2 frame periods) marks the interval as delayed.
private const val DELAYED_FRAME_MS = 32L

// getThermalHeadroom forecast window; 10 s is the documented typical use.
private const val HEADROOM_FORECAST_SECONDS = 10

private const val BYTES_PER_MB = 1024L * 1024L
private const val KB_PER_MB = 1024L
private const val MS_PER_MINUTE = 60_000L
private const val NANOS_PER_MS = 1_000_000L

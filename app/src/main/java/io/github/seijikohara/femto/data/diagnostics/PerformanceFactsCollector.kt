package io.github.seijikohara.femto.data.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.view.Choreographer
import android.view.Display
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
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

internal fun Int?.toThermalLevel(): ThermalLevel =
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
 * host was backgrounded). An interval above [delayedThresholdMs] counts as
 * delayed. Pure, so the maths is pinned by JVM tests.
 */
internal fun computeFrameStats(
    intervalsMs: List<Long>,
    delayedThresholdMs: Long,
): FrameStats? {
    if (intervalsMs.isEmpty()) return null
    val sorted = intervalsMs.sorted()
    return FrameStats(
        sampledFrames = intervalsMs.size,
        medianMs = sorted[sorted.size / 2],
        worstMs = sorted.last(),
        delayedPercent = intervalsMs.count { it > delayedThresholdMs } * 100 / intervalsMs.size,
    )
}

// Capture vsync-callback intervals on the main thread for a short window.
// postFrameCallback re-arms every vsync whether or not content changed, so
// a stretched interval means the MAIN THREAD was busy past its deadline —
// the direct cause of "taps feel laggy" — independent of GPU load.
private suspend fun sampleFrameStats(delayedThresholdMs: Long): FrameStats? =
    withContext(Dispatchers.Main) {
        val intervals = mutableListOf<Long>()
        // The Choreographer goes quiet when the display sleeps or the host
        // is backgrounded; without a ceiling the whole collection — and the
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
        computeFrameStats(intervals, delayedThresholdMs)
    }

/**
 * Collects the PERFORMANCE diagnostics section: thermal state, memory
 * pressure, process / device uptime, a short UI-thread frame sample, GC and
 * battery state. Exists because "the launcher feels sluggish" needs numbers —
 * is the device throttling, out of memory, or long-uptime — before any code
 * path is worth suspecting.
 */
internal class PerformanceFactsCollector(
    private val context: Context,
) {
    suspend fun performanceFacts(): SectionPayload.Facts {
        // The frame sample must run where the Choreographer of the UI thread
        // lives; everything else is plain reads moved off the main thread.
        val frameStats = sampleFrameStats(delayedFrameThresholdMs())
        return withContext(Dispatchers.IO) {
            val power = context.getSystemService<PowerManager>()
            val activityManager = context.getSystemService<ActivityManager>()!!
            val memory = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
            SectionPayload.Facts(
                listOf(
                    thermalFact(power),
                    DiagnosticFact(
                        "Power save",
                        FactValue.Text(if (power?.isPowerSaveMode == true) "ON" else "off"),
                    ),
                    deviceMemoryFact(memory),
                    appMemoryFact(),
                    uptimeFact(),
                    frameStatsFact(frameStats),
                    memoryClassFact(activityManager),
                    gcFact(),
                    batteryFact(),
                    dozeFact(power),
                ),
            )
        }
    }

    // Two vsync periods at the panel's actual refresh rate mark an interval
    // as delayed (generalizing the v1 60 Hz-only 32 ms constant); the floor
    // keeps a misreported high refresh rate from flagging every frame.
    private fun delayedFrameThresholdMs(): Long =
        context
            .getSystemService<DisplayManager>()
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?.refreshRate
            ?.takeIf { it.isFinite() && it > 0f }
            .let { refreshRate -> (2_000f / (refreshRate ?: FALLBACK_REFRESH_RATE_HZ)).toLong() }
            .coerceAtLeast(MIN_DELAYED_THRESHOLD_MS)

    private fun thermalFact(power: PowerManager?): DiagnosticFact {
        val thermal = power?.currentThermalStatus.toThermalLevel()
        // Locale.ROOT keeps the decimal point: the report's grep-stable
        // wording contract must hold on comma-decimal devices too.
        val headroom =
            power
                ?.getThermalHeadroom(HEADROOM_FORECAST_SECONDS)
                ?.takeIf { it.isFinite() }
                ?.let { " (headroom %.2f)".format(Locale.ROOT, it) }
                .orEmpty()
        return DiagnosticFact(
            "Thermal",
            FactValue.Status(
                "${thermal.name}$headroom",
                if (thermal.isThrottling) FactHealth.WARNING else FactHealth.OK,
            ),
        )
    }

    private fun deviceMemoryFact(memory: ActivityManager.MemoryInfo): DiagnosticFact {
        val availMb = memory.availMem / BYTES_PER_MB
        val totalMb = memory.totalMem / BYTES_PER_MB
        return DiagnosticFact(
            "Device memory",
            if (memory.lowMemory) {
                FactValue.Status("$availMb / $totalMb MB free (low memory)", FactHealth.WARNING)
            } else {
                FactValue.Text("$availMb / $totalMb MB free")
            },
        )
    }

    private fun appMemoryFact(): DiagnosticFact {
        val runtime = Runtime.getRuntime()
        val pssMb = Debug.getPss() / KB_PER_MB
        val heapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB
        val heapMaxMb = runtime.maxMemory() / BYTES_PER_MB
        val nativeMb = Debug.getNativeHeapAllocatedSize() / BYTES_PER_MB
        return DiagnosticFact(
            "App memory",
            FactValue.Text("PSS $pssMb MB, Java heap $heapUsedMb/$heapMaxMb MB, native $nativeMb MB"),
        )
    }

    private fun uptimeFact(): DiagnosticFact {
        val processMinutes =
            (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()) / MS_PER_MINUTE
        val deviceMinutes = SystemClock.elapsedRealtime() / MS_PER_MINUTE
        return DiagnosticFact(
            "Uptime",
            FactValue.Text("process ${processMinutes.asUptime()}, device ${deviceMinutes.asUptime()}"),
        )
    }

    private fun frameStatsFact(frameStats: FrameStats?): DiagnosticFact =
        frameStats?.let { frames ->
            val value = "median ${frames.medianMs} ms, worst ${frames.worstMs} ms, delayed ${frames.delayedPercent}%"
            DiagnosticFact(
                "UI frames (${frames.sampledFrames} sampled)",
                if (frames.delayedPercent >= DELAYED_PERCENT_WARNING) {
                    FactValue.Status(value, FactHealth.WARNING)
                } else {
                    FactValue.Text(value)
                },
            )
        } ?: DiagnosticFact("UI frames", FactValue.Text("sample unavailable"))

    private fun memoryClassFact(activityManager: ActivityManager): DiagnosticFact {
        val value =
            "${activityManager.memoryClass} MB (large ${activityManager.largeMemoryClass} MB" +
                "${if (activityManager.isLowRamDevice) ", low RAM" else ""})"
        return DiagnosticFact(
            "Memory class",
            if (activityManager.isLowRamDevice) {
                FactValue.Status(value, FactHealth.WARNING)
            } else {
                FactValue.Text(value)
            },
        )
    }

    private fun gcFact(): DiagnosticFact {
        val count = Debug.getRuntimeStat("art.gc.gc-count")
        val blocking = Debug.getRuntimeStat("art.gc.blocking-gc-count")
        return DiagnosticFact("GC", FactValue.Text("$count collections ($blocking blocking)"))
    }

    // A null receiver + the sticky ACTION_BATTERY_CHANGED broadcast reads the
    // battery state without registering anything. Head units typically report
    // no battery at all — itself a useful datum (the device dies with ignition).
    private fun batteryFact(): DiagnosticFact {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val value =
            when {
                intent == null -> {
                    "unknown"
                }

                !intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true) -> {
                    "none (vehicle powered)"
                }

                else -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                    "$level%, ${intent.plugLabel()}, ${temp / 10.0}°C"
                }
            }
        return DiagnosticFact("Battery", FactValue.Text(value))
    }

    private fun Intent.plugLabel(): String =
        when (getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "dock"
            else -> "unplugged"
        }

    private fun dozeFact(power: PowerManager?): DiagnosticFact =
        DiagnosticFact(
            "Doze / location power save",
            FactValue.Text("${power?.isDeviceIdleMode == true} / ${power?.locationPowerSaveMode ?: "unknown"}"),
        )
}

// "3d 7h" / "5h 4m" / "12m" — coarse on purpose; uptime is a suspect ranking
// signal, not a stopwatch.
private fun Long.asUptime(): String {
    val days = this / MINUTES_PER_DAY
    val hours = this % MINUTES_PER_DAY / MINUTES_PER_HOUR
    val minutes = this % MINUTES_PER_HOUR
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

// ~2 s of vsync callbacks at 60 Hz; long enough to catch periodic stalls,
// short enough that Refresh stays snappy. The timeout bounds a stalled or
// silent Choreographer while leaving generous room for a janky device to
// finish the full sample.
private const val FRAME_SAMPLE_COUNT = 120
private const val FRAME_SAMPLE_TIMEOUT_MS = 10_000L

// The refresh-derived delayed bar never drops below two 100 Hz periods, and
// a display that reports no usable refresh rate falls back to 60 Hz.
private const val MIN_DELAYED_THRESHOLD_MS = 20L
private const val FALLBACK_REFRESH_RATE_HZ = 60f

// The v1 screen threshold: a delayed-frame share at or above this flags the
// UI thread as a sluggishness suspect.
private const val DELAYED_PERCENT_WARNING = 10

// getThermalHeadroom forecast window; 10 s is the documented typical use.
private const val HEADROOM_FORECAST_SECONDS = 10

private const val BYTES_PER_MB = 1024L * 1024L
private const val KB_PER_MB = 1024L
private const val MS_PER_MINUTE = 60_000L
private const val NANOS_PER_MS = 1_000_000L
private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 24L * 60L

package io.github.seijikohara.femto.ui.diagnostics

import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.system.PerformanceSnapshot
import java.util.Locale

/**
 * Render the diagnostics state as a Markdown report for the clipboard.
 *
 * Markdown because the report's destination is an issue tracker or a chat —
 * headings, the permissions table, and the fenced log block survive the paste
 * with their structure intact, and the fenced block keeps log lines grep-able
 * as plain text. Deliberately English and unlocalized: stable
 * machine-greppable wording beats locale fidelity in a debug artifact. Pure,
 * so the exact shape is pinned by JVM tests.
 */
internal fun diagnosticsReport(uiState: DiagnosticsUiState): String =
    buildString {
        appendLine("# Femto Car Launcher diagnostics")
        appendLine()
        uiState.snapshot?.let { snapshot ->
            appendLine("- App: ${snapshot.appVersion}")
            appendLine(
                "- Device: ${snapshot.deviceModel} / Android ${snapshot.androidRelease} (API ${snapshot.sdkInt})",
            )
            appendLine()
            appendLine("## Permissions")
            appendLine()
            appendLine("| Permission | State |")
            appendLine("| --- | --- |")
            snapshot.permissions.forEach { state ->
                appendLine(
                    "| ${state.permission.substringAfterLast('.')} | ${if (state.granted) "granted" else "DENIED"} |",
                )
            }
            appendLine(
                "| Notification listener | ${if (snapshot.notificationListenerEnabled) "enabled" else "DISABLED"} |",
            )
            appendLine()
            appendLine("## Network")
            appendLine()
            appendLine(
                if (snapshot.networkOnline) {
                    "- Online (${snapshot.networkTransports.joinToString().ifEmpty { "unknown transport" }})"
                } else {
                    "- OFFLINE"
                },
            )
        } ?: appendLine("Snapshot UNAVAILABLE (collection failed; see app logs)")
        appendLine()
        appendLine("## Music")
        appendLine()
        appendLine("- Session: ${uiState.musicState.described()}")
        appendLine("- Spectrum capture: ${uiState.spectrum?.name ?: "not probed"}")
        uiState.performance?.let { appendPerformance(it) }
        uiState.snapshot?.recentWarningLogs?.let { logs ->
            appendLine()
            appendLine("## Recent warnings (${logs.size})")
            appendLine()
            appendLine("```text")
            logs.forEach(::appendLine)
            appendLine("```")
        }
    }

private fun StringBuilder.appendPerformance(performance: PerformanceSnapshot) {
    appendLine()
    appendLine("## Performance")
    appendLine()
    // Locale.ROOT keeps the decimal point: the report's grep-stable wording
    // contract must hold on comma-decimal devices too.
    val headroom =
        performance.thermalHeadroom
            ?.let { " (headroom %.2f)".format(Locale.ROOT, it) }
            .orEmpty()
    appendLine("- Thermal: ${performance.thermal.name}$headroom")
    appendLine("- Power save: ${if (performance.powerSaveMode) "ON" else "off"}")
    appendLine(
        "- Device memory: ${performance.availMemMb} / ${performance.totalMemMb} MB free" +
            if (performance.lowMemory) " (LOW MEMORY)" else "",
    )
    appendLine(
        "- App memory: PSS ${performance.appPssMb} MB, " +
            "Java heap ${performance.javaHeapUsedMb}/${performance.javaHeapMaxMb} MB, " +
            "native ${performance.nativeHeapMb} MB",
    )
    appendLine(
        "- Uptime: process ${performance.processUptimeMinutes.asUptime()}, " +
            "device ${performance.deviceUptimeMinutes.asUptime()}",
    )
    appendLine(
        performance.frameStats?.let { frames ->
            "- UI frames (${frames.sampledFrames} sampled): median ${frames.medianMs} ms, " +
                "worst ${frames.worstMs} ms, delayed ${frames.delayedPercent}%"
        } ?: "- UI frames: sample unavailable",
    )
    appendLine("- WebView: ${performance.webViewVersion ?: "unknown"}")
    appendLine()
    appendLine("## Map settings")
    appendLine()
    performance.mapSettings.forEach { entry ->
        appendLine("- ${entry.label}: ${entry.value}")
    }
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

private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 24L * 60L

/** One-line session description shared by the report and the screen row. */
internal fun MusicCardState?.described(): String =
    when (this) {
        is MusicCardState.Playing -> {
            "${nowPlaying.packageName} (${if (nowPlaying.isPlaying) "playing" else "paused"})"
        }

        MusicCardState.NoActiveSession -> {
            "no active session"
        }

        MusicCardState.NeedsPermission -> {
            "notification listener NOT granted"
        }

        null -> {
            "unknown"
        }
    }

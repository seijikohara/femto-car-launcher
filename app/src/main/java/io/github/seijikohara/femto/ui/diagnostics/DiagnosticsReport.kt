package io.github.seijikohara.femto.ui.diagnostics

import io.github.seijikohara.femto.data.music.MusicCardState

/**
 * Render the diagnostics state as a plain-text report for the clipboard.
 *
 * Deliberately English and unlocalized: the report is a debug artifact meant
 * to be pasted into an issue or a remote debugging session, where stable
 * machine-greppable wording beats locale fidelity. Pure, so the exact shape
 * is pinned by JVM tests.
 */
internal fun diagnosticsReport(uiState: DiagnosticsUiState): String =
    buildString {
        appendLine("== Femto Car Launcher diagnostics ==")
        uiState.snapshot?.let { snapshot ->
            appendLine("App: ${snapshot.appVersion}")
            appendLine("Device: ${snapshot.deviceModel} / Android ${snapshot.androidRelease} (API ${snapshot.sdkInt})")
            appendLine("-- Permissions --")
            snapshot.permissions.forEach { state ->
                appendLine("${state.permission.substringAfterLast('.')}: ${if (state.granted) "granted" else "DENIED"}")
            }
            appendLine("Notification listener: ${if (snapshot.notificationListenerEnabled) "enabled" else "DISABLED"}")
            appendLine("-- Network --")
            appendLine(
                if (snapshot.networkOnline) {
                    "Online (${snapshot.networkTransports.joinToString().ifEmpty { "unknown transport" }})"
                } else {
                    "OFFLINE"
                },
            )
        } ?: appendLine("Snapshot UNAVAILABLE (collection failed; see app logs)")
        appendLine("-- Music --")
        appendLine("Session: ${uiState.musicState.described()}")
        appendLine("Spectrum capture: ${uiState.spectrum?.name ?: "not probed"}")
        uiState.snapshot?.recentWarningLogs?.let { logs ->
            appendLine("-- Recent warnings (${logs.size}) --")
            logs.forEach(::appendLine)
        }
    }

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

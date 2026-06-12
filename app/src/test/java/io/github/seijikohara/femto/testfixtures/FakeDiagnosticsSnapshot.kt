package io.github.seijikohara.femto.testfixtures

import android.Manifest
import io.github.seijikohara.femto.data.system.DiagnosticPermission
import io.github.seijikohara.femto.data.system.DiagnosticsSnapshot

internal fun fakeDiagnosticsSnapshot(
    permissions: List<DiagnosticPermission> =
        listOf(
            DiagnosticPermission(Manifest.permission.ACCESS_FINE_LOCATION, granted = true),
            DiagnosticPermission(Manifest.permission.RECORD_AUDIO, granted = false),
        ),
    notificationListenerEnabled: Boolean = true,
    networkOnline: Boolean = true,
    networkTransports: List<String> = listOf("Wi-Fi"),
    recentWarningLogs: List<String> = listOf("06-12 12:00:00.000 W/AudioSpectrumRepo: sample warning"),
): DiagnosticsSnapshot =
    DiagnosticsSnapshot(
        appVersion = "1.0 (debug)",
        deviceModel = "Acme TBox",
        androidRelease = "13",
        sdkInt = 33,
        permissions = permissions,
        notificationListenerEnabled = notificationListenerEnabled,
        networkOnline = networkOnline,
        networkTransports = networkTransports,
        recentWarningLogs = recentWarningLogs,
    )

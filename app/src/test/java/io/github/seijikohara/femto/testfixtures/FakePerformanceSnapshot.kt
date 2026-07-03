package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.diagnostics.DiagnosticEntry
import io.github.seijikohara.femto.data.diagnostics.FrameStats
import io.github.seijikohara.femto.data.diagnostics.PerformanceSnapshot
import io.github.seijikohara.femto.data.diagnostics.ThermalLevel

internal fun fakePerformanceSnapshot(
    thermal: ThermalLevel = ThermalLevel.NONE,
    lowMemory: Boolean = false,
    frameStats: FrameStats? = FrameStats(sampledFrames = 120, medianMs = 16, worstMs = 48, delayedPercent = 4),
): PerformanceSnapshot =
    PerformanceSnapshot(
        thermal = thermal,
        thermalHeadroom = 0.42f,
        powerSaveMode = false,
        availMemMb = 1024,
        totalMemMb = 3962,
        lowMemory = lowMemory,
        appPssMb = 210,
        javaHeapUsedMb = 48,
        javaHeapMaxMb = 256,
        nativeHeapMb = 88,
        processUptimeMinutes = 133,
        deviceUptimeMinutes = 7444,
        frameStats = frameStats,
        webViewVersion = "com.google.android.webview 110.0.5481.154",
        mapSettings =
            listOf(
                DiagnosticEntry("Map render mode", "LIVE"),
                DiagnosticEntry("Location interval", "1000 ms"),
            ),
    )

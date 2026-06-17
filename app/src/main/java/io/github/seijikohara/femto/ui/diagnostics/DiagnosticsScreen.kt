package io.github.seijikohara.femto.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.music.SpectrumDiagnosis
import io.github.seijikohara.femto.data.system.DiagnosticsSnapshot
import io.github.seijikohara.femto.data.system.PerformanceSnapshot
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.monoReference
import java.util.Locale

/**
 * Per-feature health readout: which gate (grant, listener, capture engine,
 * network) is blocking which dashboard feature, plus the app's own recent
 * warning logs. The launcher degrades silently by design, and deployed head
 * units are rarely adb-reachable — this screen plus the copyable report is
 * the remote-debugging surface.
 */
@Composable
internal fun DiagnosticsScreen(
    uiState: DiagnosticsUiState,
    onAction: (DiagnosticsAction) -> Unit,
    onBack: () -> Unit,
    onCopyReport: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier =
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
) {
    Header(onBack = onBack)
    ActionButtons(
        isLoading = uiState.isLoading,
        onAction = onAction,
        onCopyReport = onCopyReport,
    )
    // The music rows render outside the snapshot gate: the probes degrade
    // independently in the ViewModel, and a broken snapshot collector must
    // not hide the spectrum verdict — the broken device is exactly where
    // this screen earns its keep.
    uiState.snapshot?.let { snapshot ->
        BuildSection(snapshot)
        PermissionsSection(snapshot)
        MusicSection(uiState)
        NetworkSection(snapshot)
        uiState.performance?.let { PerformanceSection(it) }
        LogsSection(snapshot.recentWarningLogs)
    } ?: run {
        Text(
            text =
                stringResource(
                    if (uiState.isLoading) R.string.diagnostics_loading else R.string.diagnostics_unavailable,
                ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MusicSection(uiState)
    }
}

@Composable
private fun Header(onBack: () -> Unit) =
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(FemtoDimens.MinTouchTarget)) {
            Icon(
                imageVector = Lucide.ArrowLeft,
                contentDescription = stringResource(R.string.settings_back),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = stringResource(R.string.diagnostics_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }

@Composable
private fun ActionButtons(
    isLoading: Boolean,
    onAction: (DiagnosticsAction) -> Unit,
    onCopyReport: () -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    Button(
        onClick = { onAction(DiagnosticsAction.Refresh) },
        enabled = !isLoading,
        modifier = Modifier.weight(1f).heightIn(min = FemtoDimens.MinTouchTarget),
    ) {
        Text(
            text =
                stringResource(
                    if (isLoading) R.string.diagnostics_loading else R.string.diagnostics_refresh,
                ),
        )
    }
    OutlinedButton(
        onClick = onCopyReport,
        modifier = Modifier.weight(1f).heightIn(min = FemtoDimens.MinTouchTarget),
    ) {
        Text(text = stringResource(R.string.diagnostics_copy))
    }
}

@Composable
private fun BuildSection(snapshot: DiagnosticsSnapshot) =
    Section(title = stringResource(R.string.diagnostics_section_build)) {
        ValueRow(label = snapshot.appVersion)
        ValueRow(label = "${snapshot.deviceModel} / Android ${snapshot.androidRelease} (API ${snapshot.sdkInt})")
    }

@Composable
private fun PermissionsSection(snapshot: DiagnosticsSnapshot) =
    Section(title = stringResource(R.string.diagnostics_section_permissions)) {
        snapshot.permissions.forEach { state ->
            StatusRow(
                label = state.permission.substringAfterLast('.'),
                value =
                    stringResource(
                        if (state.granted) R.string.diagnostics_granted else R.string.diagnostics_denied,
                    ),
                healthy = state.granted,
            )
        }
        StatusRow(
            label = stringResource(R.string.diagnostics_listener),
            value =
                stringResource(
                    if (snapshot.notificationListenerEnabled) {
                        R.string.diagnostics_enabled
                    } else {
                        R.string.diagnostics_disabled
                    },
                ),
            healthy = snapshot.notificationListenerEnabled,
        )
    }

@Composable
private fun MusicSection(uiState: DiagnosticsUiState) =
    Section(title = stringResource(R.string.diagnostics_section_music)) {
        ValueRow(label = stringResource(R.string.diagnostics_music_session, uiState.musicState.described()))
        StatusRow(
            label = stringResource(R.string.diagnostics_spectrum),
            value = spectrumLabel(uiState.spectrum),
            healthy = uiState.spectrum == SpectrumDiagnosis.ACTIVE,
        )
        Text(
            text = stringResource(R.string.diagnostics_spectrum_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

@Composable
private fun NetworkSection(snapshot: DiagnosticsSnapshot) =
    Section(title = stringResource(R.string.diagnostics_section_network)) {
        StatusRow(
            label = stringResource(R.string.diagnostics_network),
            value =
                if (snapshot.networkOnline) {
                    stringResource(R.string.diagnostics_online, snapshot.networkTransports.joinToString())
                } else {
                    stringResource(R.string.diagnostics_offline)
                },
            healthy = snapshot.networkOnline,
        )
    }

@Composable
private fun PerformanceSection(performance: PerformanceSnapshot) {
    Section(title = stringResource(R.string.diagnostics_section_performance)) {
        StatusRow(
            label = stringResource(R.string.diagnostics_thermal),
            value =
                performance.thermalHeadroom
                    ?.let {
                        stringResource(
                            R.string.diagnostics_thermal_headroom,
                            performance.thermal.name,
                            "%.2f".format(Locale.ROOT, it),
                        )
                    } ?: performance.thermal.name,
            healthy = !performance.thermal.isThrottling,
        )
        StatusRow(
            label = stringResource(R.string.diagnostics_device_memory),
            value = "${performance.availMemMb} / ${performance.totalMemMb} MB",
            healthy = !performance.lowMemory,
        )
        ValueRow(
            label =
                stringResource(
                    R.string.diagnostics_app_memory,
                    performance.appPssMb,
                    performance.javaHeapUsedMb,
                    performance.javaHeapMaxMb,
                    performance.nativeHeapMb,
                ),
        )
        ValueRow(
            label =
                stringResource(
                    R.string.diagnostics_uptime,
                    performance.processUptimeMinutes / 60,
                    performance.processUptimeMinutes % 60,
                    performance.deviceUptimeMinutes / 60,
                    performance.deviceUptimeMinutes % 60,
                ),
        )
        performance.frameStats?.let { frames ->
            StatusRow(
                label = stringResource(R.string.diagnostics_frames),
                value =
                    stringResource(
                        R.string.diagnostics_frames_value,
                        frames.medianMs,
                        frames.worstMs,
                        frames.delayedPercent,
                    ),
                healthy = frames.delayedPercent < DELAYED_HEALTHY_MAX_PERCENT,
            )
        }
        ValueRow(
            label =
                performance.webViewVersion
                    ?.let { stringResource(R.string.diagnostics_webview_value, it) }
                    ?: stringResource(R.string.diagnostics_webview_unknown),
        )
    }
    Section(title = stringResource(R.string.diagnostics_section_map_settings)) {
        performance.mapSettings.forEach { entry ->
            ValueRow(label = "${entry.label}: ${entry.value}")
        }
    }
}

@Composable
private fun LogsSection(logs: List<String>) =
    Section(title = stringResource(R.string.diagnostics_section_logs, logs.size)) {
        if (logs.isEmpty()) {
            Text(
                text = stringResource(R.string.diagnostics_logs_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            logs.forEach { line ->
                // Log lines are glance metadata, not dashboard body text, so
                // they take the sanctioned GlanceTextSize relaxation.
                Text(
                    text = line,
                    style = MaterialTheme.typography.monoReference(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
        text = title,
        // Same section-title voice as the settings sections this sheet
        // opens from.
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    content()
}

@Composable
private fun ValueRow(label: String) =
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )

@Composable
private fun StatusRow(
    label: String,
    value: String,
    healthy: Boolean,
) = Row(modifier = Modifier.fillMaxWidth()) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        // The label ellipsizes (it is the fixed caption); the value keeps its
        // intrinsic width — it is the diagnostic payload and must stay readable.
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    Text(
        text = value,
        style = MaterialTheme.typography.bodyLarge,
        color = if (healthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun spectrumLabel(diagnosis: SpectrumDiagnosis?): String =
    stringResource(
        when (diagnosis) {
            SpectrumDiagnosis.ACTIVE -> R.string.diagnostics_spectrum_active
            SpectrumDiagnosis.SILENT -> R.string.diagnostics_spectrum_silent
            SpectrumDiagnosis.ENGINE_UNAVAILABLE -> R.string.diagnostics_spectrum_engine
            SpectrumDiagnosis.NO_PERMISSION -> R.string.diagnostics_spectrum_no_permission
            null -> R.string.diagnostics_spectrum_not_probed
        },
    )

// A delayed-frame share below this reads as healthy on the status row; above
// it the row flags the UI thread as a sluggishness suspect.
private const val DELAYED_HEALTHY_MAX_PERCENT = 10

@PreviewLightDark
@Composable
private fun DiagnosticsScreenPreview() {
    FemtoTheme {
        DiagnosticsScreen(
            uiState = DiagnosticsUiState.Initial,
            onAction = {},
            onBack = {},
            onCopyReport = {},
        )
    }
}

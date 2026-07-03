package io.github.seijikohara.femto.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.diagnostics.DiagnosticFact
import io.github.seijikohara.femto.data.diagnostics.DiagnosticSection
import io.github.seijikohara.femto.data.diagnostics.FactHealth
import io.github.seijikohara.femto.data.diagnostics.FactValue
import io.github.seijikohara.femto.data.diagnostics.SectionId
import io.github.seijikohara.femto.data.diagnostics.SectionPayload
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.monoReference

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
    modifier: Modifier = Modifier,
) {
    // Interim rendering; replaced by the collapsible UI in the next task.
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Header(onBack = onBack) }
        item { ActionButtons(copyConfirmed = uiState.copyConfirmed, onAction = onAction) }
        items(uiState.sections, key = { it.id.name }) { section -> SectionBlock(section) }
    }
}

@Composable
private fun Header(onBack: () -> Unit) =
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(FemtoDimens.MinTouchTarget)) {
            FemtoIcon(
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
    copyConfirmed: Boolean,
    onAction: (DiagnosticsAction) -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    Button(
        onClick = { onAction(DiagnosticsAction.Refresh) },
        modifier = Modifier.weight(1f).heightIn(min = FemtoDimens.MinTouchTarget),
    ) {
        Text(text = stringResource(R.string.diagnostics_refresh))
    }
    OutlinedButton(
        onClick = { onAction(DiagnosticsAction.CopyReport) },
        modifier = Modifier.weight(1f).heightIn(min = FemtoDimens.MinTouchTarget),
    ) {
        Text(
            text = stringResource(if (copyConfirmed) R.string.diagnostics_copied else R.string.diagnostics_copy),
        )
    }
}

@Composable
private fun SectionBlock(section: DiagnosticSection) =
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = section.id.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        when (val payload = section.payload) {
            null -> {
                FactLine(stringResource(R.string.diagnostics_loading))
            }

            SectionPayload.Unavailable -> {
                FactLine(stringResource(R.string.diagnostics_unavailable))
            }

            is SectionPayload.Facts -> {
                payload.facts.forEach { FactLine(it.line()) }
            }

            is SectionPayload.PermissionTable -> {
                payload.rows.forEach { row ->
                    FactLine("${row.name}: ${if (row.granted) "granted" else "DENIED"}")
                }
                payload.extras.forEach { FactLine(it.line()) }
            }

            is SectionPayload.LogTail -> {
                payload.lines.forEach { line ->
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
    }

@Composable
private fun FactLine(text: String) =
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )

// The fact model is deliberately English and unlocalized (see
// DiagnosticsModel.kt); the interim rows render its raw tokens.
private fun DiagnosticFact.line(): String =
    "$label: " +
        when (val rendered = value) {
            is FactValue.Text -> rendered.value
            is FactValue.Status -> rendered.value
        }

@PreviewLightDark
@Composable
private fun DiagnosticsScreenPreview() {
    FemtoTheme {
        DiagnosticsScreen(
            uiState =
                DiagnosticsUiState(
                    sections =
                        listOf(
                            DiagnosticSection(
                                SectionId.APP,
                                SectionPayload.Facts(listOf(DiagnosticFact("App", FactValue.Text("1.0 (debug)")))),
                            ),
                            DiagnosticSection(
                                SectionId.NETWORK,
                                SectionPayload.Facts(
                                    listOf(DiagnosticFact("Online", FactValue.Status("OFFLINE", FactHealth.ERROR))),
                                ),
                            ),
                            DiagnosticSection(SectionId.LOGS, null),
                        ),
                ),
            onAction = {},
            onBack = {},
        )
    }
}

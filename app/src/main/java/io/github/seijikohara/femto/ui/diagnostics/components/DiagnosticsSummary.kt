package io.github.seijikohara.femto.ui.diagnostics.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.diagnostics.DiagnosticSection
import io.github.seijikohara.femto.data.diagnostics.issueCount
import io.github.seijikohara.femto.ui.diagnostics.DiagnosticsAction
import io.github.seijikohara.femto.ui.diagnostics.DiagnosticsUiState
import io.github.seijikohara.femto.ui.theme.FemtoDimens

/**
 * Health TL;DR above the section list: the issue-count verdict, the
 * refresh/copy actions, and the problems-only filter chip.
 */
@Composable
internal fun DiagnosticsSummary(
    uiState: DiagnosticsUiState,
    onAction: (DiagnosticsAction) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
) {
    SummaryLine(sections = uiState.sections)
    ActionButtons(copyConfirmed = uiState.copyConfirmed, onAction = onAction)
    FilterChip(
        selected = uiState.problemsOnly,
        onClick = { onAction(DiagnosticsAction.ToggleProblemsOnly) },
        label = { Text(text = stringResource(R.string.diagnostics_problems_only)) },
        modifier = Modifier.heightIn(min = FemtoDimens.MinTouchTarget),
    )
}

@Composable
private fun SummaryLine(
    sections: List<DiagnosticSection>,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
) {
    val issueCount = sections.issueCount()
    val collecting = sections.any { it.payload == null }
    when {
        issueCount > 0 -> {
            Text(
                text = stringResource(R.string.diagnostics_summary_issues, issueCount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // The all-clear is only claimable once every collector has reported.
        !collecting -> {
            Text(
                text = stringResource(R.string.diagnostics_summary_ok),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    if (collecting) {
        Text(
            text = stringResource(R.string.diagnostics_summary_collecting),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionButtons(
    copyConfirmed: Boolean,
    onAction: (DiagnosticsAction) -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    Button(
        onClick = { onAction(DiagnosticsAction.Refresh) },
        modifier =
            Modifier
                .weight(1f)
                .heightIn(min = FemtoDimens.MinTouchTarget),
    ) {
        Text(text = stringResource(R.string.diagnostics_refresh))
    }
    OutlinedButton(
        onClick = { onAction(DiagnosticsAction.CopyReport) },
        modifier =
            Modifier
                .weight(1f)
                .heightIn(min = FemtoDimens.MinTouchTarget),
    ) {
        Text(
            text = stringResource(if (copyConfirmed) R.string.diagnostics_copied else R.string.diagnostics_copy),
        )
    }
}

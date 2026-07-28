package io.github.seijikohara.femto.ui.diagnostics.components

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.diagnostics.DiagnosticFact
import io.github.seijikohara.femto.data.diagnostics.DiagnosticSection
import io.github.seijikohara.femto.data.diagnostics.FactHealth
import io.github.seijikohara.femto.data.diagnostics.FactValue
import io.github.seijikohara.femto.data.diagnostics.SectionId
import io.github.seijikohara.femto.data.diagnostics.SectionPayload
import io.github.seijikohara.femto.data.diagnostics.issues
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.monoReference
import kotlinx.coroutines.launch

/**
 * One collapsible diagnostics section: a tap-to-toggle header (title, issue
 * badge, collect spinner, chevron) over the payload rows. Section titles are
 * localized; fact labels and values render the model's raw English tokens —
 * the report's grep contract extends to the screen (see DiagnosticsModel.kt).
 */
@Composable
internal fun DiagnosticsSectionCard(
    section: DiagnosticSection,
    expanded: Boolean,
    problemsOnly: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier.fillMaxWidth()) {
    SectionHeader(section = section, expanded = expanded, onToggle = onToggle)
    if (expanded) {
        SectionBody(section = section, problemsOnly = problemsOnly)
    }
}

/** Map every section id to its localized header title. */
@Composable
internal fun SectionId.title(): String =
    when (this) {
        SectionId.APP -> stringResource(R.string.diagnostics_section_app)

        SectionId.CRASH_HISTORY -> stringResource(R.string.diagnostics_section_crash_history)

        SectionId.DEVICE -> stringResource(R.string.diagnostics_section_device)

        SectionId.DISPLAY -> stringResource(R.string.diagnostics_section_display)

        SectionId.GRAPHICS -> stringResource(R.string.diagnostics_section_graphics)

        SectionId.PERMISSIONS -> stringResource(R.string.diagnostics_section_permissions)

        SectionId.MUSIC -> stringResource(R.string.diagnostics_section_music)

        SectionId.NETWORK -> stringResource(R.string.diagnostics_section_network)

        SectionId.LOCATION -> stringResource(R.string.diagnostics_section_location)

        SectionId.LOCALE_TIME -> stringResource(R.string.diagnostics_section_locale_time)

        SectionId.PERFORMANCE -> stringResource(R.string.diagnostics_section_performance)

        SectionId.STORAGE -> stringResource(R.string.diagnostics_section_storage)

        SectionId.INPUT -> stringResource(R.string.diagnostics_section_input)

        SectionId.WEBVIEW -> stringResource(R.string.diagnostics_section_webview)

        SectionId.MAP -> stringResource(R.string.diagnostics_section_map)

        SectionId.SETTINGS -> stringResource(R.string.diagnostics_section_settings)

        // The tail length is unknowable from the id alone; SectionHeader
        // reformats with the real count once the LogTail payload arrives.
        SectionId.LOGS -> stringResource(R.string.diagnostics_section_logs, 0)
    }

@Composable
private fun SectionHeader(
    section: DiagnosticSection,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val logTail = section.payload as? SectionPayload.LogTail
    val title =
        logTail
            ?.let { stringResource(R.string.diagnostics_section_logs, it.lines.size) }
            ?: section.id.title()
    val toggleDescription =
        stringResource(
            if (expanded) R.string.diagnostics_collapse_section else R.string.diagnostics_expand_section,
            title,
        )
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = FemtoDimens.MinTouchTarget)
                .clickable(onClick = onToggle)
                .semantics { contentDescription = toggleDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            // Same section-title voice as the settings sections this sheet
            // opens from.
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        val issueCount = section.issues().size
        if (issueCount > 0) {
            IssueBadge(count = issueCount)
        }
        if (section.payload == null) {
            val collectingLabel = stringResource(R.string.diagnostics_loading)
            CircularProgressIndicator(
                modifier =
                    Modifier
                        .size(FemtoDimens.InlineIconSize)
                        .semantics { contentDescription = collectingLabel },
            )
        }
        if (section.id == SectionId.LOGS && logTail != null) {
            LogsCopyButton(lines = logTail.lines)
        }
        FemtoIcon(
            imageVector = Lucide.ChevronDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .size(FemtoDimens.InlineIconSize)
                    .rotate(if (expanded) 180f else 0f),
        )
    }
}

@Composable
private fun IssueBadge(
    count: Int,
    modifier: Modifier = Modifier,
) = Text(
    text = count.toString(),
    style = MaterialTheme.typography.bodyLarge,
    // onErrorContainer, not error: M3 pairs error with the base surface, not
    // with errorContainer — the two are close enough in tone on this fill that
    // the count read as low-contrast on the glance surface.
    color = MaterialTheme.colorScheme.onErrorContainer,
    modifier =
        modifier
            .background(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
            ).padding(horizontal = 10.dp, vertical = 2.dp),
)

// Screen-local convenience copy of one section's tail; the full Markdown
// report stays the CopyReport action.
@Composable
private fun LogsCopyButton(
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val description = stringResource(R.string.diagnostics_copy_logs)
    Box(
        modifier =
            modifier
                .size(FemtoDimens.MinTouchTarget)
                .clip(RoundedCornerShape(14.dp))
                .clickable {
                    scope.launch {
                        clipboard.setClipEntry(
                            ClipEntry(ClipData.newPlainText("diagnostics-logs", lines.joinToString("\n"))),
                        )
                    }
                }.semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        FemtoIcon(
            imageVector = Lucide.Copy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(FemtoDimens.InlineIconSize),
        )
    }
}

@Composable
private fun SectionBody(
    section: DiagnosticSection,
    problemsOnly: Boolean,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(6.dp),
) {
    when (val payload = section.payload) {
        null -> {
            BodyHint(stringResource(R.string.diagnostics_section_collecting))
        }

        SectionPayload.Unavailable -> {
            BodyHint(stringResource(R.string.diagnostics_section_unavailable))
        }

        is SectionPayload.Facts -> {
            val facts = if (problemsOnly) section.issues() else payload.facts
            facts.forEach { FactRow(it) }
        }

        is SectionPayload.PermissionTable -> {
            payload.rows.forEach { row ->
                StatusRow(
                    label = row.name,
                    // Lowercase in both branches: an uppercase "DENIED" read as a
                    // different, shoutier voice than every other status value on the
                    // screen — the WARNING color already carries the emphasis.
                    value = if (row.granted) "granted" else "denied",
                    // A denied install-time permission is informational, not a
                    // failure — only a denied runtime grant tints as an issue.
                    health = if (row.granted || !row.dangerous) FactHealth.OK else FactHealth.WARNING,
                )
            }
            payload.extras.forEach { FactRow(it) }
        }

        is SectionPayload.LogTail -> {
            LogLines(payload.lines)
        }
    }
    if (section.id == SectionId.MUSIC) {
        BodyHint(stringResource(R.string.diagnostics_spectrum_hint))
    }
}

@Composable
private fun FactRow(fact: DiagnosticFact) =
    when (val value = fact.value) {
        is FactValue.Text -> ValueRow(label = "${fact.label}: ${value.value}")
        is FactValue.Status -> StatusRow(label = fact.label, value = value.value, health = value.health)
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
    health: FactHealth,
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
        color =
            when (health) {
                FactHealth.ERROR, FactHealth.WARNING -> MaterialTheme.colorScheme.error
                FactHealth.OK -> MaterialTheme.colorScheme.primary
                FactHealth.INFO -> MaterialTheme.colorScheme.onSurface
            },
    )
}

// Stack traces stop wrapping: the block pans sideways instead, keeping one
// log line per row.
@Composable
private fun LogLines(
    lines: List<String>,
    modifier: Modifier = Modifier,
) = Box(
    modifier =
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
) {
    Column {
        lines.forEach { line ->
            // Log lines are glance metadata, not dashboard body text, so
            // they take the sanctioned GlanceTextSize relaxation.
            Text(
                text = line,
                style = MaterialTheme.typography.monoReference(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BodyHint(text: String) =
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

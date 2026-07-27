package io.github.seijikohara.femto.ui.diagnostics

import io.github.seijikohara.femto.data.diagnostics.DiagnosticSection
import io.github.seijikohara.femto.data.diagnostics.FactValue
import io.github.seijikohara.femto.data.diagnostics.SectionId
import io.github.seijikohara.femto.data.diagnostics.SectionPayload
import io.github.seijikohara.femto.data.diagnostics.issueCount
import io.github.seijikohara.femto.data.diagnostics.issues
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// The report caps the log tail below the screen's 80 lines: a pasted GitHub
// issue needs the recent tail, not the whole buffer.
internal const val REPORT_LOG_LINES = 50

// Locale.ROOT keeps wording and digits stable on every device locale — the
// report's grep contract.
private val GeneratedAtFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm xxx", Locale.ROOT)

/** English report section titles, keyed by the same enum as the screen titles. */
internal fun SectionId.reportTitle(): String =
    when (this) {
        SectionId.APP -> "App"
        SectionId.CRASH_HISTORY -> "Crash history"
        SectionId.DEVICE -> "Device"
        SectionId.DISPLAY -> "Display"
        SectionId.GRAPHICS -> "Graphics"
        SectionId.PERMISSIONS -> "Permissions"
        SectionId.MUSIC -> "Music"
        SectionId.NETWORK -> "Network"
        SectionId.LOCATION -> "Location & sensors"
        SectionId.LOCALE_TIME -> "Locale & time"
        SectionId.PERFORMANCE -> "Performance"
        SectionId.STORAGE -> "Storage"
        SectionId.INPUT -> "Input"
        SectionId.WEBVIEW -> "WebView"
        SectionId.MAP -> "Map"
        SectionId.SETTINGS -> "Settings"
        SectionId.LOGS -> "Recent warnings"
    }

/** The plain string a fact value renders as, on both the report and the table. */
private fun FactValue.rendered(): String =
    when (this) {
        is FactValue.Text -> value
        is FactValue.Status -> value
    }

/**
 * Render the section list as the clipboard Markdown report. Pure, pinned by
 * JVM tests; unlocalized by design (stable grep-able wording beats locale
 * fidelity in a debug artifact).
 */
internal fun diagnosticsReport(
    sections: List<DiagnosticSection>,
    generatedAtEpochMs: Long,
): String =
    buildString {
        appendLine("# Femto Car Launcher diagnostics")
        appendLine()
        val generatedAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(generatedAtEpochMs), ZoneId.systemDefault())
        appendLine("- Generated: ${GeneratedAtFormat.format(generatedAt)}")
        appendLine("- Note: review the log section for personal data before pasting publicly.")
        appendLine()
        appendIssues(sections)
        sections.forEach { section -> appendSection(section) }
    }

private fun StringBuilder.appendIssues(sections: List<DiagnosticSection>) {
    appendLine("## Issues")
    appendLine()
    if (sections.issueCount() == 0) {
        appendLine("No issues detected.")
    } else {
        sections.forEach { section ->
            section.issues().forEach { fact ->
                appendLine("- ${section.id.reportTitle()}: ${fact.label}: ${fact.value.rendered()}")
            }
        }
    }
    appendLine()
}

private fun StringBuilder.appendSection(section: DiagnosticSection) {
    when (val payload = section.payload) {
        null -> {
            appendLine("## ${section.id.reportTitle()}")
            appendLine()
            appendLine("(still collecting)")
            appendLine()
        }

        SectionPayload.Unavailable -> {
            appendLine("## ${section.id.reportTitle()}")
            appendLine()
            appendLine("Section UNAVAILABLE (collection failed; see app logs)")
            appendLine()
        }

        is SectionPayload.Facts -> {
            appendLine("## ${section.id.reportTitle()}")
            appendLine()
            // Settings is the bulk dump; <details> keeps the pasted issue
            // readable while staying grep-able as plain text.
            val collapse = section.id == SectionId.SETTINGS
            if (collapse) {
                appendLine("<details><summary>Settings dump</summary>")
                appendLine()
            }
            payload.facts.forEach { fact ->
                appendLine("- ${fact.label}: ${fact.value.rendered()}")
            }
            if (payload.facts.isEmpty()) appendLine("(none)")
            if (collapse) {
                appendLine("</details>")
            }
            appendLine()
        }

        is SectionPayload.PermissionTable -> {
            appendLine("## ${section.id.reportTitle()}")
            appendLine()
            appendLine("| Permission | State |")
            appendLine("| --- | --- |")
            payload.rows.forEach { row ->
                appendLine("| ${row.name} | ${if (row.granted) "granted" else "DENIED"} |")
            }
            payload.extras.forEach { extra ->
                appendLine("| ${extra.label} | ${extra.value.rendered()} |")
            }
            appendLine()
        }

        is SectionPayload.LogTail -> {
            val shown = payload.lines.takeLast(REPORT_LOG_LINES)
            val heading =
                if (payload.lines.size > REPORT_LOG_LINES) {
                    "## Recent warnings (last $REPORT_LOG_LINES of ${payload.lines.size})"
                } else {
                    "## Recent warnings (${payload.lines.size})"
                }
            appendLine(heading)
            appendLine()
            appendLine("<details><summary>Log tail</summary>")
            appendLine()
            appendLine("```text")
            shown.forEach(::appendLine)
            appendLine("```")
            appendLine("</details>")
            appendLine()
        }
    }
}

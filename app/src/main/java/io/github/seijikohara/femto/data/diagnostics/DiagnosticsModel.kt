package io.github.seijikohara.femto.data.diagnostics

// The diagnostics fact model: one declarative shape from which the screen,
// the Markdown report, the health summary, and the problems-only filter all
// derive. Labels and values are deliberately English and unlocalized on both
// surfaces — the report's grep contract (stable machine-searchable wording)
// extends to the screen so the two can never drift.

/** Health verdict of one fact; INFO carries no verdict (plain value). */
internal enum class FactHealth { OK, WARNING, ERROR, INFO }

internal sealed interface FactValue {
    /** A plain value row / `- Label: value` report bullet. */
    data class Text(
        val value: String,
    ) : FactValue

    /** A verdict row: value tinted by health on screen, shouted in the report. */
    data class Status(
        val value: String,
        val health: FactHealth,
    ) : FactValue
}

internal data class DiagnosticFact(
    val label: String,
    val value: FactValue,
)

internal val DiagnosticFact.health: FactHealth
    get() = (value as? FactValue.Status)?.health ?: FactHealth.INFO

/** One row of the permissions table (bespoke render on both surfaces). */
internal data class PermissionRow(
    val name: String,
    val granted: Boolean,
    // Runtime (user-deniable) permission: a denied dangerous grant is an
    // issue; install-time permissions are informational either way.
    val dangerous: Boolean,
)

internal sealed interface SectionPayload {
    data class Facts(
        val facts: List<DiagnosticFact>,
    ) : SectionPayload

    data class PermissionTable(
        val rows: List<PermissionRow>,
        val extras: List<DiagnosticFact>,
    ) : SectionPayload

    data class LogTail(
        val lines: List<String>,
    ) : SectionPayload

    /** The collector failed; the absence itself is the datum. */
    data object Unavailable : SectionPayload
}

/**
 * Section identity AND order: the enum declaration order is the single
 * source of section order for the screen and the report, making order
 * drift between the two surfaces unrepresentable.
 */
internal enum class SectionId {
    APP,
    CRASH_HISTORY,
    DEVICE,
    DISPLAY,
    GRAPHICS,
    PERMISSIONS,
    MUSIC,
    NETWORK,
    LOCATION,
    LOCALE_TIME,
    PERFORMANCE,
    STORAGE,
    INPUT,
    WEBVIEW,
    SETTINGS,
    LOGS,
}

internal data class DiagnosticSection(
    val id: SectionId,
    // null while the collector is still running (streaming skeleton).
    val payload: SectionPayload?,
)

/** One section's producer; the production set lives in `DiagnosticsCollectors.kt`. */
internal data class SectionCollector(
    val id: SectionId,
    val collect: suspend () -> SectionPayload,
)

/** Facts with a WARNING/ERROR verdict — the badge, summary, and TL;DR feed. */
internal fun DiagnosticSection.issues(): List<DiagnosticFact> =
    when (val payload = payload) {
        is SectionPayload.Facts -> {
            payload.facts.filter {
                it.health == FactHealth.WARNING ||
                    it.health == FactHealth.ERROR
            }
        }

        is SectionPayload.PermissionTable -> {
            payload.rows
                .filter { it.dangerous && !it.granted }
                .map { DiagnosticFact(it.name, FactValue.Status("DENIED", FactHealth.WARNING)) } +
                payload.extras.filter { it.health == FactHealth.WARNING || it.health == FactHealth.ERROR }
        }

        // A failed collector is itself a finding: it must reach the badge,
        // the problems-only filter, and the report's Issues block — hidden
        // collection failures are exactly the silent degradation this
        // surface exists to expose.
        SectionPayload.Unavailable -> {
            listOf(DiagnosticFact("Collection", FactValue.Status("UNAVAILABLE", FactHealth.WARNING)))
        }

        is SectionPayload.LogTail, null -> {
            emptyList()
        }
    }

internal fun List<DiagnosticSection>.issueCount(): Int = sumOf { it.issues().size }

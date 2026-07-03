package io.github.seijikohara.femto.ui.diagnostics

import io.github.seijikohara.femto.data.diagnostics.DiagnosticFact
import io.github.seijikohara.femto.data.diagnostics.DiagnosticSection
import io.github.seijikohara.femto.data.diagnostics.FactHealth
import io.github.seijikohara.femto.data.diagnostics.FactValue
import io.github.seijikohara.femto.data.diagnostics.PermissionRow
import io.github.seijikohara.femto.data.diagnostics.SectionId
import io.github.seijikohara.femto.data.diagnostics.SectionPayload
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticsReportTest {
    // 2026-07-03 03:04:05 UTC — the assertion checks presence/shape, not zone.
    private val generatedAt = 1_782_097_445_000L

    private fun sections(vararg overrides: DiagnosticSection): List<DiagnosticSection> {
        val byId = overrides.associateBy { it.id }
        return SectionId.entries.map { byId[it] ?: DiagnosticSection(it, SectionPayload.Facts(emptyList())) }
    }

    @Test
    fun `report opens with the title, timestamp, and privacy note`() {
        val report = diagnosticsReport(sections(), generatedAt)
        assertTrue(report.startsWith("# Femto Car Launcher diagnostics"))
        assertTrue(report.contains("- Generated: "))
        assertTrue(report.contains("review the log section for personal data before pasting publicly"))
    }

    @Test
    fun `issues block lists warning and error facts or the all-clear line`() {
        val bad =
            DiagnosticSection(
                SectionId.NETWORK,
                SectionPayload.Facts(listOf(DiagnosticFact("Online", FactValue.Status("OFFLINE", FactHealth.ERROR)))),
            )
        assertTrue(diagnosticsReport(sections(bad), generatedAt).contains("- Network: Online: OFFLINE"))
        assertTrue(diagnosticsReport(sections(), generatedAt).contains("No issues detected."))
    }

    @Test
    fun `sections render in SectionId order with facts as bullets`() {
        val report =
            diagnosticsReport(
                sections(
                    DiagnosticSection(
                        SectionId.APP,
                        SectionPayload.Facts(listOf(DiagnosticFact("App", FactValue.Text("1.0 (debug)")))),
                    ),
                ),
                generatedAt,
            )
        assertTrue(report.contains("## App\n\n- App: 1.0 (debug)"))
        assertTrue(report.indexOf("## App") < report.indexOf("## Device"))
        assertTrue(report.indexOf("## Performance") < report.indexOf("## Recent warnings"))
    }

    @Test
    fun `permission table keeps the v1 shape with denied shouted`() {
        val report =
            diagnosticsReport(
                sections(
                    DiagnosticSection(
                        SectionId.PERMISSIONS,
                        SectionPayload.PermissionTable(
                            rows =
                                listOf(
                                    PermissionRow("ACCESS_FINE_LOCATION", granted = true, dangerous = true),
                                    PermissionRow("RECORD_AUDIO", granted = false, dangerous = true),
                                ),
                            extras = listOf(
                                DiagnosticFact("Notification listener", FactValue.Status("enabled", FactHealth.OK)),
                            ),
                        ),
                    ),
                ),
                generatedAt,
            )
        assertTrue(report.contains("| Permission | State |"))
        assertTrue(report.contains("| ACCESS_FINE_LOCATION | granted |"))
        assertTrue(report.contains("| RECORD_AUDIO | DENIED |"))
        assertTrue(report.contains("| Notification listener | enabled |"))
    }

    @Test
    fun `log tail is fenced, capped at 50 with the cap named, and wrapped in details`() {
        val lines = List(80) { "06-12 12:00:${"%02d".format(it % 60)}.000 W/Tag: line $it" }
        val report =
            diagnosticsReport(
                sections(DiagnosticSection(SectionId.LOGS, SectionPayload.LogTail(lines))),
                generatedAt,
            )
        assertTrue(report.contains("## Recent warnings (last 50 of 80)"))
        assertTrue(report.contains("```text"))
        assertTrue(report.contains("<details><summary>Log tail</summary>"))
        assertFalse(report.contains("line 29"))
        assertTrue(report.contains("line 30"))
        assertTrue(report.contains("line 79"))
    }

    @Test
    fun `a short log tail keeps the v1 heading shape`() {
        val report =
            diagnosticsReport(
                sections(DiagnosticSection(SectionId.LOGS, SectionPayload.LogTail(listOf("only line")))),
                generatedAt,
            )
        assertTrue(report.contains("## Recent warnings (1)"))
    }

    @Test
    fun `settings dump is wrapped in details`() {
        val report =
            diagnosticsReport(
                sections(
                    DiagnosticSection(
                        SectionId.SETTINGS,
                        SectionPayload.Facts(listOf(DiagnosticFact("Map backend", FactValue.Text("OSM")))),
                    ),
                ),
                generatedAt,
            )
        assertTrue(report.contains("## Settings\n\n<details><summary>Settings dump</summary>\n\n- Map backend: OSM"))
    }

    @Test
    fun `pending and unavailable sections say so explicitly`() {
        val report =
            diagnosticsReport(
                sections(
                    DiagnosticSection(SectionId.GRAPHICS, payload = null),
                    DiagnosticSection(SectionId.STORAGE, SectionPayload.Unavailable),
                ),
                generatedAt,
            )
        assertTrue(report.contains("## Graphics\n\n(still collecting)"))
        assertTrue(report.contains("## Storage\n\nSection UNAVAILABLE (collection failed; see app logs)"))
    }
}

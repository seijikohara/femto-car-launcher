package io.github.seijikohara.femto.data.diagnostics

import org.junit.Test
import kotlin.test.assertEquals

class DiagnosticsModelTest {
    @Test
    fun `issues collects only warning and error facts`() {
        val section =
            DiagnosticSection(
                id = SectionId.NETWORK,
                payload =
                    SectionPayload.Facts(
                        listOf(
                            DiagnosticFact("Online", FactValue.Status("OFFLINE", FactHealth.ERROR)),
                            DiagnosticFact("Metered", FactValue.Status("yes", FactHealth.WARNING)),
                            DiagnosticFact("Transports", FactValue.Text("Wi-Fi")),
                            DiagnosticFact("VPN", FactValue.Status("none", FactHealth.OK)),
                        ),
                    ),
            )
        assertEquals(listOf("Online", "Metered"), section.issues().map { it.label })
    }

    @Test
    fun `permission table contributes denied dangerous rows and unhealthy extras as issues`() {
        val section =
            DiagnosticSection(
                id = SectionId.PERMISSIONS,
                payload =
                    SectionPayload.PermissionTable(
                        rows =
                            listOf(
                                PermissionRow("ACCESS_FINE_LOCATION", granted = true, dangerous = true),
                                PermissionRow("RECORD_AUDIO", granted = false, dangerous = true),
                                PermissionRow("INTERNET", granted = true, dangerous = false),
                            ),
                        extras =
                            listOf(
                                DiagnosticFact("Notification listener", FactValue.Status("DISABLED", FactHealth.ERROR)),
                            ),
                    ),
            )
        assertEquals(listOf("RECORD_AUDIO", "Notification listener"), section.issues().map { it.label })
    }

    @Test
    fun `issueCount sums across sections and ignores unavailable and pending payloads`() {
        val sections =
            listOf(
                DiagnosticSection(
                    SectionId.NETWORK,
                    SectionPayload.Facts(
                        listOf(DiagnosticFact("Online", FactValue.Status("OFFLINE", FactHealth.ERROR))),
                    ),
                ),
                DiagnosticSection(SectionId.LOGS, SectionPayload.Unavailable),
                DiagnosticSection(SectionId.DEVICE, payload = null),
            )
        assertEquals(1, sections.issueCount())
    }

    @Test
    fun `section order is the enum order`() {
        assertEquals(
            listOf(
                SectionId.APP,
                SectionId.CRASH_HISTORY,
                SectionId.DEVICE,
                SectionId.DISPLAY,
                SectionId.GRAPHICS,
                SectionId.PERMISSIONS,
                SectionId.MUSIC,
                SectionId.NETWORK,
                SectionId.LOCATION,
                SectionId.LOCALE_TIME,
                SectionId.PERFORMANCE,
                SectionId.STORAGE,
                SectionId.INPUT,
                SectionId.WEBVIEW,
                SectionId.SETTINGS,
                SectionId.LOGS,
            ),
            SectionId.entries.toList(),
        )
    }
}

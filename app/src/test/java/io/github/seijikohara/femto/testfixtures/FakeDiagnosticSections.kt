package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.diagnostics.DiagnosticFact
import io.github.seijikohara.femto.data.diagnostics.DiagnosticSection
import io.github.seijikohara.femto.data.diagnostics.FactHealth
import io.github.seijikohara.femto.data.diagnostics.FactValue
import io.github.seijikohara.femto.data.diagnostics.PermissionRow
import io.github.seijikohara.femto.data.diagnostics.SectionId
import io.github.seijikohara.femto.data.diagnostics.SectionPayload

/**
 * Return one deterministic [DiagnosticSection] per [SectionId], in enum order.
 * `issue = true` seeds two findings — NETWORK offline (ERROR) and a denied
 * dangerous RECORD_AUDIO grant — so problems-only and issue-count paths have
 * known positives to assert against.
 */
internal fun fakeDiagnosticSections(issue: Boolean = false): List<DiagnosticSection> =
    SectionId.entries.map { id -> DiagnosticSection(id, fakePayload(id, issue)) }

private fun fakePayload(
    id: SectionId,
    issue: Boolean,
): SectionPayload =
    when (id) {
        SectionId.APP -> {
            facts(fact("App", "1.0 (debug)"), fact("Process started", "boot +42 s"))
        }

        SectionId.CRASH_HISTORY -> {
            facts(fact("Recent crashes", "none"))
        }

        SectionId.DEVICE -> {
            facts(fact("Device", "Acme TBox"), fact("Android", "13 (API 33)"))
        }

        SectionId.DISPLAY -> {
            facts(fact("Resolution", "1280 x 720 @ 160 dpi"), fact("Refresh rate", "60 Hz"))
        }

        SectionId.GRAPHICS -> {
            facts(fact("GL renderer", "Mali-G52"))
        }

        SectionId.PERMISSIONS -> {
            SectionPayload.PermissionTable(
                rows =
                    listOf(
                        PermissionRow("ACCESS_FINE_LOCATION", granted = true, dangerous = true),
                        PermissionRow("RECORD_AUDIO", granted = !issue, dangerous = true),
                    ),
                extras =
                    listOf(
                        DiagnosticFact("Notification listener", FactValue.Status("enabled", FactHealth.OK)),
                    ),
            )
        }

        SectionId.MUSIC -> {
            facts(
                fact("Spectrum capture", "SILENT"),
                fact("Outputs", "bus"),
                fact("Volume", "7/15"),
            )
        }

        SectionId.NETWORK -> {
            facts(
                if (issue) {
                    DiagnosticFact("Online", FactValue.Status("OFFLINE", FactHealth.ERROR))
                } else {
                    DiagnosticFact("Online", FactValue.Status("yes (Wi-Fi)", FactHealth.OK))
                },
            )
        }

        SectionId.LOCATION -> {
            facts(fact("Location grant", "fine"), fact("Providers", "gps, network"))
        }

        SectionId.LOCALE_TIME -> {
            facts(fact("Locale", "en-US"), fact("Time zone", "UTC"))
        }

        SectionId.PERFORMANCE -> {
            facts(fact("Thermal", "NONE"), fact("Device memory", "1024 / 3962 MB free"))
        }

        SectionId.STORAGE -> {
            facts(fact("App storage", "12 MB"))
        }

        SectionId.INPUT -> {
            facts(fact("Touch", "multitouch"))
        }

        SectionId.WEBVIEW -> {
            facts(fact("WebView", "com.google.android.webview 110.0.5481.154"))
        }

        SectionId.SETTINGS -> {
            facts(fact("Map backend", "OSM"), fact("UI scale", "MEDIUM"))
        }

        SectionId.LOGS -> {
            SectionPayload.LogTail(listOf("06-12 12:00:00.000 W/AudioSpectrumRepo: sample warning"))
        }
    }

private fun facts(vararg facts: DiagnosticFact): SectionPayload.Facts = SectionPayload.Facts(facts.toList())

private fun fact(
    label: String,
    value: String,
): DiagnosticFact = DiagnosticFact(label, FactValue.Text(value))

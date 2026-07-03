package io.github.seijikohara.femto.data.diagnostics

import org.junit.Test
import kotlin.test.assertEquals

class WebViewFactsTest {
    @Test
    fun `webViewFactsFrom flags a chromium major below the webmap floor as a warning`() {
        val facts =
            webViewFactsFrom(
                packageLabel = "com.google.android.webview 108.0.5359.128",
                versionName = "108.0.5359.128",
                userAgent = "Mozilla/5.0",
            )

        assertEquals(
            listOf(
                DiagnosticFact("Package", FactValue.Text("com.google.android.webview 108.0.5359.128")),
                DiagnosticFact("User agent", FactValue.Text("Mozilla/5.0")),
                DiagnosticFact("Chromium major", FactValue.Status("108 (webmap floor 109)", FactHealth.WARNING)),
            ),
            facts,
        )
    }

    @Test
    fun `webViewFactsFrom clears a chromium major at or above the webmap floor`() {
        val facts =
            webViewFactsFrom(
                packageLabel = "com.google.android.webview 122.0.6261.64",
                versionName = "122.0.6261.64",
                userAgent = "Mozilla/5.0",
            )

        assertEquals(
            DiagnosticFact("Chromium major", FactValue.Status("122 (webmap floor 109)", FactHealth.OK)),
            facts.last(),
        )
    }

    @Test
    fun `webViewFactsFrom reports an unknown chromium major for an unparsable version`() {
        val facts =
            webViewFactsFrom(
                packageLabel = "vendor webview garbage",
                versionName = "garbage",
                userAgent = "Mozilla/5.0",
            )

        assertEquals(DiagnosticFact("Chromium major", FactValue.Text("unknown")), facts.last())
    }

    @Test
    fun `webViewFactsFrom reports an unknown provider when the platform has no webview package`() {
        val facts = webViewFactsFrom(packageLabel = null, versionName = null, userAgent = "Mozilla/5.0")

        assertEquals(
            DiagnosticFact("Package", FactValue.Status("unknown provider", FactHealth.WARNING)),
            facts.first(),
        )
    }

    @Test
    fun `webViewFactsFrom reports the user agent as unavailable when the platform query fails`() {
        val facts = webViewFactsFrom(packageLabel = "pkg 1.0", versionName = "1.0", userAgent = null)

        assertEquals(DiagnosticFact("User agent", FactValue.Text("unavailable")), facts[1])
    }
}

package io.github.seijikohara.femto.data.diagnostics

import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// vite.config's build.target floor (chrome109) documented in
// .claude/rules/webmap.md; a WebView below this Chromium major can't
// reliably render the live map page.
private const val WEBMAP_CHROMIUM_FLOOR = 109

/**
 * Builds the WEBVIEW section facts from already-resolved platform values.
 * Pure so [WebViewFactsTest] can pin the floor comparison without a WebView.
 */
internal fun webViewFactsFrom(
    packageLabel: String?,
    versionName: String?,
    userAgent: String?,
): List<DiagnosticFact> =
    buildList {
        add(
            packageLabel?.let { DiagnosticFact("Package", FactValue.Text(it)) }
                ?: DiagnosticFact("Package", FactValue.Status("unknown provider", FactHealth.WARNING)),
        )
        add(DiagnosticFact("User agent", FactValue.Text(userAgent ?: "unavailable")))
        add(chromiumMajorFact(versionName))
    }

private fun chromiumMajorFact(versionName: String?): DiagnosticFact {
    val major = versionName?.substringBefore('.')?.toIntOrNull()
    return when {
        major == null -> {
            DiagnosticFact("Chromium major", FactValue.Text("unknown"))
        }

        major < WEBMAP_CHROMIUM_FLOOR -> {
            DiagnosticFact(
                "Chromium major",
                FactValue.Status("$major (webmap floor $WEBMAP_CHROMIUM_FLOOR)", FactHealth.WARNING),
            )
        }

        else -> {
            DiagnosticFact(
                "Chromium major",
                FactValue.Status("$major (webmap floor $WEBMAP_CHROMIUM_FLOOR)", FactHealth.OK),
            )
        }
    }
}

/** Collects the WEBVIEW diagnostics section. */
internal class WebViewFactsCollector(
    private val context: Context,
) {
    suspend fun webViewFacts(): SectionPayload.Facts =
        withContext(Dispatchers.IO) {
            val webViewPackage = WebView.getCurrentWebViewPackage()
            val userAgent = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
            SectionPayload.Facts(
                webViewFactsFrom(
                    packageLabel = webViewPackage?.let { "${it.packageName} ${it.versionName}" },
                    versionName = webViewPackage?.versionName,
                    userAgent = userAgent,
                ),
            )
        }
}

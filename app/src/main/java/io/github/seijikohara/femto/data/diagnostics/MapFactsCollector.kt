package io.github.seijikohara.femto.data.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import androidx.core.content.getSystemService
import io.github.seijikohara.femto.data.map.MapRuntimeSignals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// WebGL 2 maps onto OpenGL ES 3.0, so that is the floor below which the map
// backends that need it cannot acquire a context. The Android 13 CDD mandates
// only ES 2.0 ([C-1-1]) and merely strongly-recommends ES 3.1, so a compliant
// device — and an uncertified one all the more — can legitimately sit below it.
private const val WEBGL2_GL_ES_FLOOR = 0x30000

/**
 * Builds the MAP section facts from already-resolved values. Pure so
 * [io.github.seijikohara.femto.data.diagnostics.MapFactsTest] can pin the floor
 * comparison and the failure formatting without a device.
 *
 * Reports what the map DID, not how it is configured: every map setting
 * (backend, style, credentials) is already a SETTINGS fact, and restating it
 * here would be a second home for the same value.
 */
internal fun mapFactsFrom(
    glEsVersion: String?,
    lastFailure: MapRuntimeSignals.MapFailure?,
    failureCount: Int,
    nowElapsedRealtimeMs: Long,
): List<DiagnosticFact> =
    buildList {
        add(webGl2Fact(glEsVersion))
        add(lastFailureFact(lastFailure, nowElapsedRealtimeMs))
        if (failureCount > 1) {
            add(DiagnosticFact("Failures this session", FactValue.Text("$failureCount")))
        }
    }

// The GLES version the platform reports is the capability behind WebGL 2, not a
// direct probe of it: a driver the WebView blocklists can still deny a context
// above the floor. Above the floor the fact therefore says "expected", and the
// authoritative answer is the failure row below it.
private fun webGl2Fact(glEsVersion: String?): DiagnosticFact {
    val encoded = glEsVersionCode(glEsVersion)
    return when {
        encoded == null -> {
            DiagnosticFact("WebGL 2", FactValue.Text("unknown (OpenGL ES unreported)"))
        }

        encoded < WEBGL2_GL_ES_FLOOR -> {
            DiagnosticFact(
                "WebGL 2",
                FactValue.Status("unavailable (OpenGL ES $glEsVersion, needs 3.0)", FactHealth.WARNING),
            )
        }

        else -> {
            DiagnosticFact("WebGL 2", FactValue.Status("expected (OpenGL ES $glEsVersion)", FactHealth.OK))
        }
    }
}

// deviceConfigurationInfo reports the version as "major.minor"; compare on the
// same packed 0xMMMMmmmm encoding the platform uses for reqGlEsVersion.
private fun glEsVersionCode(glEsVersion: String?): Int? {
    val major = glEsVersion?.substringBefore('.')?.toIntOrNull() ?: return null
    val minor = glEsVersion.substringAfter('.', "0").toIntOrNull() ?: 0
    return (major shl 16) or minor
}

private fun lastFailureFact(
    lastFailure: MapRuntimeSignals.MapFailure?,
    nowElapsedRealtimeMs: Long,
): DiagnosticFact =
    lastFailure?.let { failure ->
        val agoSeconds = ((nowElapsedRealtimeMs - failure.elapsedRealtimeMs) / 1000L).coerceAtLeast(0L)
        DiagnosticFact(
            "Last failure",
            FactValue.Status("${failure.detail} (${agoSeconds}s ago)", FactHealth.ERROR),
        )
    } ?: DiagnosticFact("Last failure", FactValue.Status("none this session", FactHealth.OK))

/** Collects the MAP diagnostics section. */
internal class MapFactsCollector(
    private val context: Context,
) {
    suspend fun mapFacts(): SectionPayload.Facts =
        withContext(Dispatchers.IO) {
            SectionPayload.Facts(
                mapFactsFrom(
                    glEsVersion = context.getSystemService<ActivityManager>()?.deviceConfigurationInfo?.glEsVersion,
                    lastFailure = MapRuntimeSignals.lastFailureOrNull(),
                    failureCount = MapRuntimeSignals.failureCount(),
                    nowElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                ),
            )
        }
}

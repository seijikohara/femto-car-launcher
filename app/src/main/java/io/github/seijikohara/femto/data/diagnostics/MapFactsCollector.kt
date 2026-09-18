package io.github.seijikohara.femto.data.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import androidx.core.content.getSystemService
import io.github.seijikohara.femto.data.display.DisplayPreferences
import io.github.seijikohara.femto.data.display.GoogleMapsRendering
import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.data.map.MapRuntimeSignals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

// WebGL 2 maps onto OpenGL ES 3.0, so that is the floor below which the map
// backends that need it cannot acquire a context. The Android 13 CDD mandates
// only ES 2.0 ([C-1-1]) and merely strongly-recommends ES 3.1, so a compliant
// device — and an uncertified one all the more — can legitimately sit below it.
private const val WEBGL2_GL_ES_FLOOR = 0x30000

/**
 * What the active map configuration loses without WebGL 2. Verified against each
 * vendor's own documentation rather than inferred:
 *
 *  - maplibre-gl 6 removed the WebGL 1 fallback — it renders nothing without it.
 *  - A Google VECTOR map (a Cloud Map ID) treats WebGL 2 as its bar too, but
 *    silently falls back to raster instead of failing: the map still draws, and
 *    only the vector opt-in (heading-up, tilt, 3D) is lost.
 *  - A Google RASTER map is server-rendered pixel tiles and needs no WebGL.
 */
private enum class WebGl2Need { TO_RENDER, FOR_VECTOR, NONE }

private fun webGl2Need(
    backend: MapBackend,
    googleRendering: GoogleMapsRendering,
    hasGoogleMapId: Boolean,
): WebGl2Need =
    when (backend) {
        MapBackend.OSM -> {
            WebGl2Need.TO_RENDER
        }

        // Mirrors `wantsVector` in webmap/src/backends/googlemaps.ts, which is the
        // page-side home of the same rule: an explicit VECTOR asks for it, AUTO
        // may get it from the Map ID's cloud configuration, and everything else
        // renders raster.
        MapBackend.GOOGLEMAPS -> {
            when (googleRendering) {
                GoogleMapsRendering.VECTOR -> WebGl2Need.FOR_VECTOR
                GoogleMapsRendering.AUTO -> if (hasGoogleMapId) WebGl2Need.FOR_VECTOR else WebGl2Need.NONE
                GoogleMapsRendering.RASTER -> WebGl2Need.NONE
            }
        }
    }

/**
 * Builds the MAP section facts from already-resolved values. Pure so
 * [io.github.seijikohara.femto.data.diagnostics.MapFactsTest] can pin the floor
 * comparison and the failure formatting without a device.
 *
 * Reports what the map DID, not how it is configured: every map setting
 * (backend, style, credentials) is already a SETTINGS fact, and restating it
 * here would be a second home for the same value. [backend], [googleRendering]
 * and [hasGoogleMapId] are read only to judge what a missing WebGL 2 actually
 * costs — flagging it unconditionally would warn about a Google raster map that
 * renders perfectly.
 */
internal fun mapFactsFrom(
    glEsVersion: String?,
    backend: MapBackend,
    googleRendering: GoogleMapsRendering,
    hasGoogleMapId: Boolean,
    lastFailure: MapRuntimeSignals.MapFailure?,
    failureCount: Int,
    nowElapsedRealtimeMs: Long,
    webGlRenderer: String? = null,
    pageFrames: MapRuntimeSignals.PageFrames? = null,
): List<DiagnosticFact> =
    buildList {
        add(webGl2Fact(glEsVersion, webGl2Need(backend, googleRendering, hasGoogleMapId)))
        add(webGlRendererFact(webGlRenderer))
        add(pageFramesFact(pageFrames, nowElapsedRealtimeMs))
        add(lastFailureFact(lastFailure, nowElapsedRealtimeMs))
        if (failureCount > 1) {
            add(DiagnosticFact("Failures this session", FactValue.Text("$failureCount")))
        }
    }

// The GLES version the platform reports is the capability behind WebGL 2, not a
// direct probe of it: a driver the WebView blocklists can still deny a context
// above the floor. Above the floor the fact therefore says "expected", and the
// authoritative answer is the failure row below it.
private fun webGl2Fact(
    glEsVersion: String?,
    need: WebGl2Need,
): DiagnosticFact {
    val encoded = glEsVersionCode(glEsVersion)
    return when {
        need == WebGl2Need.NONE -> {
            DiagnosticFact("WebGL 2", FactValue.Text("not required (Google Maps raster)"))
        }

        encoded == null -> {
            DiagnosticFact("WebGL 2", FactValue.Text("unknown (OpenGL ES unreported)"))
        }

        encoded >= WEBGL2_GL_ES_FLOOR -> {
            DiagnosticFact("WebGL 2", FactValue.Status("expected (OpenGL ES $glEsVersion)", FactHealth.OK))
        }

        // Below the floor: name the consequence, which differs by backend.
        need == WebGl2Need.FOR_VECTOR -> {
            DiagnosticFact(
                "WebGL 2",
                FactValue.Status(
                    "unavailable (OpenGL ES $glEsVersion, needs 3.0) — vector map falls back to raster",
                    FactHealth.WARNING,
                ),
            )
        }

        else -> {
            DiagnosticFact(
                "WebGL 2",
                FactValue.Status(
                    "unavailable (OpenGL ES $glEsVersion, needs 3.0) — this backend cannot render",
                    FactHealth.WARNING,
                ),
            )
        }
    }
}

// The renderer the page's WebGL actually runs on, unmasked by the WebView on
// the first render. This is the authoritative counterpart of the version fact
// above: a GPU the WebView blocklists still reports a fine OpenGL ES version
// yet hands WebGL to SwiftShader, Chromium's software rasteriser — and then a
// vector map crawls on the CPU no matter how the camera is driven, while a
// server-rendered raster map is unaffected.
private fun webGlRendererFact(renderer: String?): DiagnosticFact =
    when {
        renderer == null -> {
            DiagnosticFact("WebGL renderer", FactValue.Text("unreported (no map page has rendered yet)"))
        }

        SOFTWARE_RENDERERS.any { renderer.contains(it, ignoreCase = true) } -> {
            DiagnosticFact(
                "WebGL renderer",
                FactValue.Status("$renderer — software; vector maps render on the CPU", FactHealth.WARNING),
            )
        }

        else -> {
            DiagnosticFact("WebGL renderer", FactValue.Status(renderer, FactHealth.OK))
        }
    }

private val SOFTWARE_RENDERERS = listOf("SwiftShader", "llvmpipe", "softpipe", "Software")

// The map page's own frame cadence, from its most recent sampling burst. The
// PERFORMANCE section's UI-frame statistics measure the launcher's Compose
// frames, which stay on time while the WebView's renderer is saturated by a
// heavy map, so this is the row that shows a map that has fallen to one frame
// a second. The threshold names the point where a following map visibly
// steps rather than glides.
private fun pageFramesFact(
    frames: MapRuntimeSignals.PageFrames?,
    nowElapsedRealtimeMs: Long,
): DiagnosticFact {
    if (frames == null) return DiagnosticFact("Page frames", FactValue.Text("no sample yet"))
    val ageS = ((nowElapsedRealtimeMs - frames.elapsedRealtimeMs) / 1_000L).coerceAtLeast(0)
    // Kept short: the fact row gives the value priority over the label, and a
    // long value on the head unit's width ellipsises the label away.
    val value = "median ${frames.medianMs} ms, worst ${frames.worstMs} ms, ${ageS}s ago"
    return DiagnosticFact(
        "Page frames (${frames.sampledFrames})",
        if (frames.medianMs >= PAGE_FRAME_WARNING_MS) {
            FactValue.Status("$value (< 10 fps)", FactHealth.WARNING)
        } else {
            FactValue.Text(value)
        },
    )
}

private const val PAGE_FRAME_WARNING_MS = 100

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
            val display = DisplayPreferences(context).settings.first()
            SectionPayload.Facts(
                mapFactsFrom(
                    glEsVersion = context.getSystemService<ActivityManager>()?.deviceConfigurationInfo?.glEsVersion,
                    backend = display.mapBackend,
                    googleRendering = display.googleMapsRendering,
                    hasGoogleMapId = display.googleMapsMapId.isNotBlank(),
                    lastFailure = MapRuntimeSignals.lastFailureOrNull(),
                    failureCount = MapRuntimeSignals.failureCount(),
                    nowElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                    webGlRenderer = MapRuntimeSignals.webGlRendererOrNull(),
                    pageFrames = MapRuntimeSignals.pageFramesOrNull(),
                ),
            )
        }
}

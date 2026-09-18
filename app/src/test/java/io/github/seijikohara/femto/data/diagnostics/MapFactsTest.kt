package io.github.seijikohara.femto.data.diagnostics

import io.github.seijikohara.femto.data.display.GoogleMapsRendering
import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.data.map.MapRuntimeSignals
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapFactsTest {
    private fun facts(
        glEsVersion: String? = "3.2",
        backend: MapBackend = MapBackend.OSM,
        googleRendering: GoogleMapsRendering = GoogleMapsRendering.AUTO,
        hasGoogleMapId: Boolean = false,
        lastFailure: MapRuntimeSignals.MapFailure? = null,
        failureCount: Int = 0,
        nowElapsedRealtimeMs: Long = 0L,
        webGlRenderer: String? = null,
        pageFrames: MapRuntimeSignals.PageFrames? = null,
    ) = mapFactsFrom(
        glEsVersion,
        backend,
        googleRendering,
        hasGoogleMapId,
        lastFailure,
        failureCount,
        nowElapsedRealtimeMs,
        webGlRenderer,
        pageFrames,
    )

    @Test
    fun `page frames read no sample until the page has reported one`() {
        assertEquals(
            DiagnosticFact("Page frames", FactValue.Text("no sample yet")),
            facts().single { it.label == "Page frames" },
        )
    }

    @Test
    fun `a page keeping pace reports its cadence and the sample age as plain text`() {
        val fact =
            facts(
                pageFrames = MapRuntimeSignals.PageFrames(
                    medianMs = 33,
                    worstMs = 66,
                    sampledFrames = 30,
                    elapsedRealtimeMs = 5_000L,
                ),
                nowElapsedRealtimeMs = 17_000L,
            ).single { it.label == "Page frames (30)" }
        assertEquals(FactValue.Text("median 33 ms, worst 66 ms, 12s ago"), fact.value)
    }

    @Test
    fun `a page below ten frames a second warns`() {
        // The launcher's own UI frames can be on time while the WebView's renderer
        // has fallen to a frame a second; this row is what shows it.
        val fact =
            facts(
                pageFrames = MapRuntimeSignals.PageFrames(
                    medianMs = 980,
                    worstMs = 1400,
                    sampledFrames = 30,
                    elapsedRealtimeMs = 0L,
                ),
            ).single { it.label == "Page frames (30)" }
        assertEquals(
            FactValue.Status("median 980 ms, worst 1400 ms, 0s ago (< 10 fps)", FactHealth.WARNING),
            fact.value,
        )
    }

    @Test
    fun `the WebGL renderer reads unreported until a page has rendered`() {
        val fact = facts().single { it.label == "WebGL renderer" }
        assertEquals(FactValue.Text("unreported (no map page has rendered yet)"), fact.value)
    }

    @Test
    fun `a hardware WebGL renderer reports OK`() {
        val fact = facts(webGlRenderer = "Mali-G52 MC2").single { it.label == "WebGL renderer" }
        assertEquals(FactValue.Status("Mali-G52 MC2", FactHealth.OK), fact.value)
    }

    @Test
    fun `a software WebGL renderer warns, since a vector map then renders on the CPU`() {
        // The version fact above it can still read "expected": the platform's
        // OpenGL ES is fine, it is the WebView's blocklist that hands WebGL to
        // SwiftShader. Only the unmasked renderer string shows that.
        val fact = facts(webGlRenderer = "Google SwiftShader").single { it.label == "WebGL renderer" }
        assertEquals(
            FactValue.Status("Google SwiftShader — software; vector maps render on the CPU", FactHealth.WARNING),
            fact.value,
        )
    }

    @Test
    fun `a WebGL 2 backend below the OpenGL ES floor warns that it cannot render`() {
        // The Android 13 CDD mandates only ES 2.0, so this device is compliant and
        // still cannot host the WebGL 2 maplibre 6 requires.
        assertEquals(
            DiagnosticFact(
                "WebGL 2",
                FactValue.Status(
                    "unavailable (OpenGL ES 2.0, needs 3.0) — this backend cannot render",
                    FactHealth.WARNING,
                ),
            ),
            facts(glEsVersion = "2.0", backend = MapBackend.OSM).first(),
        )
    }

    @Test
    fun `a Google raster map never warns, because it needs no WebGL at all`() {
        // Raster tiles are rendered server-side, so flagging WebGL here would
        // report a problem on a map that draws perfectly.
        assertEquals(
            DiagnosticFact("WebGL 2", FactValue.Text("not required (Google Maps raster)")),
            facts(
                glEsVersion = "2.0",
                backend = MapBackend.GOOGLEMAPS,
                googleRendering = GoogleMapsRendering.AUTO,
                hasGoogleMapId = false,
            ).first(),
        )
    }

    @Test
    fun `an explicit Google raster choice never warns, even with a Map ID set`() {
        // RASTER overrides the Map ID's cloud configuration, so the Map ID no
        // longer implies vector — the pre-setting inference would have warned here.
        assertEquals(
            DiagnosticFact("WebGL 2", FactValue.Text("not required (Google Maps raster)")),
            facts(
                glEsVersion = "2.0",
                backend = MapBackend.GOOGLEMAPS,
                googleRendering = GoogleMapsRendering.RASTER,
                hasGoogleMapId = true,
            ).first(),
        )
    }

    @Test
    fun `an explicit Google vector choice warns without a Map ID`() {
        // Vector no longer needs a Map ID (Maps JS 3.56.10+), so the warning must
        // not depend on one.
        assertEquals(
            FactHealth.WARNING,
            facts(
                glEsVersion = "2.0",
                backend = MapBackend.GOOGLEMAPS,
                googleRendering = GoogleMapsRendering.VECTOR,
                hasGoogleMapId = false,
            ).first().health,
        )
    }

    @Test
    fun `a Google vector map warns that it silently degrades rather than fails`() {
        // Google falls back to raster instead of failing, so the map still draws —
        // what is lost is the vector opt-in the user configured a Map ID for.
        assertEquals(
            DiagnosticFact(
                "WebGL 2",
                FactValue.Status(
                    "unavailable (OpenGL ES 2.0, needs 3.0) — vector map falls back to raster",
                    FactHealth.WARNING,
                ),
            ),
            facts(
                glEsVersion = "2.0",
                backend = MapBackend.GOOGLEMAPS,
                googleRendering = GoogleMapsRendering.AUTO,
                hasGoogleMapId = true,
            ).first(),
        )
    }

    @Test
    fun `an OpenGL ES version at or above 3_0 reports WebGL 2 as expected`() {
        assertEquals(
            DiagnosticFact("WebGL 2", FactValue.Status("expected (OpenGL ES 3.2)", FactHealth.OK)),
            facts(glEsVersion = "3.2").first(),
        )
    }

    @Test
    fun `a minor version alone never crosses the floor`() {
        // "2.9" must stay below 3.0: comparing the strings, or the minor in
        // isolation, would read it as sufficient.
        assertEquals(FactHealth.WARNING, facts(glEsVersion = "2.9").first().health)
    }

    @Test
    fun `an unreported OpenGL ES version says so rather than guessing`() {
        assertEquals(
            DiagnosticFact("WebGL 2", FactValue.Text("unknown (OpenGL ES unreported)")),
            facts(glEsVersion = null).first(),
        )
    }

    @Test
    fun `a clean session reports no failure`() {
        assertEquals(
            DiagnosticFact("Last failure", FactValue.Status("none this session", FactHealth.OK)),
            facts().single { it.label == "Last failure" },
        )
    }

    @Test
    fun `a recorded failure reports its reason and age`() {
        assertEquals(
            DiagnosticFact("Last failure", FactValue.Status("no-webgl-context (90s ago)", FactHealth.ERROR)),
            facts(
                lastFailure = MapRuntimeSignals.MapFailure("no-webgl-context", elapsedRealtimeMs = 1_000L),
                failureCount = 1,
                nowElapsedRealtimeMs = 91_000L,
            ).single { it.label == "Last failure" },
        )
    }

    @Test
    fun `a single failure adds no count row`() {
        assertTrue(
            facts(
                lastFailure = MapRuntimeSignals.MapFailure("no-webgl-context", elapsedRealtimeMs = 0L),
                failureCount = 1,
            ).none { it.label == "Failures this session" },
        )
    }

    @Test
    fun `repeated failures surface the count so a flapping map is visible`() {
        assertEquals(
            DiagnosticFact("Failures this session", FactValue.Text("4")),
            facts(
                lastFailure = MapRuntimeSignals.MapFailure("style-load-failed", elapsedRealtimeMs = 0L),
                failureCount = 4,
            ).single { it.label == "Failures this session" },
        )
    }
}

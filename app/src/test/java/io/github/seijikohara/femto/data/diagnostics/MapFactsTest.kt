package io.github.seijikohara.femto.data.diagnostics

import io.github.seijikohara.femto.data.map.MapRuntimeSignals
import org.junit.Test
import kotlin.test.assertEquals

class MapFactsTest {
    @Test
    fun `an OpenGL ES version below 3_0 warns that WebGL 2 is unavailable`() {
        // The Android 13 CDD mandates only ES 2.0, so this device is compliant and
        // still cannot host the WebGL 2 the OSM and Mapbox backends require.
        val facts = mapFactsFrom(glEsVersion = "2.0", lastFailure = null, failureCount = 0, nowElapsedRealtimeMs = 0L)

        assertEquals(
            DiagnosticFact("WebGL 2", FactValue.Status("unavailable (OpenGL ES 2.0, needs 3.0)", FactHealth.WARNING)),
            facts.first(),
        )
    }

    @Test
    fun `an OpenGL ES version at or above 3_0 reports WebGL 2 as expected`() {
        val facts = mapFactsFrom(glEsVersion = "3.2", lastFailure = null, failureCount = 0, nowElapsedRealtimeMs = 0L)

        assertEquals(
            DiagnosticFact("WebGL 2", FactValue.Status("expected (OpenGL ES 3.2)", FactHealth.OK)),
            facts.first(),
        )
    }

    @Test
    fun `a minor version alone never crosses the floor`() {
        // "2.9" must stay below 3.0: comparing the strings, or the minor in
        // isolation, would read it as sufficient.
        val facts = mapFactsFrom(glEsVersion = "2.9", lastFailure = null, failureCount = 0, nowElapsedRealtimeMs = 0L)

        assertEquals(FactHealth.WARNING, facts.first().health)
    }

    @Test
    fun `an unreported OpenGL ES version says so rather than guessing`() {
        val facts = mapFactsFrom(glEsVersion = null, lastFailure = null, failureCount = 0, nowElapsedRealtimeMs = 0L)

        assertEquals(DiagnosticFact("WebGL 2", FactValue.Text("unknown (OpenGL ES unreported)")), facts.first())
    }

    @Test
    fun `a clean session reports no failure`() {
        val facts = mapFactsFrom(glEsVersion = "3.2", lastFailure = null, failureCount = 0, nowElapsedRealtimeMs = 0L)

        assertEquals(DiagnosticFact("Last failure", FactValue.Status("none this session", FactHealth.OK)), facts[1])
    }

    @Test
    fun `a recorded failure reports its reason and age`() {
        val facts =
            mapFactsFrom(
                glEsVersion = "3.2",
                lastFailure = MapRuntimeSignals.MapFailure("no-webgl-context", elapsedRealtimeMs = 1_000L),
                failureCount = 1,
                nowElapsedRealtimeMs = 91_000L,
            )

        assertEquals(
            DiagnosticFact("Last failure", FactValue.Status("no-webgl-context (90s ago)", FactHealth.ERROR)),
            facts[1],
        )
    }

    @Test
    fun `a single failure adds no count row`() {
        val facts =
            mapFactsFrom(
                glEsVersion = "3.2",
                lastFailure = MapRuntimeSignals.MapFailure("no-webgl-context", elapsedRealtimeMs = 0L),
                failureCount = 1,
                nowElapsedRealtimeMs = 0L,
            )

        assertEquals(2, facts.size)
    }

    @Test
    fun `repeated failures surface the count so a flapping map is visible`() {
        val facts =
            mapFactsFrom(
                glEsVersion = "3.2",
                lastFailure = MapRuntimeSignals.MapFailure("style-load-failed", elapsedRealtimeMs = 0L),
                failureCount = 4,
                nowElapsedRealtimeMs = 0L,
            )

        assertEquals(DiagnosticFact("Failures this session", FactValue.Text("4")), facts[2])
    }
}

package io.github.seijikohara.femto.data.diagnostics

import org.junit.Test
import kotlin.test.assertEquals

// Baseline inputs for a plausible car-display geometry; each test overrides
// only the field(s) it pins so the derivation under test stays legible.
private fun baselineFacts(
    densityDpi: Int = 160,
    stableDensityDpi: Int = 160,
    xdpi: Float = 236f,
    ydpi: Float = 236f,
    widthPx: Int = 1280,
    heightPx: Int = 720,
    fontScale: Float = 1.0f,
): List<DiagnosticFact> =
    displayGeometryFacts(
        boundsPx = "1280x720",
        maxBoundsPx = "1280x720",
        densityDpi = densityDpi,
        stableDensityDpi = stableDensityDpi,
        density = densityDpi / 160f,
        xdpi = xdpi,
        ydpi = ydpi,
        widthPx = widthPx,
        heightPx = heightPx,
        screenWidthDp = 800,
        screenHeightDp = 450,
        smallestScreenWidthDp = 450,
        fontScale = fontScale,
        orientationPortrait = false,
        nightMode = false,
    )

class DisplayFactsTest {
    @Test
    fun `displayGeometryFacts flags a stable density mismatch as an overridden warning`() {
        val facts = baselineFacts(densityDpi = 200, stableDensityDpi = 160)

        assertEquals(
            DiagnosticFact(
                "Stable density",
                FactValue.Status("160 dpi (current 200 — OVERRIDDEN)", FactHealth.WARNING),
            ),
            facts.first { it.label == "Stable density" },
        )
    }

    @Test
    fun `displayGeometryFacts clears the stable density when it matches the current density`() {
        val facts = baselineFacts(densityDpi = 160, stableDensityDpi = 160)

        assertEquals(
            DiagnosticFact("Stable density", FactValue.Status("160 dpi", FactHealth.OK)),
            facts.first { it.label == "Stable density" },
        )
    }

    @Test
    fun `displayGeometryFacts reports a plausible diagonal as info`() {
        val facts = baselineFacts(widthPx = 1280, heightPx = 720, xdpi = 236f, ydpi = 236f)

        assertEquals(
            DiagnosticFact("Physical diagonal", FactValue.Status("6.2\"", FactHealth.INFO)),
            facts.first { it.label == "Physical diagonal" },
        )
    }

    @Test
    fun `displayGeometryFacts flags an absurd diagonal outside 3 to 30 inches as a warning`() {
        val facts = baselineFacts(widthPx = 3600, heightPx = 2700, xdpi = 100f, ydpi = 100f)

        assertEquals(
            DiagnosticFact("Physical diagonal", FactValue.Status("45.0\"", FactHealth.WARNING)),
            facts.first { it.label == "Physical diagonal" },
        )
    }

    @Test
    fun `displayGeometryFacts flags a non-default font scale as a warning`() {
        val facts = baselineFacts(fontScale = 1.3f)

        assertEquals(
            DiagnosticFact("Font scale", FactValue.Status("1.3", FactHealth.WARNING)),
            facts.first { it.label == "Font scale" },
        )
    }

    @Test
    fun `displayGeometryFacts clears the default font scale`() {
        val facts = baselineFacts(fontScale = 1.0f)

        assertEquals(
            DiagnosticFact("Font scale", FactValue.Status("1.0", FactHealth.OK)),
            facts.first { it.label == "Font scale" },
        )
    }
}

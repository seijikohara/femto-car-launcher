package io.github.seijikohara.femto.testfixtures

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

// Gradle runs the :app JVM test tasks with app/ as the working directory, so
// the webmap source shared with the live page sits one level up from there.
private val RecolorDataFile = File("../webmap/src/map-recolor-data.json")

/**
 * Mirrors `webmap/src/style.ts` (`markerDrop`, `markerXFraction`,
 * `clampMarkerPos`) — the TypeScript is the SSOT; change both together.
 *
 * The golden/catalog map is a still image with no WebView to draw the live
 * DOM chevron, so [io.github.seijikohara.femto.testfixtures.MapBackdrop]
 * reproduces its screen anchor from this object, driven by the same
 * [io.github.seijikohara.femto.ui.home.components.MapConfig] placement
 * inputs the live page receives.
 */
internal object SelfMarkerAnchor {
    // Same cap the live page reads from map-recolor-data.json — never a
    // duplicated literal (AGENTS.md#ssot-dry).
    val maxMarkerDrop: Float = readMaxMarkerDrop()

    /** Marker drop below centre, as a fraction of map height. */
    fun drop(
        markerPos: Int,
        bottomSafeFraction: Float,
    ): Float {
        val cap = minOf(maxMarkerDrop, 0.5f - bottomSafeFraction).coerceAtLeast(0f)
        return clampMarkerPos(markerPos) / 100f * cap
    }

    /**
     * Horizontal shift from centre, as a fraction of map width. Positive
     * shifts right (clearing a left-hand card reserve), negative shifts left
     * (clearing a right-hand one).
     */
    fun xShift(
        leftSafeFraction: Float,
        rightSafeFraction: Float,
    ): Float = xFraction(leftSafeFraction) - xFraction(rightSafeFraction)

    private fun xFraction(safeFraction: Float): Float = (safeFraction / 2f).coerceIn(0f, 0.35f)

    private fun clampMarkerPos(markerPos: Int): Float = markerPos.toFloat().coerceIn(0f, 100f)

    private fun readMaxMarkerDrop(): Float {
        check(RecolorDataFile.isFile) {
            "${RecolorDataFile.path} not found - expected the JVM test working directory to be app/"
        }
        return Json
            .parseToJsonElement(RecolorDataFile.readText())
            .jsonObject
            .getValue("maxMarkerDrop")
            .jsonPrimitive
            .float
    }
}

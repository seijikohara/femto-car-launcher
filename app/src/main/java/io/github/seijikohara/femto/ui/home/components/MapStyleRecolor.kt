package io.github.seijikohara.femto.ui.home.components

import org.json.JSONObject

/**
 * Recolour a bundled base style's JSON with the ACCENT palette: background, water,
 * landcover / landuse / park fills, transportation lines, 2D building fills, and
 * label text (theme-tracked colour with a background-coloured halo — the bases'
 * own label colours are tuned to their own backgrounds and go illegible on the
 * Material surface). Roads keep their zoom-interpolated widths from the base
 * style, so the visual hierarchy (motorway / major / minor) survives the recolour.
 *
 * Mirrors `injectFeatures` / `roadClassOrNull` in `webmap/src/style.ts` for the
 * live backend — keep the layer groups and the road classification in sync.
 */
internal fun recolorAccent(
    styleJson: String,
    colors: AccentMapColors,
): String {
    val root = JSONObject(styleJson)
    val layers = root.optJSONArray("layers") ?: return styleJson
    for (i in 0 until layers.length()) {
        val layer = layers.getJSONObject(i)
        val sourceLayer = layer.optString("source-layer")
        val type = layer.optString("type")
        when {
            type == "background" -> {
                layer.paint().put("background-color", colors.background)
            }

            type == "fill" && sourceLayer == "water" -> {
                layer.paint().put("fill-color", colors.water)
            }

            type == "fill" && sourceLayer in AccentLandLayers -> {
                layer.paint().put("fill-color", colors.land)
            }

            type == "fill" && sourceLayer == "building" -> {
                layer.paint().put("fill-color", colors.building)
            }

            type == "line" && sourceLayer == "transportation" -> {
                roadColorOrNull(layer.optString("id"), colors)?.let { layer.paint().put("line-color", it) }
            }

            // Text-less symbol layers (oneway arrows) ignore the text-* keys.
            // The halo width is only seeded where the base omits it (e.g. the
            // motorway names); existing widths are the base's tuning, kept as-is.
            type == "symbol" -> {
                layer.paint().apply {
                    put("text-color", colors.label)
                    put("text-halo-color", colors.background)
                    if (!has("text-halo-width")) put("text-halo-width", 1)
                }
            }
        }
    }
    return root.toString()
}

// Created only on a recolour match, so untouched layers keep their exact shape
// (the TS mirror's setPaint behaves the same way).
private fun JSONObject.paint(): JSONObject = optJSONObject("paint") ?: JSONObject().also { put("paint", it) }

// Road classification by layer id, shared between the bundled light/dark styles.
// "subtle" (the low-zoom motorway representation) classifies as minor: in the dark
// base its colour is identical to highway_minor, and as "major" it would render a
// casing-less dark hairline on the dark background. Railways, piers, oneway arrows,
// and aeroways fall outside the highway_/tunnel_ prefixes and keep their base
// colours. Mirrors roadClassOrNull in webmap/src/style.ts — keep in sync.
private fun roadColorOrNull(
    id: String,
    colors: AccentMapColors,
): String? =
    when {
        !id.startsWith("highway_") && !id.startsWith("tunnel_") -> null
        "casing" in id -> colors.roadCasing
        RoadMinorKeywords.any { it in id } -> colors.roadMinor
        else -> colors.roadMajor
    }

private val AccentLandLayers = setOf("landcover", "landuse", "park")

private val RoadMinorKeywords = listOf("minor", "path", "subtle")

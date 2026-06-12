package io.github.seijikohara.femto.ui.home.components

import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Mirrors the assertions in webmap/src/style.test.ts (the live backend's recolour
// tests) so the two backends' layer groups and road classification stay in sync.
// Robolectric provides the real org.json implementation recolorAccent runs on.
@RunWith(RobolectricTestRunner::class)
class MapStyleRecolorTest {
    private val colors =
        AccentMapColors(
            background = "#101010",
            water = "#202020",
            land = "#303030",
            roadMajor = "#404040",
            roadMinor = "#505050",
            roadCasing = "#606060",
            building = "#707070",
            label = "#808080",
        )

    private fun layer(
        id: String,
        type: String,
        sourceLayer: String? = null,
    ): String =
        buildString {
            append("""{"id":"$id","type":"$type"""")
            sourceLayer?.let { append(""","source-layer":"$it"""") }
            append("}")
        }

    private val styleJson =
        """
        {"version":8,"layers":[
            ${layer("bg", "background")},
            ${layer("water", "fill", "water")},
            ${layer("park", "fill", "park")},
            ${layer("building", "fill", "building")},
            ${layer("highway_motorway_casing", "line", "transportation")},
            ${layer("highway_motorway_inner", "line", "transportation")},
            ${layer("tunnel_motorway_inner", "line", "transportation")},
            ${layer("highway_major_subtle", "line", "transportation")},
            ${layer("highway_minor", "line", "transportation")},
            ${layer("highway_path", "line", "transportation")},
            ${layer("tunnel_motorway_casing", "line", "transportation")},
            ${layer("railway_minor", "line", "transportation")},
            ${layer("road_pier", "line", "transportation")},
            ${layer("aeroway-runway", "line", "aeroway")},
            ${layer("labels", "symbol", "place")},
            {"id":"labels_haloed","type":"symbol","source-layer":"place","paint":{"text-halo-width":1.4}}
        ]}
        """.trimIndent()

    private fun recoloredPaintOrNull(id: String): JSONObject? {
        val layers = JSONObject(recolorAccent(styleJson, colors)).getJSONArray("layers")
        return (0 until layers.length())
            .map { layers.getJSONObject(it) }
            .first { it.getString("id") == id }
            .optJSONObject("paint")
    }

    @Test
    fun recolors_background_water_land_and_building_fills() {
        assertEquals(colors.background, recoloredPaintOrNull("bg")?.getString("background-color"))
        assertEquals(colors.water, recoloredPaintOrNull("water")?.getString("fill-color"))
        assertEquals(colors.land, recoloredPaintOrNull("park")?.getString("fill-color"))
        assertEquals(colors.building, recoloredPaintOrNull("building")?.getString("fill-color"))
    }

    @Test
    fun recolors_casing_roads_with_the_casing_color() {
        assertEquals(colors.roadCasing, recoloredPaintOrNull("highway_motorway_casing")?.getString("line-color"))
        assertEquals(colors.roadCasing, recoloredPaintOrNull("tunnel_motorway_casing")?.getString("line-color"))
    }

    @Test
    fun recolors_minor_path_and_subtle_roads_with_the_minor_color() {
        assertEquals(colors.roadMinor, recoloredPaintOrNull("highway_minor")?.getString("line-color"))
        assertEquals(colors.roadMinor, recoloredPaintOrNull("highway_path")?.getString("line-color"))
        assertEquals(colors.roadMinor, recoloredPaintOrNull("highway_major_subtle")?.getString("line-color"))
    }

    @Test
    fun recolors_inner_roads_with_the_major_color() {
        assertEquals(colors.roadMajor, recoloredPaintOrNull("highway_motorway_inner")?.getString("line-color"))
        assertEquals(colors.roadMajor, recoloredPaintOrNull("tunnel_motorway_inner")?.getString("line-color"))
    }

    @Test
    fun recolors_labels_with_a_background_halo_and_seeds_a_missing_halo_width() {
        val paint = recoloredPaintOrNull("labels")
        assertEquals(colors.label, paint?.getString("text-color"))
        assertEquals(colors.background, paint?.getString("text-halo-color"))
        assertEquals(1, paint?.getInt("text-halo-width"))
    }

    @Test
    fun keeps_an_existing_halo_width() {
        assertEquals(1.4, recoloredPaintOrNull("labels_haloed")?.getDouble("text-halo-width"))
    }

    @Test
    fun leaves_railways_piers_and_aeroways_untouched() {
        assertNull(recoloredPaintOrNull("railway_minor"))
        assertNull(recoloredPaintOrNull("road_pier"))
        assertNull(recoloredPaintOrNull("aeroway-runway"))
    }

    @Test
    fun returns_input_unchanged_when_style_has_no_layers() {
        val noLayers = """{"version":8}"""
        assertEquals(noLayers, recolorAccent(noLayers, colors))
    }
}

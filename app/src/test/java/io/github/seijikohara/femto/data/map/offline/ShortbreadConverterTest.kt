package io.github.seijikohara.femto.data.map.offline

import io.github.seijikohara.femto.testfixtures.gunzipped
import io.github.seijikohara.femto.testfixtures.gzipped
import io.github.seijikohara.femto.testfixtures.sourceLayer
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ShortbreadConverterTest {
    private fun text(value: String): TileValue = TileValue.Text(value)

    private fun int(value: Long): TileValue = TileValue.Integer(value)

    private fun int(value: Int): TileValue = TileValue.Integer(value.toLong())

    private fun convert(
        zoom: Int,
        vararg layers: SourceLayer,
    ): Map<String, List<Map<String, TileValue>>> =
        ShortbreadConverter
            .convertLayers(zoom, layers.toList())
            .associate { layer ->
                layer.name to
                    layer.features.map { f -> f.properties.associate { it.key to it.value } }
            }

    private fun Map<String, List<Map<String, TileValue>>>.column(
        layer: String,
        key: String,
    ): List<TileValue?> = getValue(layer).map { it[key] }

    private fun count(
        result: Map<String, List<Map<String, TileValue>>>,
        layer: String,
    ): Int = result[layer]?.size ?: 0

    private fun kinds(vararg kinds: String): Array<Map<String, TileValue>> =
        kinds
            .map {
                mapOf("kind" to text(it))
            }.toTypedArray()

    @Test
    fun `place labels are ordered by class and then by population`() {
        val result =
            convert(
                14,
                sourceLayer(
                    "place_labels",
                    mapOf("kind" to text("hamlet"), "population" to int(50)),
                    mapOf("kind" to text("city"), "population" to int(600_000)),
                    mapOf("kind" to text("town"), "population" to int(30_000)),
                    mapOf("kind" to text("city"), "population" to int(2_000_000)),
                ),
            )

        assertEquals(listOf("city", "city", "town", "hamlet").map(::text), result.column("place", "class"))
        assertEquals(listOf(2, 4, 18, 24).map(::int), result.column("place", "rank"))
    }

    @Test
    fun `a city without a population gets rank 10 and capitals carry their level`() {
        val result = convert(10, sourceLayer("place_labels", *kinds("capital", "state_capital", "city")))

        assertEquals(listOf("city", "city", "city").map(::text), result.column("place", "class"))
        assertEquals(listOf(int(2), int(4), null), result.column("place", "capital"))
        assertEquals(listOf(10, 10, 10).map(::int), result.column("place", "rank"))
    }

    @Test
    fun `small place classes appear from their first zoom`() {
        fun placesAt(
            zoom: Int,
            kind: String,
        ) = count(convert(zoom, sourceLayer("place_labels", *kinds(kind))), "place")

        assertEquals(listOf(0, 1), listOf(placesAt(10, "hamlet"), placesAt(11, "hamlet")))
        assertEquals(listOf(0, 1), listOf(placesAt(12, "quarter"), placesAt(13, "quarter")))
        assertEquals(listOf(0, 1), listOf(placesAt(13, "isolated_dwelling"), placesAt(14, "isolated_dwelling")))
        assertEquals(listOf(0, 1), listOf(placesAt(13, "farm"), placesAt(14, "farm")))
        assertEquals(0, placesAt(20, "locality"))
    }

    @Test
    fun `boundary labels become countries, states and provinces by level and area`() {
        fun area(
            level: Long,
            wayArea: Double,
        ) = mapOf("admin_level" to int(level), "way_area" to TileValue.Real(wayArea))

        val result =
            convert(
                5,
                sourceLayer("place_labels", *kinds("city")),
                sourceLayer(
                    "boundary_labels",
                    area(4, 1e6),
                    area(2, 1e6),
                    area(3, 9e9),
                    area(4, 6e6),
                    area(2, 2e8),
                    area(2, 2e7),
                ),
            )

        assertEquals(
            listOf("country", "country", "country", "state", "province", "city").map(::text),
            result.column("place", "class"),
        )
        assertEquals(listOf(1, 2, 3, 1, 1, 10).map(::int), result.column("place", "rank"))
    }

    @Test
    fun `roads map to their class and appear from their first zoom`() {
        fun transportationAt(
            zoom: Int,
            kind: String,
        ) = convert(zoom, sourceLayer("streets", *kinds(kind)))["transportation"]

        assertNull(transportationAt(11, "residential"))
        assertEquals(text("minor"), transportationAt(12, "residential")?.single()?.get("class"))
        assertNull(transportationAt(10, "tertiary"))
        assertEquals(text("tertiary"), transportationAt(11, "tertiary")?.single()?.get("class"))
        assertEquals(mapOf("class" to text("motorway")), transportationAt(5, "motorway")?.single())
        assertEquals(
            mapOf("class" to text("transit"), "subclass" to text("tram")),
            transportationAt(14, "tram")?.single(),
        )
        assertEquals(
            mapOf("class" to text("path"), "subclass" to text("footway")),
            transportationAt(14, "footway")?.single(),
        )
    }

    @Test
    fun `road attributes are rewritten`() {
        val flag = TileValue.Flag(true)
        val result =
            convert(
                14,
                sourceLayer(
                    "streets",
                    mapOf("kind" to text("residential"), "link" to flag),
                    mapOf("kind" to text("residential"), "oneway" to flag),
                    mapOf("kind" to text("residential"), "oneway_reverse" to flag),
                    mapOf("kind" to text("residential"), "bridge" to flag),
                    mapOf("kind" to text("residential"), "tunnel" to flag),
                    mapOf("kind" to text("service"), "service" to text("driveway")),
                ),
            )

        assertEquals(listOf(int(1), null, null, null, null, null), result.column("transportation", "ramp"))
        assertEquals(listOf(null, int(1), int(-1), null, null, null), result.column("transportation", "oneway"))
        assertEquals(
            listOf(null, null, null, text("bridge"), text("tunnel"), null),
            result.column("transportation", "brunnel"),
        )
        assertEquals(listOf(null, null, null, null, null, text("driveway")), result.column("transportation", "service"))
    }

    @Test
    fun `runways and taxiways leave the road layers for aeroway`() {
        val result =
            convert(
                14,
                sourceLayer("streets", *kinds("runway", "taxiway")),
                sourceLayer("street_polygons", *kinds("apron", "helipad", "pedestrian")),
            )

        assertEquals(listOf("runway", "taxiway", "apron", "helipad").map(::text), result.column("aeroway", "class"))
        assertEquals(
            mapOf("class" to text("path"), "subclass" to text("pedestrian")),
            result.getValue("transportation").single(),
        )
    }

    @Test
    fun `street labels keep the first reference and sort major roads first`() {
        val result =
            convert(
                14,
                sourceLayer(
                    "street_labels",
                    mapOf("kind" to text("residential")),
                    mapOf("kind" to text("motorway_link"), "ref" to text("A1;E5")),
                    mapOf("kind" to text("motorway"), "ref" to text("国道11\nR11")),
                    mapOf("kind" to text("mystery")),
                ),
                sourceLayer("street_labels_points", mapOf("ref" to text("12\n13"))),
            )

        assertEquals(
            listOf("motorway", "motorway", "motorway", "minor").map(::text),
            result.column("transportation_name", "class"),
        )
        assertEquals(listOf(text("A1"), text("国道11"), text("12"), null), result.column("transportation_name", "ref"))
        assertEquals(listOf(2, 4, 2).map(::int) + null, result.column("transportation_name", "ref_length"))
        assertEquals(listOf(null, null, text("junction"), null), result.column("transportation_name", "subclass"))
    }

    @Test
    fun `boundaries carry numeric levels and flags`() {
        val result =
            convert(
                3,
                sourceLayer(
                    "boundaries",
                    mapOf("admin_level" to TileValue.Real(2.0), "maritime" to TileValue.Flag(true)),
                    mapOf("admin_level" to text("4")),
                    mapOf("disputed" to TileValue.Flag(true)),
                ),
            )

        assertEquals(listOf(2, 4, 0).map(::int), result.column("boundary", "admin_level"))
        assertEquals(listOf(1, 0, 0).map(::int), result.column("boundary", "maritime"))
        assertEquals(listOf(0, 0, 1).map(::int), result.column("boundary", "disputed"))
    }

    @Test
    fun `only piers are kept from the pier layers`() {
        val result =
            convert(
                14,
                sourceLayer("pier_lines", *kinds("pier", "breakwater")),
                sourceLayer("pier_polygons", *kinds("groyne", "pier")),
            )

        assertEquals(
            listOf(mapOf("class" to text("pier")), mapOf("class" to text("pier"))),
            result.getValue("transportation"),
        )
    }

    @Test
    fun `buildings get the constant height and keep their id`() {
        val layers =
            ShortbreadConverter.convertLayers(
                14,
                listOf(sourceLayer("buildings", mapOf("x" to text("y")), mapOf("x" to text("z")))),
            )

        val feature = layers.single().features.last()
        assertEquals(2L, feature.id)
        assertEquals(
            mapOf("render_height" to int(5), "render_min_height" to int(0)),
            feature.properties.associate { it.key to it.value },
        )
        assertContentEquals(byteArrayOf(1), feature.geometry)
    }

    @Test
    fun `water kinds map to classes and glaciers move to landcover`() {
        val result =
            convert(
                5,
                sourceLayer("ocean", mapOf("x" to text("y"))),
                sourceLayer("water_polygons", *kinds("river", "mystery", "glacier")),
            )

        assertEquals(listOf("ocean", "river", "lake").map(::text), result.column("water", "class"))
        assertEquals(
            mapOf("class" to text("ice"), "subclass" to text("glacier")),
            result.getValue("landcover").single(),
        )
    }

    @Test
    fun `land kinds split into landcover and landuse`() {
        val result = convert(5, sourceLayer("land", *kinds("forest", "grave_yard", "mystery")))

        assertEquals(
            mapOf("class" to text("wood"), "subclass" to text("forest")),
            result.getValue("landcover").single(),
        )
        assertEquals(mapOf("class" to text("cemetery")), result.getValue("landuse").single())
    }

    @Test
    fun `aerodromes are labelled with their code`() {
        val result =
            convert(
                8,
                sourceLayer(
                    "public_transport",
                    mapOf("kind" to text("aerodrome"), "iata" to text("LCA")),
                    mapOf("kind" to text("aerodrome")),
                    mapOf("kind" to text("station")),
                ),
            )

        assertEquals(listOf("international", "other").map(::text), result.column("aerodrome_label", "class"))
        assertEquals(listOf(text("LCA"), null), result.column("aerodrome_label", "iata"))
    }

    @Test
    fun `layers that share a name are all read`() {
        val result =
            convert(
                14,
                sourceLayer("buildings", mapOf("a" to text("1"))),
                sourceLayer("buildings", mapOf("a" to text("2"))),
            )

        assertEquals(2, count(result, "building"))
    }

    @Test
    fun `names are derived for labelled features`() {
        val result = convert(
            8,
            sourceLayer(
                "water_polygons_labels",
                mapOf(
                    "name" to text("松山"),
                    "name_en" to text("Matsuyama"),
                ),
            ),
        )

        assertEquals(text("Matsuyama"), result.getValue("water_name").single()["name:latin"])
        assertEquals(text("lake"), result.getValue("water_name").single()["class"])
    }

    @Test
    fun `values of an unexpected kind are read as documented`() {
        val result =
            convert(
                14,
                sourceLayer(
                    "place_labels",
                    mapOf(
                        "kind" to text("city"),
                        "population" to TileValue.Real(600_000.0),
                        "name" to int(3),
                    ),
                ),
                sourceLayer(
                    "streets",
                    mapOf("kind" to int(5)),
                    mapOf("kind" to text("residential"), "bridge" to int(1)),
                ),
            )

        assertEquals(listOf(4).map(::int), result.column("place", "rank"))
        assertNull(result.getValue("place").single()["name"])
        assertEquals(listOf(text("bridge")), result.column("transportation", "brunnel"))
        assertEquals(1, count(result, "transportation"))
    }

    private fun buildingTile(): ByteArray =
        VectorTileCodec.encode(
            listOf(
                OutputLayer(
                    "buildings",
                    4096,
                    (1L..20L).map {
                        OutputFeature(
                            it,
                            3,
                            byteArrayOf(9, it.toByte()),
                            listOf(TileProperty("h", TileValue.Integer(it))),
                        )
                    },
                ),
            ),
        )

    @Test
    fun `a gzip tile and a plain tile convert to the same bytes`() {
        val plain = buildingTile()

        val fromPlain = assertNotNull(ShortbreadConverter.convertOrNull(14, plain))
        val fromGzip = assertNotNull(ShortbreadConverter.convertOrNull(14, gzipped(plain)))

        assertContentEquals(fromPlain, fromGzip)
        assertEquals(
            20,
            assertNotNull(VectorTileCodec.decodeOrNull(fromPlain)).single { it.name == "building" }.features.size,
        )
        assertContentEquals(plain, gunzipped(gzipped(plain)))
    }

    @Test
    fun `a tile with nothing to map converts to an empty array`() {
        val tile = VectorTileCodec.encode(listOf(OutputLayer("mystery", 4096, emptyList())))

        assertEquals(0, assertNotNull(ShortbreadConverter.convertOrNull(14, tile)).size)
    }

    @Test
    fun `corrupt input converts to null`() {
        val plain = buildingTile()

        assertNull(
            ShortbreadConverter.convertOrNull(14, byteArrayOf(0x1f, 0x8b.toByte(), 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)),
        )
        assertNull(ShortbreadConverter.convertOrNull(14, plain.copyOf(plain.size / 2)))
        // An even run of zeros decodes as zero layers, so only the size cap can make this one null.
        assertNull(ShortbreadConverter.convertOrNull(14, gzipped(ByteArray(MAX_RAW_TILE_BYTES + 2))))
    }
}

package io.github.seijikohara.femto.data.map.offline

import android.util.Log
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

private const val TAG = "ShortbreadConverter"

private const val GZIP_MAGIC_FIRST: Byte = 0x1f
private const val GZIP_MAGIC_SECOND: Byte = 0x8b.toByte()

/** Upper bound for an inflated tile; a decompression bomb must not exhaust the heap of a head unit. */
internal const val MAX_RAW_TILE_BYTES = 16 * 1024 * 1024

/**
 * Rewrites Shortbread vector tiles into the OpenMapTiles layers the map styles read. Only attributes, layers and
 * feature order change; geometry bytes pass through untouched.
 */
internal object ShortbreadConverter {
    /** Gunzip when [tile] starts with the gzip magic, else return it; null when corrupt or larger than [MAX_RAW_TILE_BYTES] inflated. */
    fun rawTileOrNull(tile: ByteArray): ByteArray? =
        runCatching {
            if (tile.size >= 2 && tile[0] == GZIP_MAGIC_FIRST && tile[1] == GZIP_MAGIC_SECOND) {
                GZIPInputStream(ByteArrayInputStream(tile)).use { it.readNBytes(MAX_RAW_TILE_BYTES + 1) }
            } else {
                tile
            }
        }.getOrNull()?.takeIf { it.size <= MAX_RAW_TILE_BYTES }

    fun convertLayers(
        zoom: Int,
        source: List<SourceLayer>,
    ): List<OutputLayer> {
        val sink = Sink()
        val layers = source.groupBy { it.name }
        Converter(zoom, layers, sink).run()
        return sink.layers()
    }

    /** Run [rawTileOrNull], decode, [convertLayers] and encode; null on malformed input, an empty array when nothing maps. Never throws. */
    fun convertOrNull(
        zoom: Int,
        tile: ByteArray,
    ): ByteArray? =
        runCatching {
            rawTileOrNull(tile)
                ?.let { VectorTileCodec.decodeOrNull(it) }
                ?.let { VectorTileCodec.encode(convertLayers(zoom, it)) }
        }.onFailure { Log.w(TAG, "tile conversion failed", it) }
            .getOrNull()
}

/** An output feature with the keys [Sink] sorts the two ordered layers by. */
private class Keyed(
    val feature: OutputFeature,
    val rank: Int,
    val value: Double,
)

/** Collects output features per OpenMapTiles layer; a layer takes the extent of the first source layer that adds to it. */
private class Sink {
    private val extents = LinkedHashMap<String, Int>()
    private val plain = HashMap<String, ArrayList<OutputFeature>>()
    private val keyed = HashMap<String, ArrayList<Keyed>>()

    fun add(
        layer: String,
        from: SourceLayer,
        feature: SourceFeature,
        properties: List<TileProperty>,
    ) {
        extents.getOrPut(layer) { from.extent }
        plain.getOrPut(layer) { ArrayList() }.add(outputFeature(feature, properties))
    }

    /** Add a feature to a layer that is sorted by [rank] ascending, then [value] descending, keeping source order on ties. */
    fun addKeyed(
        layer: String,
        from: SourceLayer,
        feature: SourceFeature,
        properties: List<TileProperty>,
        rank: Int,
        value: Double,
    ) {
        extents.getOrPut(layer) { from.extent }
        keyed.getOrPut(layer) { ArrayList() }.add(Keyed(outputFeature(feature, properties), rank, value))
    }

    fun layers(): List<OutputLayer> =
        extents.map { (name, extent) ->
            val features =
                plain[name].orEmpty() +
                    keyed[name]
                        .orEmpty()
                        .sortedWith(compareBy<Keyed> { it.rank }.thenByDescending { it.value })
                        .map { it.feature }
            OutputLayer(name, extent, features)
        }

    private fun outputFeature(
        feature: SourceFeature,
        properties: List<TileProperty>,
    ) = OutputFeature(feature.id, feature.type, feature.geometry, properties)
}

private fun text(
    key: String,
    value: String,
) = TileProperty(key, TileValue.Text(value))

private fun integer(
    key: String,
    value: Long,
) = TileProperty(key, TileValue.Integer(value))

// A property is "set" when its value is a true flag, a non-zero number or a non-empty text; that is how the
// Shortbread schema writes its boolean-like attributes.
private fun SourceLayer.isSet(
    feature: SourceFeature,
    key: Int,
): Boolean =
    when (val value = valueOrNull(feature, key)) {
        is TileValue.Flag -> value.value
        is TileValue.Integer -> value.value != 0L
        is TileValue.Real -> value.value != 0.0
        is TileValue.Text -> value.value.isNotEmpty()
        null -> false
    }

private fun SourceLayer.textOrNull(
    feature: SourceFeature,
    key: Int,
): String? = (valueOrNull(feature, key) as? TileValue.Text)?.value

private fun SourceLayer.number(
    feature: SourceFeature,
    key: Int,
): Double =
    when (val value = valueOrNull(feature, key)) {
        is TileValue.Integer -> value.value.toDouble()
        is TileValue.Real -> value.value
        is TileValue.Text -> value.value.toDoubleOrNull() ?: 0.0
        is TileValue.Flag, null -> 0.0
    }

private fun SourceLayer.brunnelOrNull(
    feature: SourceFeature,
    bridge: Int,
    tunnel: Int,
): String? =
    when {
        isSet(feature, bridge) -> "bridge"
        isSet(feature, tunnel) -> "tunnel"
        else -> null
    }

private fun brunnel(value: String?): TileProperty? = value?.let { text("brunnel", it) }

private fun roadRank(roadClass: String): Int = RoadNameOrder.indexOf(roadClass).takeIf { it >= 0 } ?: RoadNameOrder.size

private fun placeRank(placeClass: String): Int = PlaceOrder.indexOf(placeClass).takeIf { it >= 0 } ?: PlaceOrder.size

/** The text before the first line break; a reference is rendered on one line. */
private fun firstLine(ref: String): String = ref.substringBefore('\n')

/** The tile-wide state of one conversion: each rule below reads every source layer of its Shortbread name. */
private class Converter(
    private val zoom: Int,
    private val layers: Map<String, List<SourceLayer>>,
    private val sink: Sink,
) {
    private inline fun eachLayer(
        name: String,
        block: (SourceLayer) -> Unit,
    ) {
        layers[name]?.forEach(block)
    }

    private inline fun SourceLayer.eachFeature(block: (SourceFeature) -> Unit) {
        features.forEach(block)
    }

    fun run() {
        ocean()
        waterPolygons()
        waterPolygonLabels()
        waterLines("water_lines", labelled = false)
        waterLines("water_lines_labels", labelled = true)
        landLike("land")
        landLike("sites")
        boundaries()
        streets()
        streetPolygons()
        piers("pier_lines")
        piers("pier_polygons")
        bridges()
        ferries()
        streetLabels()
        streetLabelPoints()
        buildings()
        publicTransport()
        boundaryLabels()
        placeLabels()
    }

    private fun ocean() =
        eachLayer("ocean") { layer ->
            val properties = listOf(text("class", "ocean"))
            layer.eachFeature { sink.add("water", layer, it, properties) }
        }

    private fun waterPolygons() =
        eachLayer("water_polygons") { layer ->
            val kind = layer.keyIndex("kind")
            layer.eachFeature { feature ->
                val kindText = layer.textOrNull(feature, kind)
                if (kindText == "glacier") {
                    sink.add("landcover", layer, feature, listOf(text("class", "ice"), text("subclass", "glacier")))
                } else {
                    sink.add(
                        "water",
                        layer,
                        feature,
                        listOf(text("class", WaterClasses[kindText] ?: DEFAULT_WATER_CLASS)),
                    )
                }
            }
        }

    private fun waterPolygonLabels() =
        eachLayer("water_polygons_labels") { layer ->
            val name = NameKeys(layer)
            layer.eachFeature { sink.add("water_name", layer, it, listOf(text("class", "lake")) + name.of(layer, it)) }
        }

    private fun waterLines(
        layerName: String,
        labelled: Boolean,
    ) = eachLayer(layerName) { layer ->
        val kind = layer.keyIndex("kind")
        val bridge = layer.keyIndex("bridge")
        val tunnel = layer.keyIndex("tunnel")
        val name = NameKeys(layer)
        layer.eachFeature { feature ->
            val properties =
                listOfNotNull(
                    layer.textOrNull(feature, kind)?.let { text("class", it) },
                    brunnel(layer.brunnelOrNull(feature, bridge, tunnel)),
                ) + if (labelled) name.of(layer, feature) else emptyList()
            sink.add("waterway", layer, feature, properties)
        }
    }

    private fun landLike(layerName: String) =
        eachLayer(layerName) { layer ->
            val kind = layer.keyIndex("kind")
            layer.eachFeature { feature ->
                val kindText = layer.textOrNull(feature, kind)
                val landcover = LandcoverClasses[kindText]
                val landuse = LanduseClasses[kindText]
                when {
                    kindText != null && landcover != null -> {
                        sink.add(
                            "landcover",
                            layer,
                            feature,
                            listOf(text("class", landcover), text("subclass", kindText)),
                        )
                    }

                    landuse != null -> {
                        sink.add("landuse", layer, feature, listOf(text("class", landuse)))
                    }
                }
            }
        }

    private fun boundaries() =
        eachLayer("boundaries") { layer ->
            val level = layer.keyIndex("admin_level")
            val maritime = layer.keyIndex("maritime")
            val disputed = layer.keyIndex("disputed")
            layer.eachFeature { feature ->
                sink.add(
                    "boundary",
                    layer,
                    feature,
                    listOf(
                        integer("admin_level", layer.number(feature, level).toLong()),
                        integer("maritime", if (layer.isSet(feature, maritime)) 1 else 0),
                        integer("disputed", if (layer.isSet(feature, disputed)) 1 else 0),
                    ),
                )
            }
        }

    private fun streets() =
        eachLayer("streets") { layer ->
            val kind = layer.keyIndex("kind")
            val bridge = layer.keyIndex("bridge")
            val tunnel = layer.keyIndex("tunnel")
            val link = layer.keyIndex("link")
            val oneway = layer.keyIndex("oneway")
            val onewayReverse = layer.keyIndex("oneway_reverse")
            val service = layer.keyIndex("service")
            layer.eachFeature { feature ->
                val kindText = layer.textOrNull(feature, kind)
                val roadClass = RoadClasses[kindText]
                when {
                    kindText != null && kindText in AerowayKinds -> {
                        sink.add("aeroway", layer, feature, listOf(text("class", kindText)))
                    }

                    roadClass != null && zoom >= (RoadMinZoom[roadClass] ?: 0) -> {
                        sink.add(
                            "transportation",
                            layer,
                            feature,
                            listOfNotNull(
                                text("class", roadClass),
                                kindText?.takeIf { roadClass in RoadClassesWithSubclass }?.let { text("subclass", it) },
                                brunnel(layer.brunnelOrNull(feature, bridge, tunnel)),
                                integer("ramp", 1).takeIf { layer.isSet(feature, link) },
                                when {
                                    layer.isSet(feature, oneway) -> integer("oneway", 1)
                                    layer.isSet(feature, onewayReverse) -> integer("oneway", -1)
                                    else -> null
                                },
                                layer
                                    .textOrNull(
                                        feature,
                                        service,
                                    )?.takeIf { it.isNotEmpty() }
                                    ?.let { text("service", it) },
                            ),
                        )
                    }
                }
            }
        }

    private fun streetPolygons() =
        eachLayer("street_polygons") { layer ->
            val kind = layer.keyIndex("kind")
            val bridge = layer.keyIndex("bridge")
            val tunnel = layer.keyIndex("tunnel")
            layer.eachFeature { feature ->
                val kindText = layer.textOrNull(feature, kind)
                val roadClass = RoadClasses[kindText]
                when {
                    kindText != null && kindText in AerowayKinds -> {
                        sink.add("aeroway", layer, feature, listOf(text("class", kindText)))
                    }

                    roadClass != null && kindText != null -> {
                        sink.add(
                            "transportation",
                            layer,
                            feature,
                            listOfNotNull(
                                text("class", roadClass),
                                text("subclass", kindText),
                                brunnel(layer.brunnelOrNull(feature, bridge, tunnel)),
                            ),
                        )
                    }
                }
            }
        }

    private fun piers(layerName: String) =
        eachLayer(layerName) { layer ->
            val kind = layer.keyIndex("kind")
            val properties = listOf(text("class", "pier"))
            layer.eachFeature { feature ->
                if (layer.textOrNull(feature, kind) == "pier") sink.add("transportation", layer, feature, properties)
            }
        }

    private fun bridges() =
        eachLayer("bridges") { layer ->
            val properties = listOf(text("class", "bridge"))
            layer.eachFeature { sink.add("transportation", layer, it, properties) }
        }

    private fun ferries() =
        eachLayer("ferries") { layer ->
            val name = NameKeys(layer)
            layer.eachFeature {
                sink.add(
                    "transportation",
                    layer,
                    it,
                    listOf(text("class", "ferry")) + name.of(layer, it),
                )
            }
        }

    private fun streetLabels() =
        eachLayer("street_labels") { layer ->
            val kind = layer.keyIndex("kind")
            val ref = layer.keyIndex("ref")
            val bridge = layer.keyIndex("bridge")
            val tunnel = layer.keyIndex("tunnel")
            val name = NameKeys(layer)
            layer.eachFeature { feature ->
                val kindText = layer.textOrNull(feature, kind)?.removeSuffix("_link")
                val roadClass = RoadClasses[kindText]
                if (roadClass != null) {
                    val shortRef =
                        layer
                            .textOrNull(feature, ref)
                            ?.let { firstLine(it).substringBefore(';') }
                            ?.takeIf { it.isNotEmpty() }
                    sink.addKeyed(
                        "transportation_name",
                        layer,
                        feature,
                        listOfNotNull(
                            text("class", roadClass),
                            kindText?.takeIf { roadClass == "path" }?.let { text("subclass", it) },
                            brunnel(layer.brunnelOrNull(feature, bridge, tunnel)),
                            shortRef?.let { text("ref", it) },
                            shortRef?.let { integer("ref_length", it.codePointCount(0, it.length).toLong()) },
                        ) + name.of(layer, feature),
                        rank = roadRank(roadClass),
                        value = 0.0,
                    )
                }
            }
        }

    private fun streetLabelPoints() =
        eachLayer("street_labels_points") { layer ->
            val ref = layer.keyIndex("ref")
            val name = NameKeys(layer)
            layer.eachFeature { feature ->
                val junctionRef =
                    layer
                        .textOrNull(feature, ref)
                        ?.let(::firstLine)
                        ?.takeIf { it.isNotEmpty() }
                sink.addKeyed(
                    "transportation_name",
                    layer,
                    feature,
                    listOfNotNull(
                        text("class", "motorway"),
                        text("subclass", "junction"),
                        junctionRef?.let { text("ref", it) },
                        junctionRef?.let { integer("ref_length", it.codePointCount(0, it.length).toLong()) },
                    ) + name.of(layer, feature),
                    rank = roadRank("motorway"),
                    value = 0.0,
                )
            }
        }

    private fun buildings() =
        eachLayer("buildings") { layer ->
            val properties = listOf(integer("render_height", BUILDING_HEIGHT), integer("render_min_height", 0))
            layer.eachFeature { sink.add("building", layer, it, properties) }
        }

    private fun publicTransport() =
        eachLayer("public_transport") { layer ->
            val kind = layer.keyIndex("kind")
            val iata = layer.keyIndex("iata")
            val name = NameKeys(layer)
            layer.eachFeature { feature ->
                if (layer.textOrNull(feature, kind) == "aerodrome") {
                    val code = layer.textOrNull(feature, iata)?.takeIf { it.isNotEmpty() }
                    sink.add(
                        "aerodrome_label",
                        layer,
                        feature,
                        listOfNotNull(
                            text("class", if (code != null) "international" else "other"),
                            code?.let { text("iata", it) },
                        ) + name.of(layer, feature),
                    )
                }
            }
        }

    private fun boundaryLabels() =
        eachLayer("boundary_labels") { layer ->
            val level = layer.keyIndex("admin_level")
            val area = layer.keyIndex("way_area")
            val name = NameKeys(layer)
            layer.eachFeature { feature ->
                val wayArea = layer.number(feature, area)
                val placeClass: String?
                val rank: Long
                when (layer.number(feature, level).toLong()) {
                    2L -> {
                        placeClass = "country"
                        rank = countryRank(wayArea)
                    }

                    4L -> {
                        placeClass = if (wayArea >= STATE_MIN_AREA) "state" else "province"
                        rank = 1
                    }

                    else -> {
                        placeClass = null
                        rank = 0
                    }
                }
                if (placeClass != null) {
                    sink.addKeyed(
                        "place",
                        layer,
                        feature,
                        listOf(text("class", placeClass), integer("rank", rank)) + name.of(layer, feature),
                        rank = placeRank(placeClass),
                        value = wayArea,
                    )
                }
            }
        }

    private fun placeLabels() =
        eachLayer("place_labels") { layer ->
            val kind = layer.keyIndex("kind")
            val population = layer.keyIndex("population")
            val name = NameKeys(layer)
            layer.eachFeature { feature ->
                val place = PlaceClasses[layer.textOrNull(feature, kind)]
                if (place != null && zoom >= (PlaceMinZoom[place.placeClass] ?: 0)) {
                    val people = layer.number(feature, population)
                    val rank =
                        1 + CityRankPopulation.count { it > people } + (PlaceRankOffset[place.placeClass] ?: 0)
                    sink.addKeyed(
                        "place",
                        layer,
                        feature,
                        listOfNotNull(
                            text("class", place.placeClass),
                            place.capital?.let { integer("capital", it) },
                            integer("rank", rank.toLong()),
                        ) + name.of(layer, feature),
                        rank = placeRank(place.placeClass),
                        value = people,
                    )
                }
            }
        }
}

/** The key indices of the three name attributes of one source layer, resolved once per layer. */
private class NameKeys(
    layer: SourceLayer,
) {
    private val name = layer.keyIndex("name")
    private val nameEn = layer.keyIndex("name_en")
    private val nameDe = layer.keyIndex("name_de")

    fun of(
        layer: SourceLayer,
        feature: SourceFeature,
    ): List<TileProperty> =
        nameProperties(
            layer.textOrNull(feature, name),
            layer.textOrNull(feature, nameEn),
            layer.textOrNull(feature, nameDe),
        )
}

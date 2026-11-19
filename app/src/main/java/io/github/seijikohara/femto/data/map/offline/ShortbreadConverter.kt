package io.github.seijikohara.femto.data.map.offline

import android.util.Log
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

private const val TAG = "ShortbreadConverter"

private const val GZIP_MAGIC_FIRST: Byte = 0x1f
private const val GZIP_MAGIC_SECOND: Byte = 0x8b.toByte()

private const val GZIP_MIN_BYTES = 18
private const val INFLATE_BUFFER_BYTES = 64 * 1024

// Deflate cannot expand its input by more than this factor, so a size hint beyond it is a lie.
private const val DEFLATE_MAX_RATIO = 1032L

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
                inflateSizedOrNull(tile)
                    ?: GZIPInputStream(ByteArrayInputStream(tile)).use { it.readNBytes(MAX_RAW_TILE_BYTES + 1) }
            } else {
                tile
            }
        }.getOrNull()?.takeIf { it.size <= MAX_RAW_TILE_BYTES }

    /**
     * Inflate [tile] into one array sized by the gzip trailer, which spares the chunk-and-concatenate copy of an
     * unsized read. Return null whenever the trailer cannot be trusted (wrong size, extra members, an implausible
     * hint), so the caller falls back to the bounded unsized read and the result never depends on the hint.
     */
    private fun inflateSizedOrNull(tile: ByteArray): ByteArray? {
        if (tile.size < GZIP_MIN_BYTES) return null
        val last = tile.size - 4
        val hint =
            (tile[last].toLong() and 0xff) or
                ((tile[last + 1].toLong() and 0xff) shl 8) or
                ((tile[last + 2].toLong() and 0xff) shl 16) or
                ((tile[last + 3].toLong() and 0xff) shl 24)
        if (hint < 1 || hint > MAX_RAW_TILE_BYTES || hint > tile.size * DEFLATE_MAX_RATIO) return null
        val out = ByteArray(hint.toInt())
        return GZIPInputStream(ByteArrayInputStream(tile), INFLATE_BUFFER_BYTES).use { stream ->
            out.takeIf { stream.readNBytes(it, 0, it.size) == it.size && stream.read() == -1 }
        }
    }

    /** Return the OpenMapTiles layers for the decoded Shortbread [source] layers of a tile at [zoom]. */
    fun convertLayers(
        zoom: Int,
        source: List<SourceLayer>,
    ): List<OutputLayer> = Sink().also { Converter(zoom, source.groupBy { it.name }, it).run() }.layers()

    /** Run [rawTileOrNull], decode, [convertLayers] and encode; null on malformed input, an empty array when nothing maps. Never throws. */
    fun convertOrNull(
        zoom: Int,
        tile: ByteArray,
    ): ByteArray? =
        // A plain try, not runCatching: wrapping the failure in a Result allocates, and under memory exhaustion that
        // allocation could itself throw out of this function.
        try {
            rawTileOrNull(tile)
                ?.let { VectorTileCodec.decodeOrNull(it) }
                ?.let { VectorTileCodec.encode(convertLayers(zoom, it)) }
        } catch (t: Throwable) {
            logFailure(t)
            null
        }

    /** Log a conversion failure if the logger can; the caller has to get its null even when logging itself throws. */
    private fun logFailure(t: Throwable) {
        try {
            Log.w(TAG, "tile conversion failed", t)
        } catch (_: Throwable) {
            // Out of memory, most likely; nothing is left to report the failure through.
        }
    }
}

/** An output feature with the keys [Bucket] sorts the two ordered layers by. */
private class Keyed(
    val feature: OutputFeature,
    val rank: Int,
    val value: Double,
)

/** Rank ascending, then value descending; a plain comparator keeps the sort key unboxed. */
private val KeyedOrder =
    Comparator<Keyed> { a, b ->
        if (a.rank != b.rank) a.rank.compareTo(b.rank) else java.lang.Double.compare(b.value, a.value)
    }

/**
 * The features of one OpenMapTiles layer. A rule resolves its buckets once per source layer; the bucket only joins
 * the output (and takes its extent) when the first feature is added, so a layer no feature maps to stays absent.
 */
private class Bucket(
    val name: String,
) {
    var extent = 0
    var claimed = false
    val plain = ArrayList<OutputFeature>()
    val keyed = ArrayList<Keyed>()
}

/** Collects output features per OpenMapTiles layer; a layer takes the extent of the first source layer that adds to it. */
private class Sink {
    private val buckets = HashMap<String, Bucket>()
    private val joined = ArrayList<Bucket>()

    fun bucket(layer: String): Bucket = buckets.getOrPut(layer) { Bucket(layer) }

    fun add(
        bucket: Bucket,
        from: SourceLayer,
        feature: SourceFeature,
        properties: List<TileProperty>,
    ) {
        join(bucket, from)
        bucket.plain.add(outputFeature(feature, properties))
    }

    /** Add a feature to a layer that is sorted by [rank] ascending, then [value] descending, keeping source order on ties. */
    fun addKeyed(
        bucket: Bucket,
        from: SourceLayer,
        feature: SourceFeature,
        properties: List<TileProperty>,
        rank: Int,
        value: Double,
    ) {
        join(bucket, from)
        bucket.keyed.add(Keyed(outputFeature(feature, properties), rank, value))
    }

    fun layers(): List<OutputLayer> =
        joined.map { bucket ->
            // Sorting in place is safe: the sort is stable and nothing else reads the bucket afterwards.
            bucket.keyed.sortWith(KeyedOrder)
            val features = ArrayList<OutputFeature>(bucket.plain.size + bucket.keyed.size)
            features.addAll(bucket.plain)
            bucket.keyed.forEach { features.add(it.feature) }
            OutputLayer(bucket.name, bucket.extent, features)
        }

    private fun join(
        bucket: Bucket,
        from: SourceLayer,
    ) {
        if (!bucket.claimed) {
            bucket.claimed = true
            bucket.extent = from.extent
            joined.add(bucket)
        }
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

// Output values that do not depend on the feature are built once and shared: the encoder's lookups then hit the
// identity fast path, and a tile of tens of thousands of features allocates none of them.
private val OceanProperties = listOf(text("class", "ocean"))
private val GlacierProperties = listOf(text("class", "ice"), text("subclass", "glacier"))
private val DefaultWaterProperties = listOf(text("class", DEFAULT_WATER_CLASS))
private val LakeClass = text("class", "lake")
private val FerryClass = text("class", "ferry")
private val PierProperties = listOf(text("class", "pier"))
private val BridgeProperties = listOf(text("class", "bridge"))
private val BuildingProperties = listOf(integer("render_height", BUILDING_HEIGHT), integer("render_min_height", 0))
private val BridgeBrunnel = text("brunnel", "bridge")
private val TunnelBrunnel = text("brunnel", "tunnel")
private val RampProperty = integer("ramp", 1)
private val OnewayForward = integer("oneway", 1)
private val OnewayReverse = integer("oneway", -1)
private val MotorwayClass = text("class", "motorway")
private val JunctionSubclass = text("subclass", "junction")
private val MotorwayNameRank = roadRank("motorway")

// The tables below are keyed by the Shortbread kind, so one lookup yields everything the rule emits for it.
private val WaterProperties: Map<String, List<TileProperty>> = WaterClasses.mapValues {
    listOf(text("class", it.value))
}

private val LandcoverProperties: Map<String, List<TileProperty>> =
    LandcoverClasses.mapValues { (kind, landcover) -> listOf(text("class", landcover), text("subclass", kind)) }

private val LanduseProperties: Map<String, List<TileProperty>> = LanduseClasses.mapValues {
    listOf(text("class", it.value))
}

private val AerowayProperties: Map<String, List<TileProperty>> = AerowayKinds.associateWith {
    listOf(text("class", it))
}

/** What the converter derives from a road kind, computed once so a feature costs a single table lookup. */
private class RoadKind(
    val roadClass: String,
    kind: String,
) {
    val minZoom = RoadMinZoom[roadClass] ?: 0
    val classProperty = text("class", roadClass)
    val subclassProperty = text("subclass", kind)
    val keepsSubclass = roadClass in RoadClassesWithSubclass
    val isPath = roadClass == "path"
    val nameRank = roadRank(roadClass)
}

private val RoadKinds: Map<String, RoadKind> = RoadClasses.mapValues { (kind, roadClass) -> RoadKind(roadClass, kind) }

/** What the converter derives from a place kind; see [RoadKind]. */
private class PlaceKind(
    val place: PlaceClass,
) {
    val minZoom = PlaceMinZoom[place.placeClass] ?: 0
    val rankOffset = PlaceRankOffset[place.placeClass] ?: 0
    val classProperty = text("class", place.placeClass)
    val capitalProperty = place.capital?.let { integer("capital", it) }
    val order = placeRank(place.placeClass)
}

private val PlaceKinds: Map<String, PlaceKind> = PlaceClasses.mapValues { PlaceKind(it.value) }

private fun MutableList<TileProperty>.addNotNull(property: TileProperty?) {
    if (property != null) add(property)
}

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

private fun SourceLayer.brunnelPropertyOrNull(
    feature: SourceFeature,
    bridge: Int,
    tunnel: Int,
): TileProperty? =
    when {
        isSet(feature, bridge) -> BridgeBrunnel
        isSet(feature, tunnel) -> TunnelBrunnel
        else -> null
    }

private fun roadRank(roadClass: String): Int = RoadNameOrder.indexOf(roadClass).takeIf { it >= 0 } ?: RoadNameOrder.size

private fun placeRank(placeClass: String): Int = PlaceOrder.indexOf(placeClass).takeIf { it >= 0 } ?: PlaceOrder.size

/** The text before the first line break; a reference is rendered on one line. */
private fun firstLine(ref: String): String = ref.substringBefore('\n')

/**
 * The tile-wide state of one conversion: each rule below reads every source layer of its Shortbread name.
 *
 * A rule builds a feature's properties in an `ArrayList` sized up front and filled with `add`, and the name rules
 * append into that same list. The rules run once per feature, tens of thousands of times per tile, where
 * `listOfNotNull` would allocate a varargs array beside the list and a `+` chain a new list per step.
 */
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
            val water = sink.bucket("water")
            layer.eachFeature { sink.add(water, layer, it, OceanProperties) }
        }

    private fun waterPolygons() =
        eachLayer("water_polygons") { layer ->
            val kind = layer.keyIndex("kind")
            val landcover = sink.bucket("landcover")
            val water = sink.bucket("water")
            layer.eachFeature { feature ->
                val kindText = layer.textOrNull(feature, kind)
                if (kindText == "glacier") {
                    sink.add(landcover, layer, feature, GlacierProperties)
                } else {
                    sink.add(water, layer, feature, WaterProperties[kindText] ?: DefaultWaterProperties)
                }
            }
        }

    private fun waterPolygonLabels() =
        eachLayer("water_polygons_labels") { layer ->
            val name = NameKeys(layer)
            val waterName = sink.bucket("water_name")
            layer.eachFeature {
                val properties = ArrayList<TileProperty>(1 + MAX_NAME_PROPERTIES)
                properties.add(LakeClass)
                name.appendTo(properties, layer, it)
                sink.add(waterName, layer, it, properties)
            }
        }

    private fun waterLines(
        layerName: String,
        labelled: Boolean,
    ) = eachLayer(layerName) { layer ->
        val kind = layer.keyIndex("kind")
        val bridge = layer.keyIndex("bridge")
        val tunnel = layer.keyIndex("tunnel")
        val name = NameKeys(layer)
        val waterway = sink.bucket("waterway")
        layer.eachFeature { feature ->
            val properties = ArrayList<TileProperty>(2 + MAX_NAME_PROPERTIES)
            layer.textOrNull(feature, kind)?.let { properties.add(text("class", it)) }
            properties.addNotNull(layer.brunnelPropertyOrNull(feature, bridge, tunnel))
            if (labelled) name.appendTo(properties, layer, feature)
            sink.add(waterway, layer, feature, properties)
        }
    }

    private fun landLike(layerName: String) =
        eachLayer(layerName) { layer ->
            val kind = layer.keyIndex("kind")
            val landcover = sink.bucket("landcover")
            val landuse = sink.bucket("landuse")
            layer.eachFeature { feature ->
                val kindText = layer.textOrNull(feature, kind)
                val landcoverProperties = LandcoverProperties[kindText]
                val landuseProperties = LanduseProperties[kindText]
                when {
                    landcoverProperties != null -> sink.add(landcover, layer, feature, landcoverProperties)
                    landuseProperties != null -> sink.add(landuse, layer, feature, landuseProperties)
                }
            }
        }

    private fun boundaries() =
        eachLayer("boundaries") { layer ->
            val level = layer.keyIndex("admin_level")
            val maritime = layer.keyIndex("maritime")
            val disputed = layer.keyIndex("disputed")
            val boundary = sink.bucket("boundary")
            layer.eachFeature { feature ->
                sink.add(
                    boundary,
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
            val aeroway = sink.bucket("aeroway")
            val transportation = sink.bucket("transportation")
            layer.eachFeature { feature ->
                val kindText = layer.textOrNull(feature, kind)
                val aerowayProperties = AerowayProperties[kindText]
                val road = RoadKinds[kindText]
                when {
                    aerowayProperties != null -> {
                        sink.add(aeroway, layer, feature, aerowayProperties)
                    }

                    road != null && zoom >= road.minZoom -> {
                        val properties = ArrayList<TileProperty>(STREET_PROPERTIES)
                        properties.add(road.classProperty)
                        if (road.keepsSubclass) properties.add(road.subclassProperty)
                        properties.addNotNull(layer.brunnelPropertyOrNull(feature, bridge, tunnel))
                        if (layer.isSet(feature, link)) properties.add(RampProperty)
                        when {
                            layer.isSet(feature, oneway) -> properties.add(OnewayForward)
                            layer.isSet(feature, onewayReverse) -> properties.add(OnewayReverse)
                        }
                        layer
                            .textOrNull(
                                feature,
                                service,
                            )?.takeIf { it.isNotEmpty() }
                            ?.let { properties.add(text("service", it)) }
                        sink.add(transportation, layer, feature, properties)
                    }
                }
            }
        }

    private fun streetPolygons() =
        eachLayer("street_polygons") { layer ->
            val kind = layer.keyIndex("kind")
            val bridge = layer.keyIndex("bridge")
            val tunnel = layer.keyIndex("tunnel")
            val aeroway = sink.bucket("aeroway")
            val transportation = sink.bucket("transportation")
            layer.eachFeature { feature ->
                val kindText = layer.textOrNull(feature, kind)
                val aerowayProperties = AerowayProperties[kindText]
                val road = RoadKinds[kindText]
                when {
                    aerowayProperties != null -> {
                        sink.add(aeroway, layer, feature, aerowayProperties)
                    }

                    road != null -> {
                        val properties = ArrayList<TileProperty>(3)
                        properties.add(road.classProperty)
                        properties.add(road.subclassProperty)
                        properties.addNotNull(layer.brunnelPropertyOrNull(feature, bridge, tunnel))
                        sink.add(transportation, layer, feature, properties)
                    }
                }
            }
        }

    private fun piers(layerName: String) =
        eachLayer(layerName) { layer ->
            val kind = layer.keyIndex("kind")
            val transportation = sink.bucket("transportation")
            layer.eachFeature { feature ->
                if (layer.textOrNull(feature, kind) == "pier") sink.add(transportation, layer, feature, PierProperties)
            }
        }

    private fun bridges() =
        eachLayer("bridges") { layer ->
            val transportation = sink.bucket("transportation")
            layer.eachFeature { sink.add(transportation, layer, it, BridgeProperties) }
        }

    private fun ferries() =
        eachLayer("ferries") { layer ->
            val name = NameKeys(layer)
            val transportation = sink.bucket("transportation")
            layer.eachFeature {
                val properties = ArrayList<TileProperty>(1 + MAX_NAME_PROPERTIES)
                properties.add(FerryClass)
                name.appendTo(properties, layer, it)
                sink.add(transportation, layer, it, properties)
            }
        }

    private fun streetLabels() =
        eachLayer("street_labels") { layer ->
            val kind = layer.keyIndex("kind")
            val ref = layer.keyIndex("ref")
            val bridge = layer.keyIndex("bridge")
            val tunnel = layer.keyIndex("tunnel")
            val name = NameKeys(layer)
            val transportationName = sink.bucket("transportation_name")
            layer.eachFeature { feature ->
                val kindText = layer.textOrNull(feature, kind)?.removeSuffix("_link")
                val road = RoadKinds[kindText]
                if (road != null) {
                    val shortRef =
                        layer
                            .textOrNull(feature, ref)
                            ?.let { firstLine(it).substringBefore(';') }
                            ?.takeIf { it.isNotEmpty() }
                    val properties = ArrayList<TileProperty>(STREET_LABEL_PROPERTIES + MAX_NAME_PROPERTIES)
                    properties.add(road.classProperty)
                    if (road.isPath) properties.add(road.subclassProperty)
                    properties.addNotNull(layer.brunnelPropertyOrNull(feature, bridge, tunnel))
                    if (shortRef != null) {
                        properties.add(text("ref", shortRef))
                        properties.add(integer("ref_length", shortRef.codePointCount(0, shortRef.length).toLong()))
                    }
                    name.appendTo(properties, layer, feature)
                    sink.addKeyed(transportationName, layer, feature, properties, rank = road.nameRank, value = 0.0)
                }
            }
        }

    private fun streetLabelPoints() =
        eachLayer("street_labels_points") { layer ->
            val ref = layer.keyIndex("ref")
            val name = NameKeys(layer)
            val transportationName = sink.bucket("transportation_name")
            layer.eachFeature { feature ->
                val junctionRef =
                    layer
                        .textOrNull(feature, ref)
                        ?.let(::firstLine)
                        ?.takeIf { it.isNotEmpty() }
                val properties = ArrayList<TileProperty>(JUNCTION_PROPERTIES + MAX_NAME_PROPERTIES)
                properties.add(MotorwayClass)
                properties.add(JunctionSubclass)
                if (junctionRef != null) {
                    properties.add(text("ref", junctionRef))
                    properties.add(integer("ref_length", junctionRef.codePointCount(0, junctionRef.length).toLong()))
                }
                name.appendTo(properties, layer, feature)
                sink.addKeyed(transportationName, layer, feature, properties, rank = MotorwayNameRank, value = 0.0)
            }
        }

    private fun buildings() =
        eachLayer("buildings") { layer ->
            val building = sink.bucket("building")
            layer.eachFeature { sink.add(building, layer, it, BuildingProperties) }
        }

    private fun publicTransport() =
        eachLayer("public_transport") { layer ->
            val kind = layer.keyIndex("kind")
            val iata = layer.keyIndex("iata")
            val name = NameKeys(layer)
            val aerodromeLabel = sink.bucket("aerodrome_label")
            layer.eachFeature { feature ->
                if (layer.textOrNull(feature, kind) == "aerodrome") {
                    val code = layer.textOrNull(feature, iata)?.takeIf { it.isNotEmpty() }
                    val properties = ArrayList<TileProperty>(2 + MAX_NAME_PROPERTIES)
                    properties.add(text("class", if (code != null) "international" else "other"))
                    code?.let { properties.add(text("iata", it)) }
                    name.appendTo(properties, layer, feature)
                    sink.add(aerodromeLabel, layer, feature, properties)
                }
            }
        }

    private fun boundaryLabels() =
        eachLayer("boundary_labels") { layer ->
            val level = layer.keyIndex("admin_level")
            val area = layer.keyIndex("way_area")
            val name = NameKeys(layer)
            val place = sink.bucket("place")
            layer.eachFeature { feature ->
                val wayArea = layer.number(feature, area)
                when (layer.number(feature, level).toLong()) {
                    2L -> "country" to countryRank(wayArea)
                    4L -> (if (wayArea >= STATE_MIN_AREA) "state" else "province") to 1L
                    else -> null
                }?.let { (placeClass, rank) ->
                    val properties = ArrayList<TileProperty>(2 + MAX_NAME_PROPERTIES)
                    properties.add(text("class", placeClass))
                    properties.add(integer("rank", rank))
                    name.appendTo(properties, layer, feature)
                    sink.addKeyed(place, layer, feature, properties, rank = placeRank(placeClass), value = wayArea)
                }
            }
        }

    private fun placeLabels() =
        eachLayer("place_labels") { layer ->
            val kind = layer.keyIndex("kind")
            val population = layer.keyIndex("population")
            val name = NameKeys(layer)
            val placeBucket = sink.bucket("place")
            layer.eachFeature { feature ->
                val place = PlaceKinds[layer.textOrNull(feature, kind)]
                if (place != null && zoom >= place.minZoom) {
                    val people = layer.number(feature, population)
                    val rank = 1 + CityRankPopulation.count { it > people } + place.rankOffset
                    val properties = ArrayList<TileProperty>(3 + MAX_NAME_PROPERTIES)
                    properties.add(place.classProperty)
                    properties.addNotNull(place.capitalProperty)
                    properties.add(integer("rank", rank.toLong()))
                    name.appendTo(properties, layer, feature)
                    sink.addKeyed(placeBucket, layer, feature, properties, rank = place.order, value = people)
                }
            }
        }

    private companion object {
        const val STREET_PROPERTIES = 6
        const val STREET_LABEL_PROPERTIES = 5
        const val JUNCTION_PROPERTIES = 4
    }
}

/** The key indices of the three name attributes of one source layer, resolved once per layer. */
private class NameKeys(
    layer: SourceLayer,
) {
    private val name = layer.keyIndex("name")
    private val nameEn = layer.keyIndex("name_en")
    private val nameDe = layer.keyIndex("name_de")

    fun appendTo(
        destination: MutableList<TileProperty>,
        layer: SourceLayer,
        feature: SourceFeature,
    ) = appendNameProperties(
        destination,
        layer.textOrNull(feature, name),
        layer.textOrNull(feature, nameEn),
        layer.textOrNull(feature, nameDe),
    )
}

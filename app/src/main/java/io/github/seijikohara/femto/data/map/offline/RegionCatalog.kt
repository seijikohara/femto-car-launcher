@file:OptIn(ExperimentalSerializationApi::class)

package io.github.seijikohara.femto.data.map.offline

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/** The only host a region file is ever requested from. */
internal const val REGION_HOST_PREFIX = "https://download.geofabrik.de/"

private const val EXTRACT_SUFFIX = "-latest.osm.pbf"
private const val SHORTBREAD_SUFFIX = "-shortbread-1.0.mbtiles"

// A ring is closed, so fewer than four positions cannot enclose an area.
private const val MIN_RING_POSITIONS = 4

private val IdPattern = Regex("[a-z0-9]+([-/][a-z0-9]+)*")
private val LineBreakTag = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val AnyTag = Regex("<[^>]*>")
private val Whitespace = Regex("\\s+")

private val IndexJson = Json { ignoreUnknownKeys = true }

internal data class CatalogRegion(
    val id: String,
    val parentId: String?,
    val name: String,
    val fileUrl: String,
    /** Null when the snapshot lists no Shortbread file for this region. */
    val approxBytes: Long?,
    val polygon: RegionPolygon,
)

internal class RegionCatalog(
    regions: List<CatalogRegion>,
) {
    private val byId = regions.associateBy { it.id }

    // A region is offered when it has a file or a descendant has one; marking the ancestors of every file region finds both.
    private val offeredIds: Set<String> =
        regions
            .filter { it.approxBytes != null }
            .flatMap { region -> lineageOf(region).map { it.id }.toList() }
            .toSet()

    private val offeredChildren: Map<String?, List<CatalogRegion>> =
        regions
            .filter { it.id in offeredIds }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .groupBy { it.parentId }

    private val withFile = regions.filter { it.approxBytes != null }

    fun regionOrNull(id: String): CatalogRegion? = byId[id]

    /** [region] and the regions above it, nearest first; bounded by the catalogue's size, so no walk runs forever. */
    fun lineageOf(region: CatalogRegion): Sequence<CatalogRegion> =
        generateSequence(region) { byId[it.parentId] }.take(byId.size)

    fun childrenOf(parentId: String?): List<CatalogRegion> = offeredChildren[parentId].orEmpty()

    fun hasOfferedChildren(id: String): Boolean = childrenOf(id).isNotEmpty()

    fun smallestContainingOrNull(
        longitude: Double,
        latitude: Double,
    ): CatalogRegion? =
        withFile
            .filter { it.polygon.contains(longitude, latitude) }
            .minWithOrNull(compareBy<CatalogRegion> { it.approxBytes }.thenBy { it.id })
}

@Serializable
private class SizeSnapshot(
    val regions: Map<String, JsonElement>? = null,
)

/** The snapshot's id → bytes map, or null when the stream is not a snapshot. */
internal fun shortbreadSizesOrNull(input: InputStream): Map<String, Long>? =
    decodeOrNull<SizeSnapshot>(input)
        ?.regions
        ?.mapNotNull { (id, size) -> bytesOrNull(size)?.let { id to it } }
        ?.toMap()

private fun bytesOrNull(element: JsonElement): Long? =
    (element as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull?.takeIf { it >= 0 }

@Serializable
private class IndexDocument(
    val features: List<IndexFeature>? = null,
)

@Serializable
private class IndexFeature(
    val properties: IndexProperties? = null,
    @Serializable(with = PolygonSerializer::class) val geometry: RegionPolygon = RegionPolygon(emptyList()),
)

@Serializable
private class IndexProperties(
    val id: String? = null,
    val parent: String? = null,
    val name: String? = null,
    val urls: IndexUrls? = null,
)

@Serializable
private class IndexUrls(
    val pbf: String? = null,
)

/** Pack each geometry into rings as it is read, so the decoded JSON tree of a 3.8 MB index is never held whole. */
private object PolygonSerializer : KSerializer<RegionPolygon> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RegionPolygon")

    override fun deserialize(decoder: Decoder): RegionPolygon =
        ((decoder as JsonDecoder).decodeJsonElement() as? JsonObject)
            ?.takeIf { it["type"]?.jsonPrimitive?.content == "MultiPolygon" }
            ?.get("coordinates")
            ?.let(::ringsOf)
            ?.let(::RegionPolygon)
            ?: RegionPolygon(emptyList())

    override fun serialize(
        encoder: Encoder,
        value: RegionPolygon,
    ) = throw SerializationException("Region polygons are read-only")
}

private fun ringsOf(coordinates: JsonElement): List<DoubleArray> =
    (coordinates as? JsonArray)
        .orEmpty()
        .flatMap { polygon -> (polygon as? JsonArray).orEmpty() }
        .mapNotNull { ring -> (ring as? JsonArray)?.let(::packedRingOrNull) }

private fun packedRingOrNull(positions: JsonArray): DoubleArray? =
    positions
        .takeIf { it.size >= MIN_RING_POSITIONS }
        ?.map { position -> (position as? JsonArray)?.takeIf { it.size >= 2 } }
        ?.takeIf { pairs -> pairs.all { it != null } }
        ?.flatMap { pair -> listOf(pair!![0], pair[1]).map { (it as? JsonPrimitive)?.doubleOrNull } }
        ?.takeIf { values -> values.all { it != null } }
        ?.map { it!! }
        ?.toDoubleArray()

private class Candidate(
    val id: String,
    val statedParent: String?,
    val name: String,
    val fileUrl: String,
    val polygon: RegionPolygon,
)

/** The catalogue, or null when the stream is not a region index. Never throws on bad content. */
internal fun regionCatalogOrNull(
    index: InputStream,
    sizes: Map<String, Long>,
): RegionCatalog? =
    decodeOrNull<IndexDocument>(index)
        ?.features
        ?.let { features -> catalogOf(features.mapNotNull(::candidateOrNull).distinctBy { it.id }, sizes) }

private fun candidateOrNull(feature: IndexFeature): Candidate? {
    val properties = feature.properties
    val id = properties?.id?.takeIf { IdPattern.matches(it) }
    val extract = properties?.urls?.pbf?.takeIf { it.startsWith(REGION_HOST_PREFIX) && it.endsWith(EXTRACT_SUFFIX) }
    return if (id == null || extract == null) {
        null
    } else {
        Candidate(
            id = id,
            statedParent = properties.parent,
            name = displayName(properties.name, id),
            fileUrl = extract.removeSuffix(EXTRACT_SUFFIX) + SHORTBREAD_SUFFIX,
            polygon = feature.geometry,
        )
    }
}

private fun displayName(
    raw: String?,
    id: String,
): String =
    raw
        .orEmpty()
        .replace(LineBreakTag, " ")
        .replace(AnyTag, "")
        .replace(Whitespace, " ")
        .trim()
        .takeUnless { it.isBlank() || it == id }
        ?: id
            .substringAfterLast('/')
            .split('-')
            .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.ROOT) } }

private fun catalogOf(
    candidates: List<Candidate>,
    sizes: Map<String, Long>,
): RegionCatalog {
    val ids = candidates.map { it.id }.toSet()
    // The id's own path wins over the stated parent: the index says `north-america` for `us/california`.
    val parents =
        candidates.associate { candidate ->
            val parent =
                candidate.id
                    .takeIf { '/' in it }
                    ?.substringBeforeLast('/')
                    ?.takeIf { it in ids }
                    ?: candidate.statedParent?.takeIf { it in ids }
            candidate.id to parent
        }
    return candidates
        .filter { candidate -> generateSequence(candidate.id) { parents[it] }.take(ids.size + 1).count() <= ids.size }
        .map { candidate ->
            CatalogRegion(
                id = candidate.id,
                parentId = parents[candidate.id],
                name = candidate.name,
                fileUrl = candidate.fileUrl,
                approxBytes = sizes[candidate.id],
                polygon = candidate.polygon,
            )
        }.let(::RegionCatalog)
}

private inline fun <reified T> decodeOrNull(input: InputStream): T? =
    try {
        IndexJson.decodeFromStream<T>(input)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IOException) {
        null
    }

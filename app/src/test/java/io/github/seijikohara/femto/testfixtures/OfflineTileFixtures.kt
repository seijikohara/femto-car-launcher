package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.map.offline.SourceFeature
import io.github.seijikohara.femto.data.map.offline.SourceLayer
import io.github.seijikohara.femto.data.map.offline.TileValue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** The seven tiles under `app/src/test/resources/offlinemap/`; `app/src/test/resources/README.md` documents them. */
internal val OfflineTileFixtureNames: List<String> =
    listOf(
        "shikoku-13-7112-3265",
        "cyprus-12-2423-1627",
        "cyprus-14-9721-6496",
        "shikoku-14-14266-6569",
        "shikoku-10-886-409",
        "cyprus-9-303-202",
        "shikoku-4-14-6",
    )

private object OfflineTileResources

internal fun offlineTileResource(fileName: String): ByteArray =
    requireNotNull(OfflineTileResources::class.java.getResourceAsStream("/offlinemap/$fileName")) {
        "Missing test resource offlinemap/$fileName"
    }.use { it.readBytes() }

internal fun gunzipped(bytes: ByteArray): ByteArray =
    GZIPInputStream(ByteArrayInputStream(bytes)).use {
        it.readBytes()
    }

internal fun gzipped(bytes: ByteArray): ByteArray =
    ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

/** A feature with its tags resolved to a property map and its geometry wrapped for structural equality. */
internal data class ResolvedFeature(
    val id: Long?,
    val type: Int,
    val geometry: List<Byte>,
    val properties: Map<String, TileValue>,
)

internal fun List<SourceLayer>.resolved(): Map<String, List<ResolvedFeature>> =
    groupBy { it.name }.mapValues { (_, layers) ->
        layers.flatMap { layer ->
            layer.features.map { feature ->
                ResolvedFeature(
                    id = feature.id,
                    type = feature.type,
                    geometry = feature.geometry.toList(),
                    properties =
                        feature.tags
                            .toList()
                            .chunked(2)
                            .mapNotNull { (key, value) -> layer.values[value]?.let { layer.keys[key] to it } }
                            .toMap(),
                )
            }
        }
    }

/** Build a layer of point features (ids 1.., geometry = one byte holding the feature's index) from property maps. */
internal fun sourceLayer(
    name: String,
    vararg features: Map<String, TileValue>,
    extent: Int = 4096,
): SourceLayer {
    val keys = features.flatMap { it.keys }.distinct()
    val values = features.flatMap { it.values }.distinct()
    return SourceLayer(
        name = name,
        extent = extent,
        keys = keys,
        values = values,
        features =
            features.mapIndexed { index, properties ->
                SourceFeature(
                    id = index + 1L,
                    type = 1,
                    tags = properties.entries
                        .flatMap {
                            listOf(
                                keys.indexOf(it.key),
                                values.indexOf(it.value),
                            )
                        }.toIntArray(),
                    geometry = byteArrayOf(index.toByte()),
                )
            },
    )
}

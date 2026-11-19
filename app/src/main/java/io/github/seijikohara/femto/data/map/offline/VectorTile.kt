package io.github.seijikohara.femto.data.map.offline

/** A property value of a vector-tile feature, as the Mapbox Vector Tile value message encodes it. */
internal sealed interface TileValue {
    data class Text(
        val value: String,
    ) : TileValue

    data class Integer(
        val value: Long,
    ) : TileValue

    data class Real(
        val value: Double,
    ) : TileValue

    data class Flag(
        val value: Boolean,
    ) : TileValue
}

/**
 * A decoded feature. [tags] holds alternating key and value indices into the owning [SourceLayer]; reading
 * features by index keeps a per-feature property map off the hot path. [geometry] stays the raw command stream
 * because the converter never interprets it.
 */
internal class SourceFeature(
    val id: Long?,
    val type: Int,
    val tags: IntArray,
    val geometry: ByteArray,
)

internal class SourceLayer(
    val name: String,
    val extent: Int,
    val keys: List<String>,
    val values: List<TileValue?>,
    val features: List<SourceFeature>,
) {
    /** Return the index of [key] in [keys], or -1 when the layer has no such key. */
    fun keyIndex(key: String): Int = keys.indexOf(key)

    /** Return the value [feature] carries under [keyIndex], or null for a negative index or an absent key. */
    fun valueOrNull(
        feature: SourceFeature,
        keyIndex: Int,
    ): TileValue? {
        if (keyIndex < 0) return null
        val tags = feature.tags
        // A plain loop: this runs several times per feature, and a step progression plus a lambda per call add up on ART.
        var i = 0
        while (i < tags.size) {
            if (tags[i] == keyIndex) return values[tags[i + 1]]
            i += 2
        }
        return null
    }
}

internal data class TileProperty(
    val key: String,
    val value: TileValue,
)

internal class OutputFeature(
    val id: Long?,
    val type: Int,
    val geometry: ByteArray,
    val properties: List<TileProperty>,
)

internal class OutputLayer(
    val name: String,
    val extent: Int,
    val features: List<OutputFeature>,
)

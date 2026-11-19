package io.github.seijikohara.femto.data.map.offline

import java.nio.charset.StandardCharsets

/**
 * Reader and writer for the Mapbox Vector Tile 2.1 wire format (protobuf), written by hand to avoid a
 * dependency. Geometry is never interpreted: the reader keeps its bytes and the writer emits them verbatim.
 */
internal object VectorTileCodec {
    private const val DEFAULT_EXTENT = 4096
    private const val WRITTEN_VERSION = 2L

    private const val TILE_LAYER = 3

    private const val LAYER_NAME = 1
    private const val LAYER_FEATURE = 2
    private const val LAYER_KEY = 3
    private const val LAYER_VALUE = 4
    private const val LAYER_EXTENT = 5
    private const val LAYER_VERSION = 15

    private const val FEATURE_ID = 1
    private const val FEATURE_TAGS = 2
    private const val FEATURE_TYPE = 3
    private const val FEATURE_GEOMETRY = 4

    private const val VALUE_STRING = 1
    private const val VALUE_FLOAT = 2
    private const val VALUE_DOUBLE = 3
    private const val VALUE_INT64 = 4
    private const val VALUE_UINT64 = 5
    private const val VALUE_SINT64 = 6
    private const val VALUE_BOOL = 7

    private val NO_TAGS = IntArray(0)
    private val NO_GEOMETRY = ByteArray(0)
    private const val INITIAL_TAG_CAPACITY = 32

    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LENGTH_DELIMITED = 2
    private const val WIRE_FIXED32 = 5

    /** Return the layers of [bytes], or null when the tile is malformed; never throws. */
    fun decodeOrNull(bytes: ByteArray): List<SourceLayer>? =
        try {
            decodeTile(Reader(bytes, 0, bytes.size))
        } catch (_: MalformedTile) {
            null
        }

    /** Return the tile bytes for [layers]; a layer without features is omitted. */
    fun encode(layers: List<OutputLayer>): ByteArray =
        ByteWriter()
            .also { tile ->
                layers
                    .filter { it.features.isNotEmpty() }
                    .forEach { tile.writeBytesField(TILE_LAYER, encodeLayer(it)) }
            }.toByteArray()

    /** Signals malformed input internally; stackless because it is control flow, not a diagnostic. */
    private object MalformedTile : Exception(null, null, false, false)

    private fun decodeTile(reader: Reader): List<SourceLayer> =
        buildList {
            while (reader.hasMore()) {
                val tag = reader.varint()
                val field = (tag ushr 3).toInt()
                val wire = (tag and 7).toInt()
                if (field == TILE_LAYER && wire == WIRE_LENGTH_DELIMITED) {
                    add(decodeLayer(reader.subReader()))
                } else {
                    reader.skip(wire)
                }
            }
        }

    private fun decodeLayer(reader: Reader): SourceLayer {
        var name = ""
        var extent = DEFAULT_EXTENT
        val keys = ArrayList<String>()
        val values = ArrayList<TileValue?>()
        val features = ArrayList<SourceFeature>()
        while (reader.hasMore()) {
            val tag = reader.varint()
            val field = (tag ushr 3).toInt()
            val wire = (tag and 7).toInt()
            when {
                field == LAYER_NAME && wire == WIRE_LENGTH_DELIMITED -> name = reader.string()
                field == LAYER_FEATURE && wire == WIRE_LENGTH_DELIMITED -> features += decodeFeature(reader.subReader())
                field == LAYER_KEY && wire == WIRE_LENGTH_DELIMITED -> keys += reader.string()
                field == LAYER_VALUE && wire == WIRE_LENGTH_DELIMITED -> values += decodeValue(reader.subReader())
                field == LAYER_EXTENT && wire == WIRE_VARINT -> extent = reader.varint().toInt()
                else -> reader.skip(wire)
            }
        }
        // Tags are checked once the tables are complete: the format allows features before keys and values.
        features.forEach { feature ->
            val tags = feature.tags
            // A plain loop: a step progression would allocate once per feature.
            var i = 0
            while (i < tags.size) {
                if (tags[i] >= keys.size || tags[i + 1] >= values.size) throw MalformedTile
                i += 2
            }
        }
        return SourceLayer(name, extent, keys, values, features)
    }

    private fun decodeFeature(reader: Reader): SourceFeature {
        var id: Long? = null
        var type = 0
        var tags = NO_TAGS
        var tagCount = 0
        var geometry = NO_GEOMETRY
        while (reader.hasMore()) {
            val tag = reader.varint()
            val field = (tag ushr 3).toInt()
            val wire = (tag and 7).toInt()
            when {
                field == FEATURE_ID && wire == WIRE_VARINT -> {
                    id = reader.varint()
                }

                field == FEATURE_TAGS && wire == WIRE_LENGTH_DELIMITED -> {
                    val packed = reader.subReader()
                    // Each varint takes at least one byte, so the byte length bounds the count.
                    tags = withRoom(tags, tagCount, packed.remaining())
                    while (packed.hasMore()) tags[tagCount++] = packed.varintAsIndex()
                }

                field == FEATURE_TAGS && wire == WIRE_VARINT -> {
                    tags = withRoom(tags, tagCount, 1)
                    tags[tagCount++] = reader.varintAsIndex()
                }

                field == FEATURE_TYPE && wire == WIRE_VARINT -> {
                    type = reader.varint().toInt()
                }

                field == FEATURE_GEOMETRY && wire == WIRE_LENGTH_DELIMITED -> {
                    geometry = reader.bytes()
                }

                else -> {
                    reader.skip(wire)
                }
            }
        }
        // A trailing odd tag has no value to pair with.
        val pairedCount = tagCount and 1.inv()
        return SourceFeature(id, type, if (tags.size == pairedCount) tags else tags.copyOf(pairedCount), geometry)
    }

    /**
     * Return [tags], or a copy with room for [extra] more entries after the first [count]. The copy at least doubles,
     * so a feature of many unpacked tag fields costs linear time, not a copy per field; the trim after the feature
     * drops the slack.
     */
    private fun withRoom(
        tags: IntArray,
        count: Int,
        extra: Int,
    ): IntArray = if (tags.size - count >= extra) tags else tags.copyOf(maxOf(count + extra, tags.size * 2))

    private fun decodeValue(reader: Reader): TileValue? {
        var value: TileValue? = null
        while (reader.hasMore()) {
            val tag = reader.varint()
            val field = (tag ushr 3).toInt()
            val wire = (tag and 7).toInt()
            when {
                field == VALUE_STRING && wire == WIRE_LENGTH_DELIMITED -> value = TileValue.Text(reader.string())
                field == VALUE_FLOAT && wire == WIRE_FIXED32 -> value = TileValue.Real(reader.float().toDouble())
                field == VALUE_DOUBLE && wire == WIRE_FIXED64 -> value = TileValue.Real(reader.double())
                field == VALUE_INT64 && wire == WIRE_VARINT -> value = TileValue.Integer(reader.varint())
                field == VALUE_UINT64 && wire == WIRE_VARINT -> value = TileValue.Integer(reader.varint())
                field == VALUE_SINT64 && wire == WIRE_VARINT -> value = TileValue.Integer(unzigzag(reader.varint()))
                field == VALUE_BOOL && wire == WIRE_VARINT -> value = TileValue.Flag(reader.varint() != 0L)
                else -> reader.skip(wire)
            }
        }
        return value
    }

    private fun unzigzag(n: Long): Long = (n ushr 1) xor -(n and 1)

    private fun zigzag(n: Long): Long = (n shl 1) xor (n shr 63)

    private fun encodeLayer(layer: OutputLayer): ByteArray {
        val keyIndex = LinkedHashMap<String, Int>()
        val valueIndex = LinkedHashMap<TileValue, Int>()
        // One scratch buffer serves every feature and value message, which keeps allocation per feature flat.
        val scratch = ByteWriter()
        val body = ByteWriter()
        var tags = IntArray(INITIAL_TAG_CAPACITY)
        body.writeVarintField(LAYER_VERSION, WRITTEN_VERSION)
        body.writeBytesField(LAYER_NAME, layer.name.toByteArray(StandardCharsets.UTF_8))
        layer.features.forEach { feature ->
            scratch.reset()
            val tagCount = feature.properties.size * 2
            if (tags.size < tagCount) tags = IntArray(tagCount)
            feature.properties.forEachIndexed { i, property ->
                tags[2 * i] = keyIndex.getOrPut(property.key) { keyIndex.size }
                tags[2 * i + 1] = valueIndex.getOrPut(property.value) { valueIndex.size }
            }
            feature.id?.let { scratch.writeVarintField(FEATURE_ID, it) }
            if (tagCount > 0) scratch.writePackedField(FEATURE_TAGS, tags, tagCount)
            scratch.writeVarintField(FEATURE_TYPE, feature.type.toLong())
            scratch.writeBytesField(FEATURE_GEOMETRY, feature.geometry)
            body.writeBytesField(LAYER_FEATURE, scratch)
        }
        keyIndex.keys.forEach { body.writeBytesField(LAYER_KEY, it.toByteArray(StandardCharsets.UTF_8)) }
        valueIndex.keys.forEach { value ->
            scratch.reset()
            writeValue(scratch, value)
            body.writeBytesField(LAYER_VALUE, scratch)
        }
        body.writeVarintField(LAYER_EXTENT, layer.extent.toLong())
        return body.toByteArray()
    }

    private fun writeValue(
        out: ByteWriter,
        value: TileValue,
    ) {
        when (value) {
            is TileValue.Text -> out.writeBytesField(VALUE_STRING, value.value.toByteArray(StandardCharsets.UTF_8))
            is TileValue.Integer if value.value >= 0 -> out.writeVarintField(VALUE_UINT64, value.value)
            is TileValue.Integer -> out.writeVarintField(VALUE_SINT64, zigzag(value.value))
            is TileValue.Real -> out.writeDoubleField(VALUE_DOUBLE, value.value)
            is TileValue.Flag -> out.writeVarintField(VALUE_BOOL, if (value.value) 1L else 0L)
        }
    }

    /**
     * Cursor over `[pos, end)` of [buf]; every read checks bounds before it consumes or allocates.
     *
     * The decoder walks it, and [ByteWriter] fills its buffer, with `while` and `for` loops over `var` state instead
     * of collection chains: a tile holds tens of thousands of fields, and an iterator or a list per field would
     * allocate on the hottest path of a conversion.
     */
    private class Reader(
        private val buf: ByteArray,
        private var pos: Int,
        private val end: Int,
    ) {
        fun hasMore(): Boolean = pos < end

        fun varint(): Long {
            var result = 0L
            for (i in 0 until MAX_VARINT_BYTES) {
                if (pos >= end) throw MalformedTile
                val b = buf[pos++].toInt()
                result = result or ((b and 0x7f).toLong() shl (7 * i))
                if (b >= 0) return result
            }
            throw MalformedTile
        }

        fun varintAsIndex(): Int =
            varint().let { if (it < 0 || it > Int.MAX_VALUE) throw MalformedTile else it.toInt() }

        private fun length(): Int = varint().let { if (it < 0 || it > end - pos) throw MalformedTile else it.toInt() }

        fun subReader(): Reader {
            val len = length()
            return Reader(buf, pos, pos + len).also { pos += len }
        }

        fun string(): String {
            val len = length()
            return String(buf, pos, len, StandardCharsets.UTF_8).also { pos += len }
        }

        fun bytes(): ByteArray {
            val len = length()
            return buf.copyOfRange(pos, pos + len).also { pos += len }
        }

        fun remaining(): Int = end - pos

        fun float(): Float = Float.fromBits(fixed(4).toInt())

        fun double(): Double = Double.fromBits(fixed(8))

        private fun fixed(size: Int): Long {
            if (size > end - pos) throw MalformedTile
            var result = 0L
            for (i in 0 until size) result = result or ((buf[pos + i].toLong() and 0xff) shl (8 * i))
            pos += size
            return result
        }

        fun skip(wire: Int) {
            when (wire) {
                WIRE_VARINT -> varint()

                WIRE_FIXED64 -> fixed(8)

                // Read the length first: `pos += length()` would capture the pre-varint position.
                WIRE_LENGTH_DELIMITED -> length().let { pos += it }

                WIRE_FIXED32 -> fixed(4)

                else -> throw MalformedTile
            }
        }

        private companion object {
            const val MAX_VARINT_BYTES = 10
        }
    }

    /** Growable byte buffer with the protobuf primitives the tile writer needs. */
    private class ByteWriter {
        private var buf = ByteArray(256)
        private var size = 0

        fun reset() {
            size = 0
        }

        fun toByteArray(): ByteArray = buf.copyOf(size)

        private fun ensure(extra: Int) {
            if (size + extra > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + extra))
        }

        private fun writeByte(b: Int) {
            ensure(1)
            buf[size++] = b.toByte()
        }

        private fun writeVarint(value: Long) {
            var v = value
            while (v and 0x7fL.inv() != 0L) {
                writeByte(((v and 0x7f) or 0x80).toInt())
                v = v ushr 7
            }
            writeByte(v.toInt())
        }

        private fun writeTag(
            field: Int,
            wire: Int,
        ) = writeVarint(((field shl 3) or wire).toLong())

        private fun writeRaw(
            src: ByteArray,
            length: Int,
        ) {
            ensure(length)
            System.arraycopy(src, 0, buf, size, length)
            size += length
        }

        fun writeVarintField(
            field: Int,
            value: Long,
        ) {
            writeTag(field, WIRE_VARINT)
            writeVarint(value)
        }

        fun writeBytesField(
            field: Int,
            value: ByteArray,
        ) {
            writeTag(field, WIRE_LENGTH_DELIMITED)
            writeVarint(value.size.toLong())
            writeRaw(value, value.size)
        }

        fun writeBytesField(
            field: Int,
            value: ByteWriter,
        ) {
            writeTag(field, WIRE_LENGTH_DELIMITED)
            writeVarint(value.size.toLong())
            writeRaw(value.buf, value.size)
        }

        fun writePackedField(
            field: Int,
            values: IntArray,
            count: Int,
        ) {
            writeTag(field, WIRE_LENGTH_DELIMITED)
            // The length prefix needs the payload size first, so the sizes are summed in a plain loop before writing.
            var payload = 0
            for (i in 0 until count) payload += varintSize(values[i].toLong())
            writeVarint(payload.toLong())
            for (i in 0 until count) writeVarint(values[i].toLong())
        }

        fun writeDoubleField(
            field: Int,
            value: Double,
        ) {
            writeTag(field, WIRE_FIXED64)
            val bits = value.toRawBits()
            for (i in 0 until 8) writeByte((bits ushr (8 * i)).toInt() and 0xff)
        }

        private fun varintSize(value: Long): Int = (70 - java.lang.Long.numberOfLeadingZeros(value or 1)) / 7
    }
}

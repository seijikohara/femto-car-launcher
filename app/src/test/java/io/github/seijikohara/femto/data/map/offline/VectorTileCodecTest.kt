package io.github.seijikohara.femto.data.map.offline

import io.github.seijikohara.femto.testfixtures.OfflineTileFixtureNames
import io.github.seijikohara.femto.testfixtures.ResolvedFeature
import io.github.seijikohara.femto.testfixtures.gunzipped
import io.github.seijikohara.femto.testfixtures.offlineTileResource
import io.github.seijikohara.femto.testfixtures.resolved
import io.github.seijikohara.femto.testfixtures.sourceLayer
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Hand-built protobuf bytes, so the tests pin the wire format independently of the codec's own writer. */
private object Pb {
    fun varint(value: Long): ByteArray =
        ByteArrayOutputStream()
            .also { out ->
                var v = value
                while (v and 0x7fL.inv() != 0L) {
                    out.write(((v and 0x7f) or 0x80).toInt())
                    v = v ushr 7
                }
                out.write(v.toInt())
            }.toByteArray()

    fun tag(
        field: Int,
        wire: Int,
    ): ByteArray = varint(((field shl 3) or wire).toLong())

    fun varintField(
        field: Int,
        value: Long,
    ): ByteArray = tag(field, 0) + varint(value)

    fun bytesField(
        field: Int,
        value: ByteArray,
    ): ByteArray = tag(field, 2) + varint(value.size.toLong()) + value

    fun stringField(
        field: Int,
        value: String,
    ): ByteArray = bytesField(field, value.toByteArray())

    fun fixed32Field(
        field: Int,
        bits: Int,
    ): ByteArray = tag(field, 5) + ByteArray(4) { (bits ushr (8 * it)).toByte() }

    fun fixed64Field(
        field: Int,
        bits: Long,
    ): ByteArray = tag(field, 1) + ByteArray(8) { (bits ushr (8 * it)).toByte() }

    fun tile(vararg layers: ByteArray): ByteArray =
        layers.fold(ByteArray(0)) { acc, layer -> acc + bytesField(3, layer) }

    fun layer(vararg parts: ByteArray): ByteArray = parts.fold(ByteArray(0)) { acc, part -> acc + part }

    fun feature(vararg parts: ByteArray): ByteArray =
        bytesField(2, parts.fold(ByteArray(0)) { acc, part -> acc + part })

    fun value(vararg parts: ByteArray): ByteArray = bytesField(4, parts.fold(ByteArray(0)) { acc, part -> acc + part })

    fun packed(vararg indices: Long): ByteArray =
        bytesField(2, indices.fold(ByteArray(0)) { acc, i -> acc + varint(i) })
}

class VectorTileCodecTest {
    @Test
    fun `a round trip keeps feature order, ids, types, geometry bytes and property values`() {
        val layers =
            listOf(
                OutputLayer(
                    "poi",
                    4096,
                    listOf(
                        OutputFeature(
                            1,
                            1,
                            byteArrayOf(9, 2, 4),
                            listOf(
                                TileProperty("name", TileValue.Text("東京")),
                                TileProperty("rank", TileValue.Integer(7)),
                                TileProperty("delta", TileValue.Integer(-3)),
                            ),
                        ),
                        OutputFeature(null, 2, byteArrayOf(1, 2), emptyList()),
                    ),
                ),
                OutputLayer(
                    "water",
                    512,
                    listOf(
                        OutputFeature(
                            5,
                            3,
                            byteArrayOf(7),
                            listOf(
                                TileProperty("ratio", TileValue.Real(1.5)),
                                TileProperty("brunnel", TileValue.Flag(true)),
                                TileProperty("name", TileValue.Text("東京")),
                            ),
                        ),
                    ),
                ),
            )
        val expected =
            mapOf(
                "poi" to
                    listOf(
                        ResolvedFeature(
                            1,
                            1,
                            listOf<Byte>(9, 2, 4),
                            mapOf(
                                "name" to TileValue.Text("東京"),
                                "rank" to TileValue.Integer(7),
                                "delta" to TileValue.Integer(-3),
                            ),
                        ),
                        ResolvedFeature(null, 2, listOf<Byte>(1, 2), emptyMap()),
                    ),
                "water" to
                    listOf(
                        ResolvedFeature(
                            5,
                            3,
                            listOf<Byte>(7),
                            mapOf(
                                "ratio" to TileValue.Real(1.5),
                                "brunnel" to TileValue.Flag(true),
                                "name" to TileValue.Text("東京"),
                            ),
                        ),
                    ),
            )

        val decoded = assertNotNull(VectorTileCodec.decodeOrNull(VectorTileCodec.encode(layers)))

        assertEquals(expected, decoded.resolved())
        assertEquals(listOf(4096, 512), decoded.map { it.extent })
    }

    @Test
    fun `every value kind of the format decodes to its tile value`() {
        val tenByteMinusThree = Pb.varintField(4, -3L)
        val tile =
            Pb.tile(
                Pb.layer(
                    Pb.stringField(1, "l"),
                    Pb.value(Pb.fixed32Field(2, 0.5f.toRawBits())),
                    Pb.value(Pb.fixed64Field(3, 2.25.toRawBits())),
                    Pb.value(tenByteMinusThree),
                    Pb.value(Pb.varintField(5, 7)),
                    Pb.value(Pb.varintField(6, 7)),
                    Pb.value(Pb.varintField(7, 1)),
                    Pb.value(Pb.stringField(1, "a")),
                ),
            )

        val layer = assertNotNull(VectorTileCodec.decodeOrNull(tile)).single()

        assertEquals(10, tenByteMinusThree.size - 1)
        assertEquals(
            listOf(
                TileValue.Real(0.5),
                TileValue.Real(2.25),
                TileValue.Integer(-3),
                TileValue.Integer(7),
                TileValue.Integer(-4),
                TileValue.Flag(true),
                TileValue.Text("a"),
            ),
            layer.values,
        )
    }

    @Test
    fun `unknown fields are skipped and a trailing odd tag is ignored`() {
        val tile =
            Pb.tile(
                Pb.layer(
                    Pb.varintField(99, 5),
                    Pb.stringField(1, "l"),
                    Pb.fixed64Field(98, 1L),
                    Pb.fixed32Field(97, 1),
                    Pb.stringField(3, "k"),
                    Pb.value(Pb.stringField(1, "v"), Pb.varintField(50, 1)),
                    Pb.feature(Pb.varintField(1, 3), Pb.packed(0, 0, 0), Pb.varintField(3, 1), Pb.stringField(60, "x")),
                ),
            ) + Pb.varintField(42, 1)

        val layer = assertNotNull(VectorTileCodec.decodeOrNull(tile)).single()

        assertEquals(
            listOf(0, 0),
            layer.features
                .single()
                .tags
                .toList(),
        )
        assertEquals(
            mapOf("l" to listOf(ResolvedFeature(3, 1, emptyList(), mapOf("k" to TileValue.Text("v"))))),
            listOf(layer).resolved(),
        )
    }

    @Test
    fun `a single unpacked tag varint is accepted`() {
        val tile =
            Pb.tile(
                Pb.layer(
                    Pb.stringField(3, "k"),
                    Pb.value(Pb.stringField(1, "v")),
                    Pb.feature(Pb.varintField(2, 0), Pb.varintField(2, 0)),
                ),
            )

        val layer = assertNotNull(VectorTileCodec.decodeOrNull(tile)).single()

        assertEquals(
            listOf(0, 0),
            layer.features
                .single()
                .tags
                .toList(),
        )
    }

    @Test(timeout = 5_000)
    fun `a feature of 200,000 unpacked tag fields decodes without copying its tags per field`() {
        // Each field is two bytes, the key of the tags field with the varint wire type and then index 0. They are built
        // in one array because the Pb folds copy the whole message once per part.
        val fields = ByteArray(400_000) { (if (it % 2 == 0) 0x10 else 0).toByte() }
        val tile =
            Pb.tile(
                Pb.layer(
                    Pb.stringField(3, "k"),
                    Pb.value(Pb.stringField(1, "v")),
                    Pb.bytesField(2, fields),
                ),
            )

        val layer = assertNotNull(VectorTileCodec.decodeOrNull(tile)).single()

        assertEquals(
            200_000,
            layer.features
                .single()
                .tags.size,
        )
    }

    @Test
    fun `a value message without a known field decodes to an absent property`() {
        val tile =
            Pb.tile(
                Pb.layer(
                    Pb.stringField(3, "k"),
                    Pb.value(Pb.varintField(50, 1)),
                    Pb.feature(Pb.packed(0, 0)),
                ),
            )

        val layers = assertNotNull(VectorTileCodec.decodeOrNull(tile))

        assertEquals(listOf(null), layers.single().values)
        assertEquals(
            emptyMap(),
            layers
                .resolved()
                .getValue("")
                .single()
                .properties,
        )
    }

    @Test
    fun `an empty array decodes to no layers`() {
        assertEquals(emptyList(), VectorTileCodec.decodeOrNull(ByteArray(0)))
    }

    @Test
    fun `a layer without features is not written`() {
        assertEquals(0, VectorTileCodec.encode(listOf(OutputLayer("a", 4096, emptyList()))).size)
    }

    @Test
    fun `keyIndex and valueOrNull resolve through the layer tables`() {
        val layer =
            sourceLayer(
                "poi",
                mapOf("name" to TileValue.Text("a"), "rank" to TileValue.Integer(1)),
                mapOf("rank" to TileValue.Integer(2)),
            )
        val first = layer.features[0]
        val second = layer.features[1]

        assertEquals(-1, layer.keyIndex("missing"))
        assertEquals(TileValue.Text("a"), layer.valueOrNull(first, layer.keyIndex("name")))
        assertEquals(TileValue.Integer(2), layer.valueOrNull(second, layer.keyIndex("rank")))
        assertNull(layer.valueOrNull(first, -1))
        assertNull(layer.valueOrNull(second, layer.keyIndex("name")))
    }
}

@RunWith(Parameterized::class)
class VectorTileCodecMalformedTest(
    private val description: String,
    private val bytes: ByteArray,
) {
    @Test
    fun `a malformed tile decodes to null`() {
        assertNull(VectorTileCodec.decodeOrNull(bytes))
    }

    companion object {
        private fun layerWithFeature(tags: ByteArray): ByteArray =
            Pb.tile(
                Pb.layer(
                    Pb.stringField(3, "k"),
                    Pb.value(Pb.stringField(1, "v")),
                    Pb.feature(tags),
                ),
            )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> =
            listOf(
                arrayOf("a varint cut off at the end", byteArrayOf(0x1a, 0x80.toByte())),
                arrayOf("a length that runs past the end", byteArrayOf(0x1a, 0x7f, 0x01)),
                arrayOf("wire type 3", byteArrayOf(0x0b)),
                arrayOf("a tag key index equal to keys.size", layerWithFeature(Pb.packed(1, 0))),
                arrayOf("a tag value index equal to values.size", layerWithFeature(Pb.packed(0, 1))),
                arrayOf("a varint longer than ten bytes", ByteArray(11) { 0x80.toByte() }),
            )
    }
}

@RunWith(Parameterized::class)
class VectorTileCodecFixtureTest(
    private val name: String,
    private val featureCount: Int,
) {
    @Test
    fun `a shortbread fixture decodes with its known feature count`() {
        val layers = assertNotNull(VectorTileCodec.decodeOrNull(gunzipped(offlineTileResource("$name.sb.mvt.gz"))))

        assertEquals(featureCount, layers.sumOf { it.features.size })
    }

    companion object {
        // In the order of OfflineTileFixtureNames.
        private val FeatureCounts = listOf(611, 627, 1105, 1129, 150, 516, 260)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> =
            OfflineTileFixtureNames
                .also { require(it.size == FeatureCounts.size) { "one feature count per fixture" } }
                .zip(FeatureCounts) { name, count -> arrayOf(name, count) }
    }
}

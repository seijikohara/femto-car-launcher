package io.github.seijikohara.femto.data.map.offline

import io.github.seijikohara.femto.testfixtures.gzipped
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

/** The gzip trailer sizes the inflate buffer, so a trailer that lies or a stream that outgrows it must never change the result. */
class RawTileTest {
    private val payload = ByteArray(5_000) { (it % 251).toByte() }

    private fun withTrailerSize(
        tile: ByteArray,
        size: Int,
    ): ByteArray = tile.copyOf().also { copy -> repeat(4) { copy[copy.size - 4 + it] = (size ushr (8 * it)).toByte() } }

    @Test
    fun `a gzip tile with a truthful trailer inflates to the payload`() {
        assertContentEquals(payload, ShortbreadConverter.rawTileOrNull(gzipped(payload)))
    }

    @Test
    fun `a trailer that disagrees with the payload is rejected as corrupt`() {
        listOf(0, payload.size - 10, payload.size + 10).forEach { size ->
            assertNull(ShortbreadConverter.rawTileOrNull(withTrailerSize(gzipped(payload), size)), "trailer size $size")
        }
    }

    @Test
    fun `concatenated gzip members inflate to their joined payloads`() {
        val second = ByteArray(300) { 7 }

        assertContentEquals(payload + second, ShortbreadConverter.rawTileOrNull(gzipped(payload) + gzipped(second)))
    }

    @Test
    fun `a trailer that hides an oversized payload is rejected`() {
        assertNull(ShortbreadConverter.rawTileOrNull(withTrailerSize(gzipped(ByteArray(MAX_RAW_TILE_BYTES + 1)), 100)))
    }
}

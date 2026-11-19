package io.github.seijikohara.femto.data.map.offline

import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

/** The gzip trailer sizes the inflate buffer, so a trailer that lies or a stream that outgrows it must never change the result. */
class RawTileTest {
    private val payload = ByteArray(5_000) { (it % 251).toByte() }

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    private fun withTrailerSize(
        gzipped: ByteArray,
        size: Int,
    ): ByteArray =
        gzipped.copyOf().also { copy -> repeat(4) { copy[copy.size - 4 + it] = (size ushr (8 * it)).toByte() } }

    @Test
    fun `a gzip tile with a truthful trailer inflates to the payload`() {
        assertContentEquals(payload, ShortbreadConverter.rawTileOrNull(gzip(payload)))
    }

    @Test
    fun `a trailer that disagrees with the payload is rejected as corrupt`() {
        listOf(0, payload.size - 10, payload.size + 10).forEach { size ->
            assertNull(ShortbreadConverter.rawTileOrNull(withTrailerSize(gzip(payload), size)), "trailer size $size")
        }
    }

    @Test
    fun `concatenated gzip members inflate to their joined payloads`() {
        val second = ByteArray(300) { 7 }

        assertContentEquals(payload + second, ShortbreadConverter.rawTileOrNull(gzip(payload) + gzip(second)))
    }

    @Test
    fun `a trailer that hides an oversized payload is rejected`() {
        assertNull(ShortbreadConverter.rawTileOrNull(withTrailerSize(gzip(ByteArray(MAX_RAW_TILE_BYTES + 1)), 100)))
    }
}

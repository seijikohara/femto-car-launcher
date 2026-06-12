package io.github.seijikohara.femto.data.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SAMPLING_RATE_HZ = 44_100
private const val CAPTURE_SIZE = 1_024

/** Build a Visualizer-layout FFT capture with one complex bin set. */
private fun fftWithBin(
    bin: Int,
    re: Byte,
    im: Byte = 0,
): ByteArray =
    ByteArray(CAPTURE_SIZE).also {
        it[2 * bin] = re
        it[2 * bin + 1] = im
    }

class SpectrumBandsTest {
    @Test
    fun `edges span the requested range with one more edge than bands`() {
        val edges = spectrumBandEdgesHz(bands = 20, minHz = 50f, maxHz = 10_000f)
        assertEquals(21, edges.size)
        assertEquals(50f, edges.first(), 0.01f)
        assertEquals(10_000f, edges.last(), 1f)
    }

    @Test
    fun `edges are strictly increasing`() {
        val edges = spectrumBandEdgesHz()
        edges.toList().zipWithNext().forEach { (lo, hi) ->
            assertTrue("edge $hi must exceed $lo", hi > lo)
        }
    }

    @Test
    fun `edges are log spaced with a constant ratio`() {
        val ratios = spectrumBandEdgesHz().toList().zipWithNext { lo, hi -> hi / lo }
        ratios.forEach { ratio ->
            assertEquals("each band must span the same frequency ratio", ratios.first(), ratio, 1e-3f)
        }
    }

    @Test
    fun `silence yields all zero levels`() {
        val levels = spectrumBandLevels(ByteArray(CAPTURE_SIZE), SAMPLING_RATE_HZ)
        assertEquals(SPECTRUM_BAND_COUNT, levels.size)
        levels.forEach { assertEquals(0f, it, 0f) }
    }

    @Test
    fun `full scale bin clamps its band level to one`() {
        // -128 is the only byte whose magnitude reaches the 128 normalizer.
        val levels = spectrumBandLevels(fftWithBin(bin = 23, re = -128), SAMPLING_RATE_HZ)
        assertEquals(1f, levels.max(), 1e-4f)
    }

    @Test
    fun `pure tone peaks in the band containing its bin frequency`() {
        val edges = spectrumBandEdgesHz()
        val bin = 23
        val binFrequencyHz = bin * SAMPLING_RATE_HZ.toFloat() / CAPTURE_SIZE
        val expectedBand = edges.indexOfLast { it <= binFrequencyHz }
        val levels = spectrumBandLevels(fftWithBin(bin = bin, re = 100, im = 50), SAMPLING_RATE_HZ, edges)
        assertEquals(expectedBand, levels.indices.maxBy { levels[it] })
    }

    @Test
    fun `bands outside the tone stay at zero`() {
        val levels = spectrumBandLevels(fftWithBin(bin = 200, re = 100), SAMPLING_RATE_HZ)
        assertTrue("exactly one or two adjacent bands may light up", levels.count { it > 0f } in 1..2)
    }

    @Test
    fun `imaginary only bin still registers magnitude`() {
        val levels = spectrumBandLevels(fftWithBin(bin = 23, re = 0, im = 100), SAMPLING_RATE_HZ)
        assertTrue("hypot must include the imaginary part", levels.max() > 0f)
    }

    @Test
    fun `empty capture degrades to zero levels`() {
        val levels = spectrumBandLevels(ByteArray(0), SAMPLING_RATE_HZ)
        assertEquals(SPECTRUM_BAND_COUNT, levels.size)
        levels.forEach { assertEquals(0f, it, 0f) }
    }

    @Test
    fun `non positive sampling rate degrades to zero levels`() {
        val levels = spectrumBandLevels(fftWithBin(bin = 23, re = 100), samplingRateHz = 0)
        levels.forEach { assertEquals(0f, it, 0f) }
    }

    @Test
    fun `louder bin yields a higher level in the same band`() {
        val quiet = spectrumBandLevels(fftWithBin(bin = 23, re = 10), SAMPLING_RATE_HZ).max()
        val loud = spectrumBandLevels(fftWithBin(bin = 23, re = 100), SAMPLING_RATE_HZ).max()
        assertTrue("loud=$loud must exceed quiet=$quiet", loud > quiet)
    }
}

package io.github.seijikohara.femto.data.map.offline

import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TileBenchmarkSamplesTest {
    @Test
    fun `only files named like a sample are listed, sorted by name`() {
        val files =
            tileBenchmarkSampleFiles { dir ->
                arrayOf(
                    "NOTICE.txt",
                    "monaco-14-8529-5973.sb.mvt.gzip",
                    "stray.sb.mvt.gzip",
                    "cyprus-14-9710-6480.sb.mvt.gzip",
                    "kanto-14-14553-6450.sb.mvt",
                ).takeIf { dir == TILE_BENCHMARK_ASSET_DIR }
            }

        assertEquals(listOf("cyprus-14-9710-6480.sb.mvt.gzip", "monaco-14-8529-5973.sb.mvt.gzip"), files)
    }

    @Test
    fun `bundled samples offer the benchmark`() {
        assertTrue(hasTileBenchmarkSamples { arrayOf("NOTICE.txt", "monaco-14-8529-5973.sb.mvt.gzip") })
    }

    @Test
    fun `a file with the sample suffix but no tile coordinates does not offer the benchmark`() {
        assertFalse(hasTileBenchmarkSamples { arrayOf("NOTICE.txt", "stray.sb.mvt.gzip") })
    }

    @Test
    fun `no asset directory does not offer the benchmark`() {
        assertFalse(hasTileBenchmarkSamples { null })
    }

    @Test
    fun `a failed asset listing does not offer the benchmark`() {
        assertFalse(hasTileBenchmarkSamples { throw IOException("listing failed") })
    }
}

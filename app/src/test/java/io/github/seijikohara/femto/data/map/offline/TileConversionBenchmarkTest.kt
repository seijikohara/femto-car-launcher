package io.github.seijikohara.femto.data.map.offline

import io.github.seijikohara.femto.testfixtures.offlineTileResource
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.coroutines.coroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TileConversionBenchmarkTest {
    private val output = byteArrayOf(1, 2, 3)

    /** A fake clock that each conversion advances by the next scripted number of milliseconds. */
    private class ScriptedClock(
        private val durationsMs: List<Long>,
    ) {
        private var nanos = 0L
        private var calls = 0

        fun nanoTime(): Long = nanos

        fun advance() {
            nanos += durationsMs[calls % durationsMs.size] * 1_000_000L
            calls++
        }
    }

    private fun sample(name: String = "a") =
        BenchmarkSample(name, 10, offlineTileResource("shikoku-10-886-409.sb.mvt.gz"))

    @Test
    fun `each sample reports its first run and the warm percentiles`() =
        runTest {
            val clock = ScriptedClock(listOf(40L) + (1L..15L).toList() + List(5) { 1L })
            val outcome =
                runTileConversionBenchmark(
                    samples = listOf(sample()),
                    cores = 4,
                    elapsedRealtimeMs = { 777L },
                    nanoTime = clock::nanoTime,
                    convert = { _, _ -> clock.advance().let { output } },
                )
            val measured = assertIs<TileBenchmarkOutcome.Measured>(outcome)
            val row = measured.rows.single()
            assertEquals(40.0, row.firstMs)
            assertEquals(8.0, row.warmP50Ms)
            assertEquals(15.0, row.warmP95Ms)
            assertEquals(15, measured.warmRuns)
            assertEquals(4, measured.cores)
            assertEquals(777L, measured.finishedAtElapsedRealtimeMs)
        }

    @Test
    fun `the parallel pass times every conversion of every round`() =
        runTest {
            // 1 first + 15 warm per sample, then 5 rounds x 2 samples = 10 timings of 1..10 ms.
            val clock = ScriptedClock(List(32) { 1L } + (1L..10L).toList())
            val outcome =
                runTileConversionBenchmark(
                    samples = listOf(sample("a"), sample("b")),
                    cores = 2,
                    elapsedRealtimeMs = { 0L },
                    nanoTime = clock::nanoTime,
                    convert = { _, _ -> clock.advance().let { output } },
                )
            val measured = assertIs<TileBenchmarkOutcome.Measured>(outcome)
            assertEquals(2, measured.parallelTiles)
            assertEquals(5.0, measured.parallelP50Ms)
            assertEquals(10.0, measured.parallelP95Ms)
        }

    @Test
    fun `sizes and feature counts come from the sample itself`() =
        runTest {
            val outcome = runTileConversionBenchmark(listOf(sample()), cores = 1, elapsedRealtimeMs = { 0L })
            val row = assertIs<TileBenchmarkOutcome.Measured>(outcome).rows.single()
            assertEquals(8508, row.rawBytes)
            assertEquals(150, row.features)
            assertTrue(row.outputBytes > 0)
        }

    @Test
    fun `a sample that cannot be converted fails the run by name`() =
        runTest {
            val outcome =
                runTileConversionBenchmark(
                    samples = listOf(sample("a")),
                    cores = 1,
                    elapsedRealtimeMs = { 0L },
                    convert = { _, _ -> null },
                )
            assertEquals(TileBenchmarkOutcome.Failed("a"), outcome)
        }

    @Test
    fun `no samples means no outcome`() =
        runTest {
            assertNull(runTileConversionBenchmark(emptyList(), cores = 1, elapsedRealtimeMs = { 0L }))
        }

    @Test
    fun `cancelling the caller stops the run`() =
        runTest {
            var conversions = 0
            val job =
                launch {
                    val self = coroutineContext[Job]
                    runTileConversionBenchmark(
                        samples = listOf(sample()),
                        cores = 1,
                        elapsedRealtimeMs = { 0L },
                        convert = { _, _ ->
                            conversions++
                            if (conversions == 3) self?.cancel()
                            output
                        },
                    )
                }
            job.join()
            assertTrue(job.isCancelled)
            assertTrue(conversions < 16, "ran $conversions conversions")
        }

    @Test
    fun `a sample file name gives the name and the zoom`() {
        val sample = benchmarkSampleOrNull("kanto-14-14552-6451.sb.mvt.gz", byteArrayOf(1))
        assertEquals("kanto-14-14552-6451", sample?.name)
        assertEquals(14, sample?.zoom)
        assertNull(benchmarkSampleOrNull("NOTICE.txt", byteArrayOf(1)))
    }

    @Test
    fun `the latest outcome replaces the previous one`() {
        val second = TileBenchmarkOutcome.Failed("y")
        TileBenchmarkResults.record(TileBenchmarkOutcome.Failed("x"))
        TileBenchmarkResults.record(second)
        assertEquals(second, TileBenchmarkResults.latestOrNull())
    }
}

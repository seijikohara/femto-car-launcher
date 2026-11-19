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
            val clock = ScriptedClock(listOf(40L) + (1L..30L).toList() + List(10) { 1L })
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
            assertEquals(15.0, row.warmMedianMs)
            assertEquals(29.0, row.warmP95Ms)
            assertEquals(30.0, row.warmMaxMs)
            assertEquals(30, measured.warmRuns)
            assertEquals(4, measured.cores)
            assertEquals(777L, measured.finishedAtElapsedRealtimeMs)
        }

    @Test
    fun `the parallel pass reports each sample from its own timings`() =
        runTest {
            // 31 runs per sample, then 10 rounds of a and b at once: a takes 1..10 ms, b 11..20 ms.
            val clock = ScriptedClock(List(62) { 1L } + (1L..10L).flatMap { listOf(it, it + 10) })
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
            assertEquals(10, measured.parallelRounds)
            val (a, b) = measured.rows
            assertEquals(listOf(5.0, 10.0), listOf(a.parallelMedianMs, a.parallelMaxMs))
            assertEquals(listOf(15.0, 20.0), listOf(b.parallelMedianMs, b.parallelMaxMs))
        }

    @Test
    fun `a sample that fails in the parallel pass fails the run by name`() =
        runTest {
            var conversions = 0
            val outcome =
                runTileConversionBenchmark(
                    samples = listOf(sample("a"), BenchmarkSample("b", 11, sample().tile)),
                    cores = 2,
                    elapsedRealtimeMs = { 0L },
                    convert = { zoom, _ -> output.takeIf { ++conversions <= 62 || zoom != 11 } },
                )
            assertEquals(TileBenchmarkOutcome.Failed("b"), outcome)
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
            assertTrue(conversions < 1 + TILE_BENCHMARK_WARM_RUNS, "ran $conversions conversions")
        }

    @Test
    fun `a sample file name gives the name and the zoom`() {
        val sample = benchmarkSampleOrNull("kanto-14-14553-6450.sb.mvt.gzip", byteArrayOf(1))
        assertEquals("kanto-14-14553-6450", sample?.name)
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

    @Test
    fun `the run counter counts every recorded outcome`() {
        val before = TileBenchmarkResults.runCount()
        TileBenchmarkResults.record(TileBenchmarkOutcome.Failed("x"))
        TileBenchmarkResults.record(TileBenchmarkOutcome.Aborted("y"))
        assertEquals(before + 2, TileBenchmarkResults.runCount())
    }

    private suspend fun runAndRecord(
        loadSamples: () -> List<BenchmarkSample> = { listOf(sample()) },
        convert: (zoom: Int, tile: ByteArray) -> ByteArray? = { _, _ -> output },
    ) = runAndRecordTileBenchmark(
        loadSamples = loadSamples,
        cores = 2,
        elapsedRealtimeMs = { 0L },
        convert = convert,
    )

    @Test
    fun `a finished run records its outcome`() =
        runTest {
            TileBenchmarkResults.record(TileBenchmarkOutcome.Failed("stale"))
            runAndRecord()
            assertIs<TileBenchmarkOutcome.Measured>(TileBenchmarkResults.latestOrNull())
        }

    @Test
    fun `samples that cannot be read record an aborted run`() =
        runTest {
            runAndRecord(loadSamples = { throw java.io.IOException("x") })
            assertEquals(TileBenchmarkOutcome.Aborted("samples could not be read"), TileBenchmarkResults.latestOrNull())
        }

    @Test
    fun `no samples record an aborted run`() =
        runTest {
            runAndRecord(loadSamples = { emptyList() })
            assertEquals(TileBenchmarkOutcome.Aborted("no samples"), TileBenchmarkResults.latestOrNull())
        }

    @Test
    fun `a cancelled run records that it was interrupted and stays cancelled`() =
        runTest {
            TileBenchmarkResults.record(TileBenchmarkOutcome.Failed("stale"))
            lateinit var job: Job
            job = launch { runAndRecord(convert = { _, _ -> output.also { job.cancel() } }) }
            job.join()
            assertTrue(job.isCancelled)
            assertEquals(TileBenchmarkOutcome.Aborted("interrupted"), TileBenchmarkResults.latestOrNull())
        }

    @Test
    fun `an error during the run records its name and does not escape`() =
        runTest {
            runAndRecord(convert = { _, _ -> throw OutOfMemoryError() })
            assertEquals(TileBenchmarkOutcome.Aborted("OutOfMemoryError"), TileBenchmarkResults.latestOrNull())
        }
}

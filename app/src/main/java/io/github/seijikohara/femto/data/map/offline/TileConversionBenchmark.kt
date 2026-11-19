package io.github.seijikohara.femto.data.map.offline

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal const val TILE_BENCHMARK_WARM_RUNS = 30
internal const val TILE_BENCHMARK_PARALLEL_ROUNDS = 10

private const val NANOS_PER_MS = 1_000_000.0

// Not `.gz`: the Android asset merger gunzips every `*.gz` asset and drops the extension, and the benchmark has
// to time the stored, gzip-compressed form a real request reads.
private const val TILE_SAMPLE_SUFFIX = ".sb.mvt.gzip"

// Group 1 is the sample name, group 2 its zoom.
private val SampleFileName = Regex("""(.+-(\d+)-\d+-\d+)""" + Regex.escape(TILE_SAMPLE_SUFFIX))

internal class BenchmarkSample(
    val name: String,
    val zoom: Int,
    val tile: ByteArray,
)

internal data class TileBenchmarkRow(
    val sample: String,
    val rawBytes: Int,
    val features: Int,
    val outputBytes: Int,
    val firstMs: Double,
    val warmMedianMs: Double,
    val warmP95Ms: Double,
    val warmMaxMs: Double,
    val parallelMedianMs: Double,
    val parallelMaxMs: Double,
)

internal sealed interface TileBenchmarkOutcome {
    data class Measured(
        val rows: List<TileBenchmarkRow>,
        val warmRuns: Int,
        val parallelTiles: Int,
        val parallelRounds: Int,
        val cores: Int,
        val finishedAtElapsedRealtimeMs: Long,
    ) : TileBenchmarkOutcome

    data class Failed(
        val sample: String,
    ) : TileBenchmarkOutcome

    data class Aborted(
        val reason: String,
    ) : TileBenchmarkOutcome
}

/**
 * Process-wide record of the benchmark outcomes, which the MAP diagnostics section reads when it is collected. A run
 * belongs to the Diagnostics ViewModel, which lives as long as the activity: closing the sheet does not cancel the
 * run, only the activity's destruction does, and the record still holds the outcome once that ViewModel is gone.
 * Session-scoped by design: timings from a previous launch would describe a different thermal state.
 */
internal object TileBenchmarkResults {
    private val latest = AtomicReference<TileBenchmarkOutcome?>(null)
    private val runs = AtomicInteger(0)

    fun record(outcome: TileBenchmarkOutcome) {
        latest.set(outcome)
        runs.incrementAndGet()
    }

    fun latestOrNull(): TileBenchmarkOutcome? = latest.get()

    /** Return how many outcomes have been recorded since the process started. */
    fun runCount(): Int = runs.get()
}

/**
 * One timed conversion. It keeps the output's size, not the output: a real request drops its output once sent, and
 * keeping every output alive would load the heap and the garbage collector as no request does. [outputBytes] is null
 * when the conversion failed.
 */
private class Timing(
    val ms: Double,
    val outputBytes: Int?,
)

/** A sample's figures from the sequential pass; the parallel pass completes them into a [TileBenchmarkRow]. */
private class SequentialFigures(
    val sample: String,
    val rawBytes: Int,
    val features: Int,
    val outputBytes: Int,
    val firstMs: Double,
    val sortedWarmMs: List<Double>,
)

/**
 * Nearest-rank percentile of [sortedMs] (index `ceil(p * n) - 1`). Kept apart from
 * `computeFrameStats`, whose median is the upper middle element and has no 95th.
 */
internal fun nearestRankMs(
    sortedMs: List<Double>,
    percent: Int,
): Double = sortedMs[((percent * sortedMs.size + 99) / 100 - 1).coerceIn(0, sortedMs.size - 1)]

/** Null when [samples] is empty. Runs on the caller's dispatcher; cancellable between conversions. */
internal suspend fun runTileConversionBenchmark(
    samples: List<BenchmarkSample>,
    cores: Int,
    elapsedRealtimeMs: () -> Long,
    nanoTime: () -> Long = System::nanoTime,
    convert: (zoom: Int, tile: ByteArray) -> ByteArray? = ShortbreadConverter::convertOrNull,
): TileBenchmarkOutcome? {
    if (samples.isEmpty()) return null

    fun timed(sample: BenchmarkSample): Timing {
        val start = nanoTime()
        val outputBytes = convert(sample.zoom, sample.tile)?.size
        return Timing((nanoTime() - start) / NANOS_PER_MS, outputBytes)
    }

    val sequential =
        samples.map { sample ->
            val first = timed(sample)
            val outputBytes = first.outputBytes ?: return TileBenchmarkOutcome.Failed(sample.name)
            val warm =
                List(TILE_BENCHMARK_WARM_RUNS) {
                    currentCoroutineContext().ensureActive()
                    timed(sample)
                }
            if (warm.any { it.outputBytes == null }) return TileBenchmarkOutcome.Failed(sample.name)
            // Sizes come from the sample, outside the timed section.
            val raw = ShortbreadConverter.rawTileOrNull(sample.tile)
            SequentialFigures(
                sample = sample.name,
                rawBytes = raw?.size ?: 0,
                features = raw?.let(VectorTileCodec::decodeOrNull)?.sumOf { it.features.size } ?: 0,
                outputBytes = outputBytes,
                firstMs = first.ms,
                sortedWarmMs = warm.map { it.ms }.sorted(),
            )
        }

    // Each round converts every sample at once and times each conversion on its own. A round's timings come back in
    // sample order, so a sample's timings are the entries at its index: pooled, they would describe no tile.
    val rounds =
        List(TILE_BENCHMARK_PARALLEL_ROUNDS) {
            coroutineScope {
                ensureActive()
                samples.map { async { timed(it) } }.awaitAll()
            }
        }
    samples.indices
        .firstOrNull { index -> rounds.any { it[index].outputBytes == null } }
        ?.let { return TileBenchmarkOutcome.Failed(samples[it].name) }
    return TileBenchmarkOutcome.Measured(
        rows =
            sequential.mapIndexed { index, figures ->
                val sortedParallelMs = rounds.map { it[index].ms }.sorted()
                TileBenchmarkRow(
                    sample = figures.sample,
                    rawBytes = figures.rawBytes,
                    features = figures.features,
                    outputBytes = figures.outputBytes,
                    firstMs = figures.firstMs,
                    warmMedianMs = nearestRankMs(figures.sortedWarmMs, 50),
                    warmP95Ms = nearestRankMs(figures.sortedWarmMs, 95),
                    warmMaxMs = figures.sortedWarmMs.last(),
                    parallelMedianMs = nearestRankMs(sortedParallelMs, 50),
                    parallelMaxMs = sortedParallelMs.last(),
                )
            },
        warmRuns = TILE_BENCHMARK_WARM_RUNS,
        parallelTiles = samples.size,
        parallelRounds = TILE_BENCHMARK_PARALLEL_ROUNDS,
        cores = cores,
        finishedAtElapsedRealtimeMs = elapsedRealtimeMs(),
    )
}

/** Runs the benchmark and always leaves an outcome in [TileBenchmarkResults]: a run that cannot finish says why. */
internal suspend fun runAndRecordTileBenchmark(
    loadSamples: () -> List<BenchmarkSample>,
    cores: Int,
    elapsedRealtimeMs: () -> Long,
    nanoTime: () -> Long = System::nanoTime,
    convert: (zoom: Int, tile: ByteArray) -> ByteArray? = ShortbreadConverter::convertOrNull,
) {
    val samples =
        try {
            loadSamples()
        } catch (_: Exception) {
            TileBenchmarkResults.record(TileBenchmarkOutcome.Aborted("samples could not be read"))
            return
        }
    if (samples.isEmpty()) {
        TileBenchmarkResults.record(TileBenchmarkOutcome.Aborted("no samples"))
        return
    }
    try {
        runTileConversionBenchmark(samples, cores, elapsedRealtimeMs, nanoTime, convert)
            ?.let(TileBenchmarkResults::record)
    } catch (e: CancellationException) {
        TileBenchmarkResults.record(TileBenchmarkOutcome.Aborted("interrupted"))
        throw e
    } catch (t: Throwable) {
        // A diagnostic's own failure, OutOfMemoryError included, is the datum; it must not take the launcher down.
        TileBenchmarkResults.record(TileBenchmarkOutcome.Aborted(t.javaClass.simpleName))
    }
}

/** Whether [fileName] names a sample, `<region>-<z>-<x>-<y>.sb.mvt.gzip`; any other file (the notice text) does not. */
internal fun isBenchmarkSampleFile(fileName: String): Boolean = SampleFileName.matches(fileName)

/** Parse `<region>-<z>-<x>-<y>.sb.mvt.gzip`; null for any other file (the notice text). */
internal fun benchmarkSampleOrNull(
    fileName: String,
    tile: ByteArray,
): BenchmarkSample? =
    SampleFileName.matchEntire(fileName)?.let { BenchmarkSample(it.groupValues[1], it.groupValues[2].toInt(), tile) }

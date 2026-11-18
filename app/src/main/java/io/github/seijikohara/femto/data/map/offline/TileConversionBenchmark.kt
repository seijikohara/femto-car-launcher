package io.github.seijikohara.femto.data.map.offline

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicReference

internal const val TILE_BENCHMARK_WARM_RUNS = 15
internal const val TILE_BENCHMARK_PARALLEL_ROUNDS = 5

private const val NANOS_PER_MS = 1_000_000.0
internal const val TILE_SAMPLE_SUFFIX = ".sb.mvt.gz"
private val SampleFileName = Regex("""(.+)-(\d+)-\d+-\d+""")

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
    val warmP50Ms: Double,
    val warmP95Ms: Double,
)

internal sealed interface TileBenchmarkOutcome {
    data class Measured(
        val rows: List<TileBenchmarkRow>,
        val warmRuns: Int,
        val parallelTiles: Int,
        val parallelP50Ms: Double,
        val parallelP95Ms: Double,
        val cores: Int,
        val finishedAtElapsedRealtimeMs: Long,
    ) : TileBenchmarkOutcome

    data class Failed(
        val sample: String,
    ) : TileBenchmarkOutcome
}

/**
 * Process-wide record of the latest benchmark run, so the MAP diagnostics section
 * can report it after the screen that started the run is gone. Session-scoped by
 * design: timings from a previous launch would describe a different thermal state.
 */
internal object TileBenchmarkResults {
    private val latest = AtomicReference<TileBenchmarkOutcome?>(null)

    fun record(outcome: TileBenchmarkOutcome) = latest.set(outcome)

    fun latestOrNull(): TileBenchmarkOutcome? = latest.get()
}

private class Timing(
    val ms: Double,
    val output: ByteArray?,
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
        val output = convert(sample.zoom, sample.tile)
        return Timing((nanoTime() - start) / NANOS_PER_MS, output)
    }

    val rows =
        samples.map { sample ->
            val first = timed(sample)
            val firstOutput = first.output ?: return TileBenchmarkOutcome.Failed(sample.name)
            val warm =
                List(TILE_BENCHMARK_WARM_RUNS) {
                    currentCoroutineContext().ensureActive()
                    timed(sample)
                }
            if (warm.any { it.output == null }) return TileBenchmarkOutcome.Failed(sample.name)
            val sortedWarm = warm.map { it.ms }.sorted()
            // Sizes come from the sample, outside the timed section.
            val raw = ShortbreadConverter.rawTileOrNull(sample.tile)
            TileBenchmarkRow(
                sample = sample.name,
                rawBytes = raw?.size ?: 0,
                features = raw?.let(VectorTileCodec::decodeOrNull)?.sumOf { it.features.size } ?: 0,
                outputBytes = firstOutput.size,
                firstMs = first.ms,
                warmP50Ms = nearestRankMs(sortedWarm, 50),
                warmP95Ms = nearestRankMs(sortedWarm, 95),
            )
        }

    val parallel =
        List(TILE_BENCHMARK_PARALLEL_ROUNDS) {
            coroutineScope {
                ensureActive()
                samples.map { async { timed(it) } }.awaitAll()
            }
        }.flatten()
    parallel.indices
        .firstOrNull { parallel[it].output == null }
        ?.let { return TileBenchmarkOutcome.Failed(samples[it % samples.size].name) }
    val sortedParallel = parallel.map { it.ms }.sorted()
    return TileBenchmarkOutcome.Measured(
        rows = rows,
        warmRuns = TILE_BENCHMARK_WARM_RUNS,
        parallelTiles = samples.size,
        parallelP50Ms = nearestRankMs(sortedParallel, 50),
        parallelP95Ms = nearestRankMs(sortedParallel, 95),
        cores = cores,
        finishedAtElapsedRealtimeMs = elapsedRealtimeMs(),
    )
}

/** Parse `<region>-<z>-<x>-<y>.sb.mvt.gz`; null for any other file (the notice text). */
internal fun benchmarkSampleOrNull(
    fileName: String,
    tile: ByteArray,
): BenchmarkSample? =
    fileName
        .takeIf { it.endsWith(TILE_SAMPLE_SUFFIX) }
        ?.removeSuffix(TILE_SAMPLE_SUFFIX)
        ?.let { name ->
            SampleFileName.matchEntire(name)?.let { BenchmarkSample(name, it.groupValues[2].toInt(), tile) }
        }

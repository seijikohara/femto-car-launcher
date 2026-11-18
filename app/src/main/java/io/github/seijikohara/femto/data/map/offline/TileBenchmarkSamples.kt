package io.github.seijikohara.femto.data.map.offline

import android.content.res.AssetManager

internal const val TILE_BENCHMARK_ASSET_DIR = "offline-bench"

/** Load the sample tiles bundled with the nightly flavor, sorted by file name; empty in stable, which ships none. */
internal fun tileBenchmarkSamples(assets: AssetManager): List<BenchmarkSample> =
    assets
        .list(TILE_BENCHMARK_ASSET_DIR)
        .orEmpty()
        .sorted()
        .filter { it.endsWith(TILE_SAMPLE_SUFFIX) }
        .mapNotNull { fileName ->
            benchmarkSampleOrNull(fileName, assets.open("$TILE_BENCHMARK_ASSET_DIR/$fileName").use { it.readBytes() })
        }

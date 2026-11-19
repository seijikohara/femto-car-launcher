package io.github.seijikohara.femto.data.map.offline

import android.content.res.AssetManager
import java.io.IOException

internal const val TILE_BENCHMARK_ASSET_DIR = "offline-bench"

/**
 * Name the bundled sample files, sorted by file name. The availability check and the loader both build on this
 * listing, so the button never offers files the loader would skip. [listAssets] is `AssetManager::list`, which
 * throws [IOException] when the listing fails.
 */
internal fun tileBenchmarkSampleFiles(listAssets: (String) -> Array<String>?): List<String> =
    listAssets(TILE_BENCHMARK_ASSET_DIR)
        .orEmpty()
        .filter(::isBenchmarkSampleFile)
        // File-name order does not put the densest bundled sample first, so its `first` figure is warm code on bytes
        // the process has not seen, the steady-state worst case, while the first sample's `first` shows cold code.
        .sorted()

/** Whether any sample is bundled (stable ships none); false when the listing fails, so Diagnostics still opens. */
internal fun hasTileBenchmarkSamples(listAssets: (String) -> Array<String>?): Boolean =
    try {
        tileBenchmarkSampleFiles(listAssets).isNotEmpty()
    } catch (_: IOException) {
        false
    }

/** Load the sample tiles bundled with the nightly flavor, in the order of [tileBenchmarkSampleFiles]. */
internal fun tileBenchmarkSamples(assets: AssetManager): List<BenchmarkSample> =
    tileBenchmarkSampleFiles(assets::list).mapNotNull { fileName ->
        benchmarkSampleOrNull(fileName, assets.open("$TILE_BENCHMARK_ASSET_DIR/$fileName").use { it.readBytes() })
    }

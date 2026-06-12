package io.github.seijikohara.femto.data.music

import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow

/**
 * Number of spectrum bands the equalizer visualization renders per
 * mirrored side. SSOT for the band count: the UI derives its slot
 * layout from the array size emitted here, never from its own constant.
 */
internal const val SPECTRUM_BAND_COUNT = 20

// Band range: 50 Hz keeps the lowest band above the DC/rumble bins the
// Visualizer FFT resolves poorly at 1024-point capture, and 10 kHz is
// where musical energy (and the 8-bit capture's useful dynamic range)
// tapers off; bands above it would sit near-flat and waste slots.
private const val MIN_BAND_HZ = 50f
private const val MAX_BAND_HZ = 10_000f

// -50 dBFS renders as an empty bar. The Visualizer's 8-bit capture has
// ~48 dB of usable dynamic range, so a lower floor would only stretch
// quantization noise into visible bar height.
private const val FLOOR_DB = -50f

// Guards log10(0) for silent bins; corresponds to -80 dBFS, safely
// below FLOOR_DB so silence clamps to an exact 0 level.
private const val MIN_MAGNITUDE = 1e-4f

/**
 * Return the `bands + 1` log-spaced band edges in Hz. Logarithmic
 * spacing matches pitch perception: linear spacing would collapse all
 * musical content below 1 kHz into the first two of twenty bands.
 */
internal fun spectrumBandEdgesHz(
    bands: Int = SPECTRUM_BAND_COUNT,
    minHz: Float = MIN_BAND_HZ,
    maxHz: Float = MAX_BAND_HZ,
): FloatArray =
    FloatArray(bands + 1) { edge ->
        minHz * (maxHz / minHz).pow(edge.toFloat() / bands)
    }

/**
 * Aggregate one [android.media.audiofx.Visualizer] FFT capture into
 * per-band levels in `0..1`.
 *
 * The capture layout is the documented Visualizer packing — `fft[0]` =
 * Re(0), `fft[1]` = Re(n/2), then `fft[2k]`/`fft[2k+1]` = Re(k)/Im(k)
 * for `k` in `1..n/2-1` — so usable bins span `1..n/2-1` and bin `k`
 * sits at `k * samplingRateHz / fft.size` Hz. Each band takes the PEAK
 * magnitude over its bin range (adjacent low bands may share a bin at
 * 1024-point resolution; they then move together, which reads fine),
 * converted to dBFS and normalized against [FLOOR_DB] so bar height is
 * perceptually linear rather than amplitude-linear.
 *
 * Degenerate input (capture shorter than two complex bins, or a
 * non-positive sampling rate) yields all-zero levels — the flat-bar
 * render — rather than throwing.
 */
internal fun spectrumBandLevels(
    fft: ByteArray,
    samplingRateHz: Int,
    edgesHz: FloatArray = spectrumBandEdgesHz(),
): FloatArray {
    val bands = edgesHz.size - 1
    val maxBin = fft.size / 2 - 1
    if (maxBin < 1 || samplingRateHz <= 0) return FloatArray(bands)
    val binHz = samplingRateHz.toFloat() / fft.size
    return FloatArray(bands) { band ->
        val first = (edgesHz[band] / binHz).toInt().coerceIn(1, maxBin)
        val last = (edgesHz[band + 1] / binHz).toInt().coerceIn(first, maxBin)
        normalizedDb((first..last).maxOf { bin -> binMagnitude(fft, bin) })
    }
}

private fun binMagnitude(
    fft: ByteArray,
    bin: Int,
): Float = hypot(fft[2 * bin].toFloat(), fft[2 * bin + 1].toFloat()) / 128f

private fun normalizedDb(magnitude: Float): Float =
    (20f * log10(magnitude.coerceAtLeast(MIN_MAGNITUDE)))
        .coerceIn(FLOOR_DB, 0f)
        .let { db -> (db - FLOOR_DB) / -FLOOR_DB }

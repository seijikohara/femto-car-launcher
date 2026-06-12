package io.github.seijikohara.femto.data.music

/**
 * Outcome of a short spectrum-capture probe, surfaced as the Music spectrum
 * setting's live status line. The visualization fails silently by design
 * (a flat strip), so without this readout a head-unit user cannot tell a
 * missing grant from an unsupported audio HAL from a player whose stream
 * bypasses the mixer — and neither can we without adb access.
 */
internal enum class SpectrumDiagnosis { NO_PERMISSION, ENGINE_UNAVAILABLE, SILENT, ACTIVE }

// A band level above this counts as real signal; well above the dB floor's
// quantization noise yet far below any audible content's normalized level.
internal const val DIAGNOSIS_SIGNAL_LEVEL = 0.02f

/**
 * Classify a probe window's capture emissions. `null` emissions mark an
 * engine that refused to start (permission is pre-checked by the caller);
 * an empty window — no capture callbacks at all — reads as [SpectrumDiagnosis.SILENT]
 * because the engine constructed but produced nothing to render.
 */
internal fun classifySpectrumProbe(emissions: List<FloatArray?>): SpectrumDiagnosis =
    when {
        emissions.any { it == null } -> SpectrumDiagnosis.ENGINE_UNAVAILABLE

        emissions.any { bands ->
            bands != null && bands.any { it > DIAGNOSIS_SIGNAL_LEVEL }
        } -> SpectrumDiagnosis.ACTIVE

        else -> SpectrumDiagnosis.SILENT
    }

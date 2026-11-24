package io.github.seijikohara.femto.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.display.MotionTier

/**
 * What the wave of a wavy progress indicator does under one MotionTier: [waving]
 * is whether the line has a wave at all, [moving] whether the wave travels along it.
 */
internal data class WaveMotion(
    val waving: Boolean,
    val moving: Boolean,
)

/**
 * Return the wave behaviour for [tier]. STANDARD waves and travels; REDUCED keeps
 * the wave's shape but holds it still, because a travelling wave redraws every
 * frame on a 30 Hz head-unit panel; OFF draws a flat line, so a user who turned
 * motion off sees no wave motif either.
 */
internal fun waveMotion(tier: MotionTier): WaveMotion =
    when (tier) {
        MotionTier.STANDARD -> WaveMotion(waving = true, moving = true)
        MotionTier.REDUCED -> WaveMotion(waving = true, moving = false)
        MotionTier.OFF -> WaveMotion(waving = false, moving = false)
    }

/**
 * Draw a determinate progress bar as the Material 3 Expressive wave. The wave
 * follows the Motion setting through `MaterialTheme.motionScheme` (see
 * [waveMotion]), so a call site never threads the tier itself. The music card's
 * playback position stays a flat `LinearProgressIndicator` on purpose: a position
 * must not ripple.
 */
@Composable
internal fun FemtoLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val wave = currentWaveMotion()
    LinearWavyProgressIndicator(
        progress = progress,
        modifier = modifier,
        amplitude = if (wave.waving) WavyProgressIndicatorDefaults.indicatorAmplitude else NoAmplitude,
        wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
        waveSpeed = if (wave.moving) WavyProgressIndicatorDefaults.LinearDeterminateWavelength else NoWaveSpeed,
    )
}

/** Draw an indeterminate progress bar as the Material 3 Expressive wave; see the determinate overload. */
@Composable
internal fun FemtoLinearProgressIndicator(modifier: Modifier = Modifier) {
    val wave = currentWaveMotion()
    LinearWavyProgressIndicator(
        modifier = modifier,
        amplitude = if (wave.waving) 1f else 0f,
        wavelength = WavyProgressIndicatorDefaults.LinearIndeterminateWavelength,
        waveSpeed = if (wave.moving) WavyProgressIndicatorDefaults.LinearIndeterminateWavelength else NoWaveSpeed,
    )
}

/**
 * Draw a determinate circular progress indicator as the Material 3 Expressive
 * wave, following the Motion setting like [FemtoLinearProgressIndicator].
 * [strokeWidth] thins the ring for the inline sizes where the 4 dp default would
 * fill it; unspecified keeps the Material default.
 */
@Composable
internal fun FemtoCircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = Dp.Unspecified,
) {
    val wave = currentWaveMotion()
    val stroke = ringStroke(strokeWidth, WavyProgressIndicatorDefaults.circularIndicatorStroke)
    CircularWavyProgressIndicator(
        progress = progress,
        modifier = modifier,
        stroke = stroke,
        trackStroke = stroke,
        amplitude = if (wave.waving) WavyProgressIndicatorDefaults.indicatorAmplitude else NoAmplitude,
        wavelength = WavyProgressIndicatorDefaults.CircularWavelength,
        waveSpeed = if (wave.moving) WavyProgressIndicatorDefaults.CircularWavelength else NoWaveSpeed,
    )
}

/** Draw an indeterminate circular progress indicator as the Material 3 Expressive wave; see the determinate overload. */
@Composable
internal fun FemtoCircularProgressIndicator(
    modifier: Modifier = Modifier,
    strokeWidth: Dp = Dp.Unspecified,
) {
    val wave = currentWaveMotion()
    val stroke = ringStroke(strokeWidth, WavyProgressIndicatorDefaults.circularIndicatorStroke)
    CircularWavyProgressIndicator(
        modifier = modifier,
        stroke = stroke,
        trackStroke = stroke,
        amplitude = if (wave.waving) 1f else 0f,
        wavelength = WavyProgressIndicatorDefaults.CircularWavelength,
        waveSpeed = if (wave.moving) WavyProgressIndicatorDefaults.CircularWavelength else NoWaveSpeed,
    )
}

@Composable
private fun currentWaveMotion(): WaveMotion = waveMotion(Motion.tier(MaterialTheme.motionScheme))

// A thinner ring keeps the Material default's round cap; only the width changes.
@Composable
private fun ringStroke(
    width: Dp,
    default: Stroke,
): Stroke =
    if (width == Dp.Unspecified) {
        default
    } else {
        Stroke(width = with(LocalDensity.current) { width.toPx() }, cap = StrokeCap.Round)
    }

private val NoAmplitude: (Float) -> Float = { 0f }

private val NoWaveSpeed = 0.dp

@PreviewLightDark
@Composable
private fun WavyProgressPreview() {
    FemtoTheme {
        WavyProgressSamples()
    }
}

// One block per tier, so the still wave (REDUCED) and the flat line (OFF) can be
// compared with the moving one in a single preview.
@Preview(name = "Motion tiers", showBackground = true)
@Composable
private fun WavyProgressTiersPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MotionTier.entries.forEach { tier ->
            FemtoTheme(motionTier = tier) {
                WavyProgressSamples()
            }
        }
    }
}

@Composable
private fun WavyProgressSamples() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(0f, 0.5f, 1f).forEach { fraction ->
            FemtoLinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
        FemtoLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FemtoCircularProgressIndicator(progress = { 0.5f })
            FemtoCircularProgressIndicator()
            FemtoCircularProgressIndicator(progress = { 0.5f }, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            FemtoCircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
    }
}

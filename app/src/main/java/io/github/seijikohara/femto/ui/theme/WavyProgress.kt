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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.display.MotionTier

/**
 * What the wave of a wavy progress indicator does under one MotionTier:
 * [amplitudeScale] multiplies the indicator's own amplitude (1 keeps the Material
 * wave, 0 flattens it), [speed] is how fast the wave travels along the line.
 */
internal data class WaveSpec(
    val amplitudeScale: Float,
    val speed: Dp,
)

/**
 * Return the wave for [tier] on an indicator whose wave is [wavelength] long, the
 * one place every overload takes its wave parameters from. STANDARD keeps the
 * Material wave and travels it at one wavelength per second (the Material
 * default); REDUCED keeps the wave's shape but holds it still, because a
 * travelling wave redraws every frame on a 30 Hz head-unit panel; OFF draws a
 * flat line, so a user who turned motion off sees no wave motif either.
 */
internal fun waveSpec(
    tier: MotionTier,
    wavelength: Dp,
): WaveSpec =
    when (tier) {
        MotionTier.STANDARD -> WaveSpec(amplitudeScale = 1f, speed = wavelength)
        MotionTier.REDUCED -> WaveSpec(amplitudeScale = 1f, speed = 0.dp)
        MotionTier.OFF -> WaveSpec(amplitudeScale = 0f, speed = 0.dp)
    }

/**
 * Draw a determinate progress bar as the Material 3 Expressive wave. The wave
 * follows the Motion setting through `MaterialTheme.motionScheme` (see
 * [waveSpec]), so a call site never threads the tier itself. The music card's
 * playback position stays a flat `LinearProgressIndicator` on purpose: a position
 * must not ripple.
 */
@Composable
internal fun FemtoLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength
    val wave = currentWave(wavelength)
    LinearWavyProgressIndicator(
        progress = progress,
        modifier = modifier,
        amplitude = { WavyProgressIndicatorDefaults.indicatorAmplitude(it) * wave.amplitudeScale },
        wavelength = wavelength,
        waveSpeed = wave.speed,
    )
}

/** Draw an indeterminate progress bar as the Material 3 Expressive wave; see the determinate overload. */
@Composable
internal fun FemtoLinearProgressIndicator(modifier: Modifier = Modifier) {
    val wavelength = WavyProgressIndicatorDefaults.LinearIndeterminateWavelength
    val wave = currentWave(wavelength)
    LinearWavyProgressIndicator(
        modifier = modifier,
        amplitude = wave.amplitudeScale,
        wavelength = wavelength,
        waveSpeed = wave.speed,
    )
}

/**
 * Draw an indeterminate circular progress indicator as the Material 3 Expressive
 * wave, following the Motion setting like [FemtoLinearProgressIndicator]. The
 * track is transparent, as the flat indeterminate spinner's always was: a ring
 * behind a 20 dp inline spinner reads as a second, static indicator. There is no
 * determinate circular overload because no call site draws one.
 * [strokeWidth] thins the ring for the inline sizes where the 4 dp default would
 * fill it; unspecified keeps the Material default.
 */
@Composable
internal fun FemtoCircularProgressIndicator(
    modifier: Modifier = Modifier,
    strokeWidth: Dp = Dp.Unspecified,
) {
    val wavelength = WavyProgressIndicatorDefaults.CircularWavelength
    val wave = currentWave(wavelength)
    val stroke = ringStroke(strokeWidth, WavyProgressIndicatorDefaults.circularIndicatorStroke)
    CircularWavyProgressIndicator(
        modifier = modifier,
        trackColor = Color.Transparent,
        stroke = stroke,
        trackStroke = stroke,
        amplitude = wave.amplitudeScale,
        wavelength = wavelength,
        waveSpeed = wave.speed,
    )
}

@Composable
private fun currentWave(wavelength: Dp): WaveSpec = waveSpec(Motion.tier(MaterialTheme.motionScheme), wavelength)

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

@PreviewLightDark
@Composable
private fun WavyProgressPreview() {
    FemtoTheme {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(0f, 0.5f, 1f).forEach { fraction ->
                FemtoLinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
            FemtoLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FemtoCircularProgressIndicator()
                FemtoCircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
        }
    }
}

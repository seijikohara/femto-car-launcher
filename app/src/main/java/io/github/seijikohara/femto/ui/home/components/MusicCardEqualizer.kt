package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.seijikohara.femto.data.music.SPECTRUM_BAND_COUNT
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlin.math.exp
import kotlin.math.sin

// Asymmetric smoothing: a fast attack keeps the bars on the beat while the
// slower decay carries motion across the ~50 ms gaps between Visualizer
// captures, so 20 Hz data reads as fluid at the display frame rate.
private const val TAU_ATTACK_MS = 60f
private const val TAU_DECAY_MS = 350f

// dt for the first frame after (re)start, before a frame delta exists.
private const val FALLBACK_FRAME_MS = 16L

// Below this level a bar is invisible; once every band is under it (and the
// spectrum is inactive) the frame loop parks instead of burning frames.
private const val PARK_LEVEL = 0.005f

// Bar gradient alphas. The cap keeps the strip a background: at 0.22 peak the
// accent reads as texture behind the transport buttons, never as a competitor.
private const val GRADIENT_TOP_ALPHA = 0.06f
private const val GRADIENT_BASE_ALPHA = 0.22f

private const val BAR_WIDTH_FRACTION = 0.6f
private const val MAX_BAR_HEIGHT_FRACTION = 0.9f

/**
 * Audio-reactive equalizer strip drawn behind the music card's transport
 * controls.
 *
 * Layout is mirrored center-out: the horizontal centre is 0 Hz, and the
 * [SPECTRUM_BAND_COUNT] bands (low to high) extend symmetrically to both
 * edges — the Visualizer capture is mono, so symmetry is the honest
 * rendering. Bars use the Material accent at low alpha so the strip follows
 * the active color scheme (dynamic or preset) and stays decorative.
 *
 * Rendering notes:
 *  - The displayed levels advance toward [spectrum]'s latest value inside a
 *    [withFrameNanos] loop with asymmetric exponential smoothing, then are
 *    read ONLY inside the [Canvas] draw lambda — each frame invalidates the
 *    draw phase alone, never recomposing the card or its buttons.
 *  - When the spectrum goes null (visualization off, playback stopped,
 *    capture unavailable) the bars decay to flat and the loop parks until
 *    data returns, so an idle card costs zero frames.
 */
@Composable
internal fun EqualizerBackground(
    spectrum: StateFlow<FloatArray?>,
    modifier: Modifier = Modifier,
) {
    val target by spectrum.collectAsStateWithLifecycle()
    var displayed by remember { mutableStateOf(FloatArray(0)) }
    LaunchedEffect(spectrum) {
        var previousFrameNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                val dtMillis =
                    if (previousFrameNanos == 0L) {
                        FALLBACK_FRAME_MS
                    } else {
                        (frameNanos - previousFrameNanos) / 1_000_000
                    }
                previousFrameNanos = frameNanos
                displayed = smoothedLevels(displayed, target, dtMillis)
            }
            if (target == null && displayed.all { it < PARK_LEVEL }) {
                previousFrameNanos = 0L
                snapshotFlow { target }.first { it != null }
            }
        }
    }
    val brush = equalizerBrush()
    Canvas(modifier = modifier) {
        drawEqualizerBars(displayed, brush)
    }
}

/**
 * Advance the displayed bar levels one frame toward [target] (`null` decays
 * to flat) with per-band asymmetric exponential smoothing — rising bands use
 * the attack time constant, falling bands the slower decay. A size change
 * (first activation, band-count change) resets to flat so the bars animate
 * up from the baseline instead of snapping.
 */
internal fun smoothedLevels(
    displayed: FloatArray,
    target: FloatArray?,
    dtMillis: Long,
): FloatArray {
    val goal = target ?: FloatArray(displayed.size)
    if (displayed.size != goal.size) return FloatArray(goal.size)
    return FloatArray(displayed.size) { band ->
        val tau = if (goal[band] > displayed[band]) TAU_ATTACK_MS else TAU_DECAY_MS
        val alpha = 1f - exp(-dtMillis / tau)
        displayed[band] + (goal[band] - displayed[band]) * alpha
    }
}

// One gradient shared by every bar (it spans the strip's draw bounds), in the
// active scheme's accent so the strip tracks dynamic color and preset seeds.
@Composable
private fun equalizerBrush(): Brush {
    val accent = MaterialTheme.colorScheme.primary
    return remember(accent) {
        Brush.verticalGradient(
            colors =
                listOf(
                    accent.copy(alpha = GRADIENT_TOP_ALPHA),
                    accent.copy(alpha = GRADIENT_BASE_ALPHA),
                ),
        )
    }
}

// Mirrored center-out bars: band 0 (lowest frequency) hugs the centre line,
// the highest band sits at each edge; both sides draw the same mono levels.
private fun DrawScope.drawEqualizerBars(
    levels: FloatArray,
    brush: Brush,
) {
    if (levels.isEmpty()) return
    val slot = size.width / (2 * levels.size)
    val barWidth = slot * BAR_WIDTH_FRACTION
    val inset = (slot - barWidth) / 2f
    val corner = CornerRadius(barWidth / 2f)
    val centerX = size.width / 2f
    levels.forEachIndexed { band, level ->
        val barHeight = level * size.height * MAX_BAR_HEIGHT_FRACTION
        if (barHeight <= 0f) return@forEachIndexed
        val top = size.height - barHeight
        val barSize = Size(barWidth, barHeight)
        drawRoundRect(brush, Offset(centerX + band * slot + inset, top), barSize, corner)
        drawRoundRect(brush, Offset(centerX - (band + 1) * slot + inset, top), barSize, corner)
    }
}

// Static geometry preview: the live composable animates via the frame loop,
// which does not advance in a preview, so this draws a fixed ramp directly
// through the same DrawScope extension.
@PreviewLightDark
@Composable
private fun EqualizerBackgroundPreview() {
    FemtoTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            val levels = FloatArray(SPECTRUM_BAND_COUNT) { band -> 0.15f + 0.75f * (0.5f + 0.5f * sin(band * 0.7f)) }
            val brush = equalizerBrush()
            Canvas(modifier = Modifier.size(width = 320.dp, height = 64.dp)) {
                drawEqualizerBars(levels, brush)
            }
        }
    }
}

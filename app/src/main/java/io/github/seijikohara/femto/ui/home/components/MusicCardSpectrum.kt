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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.seijikohara.femto.data.music.SPECTRUM_BAND_COUNT
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

// Asymmetric smoothing: a fast attack keeps the bars on the beat while the
// slower decay carries motion across the ~50 ms gaps between Visualizer
// captures, so 20 Hz data reads as fluid at the display frame rate.
private const val TAU_ATTACK_MS = 60f
private const val TAU_DECAY_MS = 350f

// dt for the first frame after (re)start, before a frame delta exists.
private const val FALLBACK_FRAME_MS = 16L

// Convergence tolerance: once every band is within this of its target the
// smoothing has visually settled (sub-pixel on the strip), so the loop snaps to
// the target and parks instead of burning frames. It doubles as the decay-to-flat
// floor when the spectrum is inactive (a null target has an all-zero goal).
private const val CONVERGE_LEVEL = 0.005f

// Bar gradient alphas. The cap keeps the strip a background: at 0.22 peak the
// accent reads as texture behind the transport buttons, never as a competitor.
private const val GRADIENT_TOP_ALPHA = 0.06f
private const val GRADIENT_BASE_ALPHA = 0.22f

private const val BAR_WIDTH_FRACTION = 0.6f
private const val MAX_BAR_HEIGHT_FRACTION = 0.9f

/**
 * Audio-reactive spectrum strip drawn behind the music card's transport
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
 *  - The loop parks whenever the bars have caught up to the target — a null
 *    target (visualization off, playback stopped, capture unavailable) decays
 *    to flat, a static one settles in place — and resumes on the next distinct
 *    capture, so an idle or unchanging card costs zero frames and the
 *    composition can reach idle.
 */
@Composable
internal fun SpectrumBackground(
    spectrum: StateFlow<FloatArray?>,
    modifier: Modifier = Modifier,
) {
    val target by spectrum.collectAsStateWithLifecycle()
    var displayed by remember { mutableStateOf(FloatArray(0)) }
    // repeatOnLifecycle keeps the frame loop STARTED-only: in the background
    // collectAsStateWithLifecycle parks `target` at its last non-null value, so
    // without the lifecycle gate
    // the loop could spin against a stale target. The restart also resets the
    // frame basis, so the first frame after resume uses the fallback dt
    // instead of one giant background-spanning delta.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(spectrum) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
                // Once the bars have caught up to the target there is nothing left
                // to advance, so snap to the exact goal and park until the next
                // distinct capture. A static signal — paused visualization, or a
                // fixed test fixture — then costs zero frames and lets the
                // composition reach idle; a live-but-unchanging FloatArray would
                // otherwise re-invalidate the draw every frame forever, as arrays
                // compare by reference.
                val goal = target ?: FloatArray(displayed.size)
                if (displayed.hasConverged(goal)) {
                    displayed = goal
                    previousFrameNanos = 0L
                    val settled = target
                    snapshotFlow { target }.first { it !== settled }
                }
            }
        }
    }
    val brush = spectrumBrush()
    Canvas(modifier = modifier) {
        drawSpectrumBars(displayed, brush)
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

// True once every band sits within the convergence tolerance of its goal, so
// further smoothing frames would not change what is drawn and the loop can park.
private fun FloatArray.hasConverged(goal: FloatArray): Boolean =
    size == goal.size && indices.all { abs(this[it] - goal[it]) < CONVERGE_LEVEL }

// One gradient shared by every bar (it spans the strip's draw bounds), in the
// active scheme's accent so the strip tracks dynamic color and preset seeds.
@Composable
private fun spectrumBrush(): Brush {
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
private fun DrawScope.drawSpectrumBars(
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
private fun SpectrumBackgroundPreview() {
    FemtoTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            val levels = FloatArray(SPECTRUM_BAND_COUNT) { band -> 0.15f + 0.75f * (0.5f + 0.5f * sin(band * 0.7f)) }
            val brush = spectrumBrush()
            Canvas(modifier = Modifier.size(width = 320.dp, height = 64.dp)) {
                drawSpectrumBars(levels, brush)
            }
        }
    }
}

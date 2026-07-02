package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.progressCaption

/**
 * Draggable playback progress for the Now Playing panel, keeping the music
 * card's thin-track aesthetic rather than adopting the M3 Slider. Display-only
 * when [canSeek] is false (session lacks ACTION_SEEK_TO): same bar, no thumb,
 * no gestures — the capability gate hides affordances instead of disabling
 * them. While dragging, the local scrub position overrides the live
 * interpolation and the elapsed label previews the target; the seek command
 * dispatches once, on release (or on a plain tap).
 */
@Composable
internal fun PlaybackSeekBar(
    positionMs: Long,
    durationMs: Long,
    positionUpdateTimeMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    canSeek: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val livePosition by
        rememberInterpolatedPositionMs(
            positionMs = positionMs,
            durationMs = durationMs,
            positionUpdateTimeMs = positionUpdateTimeMs,
            isPlaying = isPlaying,
            playbackSpeed = playbackSpeed,
        )
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction =
        dragFraction
            ?: if (durationMs > 0L) (livePosition.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownMs = dragFraction?.let { seekTargetMs(it, durationMs) } ?: livePosition
    val seekLabel = stringResource(R.string.music_seek)
    val gestureModifier =
        if (canSeek) {
            Modifier
                .semantics { contentDescription = seekLabel }
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        onSeek(seekTargetMs(offset.x / size.width, durationMs))
                    }
                }.pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, _ ->
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            change.consume()
                        },
                        onDragEnd = {
                            dragFraction?.let { onSeek(seekTargetMs(it, durationMs)) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                    )
                }
        } else {
            Modifier
        }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = formatMillis(shownMs),
            style = MaterialTheme.typography.progressCaption(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        // The gesture surface is a full MinTouchTarget tall; the 6 dp visual
        // track centres inside it so the automotive tap floor holds without a
        // visually heavy bar.
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(FemtoDimens.MinTouchTarget)
                    .then(gestureModifier),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
            )
            if (canSeek) {
                Box(
                    modifier = Modifier.fillMaxWidth(fraction),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
        Text(
            text = formatMillis(durationMs),
            style = MaterialTheme.typography.progressCaption(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Map a drag fraction to the target playback position, clamped into the track. */
internal fun seekTargetMs(
    fraction: Float,
    durationMs: Long,
): Long = (fraction.coerceIn(0f, 1f) * durationMs.coerceAtLeast(0L)).toLong()

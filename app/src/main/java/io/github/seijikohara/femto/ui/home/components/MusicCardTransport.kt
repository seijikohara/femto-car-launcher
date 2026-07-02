package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.SkipBack
import com.composables.icons.lucide.SkipForward
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.music.MusicCommand
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.progressCaption

@Composable
internal fun Progress(
    positionMs: Long,
    durationMs: Long,
    positionUpdateTimeMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
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
    val fraction =
        if (durationMs > 0L) {
            (livePosition.toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = formatMillis(livePosition),
            style = MaterialTheme.typography.progressCaption(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
            )
        }
        Text(
            text = formatMillis(durationMs),
            style = MaterialTheme.typography.progressCaption(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
internal fun TransportRow(
    isPlaying: Boolean,
    onCommand: (MusicCommand) -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
) {
    TransportButton(
        icon = Lucide.SkipBack,
        description = stringResource(R.string.music_skip_previous),
        primary = false,
        onClick = { onCommand(MusicCommand.SkipPrevious) },
    )
    TransportButton(
        icon = if (isPlaying) Lucide.Pause else Lucide.Play,
        description = stringResource(R.string.music_play_pause),
        primary = true,
        onClick = { onCommand(MusicCommand.PlayPause) },
    )
    TransportButton(
        icon = Lucide.SkipForward,
        description = stringResource(R.string.music_skip_next),
        primary = false,
        onClick = { onCommand(MusicCommand.SkipNext) },
    )
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    description: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    // All transport buttons are unfilled so the spectrum background stays
    // visible across the whole strip; the primary action keeps its hierarchy
    // through the accent tint, the larger glyph, and the centre position
    // instead of a filled container (which hid the busiest centre bands).
    val width = if (primary) FemtoDimens.MusicPlayButton else FemtoDimens.MusicTransportButton
    val corner = if (primary) 16.dp else 14.dp
    val content =
        if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier =
            Modifier
                .height(FemtoDimens.MusicTransportButton)
                .width(width)
                .clip(RoundedCornerShape(corner))
                .clickable(onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        FemtoIcon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(if (primary) 40.dp else 32.dp),
        )
    }
}

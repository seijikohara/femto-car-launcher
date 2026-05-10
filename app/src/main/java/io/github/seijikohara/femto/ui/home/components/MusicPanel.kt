package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.MusicCardState
import io.github.seijikohara.femto.data.NowPlaying
import io.github.seijikohara.femto.ui.theme.FemtoDimens

internal sealed interface MusicCommand {
    data object PlayPause : MusicCommand

    data object SkipNext : MusicCommand

    data object SkipPrevious : MusicCommand
}

@Composable
internal fun MusicPanel(
    state: MusicCardState,
    onCommand: (MusicCommand) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = FemtoDimens.CardElevation,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(FemtoDimens.GridGutter),
    ) {
        // Three explicit states keep the panel from collapsing into a single
        // "is null?" branch. The CTA copy and visual weight differ between
        // "permission needed" (primary-coloured CTA) and "nothing playing"
        // (muted placeholder). See data/MusicCardState.kt for the SSOT.
        when (state) {
            MusicCardState.NeedsPermission -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ConnectCta(onConnect = onConnect)
                }
            }

            MusicCardState.NoActiveSession -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    NothingPlaying()
                }
            }

            is MusicCardState.Playing -> {
                ActiveTrack(nowPlaying = state.nowPlaying)
                Spacer(modifier = Modifier.weight(1f))
                // Progress sits above the transport row so the visual order
                // matches Spotify / Apple Music "Now Playing" widgets — read
                // position first, then act on it.
                PlaybackProgress(nowPlaying = state.nowPlaying)
                Spacer(modifier = Modifier.height(12.dp))
                TransportControls(
                    isPlaying = state.nowPlaying.isPlaying,
                    onCommand = onCommand,
                )
            }
        }
    }
}

@Composable
private fun ConnectCta(onConnect: () -> Unit) =
    // Wrapping the row in a primaryContainer Surface upgrades the CTA from
    // "tappable text" to a recognisable button affordance — first-launch users
    // need a clear action target since this is the entry point to the panel.
    Surface(
        onClick = onConnect,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(FemtoDimens.HeroIconSize),
            )
            Column {
                Text(
                    text = "Connect a player",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Allow notification access to control playback from the dashboard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

@Composable
private fun NothingPlaying() =
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.MusicOff,
            contentDescription = null,
            modifier = Modifier.size(FemtoDimens.HeroIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column {
            Text(
                text = "Nothing playing",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Open a music app to begin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

@Composable
private fun ActiveTrack(nowPlaying: NowPlaying) =
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(nowPlaying = nowPlaying)
        Column {
            Text(
                text = nowPlaying.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            nowPlaying.artist?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

@Composable
private fun AlbumArt(nowPlaying: NowPlaying) {
    val art = nowPlaying.albumArt
    if (art != null) {
        Image(
            bitmap = art,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(FemtoDimens.AlbumArtSize)
                    .clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Surface(
            modifier = Modifier.size(FemtoDimens.AlbumArtSize),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Icon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = null,
                modifier = Modifier.padding(12.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PlaybackProgress(
    nowPlaying: NowPlaying,
    modifier: Modifier = Modifier,
) {
    val duration = nowPlaying.durationMs.takeIf { it > 0L } ?: return
    val fraction = (nowPlaying.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    // 6 dp height (vs M3 default 4 dp) plus rounded caps so the bar stays
    // visible under direct sun and at glance distance — the head-unit
    // viewing distance is closer to a tablet than a watch but worse-lit.
    LinearProgressIndicator(
        progress = { fraction },
        modifier =
            modifier
                .fillMaxWidth()
                .height(6.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Round,
    )
}

@Composable
private fun TransportControls(
    isPlaying: Boolean,
    onCommand: (MusicCommand) -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    // Centered transport gives the music card a balanced silhouette and reads
    // as a deliberate widget rather than a left-aligned list row.
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
) {
    TransportButton(
        icon = Icons.Outlined.SkipPrevious,
        description = "Skip previous",
    ) { onCommand(MusicCommand.SkipPrevious) }
    TransportButton(
        icon = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
        description = "Play / pause",
        primary = true,
    ) { onCommand(MusicCommand.PlayPause) }
    TransportButton(
        icon = Icons.Outlined.SkipNext,
        description = "Skip next",
    ) { onCommand(MusicCommand.SkipNext) }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    description: String,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val container =
        if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val content =
        if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier =
            Modifier
                .size(FemtoDimens.MinTouchTarget)
                .semantics { contentDescription = description },
        shape = CircleShape,
        color = container,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = content,
            )
        }
    }
}

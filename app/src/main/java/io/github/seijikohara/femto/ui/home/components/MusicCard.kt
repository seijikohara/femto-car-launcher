package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.seijikohara.femto.data.MusicCardState
import io.github.seijikohara.femto.data.NowPlaying
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

/**
 * Transport commands the dashboard can dispatch to the music session.
 *
 * Defined here (rather than in the deleted MusicPanel) because MusicCard is
 * now the only consumer.
 */
internal sealed interface MusicCommand {
    data object PlayPause : MusicCommand

    data object SkipNext : MusicCommand

    data object SkipPrevious : MusicCommand
}

/**
 * Music card. Vertical layout with four sections distributed by
 * `Arrangement.SpaceBetween`:
 *
 *  1. Album art (140 dp, centred)
 *  2. Meta — source eyebrow, title, artist
 *  3. Progress bar with time labels
 *  4. Transport row (64 dp prev/next, 72 dp play centre)
 *
 * Two empty variants render in the same dimensions:
 *
 *  - `NeedsPermission` — CTA to grant notification listener access
 *  - `NoActiveSession` — "Nothing is playing" placeholder
 */
@Composable
internal fun MusicCard(
    state: MusicCardState,
    onCommand: (MusicCommand) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier,
    shape = RoundedCornerShape(FemtoDimens.OverlayCorner),
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = FemtoDimens.CardElevation,
) {
    when (state) {
        MusicCardState.NeedsPermission -> ConnectState(onConnect = onConnect)
        MusicCardState.NoActiveSession -> EmptyState()
        is MusicCardState.Playing -> PlayingState(state.nowPlaying, onCommand)
    }
}

@Composable
private fun PlayingState(
    nowPlaying: NowPlaying,
    onCommand: (MusicCommand) -> Unit,
) = Column(
    modifier =
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.SpaceBetween,
) {
    AlbumArt(nowPlaying = nowPlaying)
    Meta(
        source = sourceLabel(nowPlaying.packageName),
        title = nowPlaying.title,
        artist = nowPlaying.artist,
    )
    Progress(positionMs = nowPlaying.positionMs, durationMs = nowPlaying.durationMs)
    TransportRow(isPlaying = nowPlaying.isPlaying, onCommand = onCommand)
}

@Composable
private fun AlbumArt(nowPlaying: NowPlaying) {
    val art = nowPlaying.albumArt
    Box(
        modifier =
            Modifier
                .size(FemtoDimens.MusicArtSize)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary,
                                ),
                            ),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
    }
}

@Composable
private fun Meta(
    source: String,
    title: String,
    artist: String?,
) = Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp),
) {
    Text(
        text = source.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = title,
        style =
            MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.02f).em,
            ),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (!artist.isNullOrBlank()) {
        Text(
            text = artist,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Progress(
    positionMs: Long,
    durationMs: Long,
) {
    val fraction =
        if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatMillis(positionMs),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatMillis(durationMs),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransportRow(
    isPlaying: Boolean,
    onCommand: (MusicCommand) -> Unit,
) = Row(
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    TransportButton(
        icon = Icons.Outlined.SkipPrevious,
        description = "Skip previous",
        primary = false,
        onClick = { onCommand(MusicCommand.SkipPrevious) },
    )
    TransportButton(
        icon = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
        description = "Play / pause",
        primary = true,
        onClick = { onCommand(MusicCommand.PlayPause) },
    )
    TransportButton(
        icon = Icons.Outlined.SkipNext,
        description = "Skip next",
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
    val width = if (primary) FemtoDimens.MusicPlayButton else FemtoDimens.MusicTransportButton
    val container =
        if (primary) MaterialTheme.colorScheme.primary else Color.Transparent
    val content =
        if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier =
            Modifier
                .height(FemtoDimens.MusicTransportButton)
                .width(width)
                .clip(RoundedCornerShape(16.dp))
                .background(container)
                .clickable(onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun ConnectState(onConnect: () -> Unit) =
    Surface(
        onClick = onConnect,
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Box(modifier = Modifier.height(8.dp))
            Text(
                text = "Connect a player",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Allow notification access to control playback from the dashboard.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

@Composable
private fun EmptyState() =
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.MusicOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Box(modifier = Modifier.height(8.dp))
        Text(
            text = "Nothing is playing",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Open a music app to start, or say \"Hey Google, play something\".",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

private fun sourceLabel(packageName: String): String =
    when {
        packageName.contains("spotify", ignoreCase = true) -> "Spotify"
        packageName.contains("apple", ignoreCase = true) -> "Apple Music"
        packageName.contains("youtube", ignoreCase = true) -> "YouTube Music"
        packageName.contains("amazon", ignoreCase = true) -> "Amazon Music"
        else -> "Now playing"
    }

private fun formatMillis(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@PreviewLightDark
@Preview(name = "Music card · playing", widthDp = 360, heightDp = 360)
@Composable
private fun MusicCardPlayingPreview() {
    FemtoTheme {
        MusicCard(
            state =
                MusicCardState.Playing(
                    nowPlaying =
                        NowPlaying(
                            title = "Sunset Lover",
                            artist = "Petit Biscuit",
                            albumArt = null,
                            isPlaying = true,
                            positionMs = 98_000,
                            durationMs = 168_000,
                            packageName = "com.spotify.music",
                        ),
                ),
            onCommand = {},
            onConnect = {},
        )
    }
}

@PreviewLightDark
@Preview(name = "Music card · empty", widthDp = 360, heightDp = 360)
@Composable
private fun MusicCardEmptyPreview() {
    FemtoTheme {
        MusicCard(
            state = MusicCardState.NoActiveSession,
            onCommand = {},
            onConnect = {},
        )
    }
}

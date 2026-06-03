package io.github.seijikohara.femto.ui.home.components

import android.os.SystemClock
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.SkipBack
import com.composables.icons.lucide.SkipForward
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.MusicCardState
import io.github.seijikohara.femto.data.NowPlaying
import io.github.seijikohara.femto.data.sourceLabel
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.TabularFigures
import io.github.seijikohara.femto.ui.theme.sectionLabel
import kotlinx.coroutines.delay

/**
 * Transport commands the dashboard can dispatch to the music session.
 */
internal sealed interface MusicCommand {
    data object PlayPause : MusicCommand

    data object SkipNext : MusicCommand

    data object SkipPrevious : MusicCommand
}

/**
 * Music card. Vertical layout per `docs/design/dashboard-v2-mockup.html`
 * (`.music-card` rules):
 *
 *  1. Album art (140 dp, 14 dp corner)
 *  2. Meta — uppercase source eyebrow (10sp / 0.16em), 20sp title, 14sp artist
 *  3. Progress bar (4 dp) with 11sp position / duration labels
 *  4. Transport row — 64 dp prev / next + 72 dp primary play / pause
 *
 * Empty variants render in the same outer dimensions: `NeedsPermission` is
 * the connect CTA, `NoActiveSession` is the "nothing is playing" copy
 * straight from the mockup (assistant hint included).
 */
@Composable
internal fun MusicCard(
    state: MusicCardState,
    onCommand: (MusicCommand) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier,
    shape = RoundedCornerShape(FemtoDimens.CardCorner),
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
            .padding(FemtoDimens.CardPadding),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.SpaceBetween,
) {
    AlbumArt(nowPlaying = nowPlaying)
    Meta(
        source = sourceLabel(nowPlaying.packageName),
        title = nowPlaying.title,
        artist = nowPlaying.artist,
    )
    Progress(
        positionMs = nowPlaying.positionMs,
        durationMs = nowPlaying.durationMs,
        positionUpdateTimeMs = nowPlaying.positionUpdateTimeMs,
        isPlaying = nowPlaying.isPlaying,
        playbackSpeed = nowPlaying.playbackSpeed,
    )
    TransportRow(isPlaying = nowPlaying.isPlaying, onCommand = onCommand)
}

@Composable
private fun AlbumArt(nowPlaying: NowPlaying) {
    val art = nowPlaying.albumArt
    Box(
        modifier =
            Modifier
                .size(FemtoDimens.MusicArtSize)
                .clip(RoundedCornerShape(FemtoDimens.ArtCorner))
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
                    imageVector = Lucide.Music,
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Lucide.Music,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = source.uppercase(),
            style = MaterialTheme.typography.sectionLabel(10, 0.16f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
    Text(
        text = title,
        style =
            MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.02f).em,
                lineHeight = 23.sp,
            ),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (!artist.isNullOrBlank()) {
        Text(
            text = artist,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 16.sp,
                ),
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
    positionUpdateTimeMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
) {
    // Interpolate the displayed position from the PlaybackState basis while
    // playing, so the bar and elapsed label advance smoothly without waiting for
    // the next session callback. Held at positionMs when paused.
    val livePosition by
        produceState(
            initialValue = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L)),
            positionMs,
            positionUpdateTimeMs,
            isPlaying,
            playbackSpeed,
            durationMs,
        ) {
            val maxMs = durationMs.coerceAtLeast(0L)
            // Hold the reported position when paused, or when the session gives no
            // valid update-time basis (a 0 basis would interpolate from boot and
            // snap the bar to the end).
            if (!isPlaying || positionUpdateTimeMs <= 0L) {
                value = positionMs.coerceIn(0L, maxMs)
                return@produceState
            }
            while (true) {
                val elapsed = SystemClock.elapsedRealtime() - positionUpdateTimeMs
                value = (positionMs + (elapsed * playbackSpeed)).toLong().coerceIn(0L, maxMs)
                delay(500L)
            }
        }
    val fraction =
        if (durationMs > 0L) {
            (livePosition.toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = formatMillis(livePosition),
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = TabularFigures,
                ),
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
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = TabularFigures,
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
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
    val width = if (primary) FemtoDimens.MusicPlayButton else FemtoDimens.MusicTransportButton
    val corner = if (primary) 16.dp else 14.dp
    val container =
        if (primary) MaterialTheme.colorScheme.primary else Color.Transparent
    val content =
        if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier =
            Modifier
                .height(FemtoDimens.MusicTransportButton)
                .width(width)
                .clip(RoundedCornerShape(corner))
                .background(container)
                .clickable(onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(32.dp),
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
            modifier = Modifier.fillMaxSize().padding(FemtoDimens.CardPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Lucide.Music,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Box(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.music_connect_cta),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Box(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.music_connect_hint),
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp),
            )
        }
    }

@Composable
private fun EmptyState() =
    Column(
        modifier = Modifier.fillMaxSize().padding(FemtoDimens.CardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Mockup `.music-card.empty` = `grid-template-rows: 1fr auto auto 1fr`
        // with `align-content: space-between` and `.empty-icon { align-self:
        // end }`. The two flexible tracks seat the icon/title/description
        // cluster at/slightly above the vertical centre, and the icon hugs the
        // title (its row ends flush against the title row). The taller top
        // weight nudges the cluster just above centre.
        Box(modifier = Modifier.weight(1.1f))
        Icon(
            imageVector = Lucide.Music,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(36.dp),
        )
        Box(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.music_nothing_playing),
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Box(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.music_nothing_hint),
            style =
                MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        Box(modifier = Modifier.weight(0.9f))
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

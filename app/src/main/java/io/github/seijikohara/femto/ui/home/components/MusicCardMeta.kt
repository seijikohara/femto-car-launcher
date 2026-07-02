package io.github.seijikohara.femto.ui.home.components

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.composables.icons.lucide.Disc
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.User
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.music.NowPlaying
import io.github.seijikohara.femto.data.music.trackKey
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.cardMeta
import io.github.seijikohara.femto.ui.theme.cardTitle
import io.github.seijikohara.femto.ui.theme.eyebrow
import io.github.seijikohara.femto.ui.theme.singleLineBox
import kotlinx.coroutines.delay

// Grace period to keep the last album art visible while a new track's
// METADATA_KEY_ALBUM_ART is still in flight, before conceding the placeholder.
// Long enough to bridge a typical staged metadata update, short enough that a
// genuinely art-less track shows the placeholder without a noticeable lag.
private const val ART_GRACE_WINDOW_MS = 600L

@Composable
internal fun AlbumArt(
    nowPlaying: NowPlaying,
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Optional tap: on the dashboard card the art is the expand-to-panel
    // affordance (the TransportButton idiom: clickable + explicit content
    // description); in the panel itself the art is inert, so no tap modifier
    // and no misleading action semantics.
    val tapModifier =
        onTap?.let { tap ->
            val label = stringResource(R.string.music_expand_player)
            Modifier
                .clickable(onClick = tap)
                .semantics { contentDescription = label }
        } ?: Modifier
    Box(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.medium)
                .then(tapModifier)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        // Media apps deliver a new track's metadata in stages: the title usually
        // lands before METADATA_KEY_ALBUM_ART. Dropping to the placeholder the
        // instant the bitmap is momentarily absent flashes the "no art" gradient on
        // every track change. Hold the last shown art across that gap and concede
        // the placeholder only once the art stays absent past a short grace window
        // (covering a track that genuinely carries none).
        //
        // The hold keys on track identity, not the ImageBitmap instance, because
        // METADATA_KEY_ALBUM_ART re-wraps a fresh bitmap on every emission — keying
        // on the instance would re-fire the dissolve on each play/pause tick.
        val trackKey = nowPlaying.trackKey
        val hasArt = nowPlaying.albumArt != null
        var shownArt by remember { mutableStateOf(nowPlaying.albumArt) }
        LaunchedEffect(trackKey, hasArt) {
            if (hasArt) {
                shownArt = nowPlaying.albumArt
            } else {
                delay(ART_GRACE_WINDOW_MS)
                shownArt = null
            }
        }
        // The dissolve targets the held art, so it fires only on a real artwork
        // change (track switch, or art conceded after the grace window) — never on
        // the per-emission re-wrap or a transient gap mid-track-change.
        Crossfade(targetState = shownArt, label = "albumArt") { art ->
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
                    FemtoIcon(
                        imageVector = Lucide.Music,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun Meta(
    source: String,
    sourceIcon: ImageBitmap?,
    title: String,
    artist: String?,
    album: String?,
    modifier: Modifier = Modifier,
) {
    // Title, artist and album styles derive from the M3 type roles once and are
    // remembered so a track-change recomposition doesn't reallocate them.
    val typography = MaterialTheme.typography
    val titleStyle = remember(typography) { typography.cardTitle() }
    val secondaryStyle = remember(typography) { typography.cardMeta() }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // The source app's own icon precedes its name; fall back to a generic
            // music glyph when the icon could not be resolved (rare).
            if (sourceIcon != null) {
                Image(
                    bitmap = sourceIcon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)),
                )
            } else {
                FemtoIcon(
                    imageVector = Lucide.Music,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
            }
            Text(
                text = source.uppercase(),
                style = MaterialTheme.typography.eyebrow(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Track changes dissolve the text in step with the album art's
        // Crossfade. Key on the track fields, never the emission, so per-tick
        // re-wraps (position updates) do not re-fire the fade. The source
        // eyebrow stays outside: it only changes when the whole session does.
        Crossfade(
            targetState = Triple(title, artist, album),
            label = "musicMeta",
        ) { (fadedTitle, fadedArtist, fadedAlbum) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Title, then artist + album, each with a leading glyph (track /
                // person / disc) so the lines parse at a glance.
                MetaLine(
                    icon = Lucide.Music,
                    text = fadedTitle,
                    style = titleStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                MetaLine(
                    icon = Lucide.User,
                    // Radio / stream tracks often carry no artist; show an em dash so the
                    // line height stays stable rather than the title jumping down.
                    text = fadedArtist?.takeUnless { it.isBlank() } ?: "—",
                    style = secondaryStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MetaLine(
                    icon = Lucide.Disc,
                    // Album is absent for many radio / stream sessions; show an em dash
                    // rather than substituting another field, matching the artist line.
                    text = fadedAlbum?.takeUnless { it.isBlank() } ?: "—",
                    style = secondaryStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// One metadata line: a leading glyph + the text. The text is clamped to its
// style's lineHeight so a CJK line and a Latin line measure identically — without
// this the vertically centred meta block shifts a few px on script changes between
// tracks (fallback line spacing; see singleLineBox).
@Composable
private fun MetaLine(
    icon: ImageVector,
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
) {
    FemtoIcon(
        imageVector = icon,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(14.dp),
    )
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.singleLineBox(style),
    )
}

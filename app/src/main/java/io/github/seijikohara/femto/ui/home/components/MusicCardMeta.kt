package io.github.seijikohara.femto.ui.home.components

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Disc
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.SkipBack
import com.composables.icons.lucide.SkipForward
import com.composables.icons.lucide.User
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.MusicCommand
import io.github.seijikohara.femto.data.music.NowPlaying
import io.github.seijikohara.femto.data.music.sourceLabel
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.TabularFigures
import io.github.seijikohara.femto.ui.theme.cardCta
import io.github.seijikohara.femto.ui.theme.cardCtaHint
import io.github.seijikohara.femto.ui.theme.cardMeta
import io.github.seijikohara.femto.ui.theme.cardTitle
import io.github.seijikohara.femto.ui.theme.sectionLabel
import kotlinx.coroutines.delay

@Composable
internal fun AlbumArt(
    nowPlaying: NowPlaying,
    modifier: Modifier = Modifier,
) = Box(
    modifier =
        modifier
            .clip(RoundedCornerShape(FemtoDimens.ArtCorner))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    contentAlignment = Alignment.Center,
) {
    // Crossfade keys on the ImageBitmap identity, which is stable across the
    // per-second position recompositions, so the dissolve fires only on a real
    // artwork change (track switch / art arriving) — not on every tick.
    Crossfade(targetState = nowPlaying.albumArt, label = "albumArt") { art ->
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
                Icon(
                    imageVector = Lucide.Music,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
            }
            Text(
                text = source.uppercase(),
                style = MaterialTheme.typography.sectionLabel(10, 0.16f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        MetaLine(
            icon = Lucide.Music,
            text = title,
            style = titleStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        MetaLine(
            icon = Lucide.User,
            // Radio / stream tracks often carry no artist; show an em dash so the
            // line height stays stable rather than the title jumping down.
            text = artist?.takeUnless { it.isBlank() } ?: "—",
            style = secondaryStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MetaLine(
            icon = Lucide.Disc,
            // Album is absent for many radio / stream sessions; show an em dash
            // rather than substituting another field, matching the artist line.
            text = album?.takeUnless { it.isBlank() } ?: "—",
            style = secondaryStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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
    Icon(
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
    )
}

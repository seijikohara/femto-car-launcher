package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Play
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.cardCta
import io.github.seijikohara.femto.ui.theme.cardCtaHint

@Composable
internal fun MusicConnectState(
    onConnect: () -> Unit,
    // Forwarded to the height sample: the album-line toggle and the compact form
    // change the Playing card's height, and the idle card must match it either way.
    showAlbum: Boolean,
    showProgress: Boolean,
) = Surface(
    onClick = onConnect,
    modifier = Modifier.fillMaxWidth(),
    // Transparent so the card's glass (the outer MusicCard's glassChrome) shows
    // through in every state — the same translucency the Playing state has. An
    // opaque colour here would paint over the blurred map behind the card.
    color = Color.Transparent,
) {
    PlayingHeightReserve(showAlbum = showAlbum, showProgress = showProgress) {
        IdleCluster(
            icon = Lucide.Music,
            title = stringResource(R.string.music_connect_cta),
            // Actionable copy the user must read to unlock the card: clear the
            // head-unit glance floor (AGENTS.md#automotive-overrides), matching the
            // equally actionable NoActiveSession Play hint.
            hint = stringResource(R.string.music_connect_hint),
        )
    }
}

/**
 * The "nothing is playing" empty state, with a Play affordance: tapping it
 * dispatches [onPlay] (`HomeAction.PlayDefaultMusic`), which best-effort
 * resumes the last session via a synthetic media key and falls back to
 * launching the user's default music app, so the tap always visibly responds
 * even though the transport command bus itself no-ops with nothing playing
 * (`selectPrimaryController` needs a playing/paused session).
 */
@Composable
internal fun MusicEmptyState(
    onPlay: () -> Unit,
    // Forwarded to the height sample: the album-line toggle and the compact form
    // change the Playing card's height, and the idle card must match it either way.
    showAlbum: Boolean,
    showProgress: Boolean,
) = Surface(
    onClick = onPlay,
    modifier = Modifier.fillMaxWidth(),
    // Transparent so the card stays translucent glass when nothing is playing —
    // this state was a plain Column (no surface) before it became tappable, and
    // an opaque colour regressed it to a solid box over the map.
    color = Color.Transparent,
) {
    PlayingHeightReserve(showAlbum = showAlbum, showProgress = showProgress) {
        IdleCluster(
            // The Play glyph in the primary tint, not a muted Music glyph: the
            // state is clickable, so the glyph itself reads as the affordance.
            icon = Lucide.Play,
            title = stringResource(R.string.music_nothing_playing),
            // Actionable copy the user must read to unlock the affordance: clear
            // the head-unit glance floor (AGENTS.md#automotive-overrides), matching
            // MusicConnectState's hint.
            hint = stringResource(R.string.music_nothing_hint),
        )
    }
}

// The idle states' one cluster — a primary-tinted glyph, a one-line title and a
// two-line hint — centred in the Playing card's reserved height (see
// PlayingHeightReserve) so the card keeps the same size whether or not music is
// playing and the calendar / weather row above stays put. Stacked on a narrow
// card; on a wide one (MusicCardWideWidth, where the Playing card is a single
// row and only as tall as its text column) the glyph sits beside the texts, so
// the cluster stays inside that height instead of pushing the idle card past
// it. Compact padding matches the Playing state's inset.
@Composable
private fun IdleCluster(
    icon: ImageVector,
    title: String,
    hint: String,
) = BoxWithConstraints(
    modifier = Modifier.fillMaxWidth().padding(FemtoDimens.CardPaddingCompact),
    contentAlignment = Alignment.Center,
) {
    val glyph: @Composable () -> Unit = {
        FemtoIcon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(FemtoDimens.HeroIconSize),
        )
    }
    val texts: @Composable (TextAlign) -> Unit = { align ->
        Text(
            text = title,
            style = MaterialTheme.typography.cardCta(),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = align,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(modifier = Modifier.height(IdleTextGap))
        Text(
            text = hint,
            style = MaterialTheme.typography.cardCtaHint(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = align,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = IdleHintMaxWidth),
        )
    }
    if (maxWidth >= MusicCardWideWidth) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(IdleWideGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            glyph()
            Column { texts(TextAlign.Start) }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            glyph()
            Box(modifier = Modifier.height(IdleGlyphGap))
            texts(TextAlign.Center)
        }
    }
}

// The idle cluster's spacing: glyph to title (stacked), title to hint, and glyph
// to texts (side by side); the hint's readable line length.
private val IdleGlyphGap = 8.dp
private val IdleTextGap = 4.dp
private val IdleWideGap = 16.dp
private val IdleHintMaxWidth = 280.dp

/**
 * Size [content] to at least the Playing card's content height, measured from
 * a live sample of the Playing state's height-defining structure
 * ([MusicCardPlayingHeightSample]) rather than a static dp token: the meta block's
 * height moves with the user's font size / weight / spacing settings and the
 * album-line toggle, so no constant can track it (the retired
 * `FemtoDimens.MusicCardMinHeight` drifted the moment the art started
 * following the meta column's height). The sample is measured but never
 * placed, so it is not drawn, not hit-testable, and absent from semantics —
 * the height twin of SpeedOverlay's `WidthReserve` invisible-sample idiom.
 *
 * [content] must emit exactly one layout node; it is measured with the
 * sample's height as its minimum, so a centring arrangement inside it spreads
 * the idle cluster across the reserved space.
 */
@Composable
private fun PlayingHeightReserve(
    showAlbum: Boolean,
    showProgress: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = Layout(
    contents = listOf(content, { MusicCardPlayingHeightSample(showAlbum = showAlbum, showProgress = showProgress) }),
    modifier = modifier,
) { (contentMeasurables, sampleMeasurables), constraints ->
    val sampleHeight =
        sampleMeasurables
            .single()
            .measure(constraints.copy(minWidth = 0, minHeight = 0))
            .height
    val placeable =
        contentMeasurables
            .single()
            .measure(constraints.copy(minHeight = sampleHeight.coerceIn(constraints.minHeight, constraints.maxHeight)))
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}

/**
 * Measured-only mirror of the Playing card's content: the meta block over the
 * transport row inside the shared compact inset — the exact structure
 * PlayingState lays out (the album art never exceeds the meta column's height,
 * so it plays no part in the total). The line contents are irrelevant: every
 * meta line is clamped to its style's lineHeight (see MetaLine's
 * singleLineBox), so empty strings measure the same as real metadata. Shared
 * with the dashboard's card cluster, which measures the full card's height to
 * decide whether the column can afford it. Its semantics are cleared: the
 * sample is never placed, but an unplaced node still reaches the semantics
 * tree, and a second set of transport buttons there would double every
 * TalkBack announcement and node lookup.
 */
@Composable
internal fun MusicCardPlayingHeightSample(
    showAlbum: Boolean,
    showProgress: Boolean,
    modifier: Modifier = Modifier,
) = BoxWithConstraints(
    modifier =
        modifier
            .clearAndSetSemantics {}
            .padding(FemtoDimens.CardPaddingCompact),
) {
    val meta: @Composable (Modifier) -> Unit = { metaModifier ->
        MusicMetaAndProgress(
            source = "",
            sourceIcon = null,
            title = "",
            artist = null,
            album = null,
            positionMs = 0L,
            durationMs = 0L,
            positionUpdateTimeMs = 0L,
            isPlaying = false,
            playbackSpeed = 1f,
            showAlbum = showAlbum,
            showProgress = showProgress,
            modifier = metaModifier,
        )
    }
    // The same fork PlayingState takes at this width (MusicCardWideWidth): the
    // transport beside the metadata on a wide card, under it otherwise.
    if (maxWidth >= MusicCardWideWidth) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            meta(Modifier.weight(1f))
            TransportRow(isPlaying = false, onCommand = {})
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGapCompact),
        ) {
            meta(Modifier)
            TransportRow(isPlaying = false, onCommand = {})
        }
    }
}

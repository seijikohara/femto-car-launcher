package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Play
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.cardCta
import io.github.seijikohara.femto.ui.theme.cardCtaHint
import io.github.seijikohara.femto.ui.theme.cardMeta
import io.github.seijikohara.femto.ui.theme.cardTitle

@Composable
internal fun MusicConnectState(
    onConnect: () -> Unit,
    // Forwarded to the height sample: the album-line toggle changes the Playing
    // card's height, and the idle card must match it either way.
    showAlbum: Boolean,
) = Surface(
    onClick = onConnect,
    modifier = Modifier.fillMaxWidth(),
    // Transparent so the card's glass (the outer MusicCard's glassChrome) shows
    // through in every state — the same translucency the Playing state has. An
    // opaque colour here would paint over the blurred map behind the card.
    color = Color.Transparent,
) {
    PlayingHeightReserve(showAlbum = showAlbum) {
        Column(
            // The reserve above holds the Playing card's content height, and the
            // Column centres the cluster within it, so the card keeps the same
            // size whether or not music is playing — the idle card neither
            // shrinks nor grows and the calendar / weather row above stays put.
            // Compact padding matches the Playing state's inset.
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(FemtoDimens.CardPaddingCompact),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            FemtoIcon(
                imageVector = Lucide.Music,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(FemtoDimens.HeroIconSize),
            )
            Box(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.music_connect_cta),
                style = MaterialTheme.typography.cardCta(),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(modifier = Modifier.height(4.dp))
            Text(
                // Actionable copy the user must read to unlock the card: clear
                // the head-unit glance floor (AGENTS.md#automotive-overrides),
                // matching the equally actionable NoActiveSession Play hint below.
                text = stringResource(R.string.music_connect_hint),
                style = MaterialTheme.typography.cardCtaHint(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 280.dp),
            )
        }
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
    // Forwarded to the height sample: the album-line toggle changes the Playing
    // card's height, and the idle card must match it either way.
    showAlbum: Boolean,
) = Surface(
    onClick = onPlay,
    modifier = Modifier.fillMaxWidth(),
    // Transparent so the card stays translucent glass when nothing is playing —
    // this state was a plain Column (no surface) before it became tappable, and
    // an opaque colour regressed it to a solid box over the map.
    color = Color.Transparent,
) {
    PlayingHeightReserve(showAlbum = showAlbum) {
        Column(
            // The reserve above holds the Playing card's content height, and the
            // Column centres the icon / title / hint cluster within it, so the
            // card keeps the same size whether or not music is playing — the
            // idle card neither shrinks nor grows and the calendar / weather row
            // above stays put. Compact padding matches the Playing state's inset.
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(FemtoDimens.CardPaddingCompact),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            FemtoIcon(
                imageVector = Lucide.Play,
                contentDescription = null,
                // Primary tint (mirroring MusicConnectState's icon), not the old
                // muted 60%-alpha Music glyph: the state is now clickable, so the
                // glyph itself should read as the actionable Play affordance.
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(FemtoDimens.HeroIconSize),
            )
            Box(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.music_nothing_playing),
                style = MaterialTheme.typography.cardCta(),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(modifier = Modifier.height(4.dp))
            Text(
                // Actionable copy the user must read to unlock the affordance:
                // clear the head-unit glance floor (AGENTS.md#automotive-overrides),
                // matching MusicConnectState's hint now that this state is tappable.
                text = stringResource(R.string.music_nothing_hint),
                style = MaterialTheme.typography.cardCtaHint(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 280.dp),
            )
        }
    }
}

/**
 * The track that played last, offered for resuming while no session is live: the Playing card's eyebrow (here "Last
 * played" with the app's name and icon), title and artist lines, and its play button where the transport row's
 * play / pause sits. The button resumes the track ([onResume]); the rest of the card opens the app, as the Playing
 * card does ([onLaunchSource]).
 *
 * Inside the same height reserve as the idle states, so the card keeps the Playing card's size: everything here is a
 * subset of the Playing card's content (three of its meta lines, one button the transport row's height), so the
 * reserve, not the content, sets the height. Meta at the top and the button at the bottom put both where the
 * Playing card has them.
 */
@Composable
internal fun MusicLastPlayedState(
    state: MusicCardState.LastPlayed,
    onResume: (String) -> Unit,
    onLaunchSource: (String) -> Unit,
    // Forwarded to the height sample: the album-line toggle changes the Playing
    // card's height, and this card must match it either way.
    showAlbum: Boolean,
    motionTier: MotionTier,
    // The marquee gate the Playing card's lines take: long lines scroll only
    // while the vehicle is stationary.
    stationary: Boolean,
) = Box(
    // The Playing card's card-wide tap, with the label that names the app.
    modifier =
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.music_open_source, state.appName)) {
                onLaunchSource(state.track.packageName)
            },
) {
    val typography = MaterialTheme.typography
    PlayingHeightReserve(showAlbum = showAlbum) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(FemtoDimens.CardPaddingCompact),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MetaLineGap)) {
                EyebrowLine(
                    source = stringResource(R.string.music_last_played, state.appName),
                    sourceIcon = state.appIcon,
                    onExpand = null,
                )
                MetaLine(
                    text = state.track.title,
                    style = remember(typography) { typography.cardTitle() },
                    color = MaterialTheme.colorScheme.onSurface,
                    tier = motionTier,
                    marquee = stationary,
                )
                MetaLine(
                    // The Playing card's em dash for a track without an artist.
                    text = state.track.artist ?: "—",
                    style = remember(typography) { typography.cardMeta() },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    tier = motionTier,
                    marquee = stationary,
                )
            }
            ResumeButton(
                resuming = state.resuming,
                description = stringResource(R.string.music_resume, state.track.title),
                onClick = { onResume(state.track.packageName) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/**
 * Size [content] to at least the Playing card's content height, measured from
 * a live sample of the Playing state's height-defining structure
 * ([PlayingHeightSample]) rather than a static dp token: the meta block's
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
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = Layout(
    contents = listOf(content, { PlayingHeightSample(showAlbum = showAlbum) }),
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

// Measured-only mirror of the Playing card's content: the meta block over the
// transport row inside the shared compact inset — the exact structure
// PlayingState lays out (the album art never exceeds the meta column's height,
// so it plays no part in the total). The line contents are irrelevant: every
// meta line is clamped to its style's lineHeight (see MetaLine's
// singleLineBox), so empty strings measure the same as real metadata.
@Composable
private fun PlayingHeightSample(showAlbum: Boolean) =
    Column(
        modifier = Modifier.padding(FemtoDimens.CardPaddingCompact),
        verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGapCompact),
    ) {
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
        )
        TransportRow(isPlaying = false, onCommand = {})
    }

package io.github.seijikohara.femto.ui.home.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.ListMusic
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Repeat
import com.composables.icons.lucide.Repeat1
import com.composables.icons.lucide.Shuffle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.data.music.MusicCommand
import io.github.seijikohara.femto.data.music.NowPlaying
import io.github.seijikohara.femto.data.music.QueueEntry
import io.github.seijikohara.femto.data.music.RepeatMode
import io.github.seijikohara.femto.data.music.sourceLabel
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.Motion
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.PreviewTextStress
import io.github.seijikohara.femto.ui.theme.ScrollingText
import io.github.seijikohara.femto.ui.theme.cardCta
import io.github.seijikohara.femto.ui.theme.cardMeta
import io.github.seijikohara.femto.ui.theme.eyebrow
import kotlinx.coroutines.flow.StateFlow

/**
 * Full-screen "Now Playing" panel (issue #231): one large glass sheet floated
 * inside the dock-inset overlay region of the dashboard, so the live map stays
 * visible behind the blur and the dock stays on top and operable — the panel
 * expands "up to the dock", it never covers it. Entered by tapping the music
 * card's album art; left via the collapse button, the system back gesture, or
 * automatically when the session stops playing (the host drops the panel).
 *
 * The whole point over the card is untruncated metadata: parked, the title,
 * artist and album render at display size and scroll end to end
 * ([io.github.seijikohara.femto.ui.theme.ScrollingText]) instead of ellipsizing,
 * so a headline-length title is readable in full. Moving, every line rests as a
 * static ellipsis — the same motion gate the card applies, and the reason the
 * scroll can be endless without becoming ambient movement in the driver's eye.
 */
@Composable
internal fun NowPlayingPanel(
    nowPlaying: NowPlaying,
    onCommand: (MusicCommand) -> Unit,
    onLaunchSource: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
    spectrum: StateFlow<FloatArray?>? = null,
    showAlbum: Boolean = true,
    showArt: Boolean = true,
    motionTier: MotionTier = MotionTier.STANDARD,
    // Below the trip's moving-speed floor the vehicle is parked, so the title /
    // artist / album lines scroll to full length; above it they rest as a static
    // ellipsis, matching MusicCard (see TripState.stationary).
    stationary: Boolean = false,
) {
    BackHandler(onBack = onClose)
    Surface(
        modifier = modifier.glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(FemtoDimens.CardPadding)) {
            val portrait = maxHeight > maxWidth
            // The art scales with the panel but caps at its design size; the
            // fractions keep the controls column dominant in either orientation.
            val artSize = (if (portrait) maxHeight * 0.35f else maxHeight * 0.6f).coerceAtMost(
                FemtoDimens.NowPlayingArtMax,
            )
            // The queue is capped to a fraction of the panel's own height
            // (rather than its parent's leftover scroll space, which is
            // unbounded) so it always reserves a sane, geometry-proportional
            // slice instead of growing to fit however many entries the queue
            // holds — see PlayingNextList / FitWholeRows.
            val queueMaxHeight = maxHeight * FemtoDimens.NowPlayingQueueHeightFraction
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
            ) {
                PanelTopBar(nowPlaying = nowPlaying, onLaunchSource = onLaunchSource, onClose = onClose)
                if (portrait) {
                    if (showArt) {
                        // Centred: the art is the panel's focal element, so it sits
                        // centred above the metadata / seek / queue rows rather than
                        // hugging their shared left gutter.
                        AlbumArt(
                            nowPlaying = nowPlaying,
                            modifier = Modifier.size(artSize).align(Alignment.CenterHorizontally),
                            motionTier = motionTier,
                        )
                    }
                    PanelControls(
                        nowPlaying = nowPlaying,
                        onCommand = onCommand,
                        spectrum = spectrum,
                        // Portrait is tall and narrow: keep the shuffle / repeat
                        // toggles on their own row below the transport controls.
                        inlineToggles = false,
                        showAlbum = showAlbum,
                        portrait = true,
                        stationary = stationary,
                        queueMaxHeight = queueMaxHeight,
                        motionTier = motionTier,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(FemtoDimens.ScreenPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showArt) {
                            AlbumArt(
                                nowPlaying = nowPlaying,
                                modifier = Modifier.size(artSize),
                                motionTier = motionTier,
                            )
                        }
                        PanelControls(
                            nowPlaying = nowPlaying,
                            onCommand = onCommand,
                            spectrum = spectrum,
                            // Landscape is wide but often short (bar-style head
                            // units): fold the shuffle / repeat toggles onto the
                            // transport row so the controls fit the limited height
                            // without the toggles clipping below the fold.
                            inlineToggles = true,
                            showAlbum = showAlbum,
                            portrait = false,
                            stationary = stationary,
                            queueMaxHeight = queueMaxHeight,
                            motionTier = motionTier,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

// Collapse | source eyebrow | spacer | open-in-source. Both buttons are
// >= MinTouchTarget; the eyebrow mirrors the card's (icon + uppercase label).
@Composable
private fun PanelTopBar(
    nowPlaying: NowPlaying,
    onLaunchSource: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = sourceLabel(nowPlaying.packageName)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PanelIconButton(
            icon = Lucide.ChevronDown,
            description = stringResource(R.string.music_collapse_player),
            onClick = onClose,
        )
        if (nowPlaying.sourceIcon != null) {
            Image(
                bitmap = nowPlaying.sourceIcon,
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
            modifier = Modifier.weight(1f),
        )
        PanelIconButton(
            icon = Lucide.ExternalLink,
            description = stringResource(R.string.music_open_source, source),
            onClick = { onLaunchSource(nowPlaying.packageName) },
        )
    }
}

// Everything below the art: metadata, progress, transport, queue.
//
// Portrait stacks this column directly under the art and does NOT scroll: the
// meta / seek / transport stay fixed and the queue takes the leftover height
// (weight), so FitWholeRows clips it to whole rows against a bounded height —
// a scrolling column would instead hand the queue unbounded height, render
// every row, and let the panel's own edge slice the tail mid-row.
//
// Landscape sits this column beside the art and keeps the scroll + centring:
// a short bar-style head unit can be too short even for the fixed controls, so
// the column scrolls and the queue is bounded by an explicit height cap.
@Composable
private fun PanelControls(
    nowPlaying: NowPlaying,
    onCommand: (MusicCommand) -> Unit,
    spectrum: StateFlow<FloatArray?>?,
    inlineToggles: Boolean,
    showAlbum: Boolean,
    portrait: Boolean,
    stationary: Boolean,
    queueMaxHeight: Dp,
    motionTier: MotionTier,
    modifier: Modifier = Modifier,
) = Column(
    modifier = if (portrait) modifier else modifier.verticalScroll(rememberScrollState()),
    verticalArrangement =
        if (portrait) {
            // Hug the art like every row below it; centring in the leftover
            // height (as landscape does) opened a large empty gap under the art
            // for the shorter capability states (fewer rows -> more leftover).
            Arrangement.spacedBy(FemtoDimens.CardSectionGap)
        } else {
            // The art is vertically centred in the Row (see NowPlayingPanel);
            // matching that centring keeps the two visually paired.
            Arrangement.spacedBy(FemtoDimens.CardSectionGap, Alignment.CenterVertically)
        },
) {
    ExpandedMeta(
        nowPlaying = nowPlaying,
        portrait = portrait,
        stationary = stationary,
        motionTier = motionTier,
        showAlbum = showAlbum,
    )
    PlaybackSeekBar(
        positionMs = nowPlaying.positionMs,
        durationMs = nowPlaying.durationMs,
        positionUpdateTimeMs = nowPlaying.positionUpdateTimeMs,
        isPlaying = nowPlaying.isPlaying,
        playbackSpeed = nowPlaying.playbackSpeed,
        canSeek = nowPlaying.canSeek,
        onSeek = { targetMs -> onCommand(MusicCommand.SeekTo(targetMs)) },
    )
    val hasToggles = nowPlaying.canShuffle || nowPlaying.canRepeat
    // The spectrum paints behind the transport strip only, exactly like the
    // card: matchParentSize keeps the Box sized by the controls, and the
    // canvas never consumes input. It is inset horizontally to the seek bar /
    // metadata column's extent rather than spanning the full row: the seek
    // bar and metadata rows are themselves inset by a time label or a leading
    // icon, so an edge-to-edge spectrum read as wider than everything else it
    // sits behind.
    Box(modifier = Modifier.fillMaxWidth()) {
        spectrum?.let {
            SpectrumBackground(
                spectrum = it,
                modifier =
                    Modifier
                        .matchParentSize()
                        .padding(horizontal = FemtoDimens.SpectrumHorizontalInset),
            )
        }
        if (inlineToggles && hasToggles) {
            // Wide-but-short layout: the transport buttons and the shuffle /
            // repeat toggles share one centred row, so the controls fit the
            // limited height instead of the toggles clipping below the fold.
            // Both inner rows wrap their content (no fillMaxWidth), so the two
            // clusters sit side by side rather than each spanning the width.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FemtoDimens.ScreenPadding, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportRow(isPlaying = nowPlaying.isPlaying, onCommand = onCommand)
                TransportToggles(nowPlaying = nowPlaying, onCommand = onCommand)
            }
        } else {
            // This branch also covers landscape with no toggle capability (a
            // fillMaxWidth row there too), so the wider gap is gated on
            // [portrait] specifically — only the portrait panel has the wide
            // flanking-margin problem this widens for; see
            // FemtoDimens.NowPlayingPanelTransportGap. Portrait's fixed-width
            // panel leaves this row much wider than its content, and
            // Arrangement.CenterHorizontally already centres that content
            // regardless of the row's own width, so only widening the cluster
            // itself (not capping the row) tightens the margins while it stays
            // centred.
            TransportRow(
                isPlaying = nowPlaying.isPlaying,
                onCommand = onCommand,
                modifier = Modifier.fillMaxWidth(),
                gap = if (portrait) FemtoDimens.NowPlayingPanelTransportGap else FemtoDimens.MusicTransportGap,
            )
        }
    }
    // inlineToggles is always the complement of portrait (see NowPlayingPanel's
    // two call sites below), so this standalone toggle row only ever renders in
    // portrait — the wider gap always applies here.
    if (!inlineToggles && hasToggles) {
        TransportToggles(
            nowPlaying = nowPlaying,
            onCommand = onCommand,
            modifier = Modifier.fillMaxWidth(),
            gap = FemtoDimens.NowPlayingPanelTransportGap,
        )
    }
    if (nowPlaying.canSkipToQueueItem && nowPlaying.queue.isNotEmpty()) {
        PlayingNextList(
            queue = nowPlaying.queue,
            onSelect = { id -> onCommand(MusicCommand.SkipToQueueItem(id)) },
            // Portrait: absorb the leftover height so FitWholeRows clips against
            // a bounded space (weight is undefined inside the landscape scroll,
            // so there the explicit height cap bounds it instead).
            modifier =
                if (portrait) {
                    Modifier.fillMaxWidth().weight(1f)
                } else {
                    Modifier.fillMaxWidth().heightIn(max = queueMaxHeight)
                },
        )
    }
}

// Secondary transport row: shuffle / repeat, each rendered only when the
// session advertises the capability — hidden, never disabled ghosts. Active
// state reads through the accent tint, matching the primary play button's
// colour hierarchy.
@Composable
private fun TransportToggles(
    nowPlaying: NowPlaying,
    onCommand: (MusicCommand) -> Unit,
    modifier: Modifier = Modifier,
    // Widened at the portrait call site to match TransportRow's own
    // [FemtoDimens.NowPlayingPanelTransportGap] override, so the two stacked
    // centred rows read as one consistently spaced family.
    gap: Dp = FemtoDimens.MusicTransportGap,
) = Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
) {
    if (nowPlaying.canShuffle) {
        PanelIconButton(
            icon = Lucide.Shuffle,
            description = stringResource(R.string.music_shuffle),
            onClick = { onCommand(MusicCommand.ToggleShuffle) },
            tint =
                if (nowPlaying.shuffleOn) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
    if (nowPlaying.canRepeat) {
        PanelIconButton(
            icon = if (nowPlaying.repeatMode == RepeatMode.ONE) Lucide.Repeat1 else Lucide.Repeat,
            description = stringResource(R.string.music_repeat),
            onClick = { onCommand(MusicCommand.CycleRepeat) },
            tint =
                if (nowPlaying.repeatMode != RepeatMode.NONE) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

// Upcoming queue entries (already sliced to the items after the active track
// and capped at QUEUE_UPCOMING_LIMIT by the data layer). The caller bounds the
// list's height (leftover weight in portrait, an explicit cap in the landscape
// scroll); FitWholeRows fills that bound and drops any row that would not fit
// whole, so the queue always ends on a full row instead of a slice cut off by
// the panel edge.
@Composable
private fun PlayingNextList(
    queue: List<QueueEntry>,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(2.dp),
) {
    Text(
        text = stringResource(R.string.music_playing_next).uppercase(),
        style = MaterialTheme.typography.eyebrow(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FitWholeRows(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalGap = 2.dp,
        // The immediate next-up track is worth showing even if the panel is
        // so short the bound would otherwise drop it — mirrors the
        // calendar/weather cards always keeping their first mandatory row.
        mandatoryCount = 1,
    ) {
        queue.forEach { entry ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = FemtoDimens.MinTouchTarget)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onSelect(entry.id) }
                        .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FemtoIcon(
                    imageVector = Lucide.ListMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(FemtoDimens.InlineIconSize),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.cardCta(),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    entry.subtitle?.takeUnless { it.isBlank() }?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.cardMeta(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// Display-size metadata. Parked, every line scrolls to its full length rather
// than truncating; moving, every line rests as a static ellipsis. Cross-fades on
// track change in step with the art, honoring [motionTier] like every other
// content fade (Motion.contentFadeSpec) instead of the plain default Crossfade
// spec.
@Composable
private fun ExpandedMeta(
    nowPlaying: NowPlaying,
    portrait: Boolean,
    stationary: Boolean,
    motionTier: MotionTier,
    modifier: Modifier = Modifier,
    showAlbum: Boolean = true,
) = Motion.ContentCrossfade(
    targetState = Triple(nowPlaying.title, nowPlaying.artist, nowPlaying.album),
    tier = motionTier,
    label = "expandedMeta",
    modifier = modifier,
) { (title, artist, album) ->
    // Flush-left metadata lines, mirroring the small card's iconless Meta (the
    // per-line track / person / disc glyphs were dropped by design).
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ScrollingText(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            scrolling = stationary,
            // At rest, portrait's narrow headline column needs a second line
            // before ellipsizing; landscape has the width for one. Parked, the
            // scroll collapses either to a single line and shows the whole
            // title, so portrait gets one line back when the car pulls away.
            restingMaxLines = if (portrait) 2 else 1,
            modifier = Modifier.fillMaxWidth(),
        )
        PanelMetaLine(
            text = artist?.takeUnless { it.isBlank() } ?: "—",
            color = MaterialTheme.colorScheme.onSurface,
            scrolling = stationary,
        )
        if (showAlbum) {
            PanelMetaLine(
                text = album?.takeUnless { it.isBlank() } ?: "—",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                scrolling = stationary,
            )
        }
    }
}

// One panel metadata line under the title. Artist and album differ only in
// colour, so the shared style lives here rather than at both call sites.
@Composable
private fun PanelMetaLine(
    text: String,
    color: Color,
    scrolling: Boolean,
    modifier: Modifier = Modifier,
) = ScrollingText(
    text = text,
    style = MaterialTheme.typography.titleLarge,
    color = color,
    scrolling = scrolling,
    modifier = modifier.fillMaxWidth(),
)

private fun previewNowPlaying(): NowPlaying =
    NowPlaying(
        title = "An Extraordinarily Long Track Title That The Small Card Truncates",
        artist = "The Wayfinders featuring The Night Route Orchestra",
        album = "Night Routes (Deluxe Extended Edition)",
        albumArt = null,
        isPlaying = true,
        positionMs = 98_000,
        durationMs = 368_000,
        packageName = "com.example.music",
        canSeek = true,
        canShuffle = true,
        canRepeat = true,
        shuffleOn = true,
        canSkipToQueueItem = true,
        queue =
            listOf(
                QueueEntry(1L, "Next Track One", "The Wayfinders"),
                QueueEntry(2L, "Next Track Two", null),
                QueueEntry(3L, "A Third Upcoming Track With A Longer Name", "Night Routes"),
            ),
    )

@PreviewLightDark
@PreviewTextStress
@Preview(name = "Now playing · head unit", widthDp = 805, heightDp = 400)
@Preview(name = "Now playing · portrait", widthDp = 364, heightDp = 700)
@Composable
private fun NowPlayingPanelPreview() {
    FemtoTheme {
        NowPlayingPanel(
            nowPlaying = previewNowPlaying(),
            onCommand = {},
            onLaunchSource = {},
            onClose = {},
        )
    }
}

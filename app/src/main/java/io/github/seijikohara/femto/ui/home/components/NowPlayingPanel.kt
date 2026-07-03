package io.github.seijikohara.femto.ui.home.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Disc
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.ListMusic
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Repeat
import com.composables.icons.lucide.Repeat1
import com.composables.icons.lucide.Shuffle
import com.composables.icons.lucide.User
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.music.MusicCommand
import io.github.seijikohara.femto.data.music.NowPlaying
import io.github.seijikohara.femto.data.music.QueueEntry
import io.github.seijikohara.femto.data.music.RepeatMode
import io.github.seijikohara.femto.data.music.sourceLabel
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.PreviewTextStress
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
 * The whole point over the card is untruncated metadata: every line renders
 * at display size and scrolls (marquee) instead of ellipsizing.
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
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
            ) {
                PanelTopBar(nowPlaying = nowPlaying, onLaunchSource = onLaunchSource, onClose = onClose)
                if (portrait) {
                    if (showArt) {
                        AlbumArt(
                            nowPlaying = nowPlaying,
                            modifier = Modifier.align(Alignment.CenterHorizontally).size(artSize),
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
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(FemtoDimens.ScreenPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showArt) {
                            AlbumArt(nowPlaying = nowPlaying, modifier = Modifier.size(artSize))
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

@Composable
private fun PanelIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) = Box(
    modifier =
        modifier
            .size(FemtoDimens.MinTouchTarget)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
    contentAlignment = Alignment.Center,
) {
    FemtoIcon(
        imageVector = icon,
        contentDescription = null,
        tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint,
        modifier = Modifier.size(28.dp),
    )
}

// Everything below the art: metadata, progress, transport. Scrolls when the
// gated sections (Tasks 6-7) outgrow a short panel. Tasks 5-7 extend this
// column in place.
@Composable
private fun PanelControls(
    nowPlaying: NowPlaying,
    onCommand: (MusicCommand) -> Unit,
    spectrum: StateFlow<FloatArray?>?,
    inlineToggles: Boolean,
    showAlbum: Boolean,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier.verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap, Alignment.CenterVertically),
) {
    ExpandedMeta(nowPlaying = nowPlaying, showAlbum = showAlbum)
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
    // canvas never consumes input.
    Box(modifier = Modifier.fillMaxWidth()) {
        spectrum?.let {
            SpectrumBackground(spectrum = it, modifier = Modifier.matchParentSize())
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
            TransportRow(
                isPlaying = nowPlaying.isPlaying,
                onCommand = onCommand,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (!inlineToggles && hasToggles) {
        TransportToggles(
            nowPlaying = nowPlaying,
            onCommand = onCommand,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (nowPlaying.canSkipToQueueItem && nowPlaying.queue.isNotEmpty()) {
        PlayingNextList(
            queue = nowPlaying.queue,
            onSelect = { id -> onCommand(MusicCommand.SkipToQueueItem(id)) },
            modifier = Modifier.fillMaxWidth(),
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
) = Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
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
// and capped at QUEUE_UPCOMING_LIMIT by the data layer). A plain Column: the
// parent PanelControls scrolls, and nesting a lazy list inside a scrollable
// column is both forbidden and unnecessary at <= 12 rows.
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

// Display-size metadata that never truncates: one line each, marquee on
// overflow (the deliberate inverse of the card's ellipsis + FitText, which
// shrink; here there is room to scroll instead). Cross-fades on track change
// in step with the art.
@Composable
private fun ExpandedMeta(
    nowPlaying: NowPlaying,
    modifier: Modifier = Modifier,
    showAlbum: Boolean = true,
) = Crossfade(
    targetState = Triple(nowPlaying.title, nowPlaying.artist, nowPlaying.album),
    label = "expandedMeta",
    modifier = modifier,
) { (title, artist, album) ->
    // A leading glyph per line (track / person / disc) mirrors the small card's
    // Meta so the two read the same; unlike the card, the text marquees instead
    // of ellipsizing so a long title is shown in full.
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MarqueeMetaLine(
            icon = Lucide.Music,
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            iconSize = 28.dp,
        )
        MarqueeMetaLine(
            icon = Lucide.User,
            text = artist?.takeUnless { it.isBlank() } ?: "—",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            iconSize = FemtoDimens.InlineIconSize,
        )
        if (showAlbum) {
            MarqueeMetaLine(
                icon = Lucide.Disc,
                text = album?.takeUnless { it.isBlank() } ?: "—",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                iconSize = FemtoDimens.InlineIconSize,
            )
        }
    }
}

// One panel metadata line: a leading glyph sized to the line + the marquee text
// filling the rest, so the icon stays put while a long value scrolls beside it.
@Composable
private fun MarqueeMetaLine(
    icon: ImageVector,
    text: String,
    style: TextStyle,
    color: Color,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
) {
    FemtoIcon(
        imageVector = icon,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(iconSize),
    )
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        modifier = Modifier.weight(1f).basicMarquee(),
    )
}

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

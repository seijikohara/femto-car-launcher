package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Disc
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.User
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.data.music.NowPlaying
import io.github.seijikohara.femto.data.music.trackKey
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.Motion
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
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    motionTier: MotionTier = MotionTier.STANDARD,
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
        Motion.ContentCrossfade(targetState = shownArt, tier = motionTier, label = "albumArt") { art ->
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

// Slot indices into MusicMetaAndProgress's Layout children — see the fit
// algorithm below.
private const val EYEBROW_SLOT = 0
private const val TITLE_SLOT = 1
private const val ARTIST_SLOT = 2
private const val ALBUM_SLOT = 3
private const val PROGRESS_SLOT = 4

// Gap between the source eyebrow / title / artist / album lines — the tight
// rhythm the mockup used for this block.
private val MetaLineGap: Dp = 2.dp

/**
 * Title, artist, album, source eyebrow, and the playback progress row, laid
 * out to fit whatever height the music card's row allocates. The title and
 * the progress row are mandatory; the artist, source eyebrow, and album lines
 * are dropped — album first, then the eyebrow, then the artist — once the
 * available height is too tight for all of them. The artist is the most
 * protected of the three; the eyebrow outranks the album because it also
 * carries the tap-to-expand affordance ([onExpand]), so dropping it would cost
 * the card its full-screen-player entry when the album art is hidden.
 *
 * This replaces a fixed-height [Column] (`Arrangement.spacedBy(_, CenterVertically)`
 * over lines clamped to their nominal `lineHeight`): squeezed by an ancestor
 * shorter than the block's natural content — the LARGE display-scale, smallest
 * head-unit combination — that fixed height still let each line's *unbounded*
 * text render at full size while its *box* shrank, so neighbouring lines'
 * glyphs spilled into each other instead of one of them stepping aside. This
 * layout measures every line at its natural (unbounded) height first, then
 * greedily keeps only what actually fits — never a squeeze, never an overlap.
 *
 * The block always reports the *natural* height of whatever it kept — even
 * when the incoming constraints are bounded — rather than stretching to fill
 * a taller allocation. The caller ([PlayingState]) sizes the album art off the
 * available width and lays this block beside it in a top-aligned row while the
 * card wraps its content, so a block that stretched to a taller bound would both
 * reopen the dead band the content-wrapping card removes and drop the art's top
 * below the title instead of level with it.
 */
@Composable
internal fun MusicMetaAndProgress(
    source: String,
    sourceIcon: ImageBitmap?,
    title: String,
    artist: String?,
    album: String?,
    positionMs: Long,
    durationMs: Long,
    positionUpdateTimeMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    modifier: Modifier = Modifier,
    showAlbum: Boolean = true,
    // When non-null, the source eyebrow row becomes a tap-to-expand affordance —
    // the card's entry to the full-screen player, kept reachable even when the
    // album art (the other expand affordance) is hidden. The calendar / weather
    // cards apply the same tap-to-expand idiom to their whole card instead of a
    // single row, since (unlike this one) they have no other clickable children.
    onExpand: (() -> Unit)? = null,
    motionTier: MotionTier = MotionTier.STANDARD,
) {
    // Title, artist and album styles derive from the M3 type roles once and are
    // remembered so a track-change recomposition doesn't reallocate them.
    val typography = MaterialTheme.typography
    val titleStyle = remember(typography) { typography.cardTitle() }
    val secondaryStyle = remember(typography) { typography.cardMeta() }
    val density = LocalDensity.current
    val lineGapPx = with(density) { MetaLineGap.roundToPx() }
    val progressGapPx = with(density) { FemtoDimens.CardSectionGapCompact.roundToPx() }
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            EyebrowLine(source = source, sourceIcon = sourceIcon, onExpand = onExpand)
            // No leading glyph: the eyebrow above already carries the note/source
            // icon, and a second one on the title read as a redundant repeat of the
            // same affordance rather than a new piece of information.
            MetaLine(
                text = title,
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                tier = motionTier,
            )
            MetaLine(
                icon = Lucide.User,
                // Radio / stream tracks often carry no artist; show an em dash so the
                // line height stays stable rather than the title jumping down.
                text = artist?.takeUnless { it.isBlank() } ?: "—",
                style = secondaryStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                tier = motionTier,
            )
            // Composed only when showAlbum is on: the setting hides the album line
            // unconditionally, so its text must never exist in the tree (not merely
            // go unplaced) — a zero-size Spacer keeps ALBUM_SLOT's index stable for
            // the measurables below either way.
            if (showAlbum) {
                MetaLine(
                    icon = Lucide.Disc,
                    // Album is absent for many radio / stream sessions; show an em
                    // dash rather than substituting another field, matching artist.
                    text = album?.takeUnless { it.isBlank() } ?: "—",
                    style = secondaryStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    tier = motionTier,
                )
            } else {
                Spacer(modifier = Modifier.size(0.dp))
            }
            Progress(
                positionMs = positionMs,
                durationMs = durationMs,
                positionUpdateTimeMs = positionUpdateTimeMs,
                isPlaying = isPlaying,
                playbackSpeed = playbackSpeed,
            )
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val placeables = measurables.map { it.measure(loose) }

        fun gapBefore(slot: Int) = if (slot == PROGRESS_SLOT) progressGapPx else lineGapPx

        fun heightOf(slots: List<Int>): Int {
            val sorted = slots.sorted()
            var total = 0
            sorted.forEachIndexed { position, slot ->
                if (position > 0) total += gapBefore(slot)
                total += placeables[slot].height
            }
            return total
        }

        val budget = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE
        val included = mutableListOf(TITLE_SLOT, PROGRESS_SLOT)
        val optionalPriority =
            buildList {
                add(ARTIST_SLOT)
                add(EYEBROW_SLOT)
                if (showAlbum) add(ALBUM_SLOT)
            }
        for (candidate in optionalPriority) {
            if (heightOf(included + candidate) <= budget) {
                included += candidate
            }
        }

        val orderedIncluded = included.sorted()
        val naturalHeight = heightOf(orderedIncluded)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeables.maxOf { it.width }
        layout(width, naturalHeight) {
            var y = 0
            orderedIncluded.forEachIndexed { position, slot ->
                if (position > 0) y += gapBefore(slot)
                placeables[slot].placeRelative(0, y)
                y += placeables[slot].height
            }
        }
    }
}

// The source eyebrow row: the source app's own icon precedes its name; falls
// back to a generic music glyph when the icon could not be resolved (rare).
// The tap-to-expand affordance stays scoped to this row alone — never widened
// to the whole meta block — so it stays distinct from the card-wide
// "open the source app" tap the title / artist / album lines fall through to.
@Composable
private fun EyebrowLine(
    source: String,
    sourceIcon: ImageBitmap?,
    onExpand: (() -> Unit)?,
) {
    val eyebrowTap =
        onExpand?.let { tap ->
            val label = stringResource(R.string.music_expand_player_header)
            Modifier
                .fillMaxWidth()
                .clickable(onClick = tap)
                .semantics { contentDescription = label }
        } ?: Modifier
    Row(
        modifier = eyebrowTap,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
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
}

// One metadata line: an optional leading glyph + the text. The text is clamped to
// its style's lineHeight so a CJK line and a Latin line measure identically —
// without this a line's row height shifts a few px on script changes between
// tracks (fallback line spacing; see singleLineBox). MusicMetaAndProgress always
// measures this against an unbounded height, so the clamp never gets squeezed
// smaller than its nominal size the way it could inside the old fixed-height
// meta column. The text itself dissolves on a track change (Motion.ContentCrossfade
// keyed on its own value, which only changes on a real track switch) rather than
// popping, matching the album art beside it and the full-screen panel's metadata;
// the leading glyph stays static since it never changes within a MetaLine. [icon]
// is absent for the title line — see its call site — so it reads flush-left as
// the block's headline rather than indented level with the icon-led lines below it.
@Composable
private fun MetaLine(
    text: String,
    style: TextStyle,
    color: Color,
    tier: MotionTier,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) = Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
) {
    if (icon != null) {
        FemtoIcon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
    }
    Motion.ContentCrossfade(targetState = text, tier = tier, label = "musicMetaLine") { lineText ->
        Text(
            text = lineText,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.singleLineBox(style),
        )
    }
}

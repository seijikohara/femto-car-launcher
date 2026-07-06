package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Music
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.MusicCommand
import io.github.seijikohara.femto.data.music.NowPlaying
import io.github.seijikohara.femto.data.music.sourceLabel
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.PreviewTextStress
import kotlinx.coroutines.flow.StateFlow

// Gap between the album art and the meta column in the playing-state row.
private val RowContentGap = 14.dp

/**
 * Music card. Vertical layout inherited from the `.music-card` rules of the
 * retired dashboard-v2 design mockup:
 *
 *  1. Album art, up to 140 dp / 14 dp corner — narrower when the row is wide
 *     but short on height, so the meta column beside it keeps a fair share of
 *     the width ([FemtoDimens.MusicMetaMinWidth]).
 *  2. Meta — uppercase source eyebrow (10sp / 0.16em), 20sp title, 14sp artist
 *     / album, plus the progress bar. Title and progress always show; the
 *     eyebrow, artist, and album lines drop in that priority order when the
 *     row is too short for all of them ([MusicMetaAndProgress]).
 *  3. Transport row — 64 dp prev / next + 72 dp primary play / pause
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
    onLaunchSource: (String) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
    // Spectrum band levels for the transport strip's background, or null when
    // the visualization is absent (setting off, previews, tests).
    spectrum: StateFlow<FloatArray?>? = null,
    // Whether to render the album line / album art; both default true. Off yields
    // a metadata-only, minimal card (album line dropped, art block dropped).
    showAlbum: Boolean = true,
    showArt: Boolean = true,
) = Surface(
    modifier = modifier.glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig),
    shape = MaterialTheme.shapes.large,
    color = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurface,
) {
    when (state) {
        MusicCardState.NeedsPermission -> {
            MusicConnectState(onConnect = onConnect)
        }

        MusicCardState.NoActiveSession -> {
            MusicEmptyState()
        }

        is MusicCardState.Playing -> {
            PlayingState(state.nowPlaying, onCommand, onLaunchSource, onExpand, spectrum, showAlbum, showArt)
        }
    }
}

@Composable
private fun PlayingState(
    nowPlaying: NowPlaying,
    onCommand: (MusicCommand) -> Unit,
    onLaunchSource: (String) -> Unit,
    onExpand: () -> Unit,
    spectrum: StateFlow<FloatArray?>?,
    showAlbum: Boolean,
    showArt: Boolean,
) {
    // The whole card (except the transport controls, which consume their own taps)
    // opens the source app. The transport buttons are clickable children, so they
    // intercept their presses before this parent clickable fires.
    val openLabel = stringResource(R.string.music_open_source, sourceLabel(nowPlaying.packageName))
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .clickable(onClickLabel = openLabel) { onLaunchSource(nowPlaying.packageName) }
                // Compact padding/gap so the row + transport strip pack into the
                // card's capped cluster height without starving the meta block
                // below — the same tightening CalendarCard / WeatherCard use.
                .padding(FemtoDimens.CardPaddingCompact),
        verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGapCompact),
    ) {
        // Album art + track info share the top region; the transport row spans the
        // full card width below. On a narrow info-pane card a square album beside the
        // three >= 64 dp controls leaves no room for them, so the controls drop to a
        // full-width strip where they always fit.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // The art is a square sized off the row's HEIGHT (fillMaxHeight +
            // aspectRatio), so on a tall card it can grow to its full design size
            // regardless of how much WIDTH is left over — squeezing the text
            // column below what a track's title / album name needs even though
            // the card has plenty of width overall. Capping the art by whatever
            // width remains after reserving FemtoDimens.MusicMetaMinWidth for the
            // text guarantees that column a fair, growing share instead.
            val artMax =
                if (showArt) {
                    (maxWidth - FemtoDimens.MusicMetaMinWidth - RowContentGap)
                        .coerceIn(0.dp, FemtoDimens.MusicArtSize)
                } else {
                    0.dp
                }
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(RowContentGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Hidden entirely when showArt is off, so the meta column takes
                // the full width.
                if (showArt) {
                    AlbumArt(
                        nowPlaying = nowPlaying,
                        onTap = onExpand,
                        modifier = Modifier.heightIn(max = artMax).fillMaxHeight().aspectRatio(1f),
                    )
                }
                MusicMetaAndProgress(
                    source = sourceLabel(nowPlaying.packageName),
                    sourceIcon = nowPlaying.sourceIcon,
                    title = nowPlaying.title,
                    artist = nowPlaying.artist,
                    album = nowPlaying.album,
                    positionMs = nowPlaying.positionMs,
                    durationMs = nowPlaying.durationMs,
                    positionUpdateTimeMs = nowPlaying.positionUpdateTimeMs,
                    isPlaying = nowPlaying.isPlaying,
                    playbackSpeed = nowPlaying.playbackSpeed,
                    showAlbum = showAlbum,
                    onExpand = onExpand,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        // The spectrum paints behind the transport strip only: matchParentSize
        // keeps the Box sized by the controls, and the buttons (drawn on top)
        // keep their own tap handling — the canvas never consumes input.
        Box(modifier = Modifier.fillMaxWidth()) {
            spectrum?.let {
                SpectrumBackground(spectrum = it, modifier = Modifier.matchParentSize())
            }
            TransportRow(
                isPlaying = nowPlaying.isPlaying,
                onCommand = onCommand,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@PreviewLightDark
@PreviewTextStress
@Preview(name = "Music card · playing", widthDp = 360, heightDp = 200)
@Composable
private fun MusicCardPlayingPreview() {
    FemtoTheme {
        MusicCard(
            state =
                MusicCardState.Playing(
                    nowPlaying =
                        NowPlaying(
                            title = "Midnight Drive",
                            artist = "The Wayfinders",
                            album = "Night Routes",
                            albumArt = null,
                            isPlaying = true,
                            positionMs = 98_000,
                            durationMs = 168_000,
                            packageName = "com.example.music",
                        ),
                ),
            onCommand = {},
            onConnect = {},
            onLaunchSource = {},
            onExpand = {},
        )
    }
}

@PreviewLightDark
@PreviewTextStress
@Preview(name = "Music card · empty", widthDp = 360, heightDp = 360)
@Composable
private fun MusicCardEmptyPreview() {
    FemtoTheme {
        MusicCard(
            state = MusicCardState.NoActiveSession,
            onCommand = {},
            onConnect = {},
            onLaunchSource = {},
            onExpand = {},
        )
    }
}

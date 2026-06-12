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
import kotlinx.coroutines.flow.StateFlow

/**
 * Music card. Vertical layout inherited from the `.music-card` rules of the
 * retired dashboard-v2 design mockup:
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
    onLaunchSource: (String) -> Unit,
    modifier: Modifier = Modifier,
    // Spectrum band levels for the transport strip's background, or null when
    // the visualization is absent (setting off, previews, tests).
    spectrum: StateFlow<FloatArray?>? = null,
) = Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
) {
    when (state) {
        MusicCardState.NeedsPermission -> MusicConnectState(onConnect = onConnect)
        MusicCardState.NoActiveSession -> MusicEmptyState()
        is MusicCardState.Playing -> PlayingState(state.nowPlaying, onCommand, onLaunchSource, spectrum)
    }
}

@Composable
private fun PlayingState(
    nowPlaying: NowPlaying,
    onCommand: (MusicCommand) -> Unit,
    onLaunchSource: (String) -> Unit,
    spectrum: StateFlow<FloatArray?>?,
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
                .padding(FemtoDimens.CardPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Album art + track info share the top region; the transport row spans the
        // full card width below. On a narrow info-pane card a square album beside the
        // three >= 64 dp controls leaves no room for them, so the controls drop to a
        // full-width strip where they always fit.
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumArt(nowPlaying = nowPlaying, modifier = Modifier.fillMaxHeight().aspectRatio(1f))
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Meta(
                    source = sourceLabel(nowPlaying.packageName),
                    sourceIcon = nowPlaying.sourceIcon,
                    title = nowPlaying.title,
                    artist = nowPlaying.artist,
                    album = nowPlaying.album,
                )
                Progress(
                    positionMs = nowPlaying.positionMs,
                    durationMs = nowPlaying.durationMs,
                    positionUpdateTimeMs = nowPlaying.positionUpdateTimeMs,
                    isPlaying = nowPlaying.isPlaying,
                    playbackSpeed = nowPlaying.playbackSpeed,
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
@Preview(name = "Music card · playing", widthDp = 360, heightDp = 200)
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
                            album = "Petit Biscuit",
                            albumArt = null,
                            isPlaying = true,
                            positionMs = 98_000,
                            durationMs = 168_000,
                            packageName = "com.spotify.music",
                        ),
                ),
            onCommand = {},
            onConnect = {},
            onLaunchSource = {},
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
            onLaunchSource = {},
        )
    }
}

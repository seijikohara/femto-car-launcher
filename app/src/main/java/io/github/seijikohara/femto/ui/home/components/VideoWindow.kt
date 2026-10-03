package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Film
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.cardCta
import io.github.seijikohara.femto.ui.theme.cardCtaHint
import io.github.seijikohara.femto.ui.video.VideoAction
import io.github.seijikohara.femto.ui.video.VideoFileState
import io.github.seijikohara.femto.ui.video.VideoUiState

/** The video window's and the video panel's shape: 16:9, the common video frame. */
internal const val VIDEO_ASPECT_RATIO = 16f / 9f

/**
 * The dashboard's small video window (issue #390), a 16:9 glass frame over the
 * map. With no playable file it is one "Pick a video" target, under a line
 * saying why when a picked file cannot be opened or a pick was refused
 * ([VideoNoticeLine]). With one, it
 * shows the picture through [surface] while [pictureVisible] (the motion
 * gate's verdict), and
 * otherwise no frame at all, only a line saying why; the play / pause button
 * stays either way, since the audio does. A tap anywhere else opens the full
 * panel ([onExpand]).
 */
@Composable
internal fun VideoWindow(
    state: VideoUiState,
    pictureVisible: Boolean,
    onAction: (VideoAction) -> Unit,
    onExpand: () -> Unit,
    surface: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
) {
    val expandLabel = stringResource(R.string.video_expand)
    Box(
        modifier =
            modifier
                .aspectRatio(VIDEO_ASPECT_RATIO)
                .glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig)
                .clip(MaterialTheme.shapes.large)
                .then(
                    if (state.fileReady) {
                        Modifier
                            .clickable(onClick = onExpand)
                            .semantics { contentDescription = expandLabel }
                    } else {
                        Modifier.clickable { onAction(VideoAction.PickFile) }
                    },
                ),
    ) {
        if (state.fileReady) {
            VideoPicture(
                pictureVisible = pictureVisible,
                playing = state.playing,
                surface = surface,
                modifier = Modifier.fillMaxSize(),
            )
            VideoPlayButton(
                playing = state.playing,
                onAction = onAction,
                hazeState = hazeState,
                glassConfig = glassConfig,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // One line at most: the window has room for one beside the
                // prompt. A refused pick is the newer news, so it wins.
                val notice =
                    when {
                        state.pickFailed -> R.string.video_pick_failed
                        state.file == VideoFileState.UNAVAILABLE -> R.string.video_file_unavailable
                        else -> null
                    }
                notice?.let {
                    VideoNoticeLine(
                        text = stringResource(it),
                        modifier = Modifier.padding(horizontal = FemtoDimens.CardPadding),
                    )
                }
                PickVideoPrompt()
            }
        }
    }
}

/**
 * A line that says why there is nothing to play: the picked file cannot be
 * opened, or the last pick could not be kept.
 */
@Composable
internal fun VideoNoticeLine(
    text: String,
    modifier: Modifier = Modifier,
) = Text(
    text = text,
    style = MaterialTheme.typography.cardCtaHint(),
    color = MaterialTheme.colorScheme.error,
    textAlign = TextAlign.Center,
    modifier = modifier,
)

/**
 * The picture, or in its place the line that says why it is hidden. A hidden
 * picture composes no [surface] at all, so the player has nowhere to draw: no
 * paused frame and no thumbnail stays behind. The line says the audio keeps
 * playing only while it does ([playing]); a file loads paused, and a hidden
 * picture never pauses it. The line keeps clear of the play / pause button in
 * the bottom corner.
 */
@Composable
internal fun VideoPicture(
    pictureVisible: Boolean,
    playing: Boolean,
    surface: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) = Box(modifier = modifier, contentAlignment = Alignment.Center) {
    if (pictureVisible) {
        surface(Modifier.fillMaxSize())
    } else {
        Text(
            text =
                stringResource(
                    if (playing) R.string.video_picture_hidden_playing else R.string.video_picture_hidden_paused,
                ),
            style = MaterialTheme.typography.cardCtaHint(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.padding(
                    start = FemtoDimens.CardPadding,
                    end = FemtoDimens.MinTouchTarget,
                    top = FemtoDimens.CardPadding,
                    bottom = FemtoDimens.CardPadding,
                ),
        )
    }
}

/**
 * Play / pause, on its own glass chip so it reads over a moving picture. It
 * stays while the picture is hidden: only the picture is gated, never the
 * audio.
 */
@Composable
internal fun VideoPlayButton(
    playing: Boolean,
    onAction: (VideoAction) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
) = PanelIconButton(
    icon = if (playing) Lucide.Pause else Lucide.Play,
    description = stringResource(if (playing) R.string.video_pause else R.string.video_play),
    onClick = { onAction(VideoAction.TogglePlayback) },
    modifier = modifier.glassChrome(PanelIconButtonShape, hazeState, glassConfig),
)

// The window's whole face when there is no playable file: one target, so the
// first step is the obvious one.
@Composable
internal fun PickVideoPrompt(modifier: Modifier = Modifier) =
    Row(
        modifier = modifier.padding(FemtoDimens.CardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
    ) {
        FemtoIcon(
            imageVector = Lucide.Film,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(FemtoDimens.HeroIconSize),
        )
        Text(
            text = stringResource(R.string.video_pick),
            style = MaterialTheme.typography.cardCta(),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

@PreviewLightDark
@Preview(name = "Video window", widthDp = 240, heightDp = 135)
@Composable
private fun VideoWindowHiddenPreview() =
    FemtoTheme {
        VideoWindow(
            state = VideoUiState(windowEnabled = true, file = VideoFileState.READY, playing = true),
            pictureVisible = false,
            onAction = {},
            onExpand = {},
            surface = {},
        )
    }

@PreviewLightDark
@Composable
private fun VideoWindowNoFilePreview() =
    FemtoTheme {
        VideoWindow(
            state = VideoUiState.Off.copy(windowEnabled = true),
            pictureVisible = false,
            onAction = {},
            onExpand = {},
            surface = {},
            modifier = Modifier.size(width = 240.dp, height = 135.dp),
        )
    }

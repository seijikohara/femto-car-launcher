package io.github.seijikohara.femto.ui.home.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.eyebrow
import io.github.seijikohara.femto.ui.video.VideoAction
import io.github.seijikohara.femto.ui.video.VideoFileState
import io.github.seijikohara.femto.ui.video.VideoUiState

/**
 * The video window's full panel (issue #390), in the maximize-panel family:
 * one glass sheet inside the dashboard's dock-inset overlay region, so the
 * dock stays operable. It holds the larger picture, under the same motion
 * gate as the window ([VideoPicture]), with play / pause; its top bar adds
 * picking another file and closing, which turns the window off and stops
 * playback ([VideoAction.Close]); the panel then goes with the window. The
 * back gesture and the collapse button return to the window ([onCollapse]).
 */
@Composable
internal fun VideoPanel(
    state: VideoUiState,
    pictureVisible: Boolean,
    onAction: (VideoAction) -> Unit,
    onCollapse: () -> Unit,
    surface: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
) {
    BackHandler(onBack = onCollapse)
    Surface(
        modifier = modifier.glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(FemtoDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PanelIconButton(
                    icon = Lucide.ChevronDown,
                    description = stringResource(R.string.panel_collapse),
                    onClick = onCollapse,
                )
                Text(
                    text = stringResource(R.string.video_title).uppercase(),
                    style = MaterialTheme.typography.eyebrow(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                PanelIconButton(
                    icon = Lucide.FolderOpen,
                    description = stringResource(R.string.video_pick_another),
                    onClick = { onAction(VideoAction.PickFile) },
                )
                PanelIconButton(
                    icon = Lucide.X,
                    description = stringResource(R.string.video_close),
                    // No collapse here: the window reading off collapses the
                    // panel (DashboardScaffold). Collapsing first would show
                    // the small window again until the off reached the state.
                    onClick = { onAction(VideoAction.Close) },
                )
            }
            if (state.pickFailed) {
                VideoNoticeLine(text = stringResource(R.string.video_pick_failed), modifier = Modifier.fillMaxWidth())
            }
            // The largest 16:9 frame the remaining space holds, centred.
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val frame =
                    Modifier.aspectRatio(
                        VIDEO_ASPECT_RATIO,
                        matchHeightConstraintsFirst = maxWidth / maxHeight > VIDEO_ASPECT_RATIO,
                    )
                if (state.fileReady) {
                    Box(modifier = frame.clip(MaterialTheme.shapes.medium)) {
                        VideoPicture(
                            pictureVisible = pictureVisible,
                            surface = surface,
                            modifier = Modifier.fillMaxSize(),
                        )
                        VideoPlayButton(
                            playing = state.playing,
                            onAction = onAction,
                            hazeState = hazeState,
                            glassConfig = glassConfig,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(FemtoDimens.CardSectionGap),
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (state.file == VideoFileState.UNAVAILABLE) {
                            VideoNoticeLine(text = stringResource(R.string.video_file_unavailable))
                        }
                        PickVideoPrompt(
                            modifier =
                                Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { onAction(VideoAction.PickFile) },
                        )
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Preview(name = "Video panel", widthDp = 800, heightDp = 420)
@Composable
private fun VideoPanelPreview() =
    FemtoTheme {
        VideoPanel(
            state = VideoUiState(windowEnabled = true, file = VideoFileState.READY, playing = true),
            pictureVisible = false,
            onAction = {},
            onCollapse = {},
            surface = {},
            modifier = Modifier.size(width = 800.dp, height = 420.dp),
        )
    }

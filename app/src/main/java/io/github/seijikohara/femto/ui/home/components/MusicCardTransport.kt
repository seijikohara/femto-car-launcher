package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.SkipBack
import com.composables.icons.lucide.SkipForward
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.music.MusicCommand
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.progressCaption

private val PlayButtonShape = RoundedCornerShape(16.dp)
private val PlayIconSize = 40.dp
private val SkipIconSize = 32.dp

/**
 * The music card's elapsed / total row around a flat Material progress line
 * (the Expressive gap and stop dot). Flat on purpose, not the wavy
 * indicator: a rippling line draws the eye on a dashboard the driver only
 * glances at.
 */
@Composable
internal fun Progress(
    positionMs: Long,
    durationMs: Long,
    positionUpdateTimeMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    modifier: Modifier = Modifier,
) {
    val livePosition =
        rememberInterpolatedPositionMs(
            positionMs = positionMs,
            durationMs = durationMs,
            positionUpdateTimeMs = positionUpdateTimeMs,
            isPlaying = isPlaying,
            playbackSpeed = playbackSpeed,
        )
    // The indicator reads the position when it draws, so the interpolation
    // tick redraws the line without handing it a new lambda.
    val progress =
        remember(livePosition, durationMs) {
            {
                if (durationMs > 0L) (livePosition.value.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            }
        }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = formatMillis(livePosition.value),
            style = MaterialTheme.typography.progressCaption(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            text = formatMillis(durationMs),
            style = MaterialTheme.typography.progressCaption(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * Skip previous / play-pause / skip next. Every button stays unfilled so the
 * spectrum background shows across the whole strip; play / pause keeps its
 * hierarchy through the accent tint, the larger glyph and the centre position
 * instead of a filled container, which hid the busiest centre bands.
 *
 * The row measures its width and picks one of three layouts (see
 * [transportGapPx]), so the Material button group never reaches its overflow
 * path:
 * - room for the buttons and two [gap]s: a button group, where a press widens
 *   the pressed button and narrows its neighbours;
 * - room for the buttons only: the same group with the gap shrunk to fit
 *   exactly. The press widening cannot overflow an exact fit: the group
 *   decides overflow from the buttons' fixed intrinsic widths before it
 *   widens one, and the widening only moves width between neighbours;
 * - less: a plain row in which the trailing buttons shrink below
 *   [FemtoDimens.MinTouchTarget]. Only a Display size above the default with
 *   a side rail on a phone in portrait gets here today. The floors are
 *   authored at the default size and AGENTS.md#automotive-overrides sanctions
 *   crossing them only at SMALL, so this is a pre-existing floor break that
 *   the row carries over, not one it introduces.
 */
@Composable
internal fun TransportRow(
    isPlaying: Boolean,
    onCommand: (MusicCommand) -> Unit,
    modifier: Modifier = Modifier,
    // Overridable only for the Now Playing panel's portrait layout, which
    // widens it to tighten the centred row's flanking margins — see
    // [FemtoDimens.NowPlayingPanelTransportGap]. Every other call site (the
    // small card, the panel's landscape row) relies on this default, so it
    // must not change.
    gap: Dp = FemtoDimens.MusicTransportGap,
) = BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
    val previousLabel = stringResource(R.string.music_skip_previous)
    val playPauseLabel = stringResource(R.string.music_play_pause)
    val nextLabel = stringResource(R.string.music_skip_next)
    val onPrevious = { onCommand(MusicCommand.SkipPrevious) }
    val onPlayPause = { onCommand(MusicCommand.PlayPause) }
    val onNext = { onCommand(MusicCommand.SkipNext) }
    val density = LocalDensity.current
    // Pixels, not dp: the group compares its children's rounded pixel widths
    // plus the rounded gap against its own width, so a dp fit could still
    // overflow by a pixel.
    val fittedGapPx =
        with(density) {
            transportGapPx(
                availablePx = constraints.maxWidth,
                buttonsPx = 2 * FemtoDimens.MusicTransportButton.roundToPx() + FemtoDimens.MusicPlayButton.roundToPx(),
                preferredGapPx = gap.roundToPx(),
            )
        }
    if (fittedGapPx != null) {
        ButtonGroup(
            // Unreachable: the gap above always leaves room for every button.
            overflowIndicator = {},
            horizontalArrangement = Arrangement.spacedBy(with(density) { fittedGapPx.toDp() }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            transportItem { interactionSource, itemModifier ->
                SkipButton(Lucide.SkipBack, previousLabel, onPrevious, itemModifier, interactionSource)
            }
            transportItem { interactionSource, itemModifier ->
                PlayPauseButton(isPlaying, playPauseLabel, onPlayPause, itemModifier, interactionSource)
            }
            transportItem { interactionSource, itemModifier ->
                SkipButton(Lucide.SkipForward, nextLabel, onNext, itemModifier, interactionSource)
            }
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkipButton(icon = Lucide.SkipBack, description = previousLabel, onClick = onPrevious)
            PlayPauseButton(isPlaying = isPlaying, description = playPauseLabel, onClick = onPlayPause)
            SkipButton(icon = Lucide.SkipForward, description = nextLabel, onClick = onNext)
        }
    }
}

/**
 * Return the gap that fits the transport buttons ([buttonsPx] together) into
 * [availablePx]: [preferredGapPx] when there is room for it, the largest gap
 * that fits exactly when there is not, or null when even no gap overflows.
 */
internal fun transportGapPx(
    availablePx: Int,
    buttonsPx: Int,
    preferredGapPx: Int,
): Int? =
    when {
        availablePx - buttonsPx >= 2 * preferredGapPx -> preferredGapPx
        availablePx >= buttonsPx -> (availablePx - buttonsPx) / 2
        else -> null
    }

@Composable
private fun SkipButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
) = IconButton(
    onClick = onClick,
    shapes = IconButtonDefaults.shapes(shape = PanelIconButtonShape),
    modifier =
        modifier
            .size(FemtoDimens.MusicTransportButton)
            .semantics { contentDescription = description },
    interactionSource = interactionSource,
) {
    FemtoIcon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(SkipIconSize),
    )
}

// A toggle checked while the track plays, so accessibility services announce
// the playback state; the tap always sends the session's own play / pause
// command rather than the requested value.
@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
) = IconToggleButton(
    checked = isPlaying,
    onCheckedChange = { onClick() },
    shapes = IconButtonDefaults.toggleableShapes(shape = PlayButtonShape, checkedShape = PlayButtonShape),
    modifier =
        modifier
            .size(width = FemtoDimens.MusicPlayButton, height = FemtoDimens.MusicTransportButton)
            .semantics { contentDescription = description },
    colors =
        IconButtonDefaults.iconToggleButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.primary,
        ),
    interactionSource = interactionSource,
) {
    FemtoIcon(
        imageVector = if (isPlaying) Lucide.Pause else Lucide.Play,
        contentDescription = null,
        modifier = Modifier.size(PlayIconSize),
    )
}

// One transport button in the group: [button] receives the interaction source
// the group watches for the press widening, and the modifier that registers
// it. The menu entry is required by the API but unreachable (see TransportRow).
private fun ButtonGroupScope.transportItem(
    button: @Composable (interactionSource: MutableInteractionSource, modifier: Modifier) -> Unit,
) = customItem(
    buttonGroupContent = {
        val interactionSource = remember { MutableInteractionSource() }
        button(interactionSource, Modifier.animateWidth(interactionSource))
    },
    menuContent = {},
)

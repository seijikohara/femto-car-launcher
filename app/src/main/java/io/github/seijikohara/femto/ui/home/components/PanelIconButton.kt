package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon

/**
 * The panel icon button's corner shape, shared with anything that frames one
 * (the video window's glass chip behind its play button).
 */
internal val PanelIconButtonShape = RoundedCornerShape(14.dp)

private val PanelIconSize = 28.dp

/**
 * The shared 64 dp glass-panel top-bar icon button used by every maximize panel
 * (MaximizePanel, NowPlayingPanel, AppsPanel): a [FemtoDimens.MinTouchTarget]
 * transparent icon button wrapping a 28 dp [FemtoIcon]. [tint] defaults to
 * `onSurface`; pass an explicit colour for a state-tinted control.
 *
 * A press morphs [PanelIconButtonShape] to the Material icon button's default
 * pressed shape (the theme's small corner, 8 dp): the same press target every
 * other icon button in the app uses, and a visibly tighter corner on a 64 dp box.
 */
@Composable
internal fun PanelIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) = IconButton(
    onClick = onClick,
    shapes = IconButtonDefaults.shapes(shape = PanelIconButtonShape),
    modifier =
        modifier
            .size(FemtoDimens.MinTouchTarget)
            .semantics { contentDescription = description },
) {
    FemtoIcon(
        imageVector = icon,
        contentDescription = null,
        tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint,
        modifier = Modifier.size(PanelIconSize),
    )
}

/**
 * The toggle form of [PanelIconButton] (shuffle, repeat): the same size, shape
 * and press morph, with the state read through the tint alone — `primary` when
 * [checked], `onSurfaceVariant` otherwise — so the container stays transparent
 * over the glass. [stateDescription] replaces the plain on / off announcement
 * where the checked state has more than one meaning (repeat all / repeat one).
 */
@Composable
internal fun PanelIconToggleButton(
    icon: ImageVector,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    stateDescription: String? = null,
) = IconToggleButton(
    checked = checked,
    onCheckedChange = onCheckedChange,
    shapes =
        IconButtonDefaults.toggleableShapes(
            shape = PanelIconButtonShape,
            checkedShape = PanelIconButtonShape,
        ),
    modifier =
        modifier
            .size(FemtoDimens.MinTouchTarget)
            .semantics {
                contentDescription = description
                stateDescription?.let { this.stateDescription = it }
            },
    colors =
        IconButtonDefaults.iconToggleButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContentColor = MaterialTheme.colorScheme.primary,
        ),
) {
    FemtoIcon(imageVector = icon, contentDescription = null, modifier = Modifier.size(PanelIconSize))
}

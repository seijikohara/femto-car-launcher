package io.github.seijikohara.femto.ui.drawer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pin
import io.github.seijikohara.femto.data.apps.AppEntry
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.tileLabel

private val DefaultIconSize = 64.dp
private val IconLabelGap = 8.dp
private val TilePadding = 8.dp
private val PinBadgeSize = 20.dp
private val PinBadgePadding = 3.dp

/**
 * A single app tile in the launcher grid: icon + label, ≥ 64 dp
 * tap target enforced by [FemtoDimens.MinTouchTarget]. [onLongClick] opens the
 * drawer's pin / unpin menu; [isPinned] overlays a small pin badge on the icon.
 */
@Composable
internal fun AppTile(
    entry: AppEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    iconSize: Dp = DefaultIconSize,
    isPinned: Boolean = false,
) = Column(
    // fillMaxWidth claims the whole grid cell: a wrap-content tile measures to
    // its label, so label width would shift each icon's centre and break the
    // grid's columns. With the cell claimed, every icon centres identically.
    modifier =
        modifier
            .fillMaxWidth()
            .defaultMinSize(minWidth = FemtoDimens.MinTouchTarget, minHeight = FemtoDimens.MinTouchTarget)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(TilePadding),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
) {
    Box {
        Icon(
            painter = BitmapPainter(entry.icon.asImageBitmap()),
            contentDescription = entry.label,
            tint = Color.Unspecified,
            modifier = Modifier.size(iconSize),
        )
        if (isPinned) PinBadge(modifier = Modifier.align(Alignment.TopEnd))
    }
    Spacer(Modifier.height(IconLabelGap))
    // tileLabel's deterministic line box keeps every tile the same height, so
    // the drawer grid stays a regular lattice across scripts and fallbacks.
    Text(
        text = entry.label,
        style = MaterialTheme.typography.tileLabel(),
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PinBadge(modifier: Modifier = Modifier) =
    Icon(
        imageVector = Lucide.Pin,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier =
            modifier
                .size(PinBadgeSize)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                .padding(PinBadgePadding),
    )

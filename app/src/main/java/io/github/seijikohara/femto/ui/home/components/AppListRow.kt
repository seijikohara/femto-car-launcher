@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.AppEntry
import io.github.seijikohara.femto.ui.theme.FemtoDimens

private val ListIconSize = 40.dp

/**
 * A single app row in the drawer's list layout: a small icon beside the label, the
 * whole row a ≥ 64 dp tap target. [onLongClick] opens the pin / unpin menu.
 */
@Composable
internal fun AppListRow(
    entry: AppEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) = Row(
    modifier =
        modifier
            .fillMaxWidth()
            .heightIn(min = FemtoDimens.MinTouchTarget)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = FemtoDimens.ScreenPadding, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
) {
    Icon(
        painter = BitmapPainter(entry.icon.asImageBitmap()),
        contentDescription = entry.label,
        tint = Color.Unspecified,
        modifier = Modifier.size(ListIconSize),
    )
    Text(
        text = entry.label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = FemtoDimens.MinBodyTextSize,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

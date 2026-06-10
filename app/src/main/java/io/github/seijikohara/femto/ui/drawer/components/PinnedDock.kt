@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package io.github.seijikohara.femto.ui.drawer.components

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PinOff
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.AppEntry
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

// Wide enough for a readable one-line 18 sp label while keeping several pins
// visible at once on the 853 dp-wide reference head unit.
private val DockTileWidth = 96.dp
private val DockIconSize = 64.dp
private val DockIconLabelGap = 4.dp
private val DockVerticalPadding = 8.dp

/**
 * Pinned-apps dock fixed at the bottom of the drawer sheet: a horizontally
 * scrolling row of icon + label tiles in pin order, visually separated from the
 * scrolling app grid by a divider on a raised container colour. Tap launches;
 * long-press offers Unpin. Callers skip composing the dock when [apps] is empty.
 */
@Composable
internal fun PinnedDock(
    apps: List<AppEntry>,
    onLaunch: (ComponentName) -> Unit,
    onUnpin: (ComponentName) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier.fillMaxWidth()) {
    HorizontalDivider()
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding =
                PaddingValues(horizontal = FemtoDimens.ScreenPadding, vertical = DockVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(FemtoDimens.GridGutter),
        ) {
            items(items = apps, key = { it.componentName.flattenToString() }) { entry ->
                DockTile(entry = entry, onLaunch = onLaunch, onUnpin = onUnpin)
            }
        }
    }
}

@Composable
private fun DockTile(
    entry: AppEntry,
    onLaunch: (ComponentName) -> Unit,
    onUnpin: (ComponentName) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .width(DockTileWidth)
                    .combinedClickable(
                        onClick = { onLaunch(entry.componentName) },
                        onLongClick = { menuOpen = true },
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = BitmapPainter(entry.icon.asImageBitmap()),
                contentDescription = entry.label,
                tint = Color.Unspecified,
                modifier = Modifier.size(DockIconSize),
            )
            Spacer(Modifier.height(DockIconLabelGap))
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = FemtoDimens.MinBodyTextSize,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.drawer_unpin)) },
                leadingIcon = { Icon(imageVector = Lucide.PinOff, contentDescription = null) },
                onClick = {
                    onUnpin(entry.componentName)
                    menuOpen = false
                },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PinnedDockPreview() {
    val icon = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    FemtoTheme {
        PinnedDock(
            apps =
                listOf(
                    AppEntry(ComponentName("com.maps", ".Main"), "Maps", icon),
                    AppEntry(ComponentName("com.music", ".Main"), "Music", icon),
                    AppEntry(ComponentName("com.phone", ".Main"), "Phone", icon),
                ),
            onLaunch = {},
            onUnpin = {},
        )
    }
}

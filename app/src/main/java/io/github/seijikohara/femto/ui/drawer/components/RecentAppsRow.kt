package io.github.seijikohara.femto.ui.drawer.components

import android.content.ComponentName
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.apps.AppEntry
import io.github.seijikohara.femto.data.apps.DrawerIconSize
import io.github.seijikohara.femto.ui.drawer.DrawerDimensions
import io.github.seijikohara.femto.ui.drawer.dimensions
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.tileLabel

private val RowVerticalPadding = 8.dp
private val IconLabelGap = 4.dp
private val LabelBottomPadding = 4.dp

/**
 * "Recent" section: a horizontal row of the most-recently-launched apps
 * (backed by the drawer's launch-history store), tap to relaunch, long-press
 * for the shared [AppItemMenu]. Mirrors [PinnedDock]'s tile idiom (icon +
 * single-line label, sized via the shared [DrawerIconSize.dimensions] tile
 * metric) but without its drag-reorder affordance — history order is derived,
 * not user-curated. Callers skip composing the row when [apps] is empty (a
 * fresh install, or nothing launched from the drawer yet).
 */
@Composable
internal fun RecentAppsRow(
    apps: List<AppEntry>,
    iconSize: DrawerIconSize,
    pinned: Set<String>,
    hidden: Set<String>,
    onLaunch: (ComponentName) -> Unit,
    onTogglePin: (ComponentName) -> Unit,
    onToggleHide: (ComponentName) -> Unit,
    onOpenAppInfo: (ComponentName) -> Unit,
    onRequestUninstall: (ComponentName) -> Unit,
    modifier: Modifier = Modifier,
    // ScreenPadding when the row stands alone; the drawer passes 0 when it embeds
    // the row inside the app grid, whose contentPadding already insets the row.
    horizontalPadding: Dp = FemtoDimens.ScreenPadding,
) = Column(modifier = modifier.fillMaxWidth()) {
    DrawerSectionHeader(text = stringResource(R.string.drawer_recent_apps), horizontalPadding = horizontalPadding)
    val dimensions = iconSize.dimensions()
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = RowVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(FemtoDimens.GridGutter),
    ) {
        items(items = apps, key = { it.componentName.flattenToString() }) { entry ->
            RecentTile(
                entry = entry,
                dimensions = dimensions,
                isPinned = entry.componentName.flattenToString() in pinned,
                isHidden = entry.componentName.flattenToString() in hidden,
                onLaunch = onLaunch,
                onTogglePin = onTogglePin,
                onToggleHide = onToggleHide,
                onOpenAppInfo = onOpenAppInfo,
                onRequestUninstall = onRequestUninstall,
            )
        }
    }
}

/**
 * Shared section-header recipe for the drawer's Recent and All-apps labels.
 * Uppercased to read as a structural section marker rather than another app
 * label: the grid tile label ([tileLabel]) is also titleSmall-sized SemiBold,
 * so at Title case the two were indistinguishable but for colour — uppercase is
 * the app's established section-eyebrow idiom (the top-bar APPS label, the music
 * source) applied here, untracked per the Bold Minimal type system. titleSmall
 * clears exactly the automotive body-text floor: the drawer is not one of the
 * sanctioned card-relaxation areas in AGENTS.md#automotive-overrides, so this
 * label may not go smaller (hence 16 sp, not the 12 sp eyebrow).
 */
@Composable
internal fun DrawerSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = FemtoDimens.ScreenPadding,
) = Text(
    text = text.uppercase(),
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier =
        modifier.padding(
            horizontal = horizontalPadding,
            vertical = LabelBottomPadding,
        ),
)

@Composable
private fun RecentTile(
    entry: AppEntry,
    dimensions: DrawerDimensions,
    isPinned: Boolean,
    isHidden: Boolean,
    onLaunch: (ComponentName) -> Unit,
    onTogglePin: (ComponentName) -> Unit,
    onToggleHide: (ComponentName) -> Unit,
    onOpenAppInfo: (ComponentName) -> Unit,
    onRequestUninstall: (ComponentName) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .width(dimensions.tileWidth)
                    .defaultMinSize(minHeight = FemtoDimens.MinTouchTarget)
                    // combinedClickable, not clickable: a recent tile is an app
                    // tile like any other, so it carries the same long-press
                    // management menu as the grid, list, and dock surfaces.
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
                modifier = Modifier.size(dimensions.gridIconSize),
            )
            Spacer(Modifier.height(IconLabelGap))
            // Same deterministic line box as the grid / dock tiles (see
            // tileLabel), so every tile row measures identically across
            // scripts and fallbacks.
            Text(
                text = entry.label,
                style = MaterialTheme.typography.tileLabel(),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AppItemMenu(
            entry = entry,
            isPinned = isPinned,
            isHidden = isHidden,
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onTogglePin = onTogglePin,
            onToggleHide = onToggleHide,
            onOpenAppInfo = onOpenAppInfo,
            onRequestUninstall = onRequestUninstall,
        )
    }
}

@PreviewLightDark
@Composable
private fun RecentAppsRowPreview() {
    val icon = createBitmap(1, 1)
    FemtoTheme {
        RecentAppsRow(
            apps =
                listOf(
                    AppEntry(ComponentName("com.maps", ".Main"), "Maps", icon),
                    AppEntry(ComponentName("com.music", ".Main"), "Music", icon),
                    AppEntry(ComponentName("com.phone", ".Main"), "Phone", icon),
                ),
            iconSize = DrawerIconSize.MEDIUM,
            pinned = setOf("com.maps/.Main"),
            hidden = emptySet(),
            onLaunch = {},
            onTogglePin = {},
            onToggleHide = {},
            onOpenAppInfo = {},
            onRequestUninstall = {},
        )
    }
}

package io.github.seijikohara.femto.ui.drawer

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.LayoutList
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pin
import com.composables.icons.lucide.PinOff
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.AppEntry
import io.github.seijikohara.femto.data.DrawerIconSize
import io.github.seijikohara.femto.data.DrawerLayout
import io.github.seijikohara.femto.ui.drawer.components.PinnedDock
import io.github.seijikohara.femto.ui.home.components.AppListRow
import io.github.seijikohara.femto.ui.home.components.AppTile
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

// Per-preset drawer dimensions. MEDIUM matches the pre-preset values: a 120 dp
// minimum tile yields ~5 columns on the 853 dp-wide reference head unit, giving
// each tile room for a 64 dp icon plus its label without crowding. Every preset
// keeps tiles and rows above FemtoDimens.MinTouchTarget.
private data class DrawerDimensions(
    val minTileWidth: Dp,
    val gridIconSize: Dp,
    val listIconSize: Dp,
)

private fun DrawerIconSize.dimensions(): DrawerDimensions =
    when (this) {
        DrawerIconSize.SMALL -> DrawerDimensions(minTileWidth = 96.dp, gridIconSize = 48.dp, listIconSize = 32.dp)
        DrawerIconSize.MEDIUM -> DrawerDimensions(minTileWidth = 120.dp, gridIconSize = 64.dp, listIconSize = 40.dp)
        DrawerIconSize.LARGE -> DrawerDimensions(minTileWidth = 160.dp, gridIconSize = 88.dp, listIconSize = 56.dp)
    }

internal const val APP_DRAWER_PROGRESS_TEST_TAG = "app-drawer-progress"
internal const val APP_DRAWER_SEARCH_TEST_TAG = "app-drawer-search"

@Composable
internal fun AppDrawerScreen(
    uiState: AppDrawerUiState,
    layout: DrawerLayout,
    iconSize: DrawerIconSize,
    pinned: List<String>,
    onLaunch: (ComponentName) -> Unit,
    onTogglePin: (ComponentName) -> Unit,
    onToggleLayout: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
) {
    when (uiState) {
        AppDrawerUiState.Loading -> {
            LoadingState()
        }

        is AppDrawerUiState.Content -> {
            ContentState(
                apps = uiState.apps,
                layout = layout,
                iconSize = iconSize,
                pinned = pinned,
                onLaunch = onLaunch,
                onTogglePin = onTogglePin,
                onToggleLayout = onToggleLayout,
            )
        }

        AppDrawerUiState.Error -> {
            ErrorState(onRetry = onRetry)
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) =
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.testTag(APP_DRAWER_PROGRESS_TEST_TAG))
    }

@Composable
private fun ContentState(
    apps: List<AppEntry>,
    layout: DrawerLayout,
    iconSize: DrawerIconSize,
    pinned: List<String>,
    onLaunch: (ComponentName) -> Unit,
    onTogglePin: (ComponentName) -> Unit,
    onToggleLayout: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier.fillMaxSize()) {
    var query by remember { mutableStateOf("") }
    DrawerTopBar(
        query = query,
        onQueryChange = { query = it },
        layout = layout,
        onToggleLayout = onToggleLayout,
    )
    if (apps.isEmpty()) {
        CenteredMessage(text = stringResource(R.string.drawer_no_apps))
        return@Column
    }
    val pinnedSet = remember(pinned) { pinned.toSet() }
    // Prefix matches rank before substring matches; an empty query shows everything.
    val matched = remember(apps, query) { filterAndRank(apps, query) { it.label } }
    Box(modifier = Modifier.weight(1f)) {
        if (matched.isEmpty()) {
            CenteredMessage(text = stringResource(R.string.drawer_no_matches))
        } else {
            val dimensions = iconSize.dimensions()
            // A query forces the list layout so labels stay readable while searching.
            when (effectiveLayout(layout, query)) {
                DrawerLayout.GRID -> GridApps(matched, pinnedSet, dimensions, onLaunch, onTogglePin)
                DrawerLayout.LIST -> ListApps(matched, pinnedSet, dimensions, onLaunch, onTogglePin)
            }
        }
    }
    // The dock renders pins in pin order, unaffected by the search query, and is
    // skipped entirely when nothing is pinned.
    val dockApps =
        remember(apps, pinned) {
            val byComponent = apps.associateBy { it.componentName.flattenToString() }
            pinned.mapNotNull { byComponent[it] }
        }
    if (dockApps.isNotEmpty()) {
        PinnedDock(
            apps = dockApps,
            onLaunch = onLaunch,
            onUnpin = onTogglePin,
        )
    }
}

// Search field + layout toggle. Filtering by app name is the main usability win on a
// head unit with a long app list; the layout toggle stays at the trailing edge.
@Composable
private fun DrawerTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    layout: DrawerLayout,
    onToggleLayout: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxWidth().padding(horizontal = FemtoDimens.ScreenPadding, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.weight(1f).testTag(APP_DRAWER_SEARCH_TEST_TAG),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = FemtoDimens.MinBodyTextSize),
        placeholder = {
            Text(text = stringResource(R.string.drawer_search_hint), fontSize = FemtoDimens.MinBodyTextSize)
        },
        leadingIcon = { Icon(imageVector = Lucide.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(imageVector = Lucide.X, contentDescription = stringResource(R.string.drawer_search_clear))
                }
            }
        },
        singleLine = true,
    )
    // The icon shows the layout the tap switches TO.
    IconButton(onClick = onToggleLayout, modifier = Modifier.size(FemtoDimens.MinTouchTarget)) {
        Icon(
            imageVector = if (layout == DrawerLayout.GRID) Lucide.LayoutList else Lucide.LayoutGrid,
            contentDescription =
                stringResource(
                    if (layout == DrawerLayout.GRID) R.string.drawer_layout_list else R.string.drawer_layout_grid,
                ),
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun GridApps(
    apps: List<AppEntry>,
    pinnedSet: Set<String>,
    dimensions: DrawerDimensions,
    onLaunch: (ComponentName) -> Unit,
    onTogglePin: (ComponentName) -> Unit,
    modifier: Modifier = Modifier,
) = LazyVerticalGrid(
    modifier = modifier,
    columns = GridCells.Adaptive(minSize = dimensions.minTileWidth),
    contentPadding = PaddingValues(FemtoDimens.ScreenPadding),
    horizontalArrangement = Arrangement.spacedBy(FemtoDimens.GridGutter),
    verticalArrangement = Arrangement.spacedBy(FemtoDimens.GridGutter),
) {
    items(items = apps, key = { it.componentName.flattenToString() }) { entry ->
        DrawerAppItem(
            entry = entry,
            layout = DrawerLayout.GRID,
            dimensions = dimensions,
            isPinned = entry.componentName.flattenToString() in pinnedSet,
            onLaunch = onLaunch,
            onTogglePin = onTogglePin,
        )
    }
}

@Composable
private fun ListApps(
    apps: List<AppEntry>,
    pinnedSet: Set<String>,
    dimensions: DrawerDimensions,
    onLaunch: (ComponentName) -> Unit,
    onTogglePin: (ComponentName) -> Unit,
    modifier: Modifier = Modifier,
) = LazyColumn(modifier = modifier, contentPadding = PaddingValues(vertical = FemtoDimens.GridGutter)) {
    items(items = apps, key = { it.componentName.flattenToString() }) { entry ->
        DrawerAppItem(
            entry = entry,
            layout = DrawerLayout.LIST,
            dimensions = dimensions,
            isPinned = entry.componentName.flattenToString() in pinnedSet,
            onLaunch = onLaunch,
            onTogglePin = onTogglePin,
        )
    }
}

// One app entry (grid tile or list row) wrapping a long-press pin / unpin menu.
@Composable
private fun DrawerAppItem(
    entry: AppEntry,
    layout: DrawerLayout,
    dimensions: DrawerDimensions,
    isPinned: Boolean,
    onLaunch: (ComponentName) -> Unit,
    onTogglePin: (ComponentName) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        when (layout) {
            DrawerLayout.GRID -> {
                AppTile(
                    entry = entry,
                    onClick = { onLaunch(entry.componentName) },
                    onLongClick = { menuOpen = true },
                    iconSize = dimensions.gridIconSize,
                    isPinned = isPinned,
                )
            }

            DrawerLayout.LIST -> {
                AppListRow(
                    entry = entry,
                    onClick = { onLaunch(entry.componentName) },
                    onLongClick = { menuOpen = true },
                    iconSize = dimensions.listIconSize,
                    isPinned = isPinned,
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = {
                    Text(stringResource(if (isPinned) R.string.drawer_unpin else R.string.drawer_pin))
                },
                leadingIcon = {
                    Icon(imageVector = if (isPinned) Lucide.PinOff else Lucide.Pin, contentDescription = null)
                },
                onClick = {
                    onTogglePin(entry.componentName)
                    menuOpen = false
                },
            )
        }
    }
}

@Composable
private fun ErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier.fillMaxSize().padding(FemtoDimens.ScreenPadding),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
) {
    Text(
        text = stringResource(R.string.drawer_load_error),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        fontSize = FemtoDimens.MinBodyTextSize,
    )
    Button(
        onClick = onRetry,
        modifier =
            Modifier
                .padding(top = FemtoDimens.GridGutter)
                .defaultMinSize(
                    minWidth = FemtoDimens.MinTouchTarget,
                    minHeight = FemtoDimens.MinTouchTarget,
                ),
    ) {
        Text(text = stringResource(R.string.drawer_retry), fontSize = FemtoDimens.MinBodyTextSize)
    }
}

@Composable
private fun CenteredMessage(
    text: String,
    modifier: Modifier = Modifier,
) = Box(
    modifier = modifier.fillMaxSize().padding(FemtoDimens.ScreenPadding),
    contentAlignment = Alignment.Center,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        fontSize = FemtoDimens.MinBodyTextSize,
    )
}

@PreviewLightDark
@Composable
private fun AppDrawerContentPreview() {
    val icon = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    FemtoTheme {
        AppDrawerScreen(
            uiState =
                AppDrawerUiState.Content(
                    apps =
                        listOf(
                            AppEntry(ComponentName("com.maps", ".Main"), "Maps", icon),
                            AppEntry(ComponentName("com.music", ".Main"), "Music", icon),
                            AppEntry(ComponentName("com.phone", ".Main"), "Phone", icon),
                        ),
                ),
            layout = DrawerLayout.GRID,
            iconSize = DrawerIconSize.MEDIUM,
            pinned = listOf("com.maps/.Main"),
            onLaunch = {},
            onTogglePin = {},
            onToggleLayout = {},
            onRetry = {},
        )
    }
}

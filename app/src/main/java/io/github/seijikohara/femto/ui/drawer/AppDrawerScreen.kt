package io.github.seijikohara.femto.ui.drawer

import android.content.ComponentName
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.LayoutList
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pin
import com.composables.icons.lucide.PinOff
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import com.composables.icons.lucide.ZoomIn
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.apps.AppEntry
import io.github.seijikohara.femto.data.apps.DrawerIconSize
import io.github.seijikohara.femto.data.apps.DrawerLayout
import io.github.seijikohara.femto.ui.drawer.components.AlphabetIndexRail
import io.github.seijikohara.femto.ui.drawer.components.AppListRow
import io.github.seijikohara.femto.ui.drawer.components.AppTile
import io.github.seijikohara.femto.ui.drawer.components.FloatingLetterIndicator
import io.github.seijikohara.femto.ui.drawer.components.IndexRailWidth
import io.github.seijikohara.femto.ui.drawer.components.PinnedDock
import io.github.seijikohara.femto.ui.drawer.components.RecentAppsRow
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import kotlinx.coroutines.launch

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
internal const val APP_DRAWER_ICON_SIZE_TEST_TAG = "app-drawer-icon-size"

// Fixed footprint for the compact view's loading / error placeholder so the
// wrap-content sheet keeps a graspable height before the app list lands.
private val CompactPlaceholderHeight = 96.dp

@Composable
internal fun AppDrawerScreen(
    uiState: AppDrawerUiState,
    layout: DrawerLayout,
    iconSize: DrawerIconSize,
    pinned: List<String>,
    onLaunch: (ComponentName) -> Unit,
    onTogglePin: (ComponentName) -> Unit,
    onToggleLayout: () -> Unit,
    onSelectIconSize: (DrawerIconSize) -> Unit,
    onReorderPins: (List<String>) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onExpand: () -> Unit = {},
) = Surface(
    // Compact mode wraps its content so the sheet stays a low strip; the full
    // drawer fills the height-bounded box the sheet provides.
    modifier = modifier.then(if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxSize()),
    color = MaterialTheme.colorScheme.background,
) {
    when {
        compact -> {
            CompactContent(
                uiState = uiState,
                iconSize = iconSize,
                pinned = pinned,
                onLaunch = onLaunch,
                onTogglePin = onTogglePin,
                onReorderPins = onReorderPins,
                onExpand = onExpand,
            )
        }

        uiState is AppDrawerUiState.Content -> {
            ContentState(
                apps = uiState.apps,
                layout = layout,
                iconSize = iconSize,
                pinned = pinned,
                recentApps = uiState.recentApps,
                onLaunch = onLaunch,
                onTogglePin = onTogglePin,
                onToggleLayout = onToggleLayout,
                onSelectIconSize = onSelectIconSize,
                onReorderPins = onReorderPins,
            )
        }

        uiState is AppDrawerUiState.Loading -> {
            LoadingState()
        }

        else -> {
            ErrorState(onRetry = onRetry)
        }
    }
}

/**
 * The quick pinned view the dock's apps button opens: the Recent row (when
 * there is any launch history) above the pinned dock, plus an All-apps row
 * that expands to the full drawer. Loading / error fall back to a
 * fixed-height placeholder so the wrap-content sheet never collapses to zero.
 */
@Composable
private fun CompactContent(
    uiState: AppDrawerUiState,
    iconSize: DrawerIconSize,
    pinned: List<String>,
    onLaunch: (ComponentName) -> Unit,
    onTogglePin: (ComponentName) -> Unit,
    onReorderPins: (List<String>) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier.fillMaxWidth()) {
    when (uiState) {
        is AppDrawerUiState.Content -> {
            val dockApps = rememberDockApps(uiState.apps, pinned)
            // Every pin can go stale (its app uninstalled since pinning); an empty
            // quick view is useless, so fall through to the full drawer.
            val currentOnExpand by rememberUpdatedState(onExpand)
            LaunchedEffect(dockApps.isEmpty()) { if (dockApps.isEmpty()) currentOnExpand() }
            if (uiState.recentApps.isNotEmpty()) {
                RecentAppsRow(apps = uiState.recentApps, iconSize = iconSize, onLaunch = onLaunch)
            }
            PinnedDock(
                apps = dockApps,
                iconSize = iconSize,
                onLaunch = onLaunch,
                onUnpin = onTogglePin,
                onReorder = onReorderPins,
            )
        }

        AppDrawerUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxWidth().height(CompactPlaceholderHeight),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(modifier = Modifier.testTag(APP_DRAWER_PROGRESS_TEST_TAG)) }
        }

        AppDrawerUiState.Error -> {
            Box(
                modifier = Modifier.fillMaxWidth().height(CompactPlaceholderHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.drawer_load_error),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = FemtoDimens.MinBodyTextSize,
                )
            }
        }
    }
    AllAppsRow(onExpand = onExpand)
}

// Expand-to-full-drawer affordance pinned under the compact dock.
@Composable
private fun AllAppsRow(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier =
        modifier
            .fillMaxWidth()
            .heightIn(min = FemtoDimens.MinTouchTarget)
            .clickable(onClick = onExpand)
            .padding(horizontal = FemtoDimens.ScreenPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    FemtoIcon(
        imageVector = Lucide.LayoutGrid,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = stringResource(R.string.drawer_all_apps),
        style = MaterialTheme.typography.bodyLarge,
        fontSize = FemtoDimens.MinBodyTextSize,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

// Pin order resolved against the loaded app list; shared by the compact view
// and the full drawer's bottom dock so both render the same set.
@Composable
private fun rememberDockApps(
    apps: List<AppEntry>,
    pinned: List<String>,
): List<AppEntry> = remember(apps, pinned) { resolveByOrder(apps, pinned) { it.componentName.flattenToString() } }

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
    recentApps: List<AppEntry>,
    onLaunch: (ComponentName) -> Unit,
    onTogglePin: (ComponentName) -> Unit,
    onToggleLayout: () -> Unit,
    onSelectIconSize: (DrawerIconSize) -> Unit,
    onReorderPins: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier.fillMaxSize()) {
    var query by remember { mutableStateOf("") }
    DrawerTopBar(
        query = query,
        onQueryChange = { query = it },
        layout = layout,
        iconSize = iconSize,
        onToggleLayout = onToggleLayout,
        onSelectIconSize = onSelectIconSize,
    )
    if (apps.isEmpty()) {
        CenteredMessage(text = stringResource(R.string.drawer_no_apps))
        return@Column
    }
    val pinnedSet = remember(pinned) { pinned.toSet() }
    // Prefix matches rank before substring matches; an empty query shows everything.
    val matched = remember(apps, query) { filterAndRank(apps, query) { it.label } }
    // Both the Recent row and the A-Z index are browsing aids; they step
    // aside the moment a query is active, when the filtered flat list is the
    // primary signal (and a jump-to-letter rail over a relevance-ranked
    // subset would be confusing: the ranking is relevance, not alphabetical).
    val isSearching = query.isNotBlank()
    // Letter -> first-matching-app flat index (see sectionStartIndices):
    // computed once here so the Recent row above and the grid/list + rail
    // below agree on whether the rail is showing, and inset by the same
    // width when it is — see IndexRailWidth.
    val sectionIndex: Map<String, Int> =
        remember(matched, isSearching) {
            if (isSearching) emptyMap() else sectionStartIndices(matched) { it.label }
        }
    // A single bucket (or none) means nothing to jump between.
    val showRail = sectionIndex.size > 1
    val railInset = if (showRail) IndexRailWidth else 0.dp
    if (!isSearching && recentApps.isNotEmpty()) {
        RecentAppsRow(
            apps = recentApps,
            iconSize = iconSize,
            onLaunch = onLaunch,
            modifier = Modifier.padding(end = railInset),
        )
    }
    Box(modifier = Modifier.weight(1f)) {
        if (matched.isEmpty()) {
            CenteredMessage(text = stringResource(R.string.drawer_no_matches))
        } else {
            val dimensions = iconSize.dimensions()
            val letters = remember(sectionIndex) { sectionIndex.keys.toList() }
            val sectionStarts = remember(sectionIndex) { sectionIndex.values.toSet() }
            // A query forces the list layout so labels stay readable while searching.
            val effective = effectiveLayout(layout, query)
            val gridState = rememberLazyGridState()
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            // The app list/grid flows continuously (no per-letter header breaking a
            // row), inset from the rail's width so nothing scrolls under it.
            val contentModifier = Modifier.fillMaxSize().padding(end = railInset)
            when (effective) {
                DrawerLayout.GRID -> {
                    GridApps(
                        matched,
                        pinnedSet,
                        dimensions,
                        onLaunch,
                        onTogglePin,
                        modifier = contentModifier,
                        state = gridState,
                    )
                }

                DrawerLayout.LIST -> {
                    ListApps(
                        matched,
                        pinnedSet,
                        dimensions,
                        sectionStarts,
                        onLaunch,
                        onTogglePin,
                        modifier = contentModifier,
                        state = listState,
                    )
                }
            }
            if (showRail) {
                var activeLetter by remember { mutableStateOf<String?>(null) }
                AlphabetIndexRail(
                    letters = letters,
                    onSelectLetter = { letter ->
                        val index = sectionIndex[letter] ?: return@AlphabetIndexRail
                        scope.launch {
                            when (effective) {
                                DrawerLayout.GRID -> gridState.animateScrollToItem(index)
                                DrawerLayout.LIST -> listState.animateScrollToItem(index)
                            }
                        }
                    },
                    onActiveLetterChange = { activeLetter = it },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
                // Visible only while the rail is actively pressed/dragged (see
                // onActiveLetterChange): the "where am I" feedback a fast-scroll
                // rail needs since it is too narrow to show the letter itself.
                activeLetter?.let { letter ->
                    FloatingLetterIndicator(letter = letter, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
    // The dock renders pins in pin order, unaffected by the search query, and is
    // skipped entirely when nothing is pinned.
    val dockApps = rememberDockApps(apps, pinned)
    if (dockApps.isNotEmpty()) {
        PinnedDock(
            apps = dockApps,
            iconSize = iconSize,
            onLaunch = onLaunch,
            onUnpin = onTogglePin,
            onReorder = onReorderPins,
        )
    }
}

// Search field + layout toggle + icon-size menu. Filtering by app name is the main
// usability win on a head unit with a long app list; the display controls stay at
// the trailing edge.
@Composable
private fun DrawerTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    layout: DrawerLayout,
    iconSize: DrawerIconSize,
    onToggleLayout: () -> Unit,
    onSelectIconSize: (DrawerIconSize) -> Unit,
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
        leadingIcon = { FemtoIcon(imageVector = Lucide.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    FemtoIcon(imageVector = Lucide.X, contentDescription = stringResource(R.string.drawer_search_clear))
                }
            }
        },
        singleLine = true,
    )
    // The icon shows the layout the tap switches TO.
    IconButton(onClick = onToggleLayout, modifier = Modifier.size(FemtoDimens.MinTouchTarget)) {
        FemtoIcon(
            imageVector = if (layout == DrawerLayout.GRID) Lucide.LayoutList else Lucide.LayoutGrid,
            contentDescription =
                stringResource(
                    if (layout == DrawerLayout.GRID) R.string.drawer_layout_list else R.string.drawer_layout_grid,
                ),
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
    IconSizeMenuButton(iconSize = iconSize, onSelectIconSize = onSelectIconSize)
}

// In-sheet icon-size control (moved out of Settings): a menu of the S/M/L tile
// presets, anchored to a zoom glyph beside the layout toggle.
@Composable
private fun IconSizeMenuButton(
    iconSize: DrawerIconSize,
    onSelectIconSize: (DrawerIconSize) -> Unit,
    modifier: Modifier = Modifier,
) = Box(modifier = modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    IconButton(
        onClick = { menuOpen = true },
        modifier = Modifier.size(FemtoDimens.MinTouchTarget).testTag(APP_DRAWER_ICON_SIZE_TEST_TAG),
    ) {
        FemtoIcon(
            imageVector = Lucide.ZoomIn,
            contentDescription = stringResource(R.string.drawer_icon_size),
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        IconSizeOptions.forEach { (size, labelRes) ->
            DropdownMenuItem(
                text = { Text(stringResource(labelRes)) },
                // M3's default menu-item height (48 dp) sits below the automotive floor.
                modifier = Modifier.sizeIn(minHeight = FemtoDimens.MinTouchTarget),
                trailingIcon = {
                    if (size == iconSize) FemtoIcon(imageVector = Lucide.Check, contentDescription = null)
                },
                onClick = {
                    onSelectIconSize(size)
                    menuOpen = false
                },
            )
        }
    }
}

private val IconSizeOptions =
    listOf(
        DrawerIconSize.SMALL to R.string.drawer_icon_size_small,
        DrawerIconSize.MEDIUM to R.string.drawer_icon_size_medium,
        DrawerIconSize.LARGE to R.string.drawer_icon_size_large,
    )

// Continuous, densely-packed grid: apps flow multiple-per-row with no
// per-letter header breaking a row (the sparse-grid fix — a header-per-letter
// forced a new row at every letter, and most letters in a realistic app list
// hold only 1-2 apps). AlphabetIndexRail still lets the user jump straight to
// a letter's first app via sectionStartIndices.
@Composable
private fun GridApps(
    apps: List<AppEntry>,
    pinnedSet: Set<String>,
    dimensions: DrawerDimensions,
    onLaunch: (ComponentName) -> Unit,
    onTogglePin: (ComponentName) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
) = LazyVerticalGrid(
    modifier = modifier,
    state = state,
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

// Dense single-column list: unlike the grid, a letter change costs no extra
// row here (a list is already one item per row), so [InlineLetterMarker]
// decorates the first row of each bucket in [sectionStarts] rather than
// inserting a separate header item.
@Composable
private fun ListApps(
    apps: List<AppEntry>,
    pinnedSet: Set<String>,
    dimensions: DrawerDimensions,
    sectionStarts: Set<Int>,
    onLaunch: (ComponentName) -> Unit,
    onTogglePin: (ComponentName) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
) = LazyColumn(modifier = modifier, state = state, contentPadding = PaddingValues(vertical = FemtoDimens.GridGutter)) {
    itemsIndexed(items = apps, key = { _, entry -> entry.componentName.flattenToString() }) { index, entry ->
        Column {
            if (index in sectionStarts) {
                InlineLetterMarker(letter = sectionKeyOf(entry.label))
            }
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
}

// Compact alphabetical marker ("A", "B", ..., or NON_LETTER_SECTION_KEY) shown
// above the LIST layout's first row of a bucket. Decorates that row rather
// than inserting a separate full-width item — see ListApps.
@Composable
private fun InlineLetterMarker(
    letter: String,
    modifier: Modifier = Modifier,
) = Text(
    text = letter,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = modifier.fillMaxWidth().padding(horizontal = FemtoDimens.ScreenPadding, vertical = 4.dp),
)

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
                // M3's default menu-item height (48 dp) sits below the automotive floor.
                modifier = Modifier.sizeIn(minHeight = FemtoDimens.MinTouchTarget),
                leadingIcon = {
                    FemtoIcon(imageVector = if (isPinned) Lucide.PinOff else Lucide.Pin, contentDescription = null)
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
    val icon = createBitmap(1, 1)
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
            onSelectIconSize = {},
            onReorderPins = {},
            onRetry = {},
        )
    }
}

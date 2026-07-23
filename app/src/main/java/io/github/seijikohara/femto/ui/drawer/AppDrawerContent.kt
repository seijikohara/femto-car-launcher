package io.github.seijikohara.femto.ui.drawer

import android.content.ComponentName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.apps.AppEntry
import io.github.seijikohara.femto.data.apps.DrawerIconSize
import io.github.seijikohara.femto.data.apps.DrawerLayout
import io.github.seijikohara.femto.ui.drawer.components.AlphabetIndexRail
import io.github.seijikohara.femto.ui.drawer.components.AppItemMenu
import io.github.seijikohara.femto.ui.drawer.components.AppListRow
import io.github.seijikohara.femto.ui.drawer.components.AppTile
import io.github.seijikohara.femto.ui.drawer.components.DrawerSectionHeader
import io.github.seijikohara.femto.ui.drawer.components.FloatingLetterIndicator
import io.github.seijikohara.femto.ui.drawer.components.IndexRailWidth
import io.github.seijikohara.femto.ui.drawer.components.PinnedDock
import io.github.seijikohara.femto.ui.drawer.components.RecentAppsRow
import io.github.seijikohara.femto.ui.home.components.FemtoHorizontalDivider
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.drawerBody
import kotlinx.coroutines.launch

// Per-preset drawer dimensions — the one tile-metric SSOT for the grid, the
// Recent row, and the pinned dock (the dock and Recent row previously carried
// their own narrower widths, which broke the sections' shared column lines).
// MEDIUM keeps the pre-preset look: a 120 dp tile yields ~5 columns on the
// 853 dp-wide reference head unit, with room for a 64 dp icon plus its label.
// tileWidth is the exact FixedSize grid cell, not a minimum: fixed cells
// left-pack instead of stretching, so every section's first column starts on
// the same left line at the same pitch. Every preset keeps tiles and rows
// above FemtoDimens.MinTouchTarget.
internal data class DrawerDimensions(
    val tileWidth: Dp,
    val gridIconSize: Dp,
    val listIconSize: Dp,
)

internal fun DrawerIconSize.dimensions(): DrawerDimensions =
    when (this) {
        DrawerIconSize.SMALL -> DrawerDimensions(tileWidth = 96.dp, gridIconSize = 48.dp, listIconSize = 32.dp)
        DrawerIconSize.MEDIUM -> DrawerDimensions(tileWidth = 120.dp, gridIconSize = 64.dp, listIconSize = 40.dp)
        DrawerIconSize.LARGE -> DrawerDimensions(tileWidth = 160.dp, gridIconSize = 88.dp, listIconSize = 56.dp)
    }

internal const val APP_DRAWER_PROGRESS_TEST_TAG = "app-drawer-progress"

/**
 * The app grid the maximize panel fills: Recent row (browsing aid) above the
 * height-bounded all-apps grid/list with its A-Z rail, and the pinned dock
 * pinned to the bottom. Rendered transparently over the panel's glass chrome —
 * no opaque surface of its own — so the map blurs through.
 *
 * [query] is owned by the panel's search bar and flows down: an active query
 * ranks/filters the flat list and steps the Recent row and A-Z rail aside (a
 * jump-to-letter rail over a relevance-ranked subset would be confusing). The
 * caller supplies [layout] and [iconSize] from the persisted drawer
 * preferences; the panel bar owns their toggles.
 */
@Composable
internal fun AppDrawerContent(
    uiState: AppDrawerUiState,
    layout: DrawerLayout,
    iconSize: DrawerIconSize,
    pinned: List<String>,
    query: String,
    onLaunch: (ComponentName) -> Unit,
    onTogglePin: (ComponentName) -> Unit,
    onOpenAppInfo: (ComponentName) -> Unit,
    onRequestUninstall: (ComponentName) -> Unit,
    onReorderPins: (List<String>) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) = when (uiState) {
    is AppDrawerUiState.Content -> {
        ContentState(
            apps = uiState.apps,
            layout = layout,
            iconSize = iconSize,
            pinned = pinned,
            query = query,
            recentApps = uiState.recentApps,
            onLaunch = onLaunch,
            onTogglePin = onTogglePin,
            onOpenAppInfo = onOpenAppInfo,
            onRequestUninstall = onRequestUninstall,
            onReorderPins = onReorderPins,
            modifier = modifier,
        )
    }

    AppDrawerUiState.Loading -> {
        LoadingState(modifier = modifier)
    }

    AppDrawerUiState.Error -> {
        ErrorState(onRetry = onRetry, modifier = modifier)
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
    query: String,
    recentApps: List<AppEntry>,
    onLaunch: (ComponentName) -> Unit,
    onTogglePin: (ComponentName) -> Unit,
    onOpenAppInfo: (ComponentName) -> Unit,
    onRequestUninstall: (ComponentName) -> Unit,
    onReorderPins: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier.fillMaxSize()) {
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
    val showRecent = !isSearching && recentApps.isNotEmpty()
    // Recent row + "All apps" header no longer sit fixed above the grid — they
    // ride along as the app list's leading item (built below), so the whole
    // region scrolls as one and the all-apps grid reclaims their height once
    // scrolled up, instead of being boxed into the sliver left beneath them.
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
            // The grid already insets a full-span item by its own ScreenPadding
            // contentPadding, so the embedded leading block drops its horizontal
            // padding there to stay column-aligned with the tiles; the list carries
            // no horizontal contentPadding, so the block keeps ScreenPadding to
            // match the rows. Absent while searching (Recent and the header both
            // step aside for the flat results).
            val leadingPadding = if (effective == DrawerLayout.GRID) 0.dp else FemtoDimens.ScreenPadding
            val leading: (@Composable () -> Unit)? =
                if (isSearching) {
                    null
                } else {
                    {
                        DrawerLeadingSections(
                            showRecent = showRecent,
                            recentApps = recentApps,
                            iconSize = iconSize,
                            pinnedSet = pinnedSet,
                            onLaunch = onLaunch,
                            onTogglePin = onTogglePin,
                            onOpenAppInfo = onOpenAppInfo,
                            onRequestUninstall = onRequestUninstall,
                            horizontalPadding = leadingPadding,
                        )
                    }
                }
            // The leading block occupies one lazy slot ahead of the apps, so an
            // A-Z jump into the flat app index steps past it.
            val leadingOffset = if (leading != null) 1 else 0
            when (effective) {
                DrawerLayout.GRID -> {
                    GridApps(
                        matched,
                        pinnedSet,
                        dimensions,
                        onLaunch,
                        onTogglePin,
                        onOpenAppInfo,
                        onRequestUninstall,
                        modifier = contentModifier,
                        state = gridState,
                        leading = leading,
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
                        onOpenAppInfo,
                        onRequestUninstall,
                        modifier = contentModifier,
                        state = listState,
                        leading = leading,
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
                                DrawerLayout.GRID -> gridState.animateScrollToItem(index + leadingOffset)
                                DrawerLayout.LIST -> listState.animateScrollToItem(index + leadingOffset)
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
            onOpenAppInfo = onOpenAppInfo,
            onRequestUninstall = onRequestUninstall,
            onReorder = onReorderPins,
        )
    }
}

// Pin order resolved against the loaded app list; shared by the recent row's
// resolution idiom and the full drawer's bottom dock so both render the same set.
@Composable
private fun rememberDockApps(
    apps: List<AppEntry>,
    pinned: List<String>,
): List<AppEntry> = remember(apps, pinned) { resolveByOrder(apps, pinned) { it.componentName.flattenToString() } }

// The drawer's leading block: the Recent row (a browsing aid) over a seam and
// the "All apps" header. Emitted as the app list's first scrolling item (see
// GridApps / ListApps) rather than pinned above it, so it scrolls away with the
// list and the all-apps grid is no longer confined to the height left beneath a
// fixed header. The seam is skipped when Recent is absent (nothing to separate).
// [horizontalPadding] aligns the block with the app column under each layout's
// differing content inset — see the call site.
@Composable
private fun DrawerLeadingSections(
    showRecent: Boolean,
    recentApps: List<AppEntry>,
    iconSize: DrawerIconSize,
    pinnedSet: Set<String>,
    onLaunch: (ComponentName) -> Unit,
    onTogglePin: (ComponentName) -> Unit,
    onOpenAppInfo: (ComponentName) -> Unit,
    onRequestUninstall: (ComponentName) -> Unit,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier.fillMaxWidth()) {
    if (showRecent) {
        RecentAppsRow(
            apps = recentApps,
            iconSize = iconSize,
            pinned = pinnedSet,
            onLaunch = onLaunch,
            onTogglePin = onTogglePin,
            onOpenAppInfo = onOpenAppInfo,
            onRequestUninstall = onRequestUninstall,
            horizontalPadding = horizontalPadding,
        )
        FemtoHorizontalDivider()
    }
    DrawerSectionHeader(
        text = stringResource(R.string.drawer_all_apps),
        horizontalPadding = horizontalPadding,
    )
}

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
    onOpenAppInfo: (ComponentName) -> Unit,
    onRequestUninstall: (ComponentName) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    // Full-span leading block (Recent + header) that scrolls with the grid; null
    // while searching. Rendered as the first item so the grid reclaims its height
    // on scroll — see DrawerLeadingSections.
    leading: (@Composable () -> Unit)? = null,
) = LazyVerticalGrid(
    modifier = modifier,
    state = state,
    columns = GridCells.FixedSize(dimensions.tileWidth),
    contentPadding = PaddingValues(FemtoDimens.ScreenPadding),
    horizontalArrangement = Arrangement.spacedBy(FemtoDimens.GridGutter),
    verticalArrangement = Arrangement.spacedBy(FemtoDimens.GridGutter),
) {
    if (leading != null) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "drawer-leading", contentType = "leading") { leading() }
    }
    items(items = apps, key = { it.componentName.flattenToString() }) { entry ->
        DrawerAppItem(
            entry = entry,
            layout = DrawerLayout.GRID,
            dimensions = dimensions,
            isPinned = entry.componentName.flattenToString() in pinnedSet,
            onLaunch = onLaunch,
            onTogglePin = onTogglePin,
            onOpenAppInfo = onOpenAppInfo,
            onRequestUninstall = onRequestUninstall,
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
    onOpenAppInfo: (ComponentName) -> Unit,
    onRequestUninstall: (ComponentName) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    // Leading block (Recent + header) that scrolls with the list; null while
    // searching. See DrawerLeadingSections.
    leading: (@Composable () -> Unit)? = null,
) = LazyColumn(modifier = modifier, state = state, contentPadding = PaddingValues(vertical = FemtoDimens.GridGutter)) {
    if (leading != null) {
        item(key = "drawer-leading", contentType = "leading") { leading() }
    }
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
                onOpenAppInfo = onOpenAppInfo,
                onRequestUninstall = onRequestUninstall,
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

// One app entry (grid tile or list row) wrapping the shared long-press menu.
@Composable
private fun DrawerAppItem(
    entry: AppEntry,
    layout: DrawerLayout,
    dimensions: DrawerDimensions,
    isPinned: Boolean,
    onLaunch: (ComponentName) -> Unit,
    onTogglePin: (ComponentName) -> Unit,
    onOpenAppInfo: (ComponentName) -> Unit,
    onRequestUninstall: (ComponentName) -> Unit,
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
        AppItemMenu(
            entry = entry,
            isPinned = isPinned,
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onTogglePin = onTogglePin,
            onOpenAppInfo = onOpenAppInfo,
            onRequestUninstall = onRequestUninstall,
        )
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
        style = MaterialTheme.typography.drawerBody(),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
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
        // Button content defaults to labelLarge, not bodyLarge — already exactly
        // at the 18sp floor in this type scale, so this is not a drawerBody()
        // site despite the visual similarity to its siblings above (verified:
        // the previous fontSize override here was a no-op against that default).
        Text(text = stringResource(R.string.drawer_retry), style = MaterialTheme.typography.labelLarge)
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
        style = MaterialTheme.typography.drawerBody(),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
}

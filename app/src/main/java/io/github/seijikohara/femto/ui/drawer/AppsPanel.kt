package io.github.seijikohara.femto.ui.drawer

import android.content.ComponentName
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.LayoutList
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.apps.AppEntry
import io.github.seijikohara.femto.data.apps.DrawerIconSize
import io.github.seijikohara.femto.data.apps.DrawerLayout
import io.github.seijikohara.femto.data.apps.DrawerPreferences
import io.github.seijikohara.femto.ui.home.components.FemtoHorizontalDivider
import io.github.seijikohara.femto.ui.home.components.GlassConfig
import io.github.seijikohara.femto.ui.home.components.PanelIconButton
import io.github.seijikohara.femto.ui.home.components.glassChrome
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.drawerBody
import io.github.seijikohara.femto.ui.theme.eyebrow
import kotlinx.coroutines.launch

internal const val APP_DRAWER_SEARCH_TEST_TAG = "app-drawer-search"
internal const val APP_DRAWER_ICON_SIZE_TEST_TAG = "app-drawer-icon-size"

/**
 * The app launcher as a maximize panel, mirroring the calendar / weather panels:
 * one glass sheet floated inside the dashboard's dock-inset overlay region (the
 * map blurs through, the dock stays operable). Replaces the former app-drawer
 * ModalBottomSheet and its compact "quick pinned view" — the panel shows the
 * Recent row, the full height-bounded all-apps grid, and the pinned dock all at
 * once, so the all-apps list is never demoted to a single line behind the pinned
 * and recent sections (issue #273).
 *
 * Unlike the shared [io.github.seijikohara.femto.ui.home.components.MaximizePanel],
 * whose top bar ends in one "open external app" button, this panel's bar is
 * app-native: collapse, the APPS eyebrow, a search toggle, and a display-options
 * overflow (grid/list layout + icon size). Search state is owned here and flows
 * down to [AppDrawerContent]; the system back gesture collapses via [onClose].
 *
 * Pure UI — the caller ([AppDrawerPanelHost]) owns the view-model and preferences
 * so this composable stays previewable and golden-testable.
 */
@Composable
internal fun AppsPanel(
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
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
) {
    BackHandler(onBack = onClose)
    // Reset per open: the panel leaves composition on collapse, so a plain
    // remember starts each open with search closed and an empty query.
    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
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
            AppsTopBar(
                query = query,
                onQueryChange = { query = it },
                searchActive = searchActive,
                onSearchActiveChange = { active ->
                    searchActive = active
                    if (!active) query = ""
                },
                layout = layout,
                onToggleLayout = onToggleLayout,
                iconSize = iconSize,
                onSelectIconSize = onSelectIconSize,
                onClose = onClose,
            )
            AppDrawerContent(
                uiState = uiState,
                layout = layout,
                iconSize = iconSize,
                pinned = pinned,
                query = query,
                onLaunch = onLaunch,
                onTogglePin = onTogglePin,
                onReorderPins = onReorderPins,
                onRetry = onRetry,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

// Collapse | (APPS eyebrow + search toggle + display options) OR (search field +
// close). Search folds the title and display controls away — the layout toggle
// is moot while a query forces the list view (see effectiveLayout).
@Composable
private fun AppsTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    searchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    layout: DrawerLayout,
    onToggleLayout: () -> Unit,
    iconSize: DrawerIconSize,
    onSelectIconSize: (DrawerIconSize) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
) {
    PanelIconButton(
        icon = Lucide.ChevronDown,
        description = stringResource(R.string.panel_collapse),
        onClick = onClose,
    )
    if (searchActive) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).testTag(APP_DRAWER_SEARCH_TEST_TAG),
            textStyle = MaterialTheme.typography.drawerBody(),
            placeholder = {
                Text(text = stringResource(R.string.drawer_search_hint), style = MaterialTheme.typography.drawerBody())
            },
            leadingIcon = { FemtoIcon(imageVector = Lucide.Search, contentDescription = null) },
            singleLine = true,
        )
        PanelIconButton(
            icon = Lucide.X,
            description = stringResource(R.string.drawer_search_close),
            onClick = { onSearchActiveChange(false) },
        )
    } else {
        Text(
            text = stringResource(R.string.nav_apps).uppercase(),
            style = MaterialTheme.typography.eyebrow(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        PanelIconButton(
            icon = Lucide.Search,
            description = stringResource(R.string.drawer_search),
            onClick = { onSearchActiveChange(true) },
        )
        DisplayOptionsButton(
            layout = layout,
            onToggleLayout = onToggleLayout,
            iconSize = iconSize,
            onSelectIconSize = onSelectIconSize,
        )
    }
}

// The display-options overflow: the grid/list layout toggle plus the S/M/L icon
// size presets, gathered behind one bar button so the top bar stays to three
// controls in portrait.
@Composable
private fun DisplayOptionsButton(
    layout: DrawerLayout,
    onToggleLayout: () -> Unit,
    iconSize: DrawerIconSize,
    onSelectIconSize: (DrawerIconSize) -> Unit,
    modifier: Modifier = Modifier,
) = Box(modifier = modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    PanelIconButton(
        icon = Lucide.EllipsisVertical,
        description = stringResource(R.string.drawer_display_options),
        onClick = { menuOpen = true },
        modifier = Modifier.testTag(APP_DRAWER_ICON_SIZE_TEST_TAG),
    )
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
            // The icon and label show the layout the tap switches TO.
            text = {
                Text(
                    stringResource(
                        if (layout == DrawerLayout.GRID) R.string.drawer_layout_list else R.string.drawer_layout_grid,
                    ),
                )
            },
            modifier = Modifier.sizeIn(minHeight = FemtoDimens.MinTouchTarget),
            leadingIcon = {
                FemtoIcon(
                    imageVector = if (layout == DrawerLayout.GRID) Lucide.LayoutList else Lucide.LayoutGrid,
                    contentDescription = null,
                )
            },
            onClick = {
                onToggleLayout()
                menuOpen = false
            },
        )
        FemtoHorizontalDivider()
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

/**
 * Route wrapper for [AppsPanel]: obtains the activity-scoped [AppDrawerViewModel]
 * (self-contained — it launches apps and records the launch in the recent
 * history) and the persisted [DrawerPreferences], and wires their writes.
 *
 * Dispatched from [DashboardOverlays] inside the maximize-panel AnimatedVisibility.
 * A [AppDrawerAction.Refresh] runs on every open because the view-model outlives
 * collapse, so installs/uninstalls since the last open appear. A launch collapses
 * the panel via [onClose] so returning to the launcher shows the dashboard.
 */
@Composable
internal fun AppDrawerPanelHost(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
) {
    val context = LocalContext.current
    val viewModel: AppDrawerViewModel = viewModel(factory = AppDrawerViewModelFactory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onAction(AppDrawerAction.Refresh) }

    val drawerPreferences = remember { DrawerPreferences(context) }
    val layout by drawerPreferences.layout.collectAsStateWithLifecycle(initialValue = DrawerLayout.GRID)
    val iconSize by drawerPreferences.iconSize.collectAsStateWithLifecycle(initialValue = DrawerIconSize.MEDIUM)
    // The compact-vs-full decision that once needed a null "not loaded yet" sentinel
    // is gone with the compact view, so the empty-list default is safe: the pinned
    // dock simply stays hidden until the persisted pins land, then appears.
    val pinned by drawerPreferences.pinned.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    AppsPanel(
        uiState = uiState,
        layout = layout,
        iconSize = iconSize,
        pinned = pinned,
        onLaunch = { component ->
            viewModel.onAction(AppDrawerAction.Launch(component))
            onClose()
        },
        onTogglePin = { component ->
            scope.launch { drawerPreferences.togglePinned(component.flattenToString()) }
        },
        onToggleLayout = {
            scope.launch {
                drawerPreferences.setLayout(
                    if (layout == DrawerLayout.GRID) DrawerLayout.LIST else DrawerLayout.GRID,
                )
            }
        },
        onSelectIconSize = { size -> scope.launch { drawerPreferences.setIconSize(size) } },
        onReorderPins = { order -> scope.launch { drawerPreferences.setPinnedOrder(order) } },
        onRetry = { viewModel.onAction(AppDrawerAction.Refresh) },
        onClose = onClose,
        modifier = modifier,
        hazeState = hazeState,
        glassConfig = glassConfig,
    )
}

@PreviewLightDark
@Composable
private fun AppsPanelPreview() {
    val icon = createBitmap(1, 1)
    // Alphabetical, matching AppsRepository.queryApps — the A-Z rail depends on it.
    val apps =
        listOf("Maps", "Music", "Phone", "Messages", "Calendar", "Weather", "Camera", "Settings")
            .sorted()
            .map { AppEntry(ComponentName("com.example.${it.lowercase()}", ".Main"), it, icon) }
    FemtoTheme {
        AppsPanel(
            uiState =
                AppDrawerUiState.Content(
                    apps = apps,
                    recentApps = apps.take(4),
                ),
            layout = DrawerLayout.GRID,
            iconSize = DrawerIconSize.MEDIUM,
            pinned = listOf("com.example.maps/.Main", "com.example.phone/.Main"),
            onLaunch = {},
            onTogglePin = {},
            onToggleLayout = {},
            onSelectIconSize = {},
            onReorderPins = {},
            onRetry = {},
            onClose = {},
        )
    }
}

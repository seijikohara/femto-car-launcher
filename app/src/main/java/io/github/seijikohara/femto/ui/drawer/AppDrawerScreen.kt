package io.github.seijikohara.femto.ui.drawer

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.LayoutList
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pin
import com.composables.icons.lucide.PinOff
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.AppEntry
import io.github.seijikohara.femto.data.DrawerLayout
import io.github.seijikohara.femto.ui.home.components.AppListRow
import io.github.seijikohara.femto.ui.home.components.AppTile
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

private val MinTileWidth = 96.dp

internal const val APP_DRAWER_PROGRESS_TEST_TAG = "app-drawer-progress"

@Composable
internal fun AppDrawerScreen(
    uiState: AppDrawerUiState,
    layout: DrawerLayout,
    pinned: Set<String>,
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
    pinned: Set<String>,
    onLaunch: (ComponentName) -> Unit,
    onTogglePin: (ComponentName) -> Unit,
    onToggleLayout: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier.fillMaxSize()) {
    LayoutToggleBar(layout = layout, onToggleLayout = onToggleLayout)
    if (apps.isEmpty()) {
        CenteredMessage(text = stringResource(R.string.drawer_no_apps))
        return@Column
    }
    // Split into the pinned section and the rest (both keep the shared label sort).
    val pinnedApps = apps.filter { it.componentName.flattenToString() in pinned }
    val otherApps = apps.filterNot { it.componentName.flattenToString() in pinned }
    when (layout) {
        DrawerLayout.GRID -> GridApps(pinnedApps, otherApps, onLaunch, onTogglePin)
        DrawerLayout.LIST -> ListApps(pinnedApps, otherApps, onLaunch, onTogglePin)
    }
}

@Composable
private fun LayoutToggleBar(
    layout: DrawerLayout,
    onToggleLayout: () -> Unit,
    modifier: Modifier = Modifier,
) = Box(
    modifier = modifier.fillMaxWidth().padding(horizontal = FemtoDimens.ScreenPadding, vertical = 4.dp),
    contentAlignment = Alignment.CenterEnd,
) {
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
    pinnedApps: List<AppEntry>,
    otherApps: List<AppEntry>,
    onLaunch: (ComponentName) -> Unit,
    onTogglePin: (ComponentName) -> Unit,
    modifier: Modifier = Modifier,
) = LazyVerticalGrid(
    modifier = modifier,
    columns = GridCells.Adaptive(minSize = MinTileWidth),
    contentPadding = PaddingValues(FemtoDimens.ScreenPadding),
    horizontalArrangement = Arrangement.spacedBy(FemtoDimens.GridGutter),
    verticalArrangement = Arrangement.spacedBy(FemtoDimens.GridGutter),
) {
    if (pinnedApps.isNotEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.drawer_section_pinned)) }
        items(items = pinnedApps, key = { it.componentName.flattenToString() }) { entry ->
            DrawerAppItem(entry, DrawerLayout.GRID, isPinned = true, onLaunch, onTogglePin)
        }
        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.drawer_section_apps)) }
    }
    // otherApps are non-pinned by construction (filterNot), so isPinned = false.
    items(items = otherApps, key = { it.componentName.flattenToString() }) { entry ->
        DrawerAppItem(entry, DrawerLayout.GRID, isPinned = false, onLaunch, onTogglePin)
    }
}

@Composable
private fun ListApps(
    pinnedApps: List<AppEntry>,
    otherApps: List<AppEntry>,
    onLaunch: (ComponentName) -> Unit,
    onTogglePin: (ComponentName) -> Unit,
    modifier: Modifier = Modifier,
) = LazyColumn(modifier = modifier, contentPadding = PaddingValues(vertical = FemtoDimens.GridGutter)) {
    if (pinnedApps.isNotEmpty()) {
        item { SectionHeader(stringResource(R.string.drawer_section_pinned)) }
        items(items = pinnedApps, key = { it.componentName.flattenToString() }) { entry ->
            DrawerAppItem(entry, DrawerLayout.LIST, isPinned = true, onLaunch, onTogglePin)
        }
        item { SectionHeader(stringResource(R.string.drawer_section_apps)) }
    }
    // otherApps are non-pinned by construction (filterNot), so isPinned = false.
    items(items = otherApps, key = { it.componentName.flattenToString() }) { entry ->
        DrawerAppItem(entry, DrawerLayout.LIST, isPinned = false, onLaunch, onTogglePin)
    }
}

// One app entry (grid tile or list row) wrapping a long-press pin / unpin menu.
@Composable
private fun DrawerAppItem(
    entry: AppEntry,
    layout: DrawerLayout,
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
                )
            }

            DrawerLayout.LIST -> {
                AppListRow(
                    entry = entry,
                    onClick = { onLaunch(entry.componentName) },
                    onLongClick = { menuOpen = true },
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
private fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) = Text(
    text = text.uppercase(),
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = modifier.padding(horizontal = FemtoDimens.ScreenPadding, vertical = 8.dp),
)

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
            pinned = setOf("com.maps/.Main"),
            onLaunch = {},
            onTogglePin = {},
            onToggleLayout = {},
            onRetry = {},
        )
    }
}

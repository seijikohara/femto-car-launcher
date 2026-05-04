package io.github.seijikohara.femto.ui.drawer

import android.content.ComponentName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.AppEntry
import io.github.seijikohara.femto.ui.home.components.AppTile
import io.github.seijikohara.femto.ui.theme.FemtoDimens

private val MinTileWidth = 96.dp

@Composable
internal fun AppDrawerScreen(
    apps: List<AppEntry>,
    onLaunch: (ComponentName) -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = MinTileWidth),
        contentPadding = PaddingValues(FemtoDimens.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(FemtoDimens.GridGutter),
        verticalArrangement = Arrangement.spacedBy(FemtoDimens.GridGutter),
    ) {
        items(items = apps, key = { it.componentName.flattenToString() }) { entry ->
            AppTile(entry = entry, onClick = { onLaunch(entry.componentName) })
        }
    }
}

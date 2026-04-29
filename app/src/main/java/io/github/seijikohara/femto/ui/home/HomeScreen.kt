package io.github.seijikohara.femto.ui.home

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.AppEntry
import io.github.seijikohara.femto.data.rememberDrivingLockState
import io.github.seijikohara.femto.ui.home.components.AppTile
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

private val MinTileWidth = 96.dp

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        val full = Modifier.fillMaxSize()
        when {
            rememberDrivingLockState() -> {
                DrivingLockedPlaceholder(modifier = full)
            }

            uiState.isLoading -> {
                LoadingPlaceholder(modifier = full)
            }

            else -> {
                AppsGrid(
                    apps = uiState.apps,
                    onLaunch = { onAction(HomeAction.LaunchApp(it)) },
                    modifier = full,
                )
            }
        }
    }
}

@Composable
private fun AppsGrid(
    apps: List<AppEntry>,
    onLaunch: (ComponentName) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = MinTileWidth),
        modifier = modifier,
        contentPadding = PaddingValues(FemtoDimens.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(FemtoDimens.GridGutter),
        verticalArrangement = Arrangement.spacedBy(FemtoDimens.GridGutter),
    ) {
        items(
            items = apps,
            key = { entry -> entry.componentName.flattenToString() },
        ) { entry ->
            AppTile(
                entry = entry,
                onClick = { onLaunch(entry.componentName) },
            )
        }
    }
}

@Composable
private fun LoadingPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "Loading",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DrivingLockedPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "Available when stopped",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@PreviewLightDark
@Composable
private fun HomeScreenPreview() {
    FemtoTheme {
        HomeScreen(
            uiState =
                HomeUiState(
                    isLoading = false,
                    apps =
                        listOf("Maps", "Music", "Settings", "Phone", "Messages", "Camera").map { name ->
                            AppEntry(
                                componentName = ComponentName("preview.${name.lowercase()}", name),
                                label = name,
                                icon = previewIcon(),
                            )
                        },
                ),
            onAction = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun HomeScreenLoadingPreview() {
    FemtoTheme {
        HomeScreen(
            uiState = HomeUiState.Initial,
            onAction = {},
        )
    }
}

private fun previewIcon(): Bitmap =
    Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.LTGRAY)
    }

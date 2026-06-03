package io.github.seijikohara.femto.ui.drawer

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.AppEntry
import io.github.seijikohara.femto.ui.home.components.AppTile
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

private val MinTileWidth = 96.dp

internal const val APP_DRAWER_PROGRESS_TEST_TAG = "app-drawer-progress"

@Composable
internal fun AppDrawerScreen(
    uiState: AppDrawerUiState,
    onLaunch: (ComponentName) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
) {
    when (uiState) {
        AppDrawerUiState.Loading -> LoadingState()
        is AppDrawerUiState.Content -> ContentState(apps = uiState.apps, onLaunch = onLaunch)
        AppDrawerUiState.Error -> ErrorState(onRetry = onRetry)
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
    onLaunch: (ComponentName) -> Unit,
    modifier: Modifier = Modifier,
) = if (apps.isEmpty()) {
    CenteredMessage(text = "No apps installed", modifier = modifier)
} else {
    LazyVerticalGrid(
        modifier = modifier,
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
        text = "Couldn't load apps",
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
        Text(text = "Retry", fontSize = FemtoDimens.MinBodyTextSize)
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
            onLaunch = {},
            onRetry = {},
        )
    }
}

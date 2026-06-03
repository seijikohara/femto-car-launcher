package io.github.seijikohara.femto.ui.drawer

import android.content.ComponentName
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.seijikohara.femto.data.AppsRepository

@Composable
internal fun AppDrawerRoute(
    onLaunch: (ComponentName) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var uiState by remember { mutableStateOf<AppDrawerUiState>(AppDrawerUiState.Loading) }
    // Bumping the retry key both re-runs the query and flips the surface
    // back to Loading, so a retry shows progress instead of stale Error.
    var retryKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(retryKey) {
        uiState = AppDrawerUiState.Loading
        runCatching { AppsRepository(context).queryApps() }
            .onSuccess { apps -> uiState = AppDrawerUiState.Content(apps) }
            .onFailure { uiState = AppDrawerUiState.Error }
    }
    BackHandler(onBack = onBack)
    AppDrawerScreen(
        uiState = uiState,
        onLaunch = onLaunch,
        onRetry = { retryKey++ },
        modifier = modifier,
    )
}

package io.github.seijikohara.femto.ui.home

import android.Manifest
import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.seijikohara.femto.data.hasFineLocationPermission

@Composable
internal fun HomeRoute(
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: HomeViewModel =
        viewModel(factory = HomeViewModelFactory(context.applicationContext as Application))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LocationPermissionRequest()
    val currentOnOpenDrawer by rememberUpdatedState(onOpenDrawer)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HomeEvent.OpenDrawer -> currentOnOpenDrawer()
            }
        }
    }
    HomeScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

/**
 * Request `ACCESS_FINE_LOCATION` once when the route first composes.
 *
 * The permission powers the head-unit dashboard's location-driven
 * surfaces (map centre, speed / altitude / address overlays, weather
 * lookup). On denial the launcher continues to function; the
 * dependent panels render empty placeholders until the user grants
 * the permission via system Settings.
 *
 * A richer rationale UI is deferred; the current request relies on
 * the system dialog alone.
 */
@Composable
private fun LocationPermissionRequest() {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { /* Result reflects on next STARTED via repeatOnLifecycle. */ }

    LaunchedEffect(Unit) {
        if (!context.hasFineLocationPermission()) {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}

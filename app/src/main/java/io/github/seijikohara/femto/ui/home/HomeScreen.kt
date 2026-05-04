package io.github.seijikohara.femto.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.text.util.LocalePreferences
import io.github.seijikohara.femto.ui.home.components.DashboardScaffold
import io.github.seijikohara.femto.ui.locale.distanceUnitFor
import io.github.seijikohara.femto.ui.locale.speedUnitFor
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
) {
    if (uiState.isLoading) {
        LoadingPlaceholder(modifier = Modifier.fillMaxSize())
    } else {
        val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
        DashboardScaffold(
            clock = uiState.clock,
            is24Hour = is24Hour,
            weather = uiState.weather,
            temperatureUnit = LocalePreferences.getTemperatureUnit(),
            address = uiState.address,
            location = uiState.location,
            speedUnit = speedUnitFor(),
            distanceUnit = distanceUnitFor(),
            mapAvailable = uiState.mapAvailable,
            nowPlaying = uiState.nowPlaying,
            onMapTap = { onAction(HomeAction.OpenMaps) },
            onMusicCommand = { onAction(HomeAction.Music(it)) },
            onConnectMusic = { onAction(HomeAction.ConnectMusicPlayer) },
            onOpenDrawer = { onAction(HomeAction.OpenAppDrawer) },
            onShortcut = { onAction(HomeAction.Shortcut(it)) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun LoadingPlaceholder(modifier: Modifier = Modifier) =
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "Loading",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

@PreviewLightDark
@Composable
private fun HomeScreenPreview() = FemtoTheme { HomeScreen(uiState = HomeUiState.Initial, onAction = {}) }

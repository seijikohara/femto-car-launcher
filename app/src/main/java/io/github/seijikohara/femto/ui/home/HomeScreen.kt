package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.seijikohara.femto.ui.home.components.DashboardScaffold
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    is24Hour: Boolean,
    speedUnit: SpeedUnit,
    temperatureUnit: TemperatureUnit,
    mapFps: Int,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
) {
    DashboardScaffold(
        uiState = uiState,
        is24Hour = is24Hour,
        speedUnit = speedUnit,
        temperatureUnit = temperatureUnit,
        mapFps = mapFps,
        onAction = onAction,
        modifier = Modifier.fillMaxSize(),
    )
}

@PreviewLightDark
@Composable
private fun HomeScreenPreview() =
    FemtoTheme {
        HomeScreen(
            uiState = HomeUiState.Initial,
            is24Hour = true,
            speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
            temperatureUnit = TemperatureUnit.CELSIUS,
            mapFps = 10,
            onAction = {},
        )
    }

package io.github.seijikohara.femto.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.seijikohara.femto.ui.home.components.DashboardScaffold
import io.github.seijikohara.femto.ui.locale.speedUnitFor
import io.github.seijikohara.femto.ui.locale.temperatureUnitFor
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
    DashboardScaffold(
        uiState = uiState,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        speedUnit = speedUnitFor(),
        temperatureUnit = temperatureUnitFor(),
        onAction = onAction,
        modifier = Modifier.fillMaxSize(),
    )
}

@PreviewLightDark
@Composable
private fun HomeScreenPreview() = FemtoTheme { HomeScreen(uiState = HomeUiState.Initial, onAction = {}) }

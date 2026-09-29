package io.github.seijikohara.femto.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.display.DockWidth
import io.github.seijikohara.femto.data.display.DriverSide
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.data.update.UpdateManifest
import io.github.seijikohara.femto.ui.home.components.DashboardScaffold
import io.github.seijikohara.femto.ui.home.components.DockConfig
import io.github.seijikohara.femto.ui.home.components.GlassConfig
import io.github.seijikohara.femto.ui.home.components.MapConfig
import io.github.seijikohara.femto.ui.home.components.PanelVisibility
import io.github.seijikohara.femto.ui.home.components.UpdatePromptDialog
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    is24Hour: Boolean,
    showClockSeconds: Boolean,
    speedUnit: SpeedUnit,
    temperatureUnit: TemperatureUnit,
    mapConfig: MapConfig,
    panels: PanelVisibility,
    glassConfig: GlassConfig,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    dockPosition: DockPosition = DockPosition.BOTTOM,
    dockWidth: DockWidth = DockWidth.COMPACT,
    dockConfig: DockConfig = DockConfig(),
    driverSide: DriverSide = DriverSide.RIGHT,
    musicShowAlbum: Boolean = true,
    musicShowArt: Boolean = true,
    spectrum: StateFlow<FloatArray?>? = null,
    motionTier: MotionTier = MotionTier.STANDARD,
    // The build the update prompt asks about (HomeViewModel.updatePrompt), or null.
    updatePrompt: UpdateManifest? = null,
    // Whether one of the host's sheets covers the dashboard (Settings, the
    // assistant, ...).
    sheetOpen: Boolean = false,
    // The fullscreen choice, for the update prompt's own window.
    fullscreen: Boolean = false,
) = Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
) {
    DashboardScaffold(
        uiState = uiState,
        is24Hour = is24Hour,
        showClockSeconds = showClockSeconds,
        speedUnit = speedUnit,
        temperatureUnit = temperatureUnit,
        mapConfig = mapConfig,
        panels = panels,
        glassConfig = glassConfig,
        onAction = onAction,
        modifier = Modifier.fillMaxSize(),
        dockPosition = dockPosition,
        dockWidth = dockWidth,
        dockConfig = dockConfig,
        driverSide = driverSide,
        musicShowAlbum = musicShowAlbum,
        musicShowArt = musicShowArt,
        spectrum = spectrum,
        motionTier = motionTier,
    )
    // The update prompt waits while a sheet covers the dashboard. Over Settings
    // it could ask about the very offer the Updates section shows (and records
    // as seen) right then; over any sheet it would cut into what the user is
    // doing there. It shows only while the dashboard is resumed, too (see
    // UpdatePromptDialog on the task snapshot).
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    updatePrompt?.takeIf { lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && !sheetOpen }?.let { update ->
        UpdatePromptDialog(update = update, onAction = onAction, fullscreen = fullscreen)
    }
}

@PreviewLightDark
@Composable
private fun HomeScreenPreview() =
    FemtoTheme {
        HomeScreen(
            uiState = HomeUiState.Initial,
            is24Hour = true,
            showClockSeconds = true,
            speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
            temperatureUnit = TemperatureUnit.CELSIUS,
            mapConfig = MapConfig(),
            panels = PanelVisibility(),
            glassConfig = GlassConfig(),
            onAction = {},
        )
    }

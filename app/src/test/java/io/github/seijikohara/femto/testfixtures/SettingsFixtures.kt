package io.github.seijikohara.femto.testfixtures

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.seijikohara.femto.ui.settings.RoutingDataSummary
import io.github.seijikohara.femto.ui.settings.SettingsCategoryId
import io.github.seijikohara.femto.ui.settings.SettingsScreen
import io.github.seijikohara.femto.ui.settings.SettingsUiState
import io.github.seijikohara.femto.ui.theme.FemtoTheme

/**
 * The Settings state of a build and a map backend that offer routing, with no routing data on the device yet: the
 * state in which the Navigation category and the routing data row show.
 */
internal fun routingOfferedSettingsState(): SettingsUiState =
    SettingsUiState.Initial.copy(routingData = RoutingDataSummary(tileCount = 0, totalBytes = 0L))

/** Render the whole Settings screen for a screenshot, opened on [initialCategory], every callback a no-op. */
@Composable
internal fun SettingsForCapture(
    initialCategory: SettingsCategoryId?,
    darkTheme: Boolean = false,
    uiState: SettingsUiState = SettingsUiState.Initial,
) = FemtoTheme(darkTheme = darkTheme) {
    SettingsScreen(
        uiState = uiState,
        onAction = {},
        onBack = {},
        onOpenNotificationAccess = {},
        onOpenSystemSettings = {},
        onOpenFontPicker = {},
        onOpenOfflineRegions = {},
        onOpenRoutingData = {},
        onOpenDiagnostics = {},
        onOpenLicenses = {},
        onOpenDocument = {},
        modifier = Modifier.fillMaxSize(),
        initialCategory = initialCategory,
    )
}

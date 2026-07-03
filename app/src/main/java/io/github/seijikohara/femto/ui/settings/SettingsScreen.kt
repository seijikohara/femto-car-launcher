package io.github.seijikohara.femto.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.fonts.FontSlot
import io.github.seijikohara.femto.ui.settings.components.AppearanceSection
import io.github.seijikohara.femto.ui.settings.components.DrivingSection
import io.github.seijikohara.femto.ui.settings.components.Header
import io.github.seijikohara.femto.ui.settings.components.LocationSection
import io.github.seijikohara.femto.ui.settings.components.MapSection
import io.github.seijikohara.femto.ui.settings.components.PanelsSection
import io.github.seijikohara.femto.ui.settings.components.ScreenSection
import io.github.seijikohara.femto.ui.settings.components.SystemSection
import io.github.seijikohara.femto.ui.settings.components.UnitsSection
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

/**
 * In-app settings, laid out like the Android system Settings app: category
 * sections, each a flat rounded card of rows. A row carries a title plus the
 * current value as a summary; single-choice rows open a radio dialog, boolean
 * rows toggle an inline switch, numeric rows host an inline slider, and the
 * System rows link out.
 *
 * Pure UI — persisted changes flow up via [onAction]; host-level navigation and
 * system intents flow up via the dedicated callbacks so the screen stays
 * previewable and testable in isolation.
 */
@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onBack: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenFontPicker: (FontSlot) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier.fillMaxSize(),
    // Hosted in the settings bottom sheet: match the M3 sheet container colour so the
    // surface reads as the sheet rather than painting the opaque app background.
    color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(FemtoDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Header(onBack = onBack)

        AppearanceSection(uiState = uiState, onAction = onAction, onOpenFontPicker = onOpenFontPicker)

        ScreenSection(uiState = uiState, onAction = onAction)

        DrivingSection(uiState = uiState, onAction = onAction)

        UnitsSection(uiState = uiState, onAction = onAction)

        MapSection(uiState = uiState, onAction = onAction)

        LocationSection(uiState = uiState, onAction = onAction)

        PanelsSection(uiState = uiState, onAction = onAction, onOpenSystemSettings = onOpenSystemSettings)

        SystemSection(
            onAction = onAction,
            onOpenNotificationAccess = onOpenNotificationAccess,
            onOpenSystemSettings = onOpenSystemSettings,
            onOpenDiagnostics = onOpenDiagnostics,
            onOpenLicenses = onOpenLicenses,
            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
        )
    }
}

@PreviewLightDark
@Composable
private fun SettingsScreenPreview() {
    FemtoTheme {
        SettingsScreen(
            uiState = SettingsUiState.Initial,
            onAction = {},
            onBack = {},
            onOpenNotificationAccess = {},
            onOpenSystemSettings = {},
            onOpenFontPicker = {},
            onOpenDiagnostics = {},
            onOpenLicenses = {},
            onOpenPrivacyPolicy = {},
        )
    }
}

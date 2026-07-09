package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.settings.SettingsAction

// The System category's rows: action links plus the global reset. Unlike the
// other categories, System has no SettingsSectionId of its own (see
// SettingsCategoryId), so the shared master-detail wrapper never gives it a
// per-category reset icon — ResetRow below is its only reset affordance.
@Composable
internal fun SystemSection(
    onAction: (SettingsAction) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier) {
    ActionRow(
        title = stringResource(R.string.settings_open_notification_access),
        onClick = onOpenNotificationAccess,
    )
    ActionRow(
        title = stringResource(R.string.settings_open_system_settings),
        onClick = onOpenSystemSettings,
    )
    ActionRow(
        title = stringResource(R.string.settings_open_diagnostics),
        onClick = onOpenDiagnostics,
    )
    ActionRow(
        title = stringResource(R.string.settings_open_licenses),
        onClick = onOpenLicenses,
    )
    ActionRow(
        title = stringResource(R.string.settings_open_privacy),
        onClick = onOpenPrivacyPolicy,
    )
    ResetRow(onConfirm = { onAction(SettingsAction.ResetToDefaults) })
}

package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.settings.SettingsAction

@Composable
internal fun SystemSection(
    onAction: (SettingsAction) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) = SettingsSection(title = stringResource(R.string.settings_group_system), modifier = modifier) {
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

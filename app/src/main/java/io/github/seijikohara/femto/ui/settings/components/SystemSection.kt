package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.settings.SettingsAction
import io.github.seijikohara.femto.ui.settings.SettingsCategoryId
import io.github.seijikohara.femto.ui.settings.SettingsDocument

// The System category's rows: action links plus the global reset. Unlike the
// other categories, System has no SettingsSectionId of its own (see
// SettingsCategoryId), so the shared master-detail wrapper never gives it a
// per-category reset icon — ResetSettingsRow below is its only reset
// affordance, offering each of [categories] (the ones Settings lists).
@Composable
internal fun SystemSection(
    categories: List<SettingsCategoryId>,
    onAction: (SettingsAction) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenDocument: (SettingsDocument) -> Unit,
    modifier: Modifier = Modifier,
) = SettingsGroup(modifier = modifier) {
    item { shapes ->
        ActionRow(
            title = stringResource(R.string.settings_open_notification_access),
            onClick = onOpenNotificationAccess,
            shapes = shapes,
        )
    }
    item { shapes ->
        ActionRow(
            title = stringResource(R.string.settings_open_system_settings),
            onClick = onOpenSystemSettings,
            shapes = shapes,
        )
    }
    item { shapes ->
        ActionRow(
            title = stringResource(R.string.settings_open_diagnostics),
            onClick = onOpenDiagnostics,
            shapes = shapes,
        )
    }
    item { shapes ->
        ActionRow(
            title = stringResource(R.string.settings_open_licenses),
            onClick = onOpenLicenses,
            shapes = shapes,
        )
    }
    item { shapes ->
        ActionRow(
            title = stringResource(R.string.settings_open_privacy),
            onClick = { onOpenDocument(SettingsDocument.PRIVACY_POLICY) },
            shapes = shapes,
        )
    }
    item { shapes ->
        ActionRow(
            title = stringResource(R.string.settings_open_terms),
            onClick = { onOpenDocument(SettingsDocument.TERMS) },
            shapes = shapes,
        )
    }
    item { shapes -> ResetSettingsRow(categories = categories, onReset = onAction, shapes = shapes) }
}

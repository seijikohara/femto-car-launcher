package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.AssistantLaunchSetting
import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.display.FullscreenSetting
import io.github.seijikohara.femto.data.display.OrientationSetting
import io.github.seijikohara.femto.data.display.SettingsSectionId
import io.github.seijikohara.femto.data.display.UiScale
import io.github.seijikohara.femto.ui.settings.SettingsAction
import io.github.seijikohara.femto.ui.settings.SettingsUiState

@Composable
internal fun ScreenSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) = SettingsSection(
    title = stringResource(R.string.settings_section_screen),
    modifier = modifier,
    onReset = { onAction(SettingsAction.ResetSection(SettingsSectionId.SCREEN)) },
) {
    SliderRow(
        title = stringResource(R.string.settings_group_ui_scale),
        valueLabel = stringResource(uiState.uiScale.labelRes()),
        value = UiScale.entries.indexOf(uiState.uiScale),
        range = 0..UiScale.entries.lastIndex,
        onValueChange = { onAction(SettingsAction.SetUiScale(UiScale.entries[it])) },
    )
    ChoiceRow(
        title = stringResource(R.string.settings_group_orientation),
        options =
            listOf(
                OrientationSetting.AUTO to stringResource(R.string.settings_option_auto),
                OrientationSetting.LANDSCAPE to stringResource(R.string.settings_orientation_landscape),
                OrientationSetting.PORTRAIT to stringResource(R.string.settings_orientation_portrait),
            ),
        selected = uiState.orientation,
        onSelect = { onAction(SettingsAction.SetOrientation(it)) },
    )
    SwitchRow(
        title = stringResource(R.string.settings_group_fullscreen),
        checked = uiState.fullscreen == FullscreenSetting.ON,
        onCheckedChange = { onAction(SettingsAction.SetFullscreen(it.toFullscreen())) },
    )
    SwitchRow(
        title = stringResource(R.string.settings_keep_screen_on),
        checked = uiState.keepScreenOn,
        onCheckedChange = { onAction(SettingsAction.SetKeepScreenOn(it)) },
    )
    ChoiceRow(
        title = stringResource(R.string.settings_group_dock_position),
        options =
            listOf(
                DockPosition.BOTTOM to stringResource(R.string.settings_dock_bottom),
                DockPosition.TOP to stringResource(R.string.settings_dock_top),
                DockPosition.LEFT to stringResource(R.string.settings_dock_left),
                DockPosition.RIGHT to stringResource(R.string.settings_dock_right),
            ),
        selected = uiState.dockPosition,
        onSelect = { onAction(SettingsAction.SetDockPosition(it)) },
    )
    ChoiceRow(
        title = stringResource(R.string.settings_group_assistant),
        options =
            listOf(
                AssistantLaunchSetting.SYSTEM to stringResource(R.string.settings_assistant_system),
                AssistantLaunchSetting.IN_APP to stringResource(R.string.settings_assistant_in_app),
            ),
        selected = uiState.assistantLaunch,
        onSelect = { onAction(SettingsAction.SetAssistantLaunch(it)) },
    )
}

private fun Boolean.toFullscreen(): FullscreenSetting = if (this) FullscreenSetting.ON else FullscreenSetting.OFF

// The human-readable label for a display-size step, shown as the slider's current
// value (the row title already reads "Display size", so this names only the step).
private fun UiScale.labelRes(): Int =
    when (this) {
        UiScale.SMALL -> R.string.settings_ui_scale_small
        UiScale.COMPACT -> R.string.settings_ui_scale_compact
        UiScale.MEDIUM -> R.string.settings_ui_scale_medium
        UiScale.COMFORTABLE -> R.string.settings_ui_scale_comfortable
        UiScale.LARGE -> R.string.settings_ui_scale_large
    }

package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.DriverSide
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.data.display.PresetMode
import io.github.seijikohara.femto.ui.settings.SettingsAction
import io.github.seijikohara.femto.ui.settings.SettingsUiState

private const val MIN_DRIVING_THRESHOLD_KMH = 3
private const val MAX_DRIVING_THRESHOLD_KMH = 40

// The Driving category's rows; see AppearanceSection's header comment on why
// there is no title / reset wiring here.
@Composable
internal fun DrivingSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier) {
    ChoiceRow(
        title = stringResource(R.string.settings_group_preset_mode),
        options =
            listOf(
                PresetMode.AUTO to stringResource(R.string.settings_preset_auto),
                PresetMode.COCKPIT to stringResource(R.string.settings_preset_cockpit),
                PresetMode.DRIVING to stringResource(R.string.settings_preset_driving),
            ),
        selected = uiState.presetMode,
        onSelect = { onAction(SettingsAction.SetPresetMode(it)) },
    )
    // Driver side is the most fundamental cockpit-layout choice, so it sits right
    // under the auto-switch face at the top of the Driving section.
    ChoiceRow(
        title = stringResource(R.string.settings_group_driver_side),
        options =
            listOf(
                DriverSide.RIGHT to stringResource(R.string.settings_driver_side_right),
                DriverSide.LEFT to stringResource(R.string.settings_driver_side_left),
            ),
        selected = uiState.driverSide,
        onSelect = { onAction(SettingsAction.SetDriverSide(it)) },
    )
    SliderRow(
        title = stringResource(R.string.settings_group_driving_threshold),
        valueLabel = stringResource(R.string.settings_driving_threshold_value, uiState.drivingThresholdKmh),
        value = uiState.drivingThresholdKmh,
        range = MIN_DRIVING_THRESHOLD_KMH..MAX_DRIVING_THRESHOLD_KMH,
        onValueChange = { onAction(SettingsAction.SetDrivingThresholdKmh(it)) },
        description = stringResource(R.string.settings_driving_threshold_desc),
    )
    ChoiceRow(
        title = stringResource(R.string.settings_group_motion),
        options =
            listOf(
                MotionTier.STANDARD to stringResource(R.string.settings_motion_standard),
                MotionTier.REDUCED to stringResource(R.string.settings_motion_reduced),
                MotionTier.OFF to stringResource(R.string.settings_motion_off),
            ),
        selected = uiState.motionTier,
        onSelect = { onAction(SettingsAction.SetMotionTier(it)) },
    )
    SettingsSubheader(stringResource(R.string.settings_group_briefing))
    SwitchRow(
        title = stringResource(R.string.settings_briefing_show_event),
        checked = uiState.briefingShowEvent,
        onCheckedChange = { onAction(SettingsAction.SetBriefingShowEvent(it)) },
    )
    SwitchRow(
        title = stringResource(R.string.settings_briefing_show_weather),
        checked = uiState.briefingShowWeather,
        onCheckedChange = { onAction(SettingsAction.SetBriefingShowWeather(it)) },
    )
}

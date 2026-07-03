package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.data.display.PresetMode
import io.github.seijikohara.femto.ui.settings.SettingsAction
import io.github.seijikohara.femto.ui.settings.SettingsUiState

private const val MIN_DRIVING_THRESHOLD_KMH = 3
private const val MAX_DRIVING_THRESHOLD_KMH = 40

@Composable
internal fun DrivingSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) = SettingsSection(title = stringResource(R.string.settings_section_driving), modifier = modifier) {
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
}

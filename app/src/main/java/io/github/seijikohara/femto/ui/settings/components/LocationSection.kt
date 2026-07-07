package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.SettingsSectionId
import io.github.seijikohara.femto.data.location.LocationQualitySetting
import io.github.seijikohara.femto.ui.settings.SettingsAction
import io.github.seijikohara.femto.ui.settings.SettingsUiState

private const val LOCATION_INTERVAL_STEP_MS = 250L
private const val MIN_LOCATION_INTERVAL_STEPS = 1
private const val MAX_LOCATION_INTERVAL_STEPS = 8
private const val MIN_LOCATION_MIN_DISTANCE = 0
private const val MAX_LOCATION_MIN_DISTANCE = 25

@Composable
internal fun LocationSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) = SettingsSection(
    title = stringResource(R.string.settings_section_location),
    modifier = modifier,
    onReset = { onAction(SettingsAction.ResetSection(SettingsSectionId.LOCATION)) },
) {
    ChoiceRow(
        title = stringResource(R.string.settings_group_location_quality),
        options =
            listOf(
                LocationQualitySetting.HIGH_ACCURACY to
                    stringResource(R.string.settings_location_quality_high),
                LocationQualitySetting.BALANCED to
                    stringResource(R.string.settings_location_quality_balanced),
                LocationQualitySetting.LOW_POWER to
                    stringResource(R.string.settings_location_quality_low),
            ),
        selected = uiState.locationQuality,
        onSelect = { onAction(SettingsAction.SetLocationQuality(it)) },
    )
    // The slider works in 250 ms steps so the knob lands on round values;
    // the persisted value stays in milliseconds.
    SliderRow(
        title = stringResource(R.string.settings_group_location_interval),
        valueLabel =
            stringResource(R.string.settings_location_interval_value, uiState.locationIntervalMillis),
        value = (uiState.locationIntervalMillis / LOCATION_INTERVAL_STEP_MS).toInt(),
        range = MIN_LOCATION_INTERVAL_STEPS..MAX_LOCATION_INTERVAL_STEPS,
        onValueChange = { onAction(SettingsAction.SetLocationIntervalMillis(it * LOCATION_INTERVAL_STEP_MS)) },
        description = stringResource(R.string.settings_location_interval_desc),
    )
    SliderRow(
        title = stringResource(R.string.settings_group_location_min_distance),
        valueLabel =
            stringResource(R.string.settings_location_min_distance_value, uiState.locationMinDistanceMeters),
        value = uiState.locationMinDistanceMeters,
        range = MIN_LOCATION_MIN_DISTANCE..MAX_LOCATION_MIN_DISTANCE,
        onValueChange = { onAction(SettingsAction.SetLocationMinDistance(it)) },
        description = stringResource(R.string.settings_location_min_distance_desc),
    )
    SwitchRow(
        title = stringResource(R.string.settings_group_background_ranging),
        checked = uiState.backgroundRangingEnabled,
        onCheckedChange = { onAction(SettingsAction.SetBackgroundRanging(it)) },
        summary = stringResource(R.string.settings_background_ranging_desc),
    )
}

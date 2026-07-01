package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.ClockSetting
import io.github.seijikohara.femto.data.display.SpeedUnitSetting
import io.github.seijikohara.femto.data.display.TemperatureUnitSetting
import io.github.seijikohara.femto.ui.settings.SettingsAction
import io.github.seijikohara.femto.ui.settings.SettingsUiState

@Composable
internal fun UnitsSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) = SettingsSection(title = stringResource(R.string.settings_section_units), modifier = modifier) {
    ChoiceRow(
        title = stringResource(R.string.settings_group_speed),
        options =
            listOf(
                SpeedUnitSetting.AUTO to stringResource(R.string.settings_option_auto),
                SpeedUnitSetting.KILOMETERS to stringResource(R.string.settings_speed_km),
                SpeedUnitSetting.MILES to stringResource(R.string.settings_speed_mi),
            ),
        selected = uiState.speedUnit,
        onSelect = { onAction(SettingsAction.SetSpeedUnit(it)) },
    )
    ChoiceRow(
        title = stringResource(R.string.settings_group_temperature),
        options =
            listOf(
                TemperatureUnitSetting.AUTO to stringResource(R.string.settings_option_auto),
                TemperatureUnitSetting.CELSIUS to stringResource(R.string.settings_temp_celsius),
                TemperatureUnitSetting.FAHRENHEIT to stringResource(R.string.settings_temp_fahrenheit),
            ),
        selected = uiState.temperatureUnit,
        onSelect = { onAction(SettingsAction.SetTemperatureUnit(it)) },
    )
    ChoiceRow(
        title = stringResource(R.string.settings_group_clock),
        options =
            listOf(
                ClockSetting.AUTO to stringResource(R.string.settings_option_auto),
                ClockSetting.TWELVE_HOUR to stringResource(R.string.settings_clock_12),
                ClockSetting.TWENTY_FOUR_HOUR to stringResource(R.string.settings_clock_24),
            ),
        selected = uiState.clock,
        onSelect = { onAction(SettingsAction.SetClock(it)) },
    )
    SwitchRow(
        title = stringResource(R.string.settings_group_clock_seconds),
        checked = uiState.showClockSeconds,
        onCheckedChange = { onAction(SettingsAction.SetShowClockSeconds(it)) },
    )
}

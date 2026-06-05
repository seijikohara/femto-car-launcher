package io.github.seijikohara.femto.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.ClockSetting
import io.github.seijikohara.femto.data.FullscreenSetting
import io.github.seijikohara.femto.data.SpeedUnitSetting
import io.github.seijikohara.femto.data.TemperatureUnitSetting
import io.github.seijikohara.femto.data.ThemeMode
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.FontTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import kotlin.math.roundToInt

/**
 * In-app settings: theme, units, clock, font, and links out to the relevant
 * system screens. Pure UI — persisted changes flow up via [onAction]; the
 * host-level navigation / system intents flow up via the dedicated callbacks so
 * the screen stays previewable and testable in isolation.
 */
@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onBack: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
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

        SettingGroup(
            title = stringResource(R.string.settings_group_theme),
            options =
                listOf(
                    ThemeMode.SYSTEM to stringResource(R.string.settings_option_auto),
                    ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                    ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
                ),
            selected = uiState.themeMode,
            onSelect = { onAction(SettingsAction.SetThemeMode(it)) },
        )

        SettingGroup(
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

        SettingGroup(
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

        SettingGroup(
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

        SettingGroup(
            title = stringResource(R.string.settings_group_fullscreen),
            options =
                listOf(
                    FullscreenSetting.OFF to stringResource(R.string.settings_fullscreen_off),
                    FullscreenSetting.ON to stringResource(R.string.settings_fullscreen_on),
                ),
            selected = uiState.fullscreen,
            onSelect = { onAction(SettingsAction.SetFullscreen(it)) },
        )

        val maxFps = rememberMaxDisplayFps()
        SliderSetting(
            title = stringResource(R.string.settings_group_map_refresh),
            valueLabel = stringResource(R.string.settings_map_fps_value, uiState.mapFps),
            value = uiState.mapFps,
            range = MIN_MAP_FPS..maxFps,
            onValueChange = { onAction(SettingsAction.SetMapFps(it)) },
        )

        SettingGroup(
            title = stringResource(R.string.settings_group_font),
            options = FontTheme.entries.map { it to it.displayName() },
            selected = uiState.fontTheme,
            onSelect = { onAction(SettingsAction.SetFontTheme(it)) },
        )

        SystemGroup(
            onOpenNotificationAccess = onOpenNotificationAccess,
            onOpenSystemSettings = onOpenSystemSettings,
        )
    }
}

@Composable
private fun Header(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    Box(
        modifier =
            Modifier
                .size(FemtoDimens.MinTouchTarget)
                .clipClickable(onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Lucide.ArrowLeft,
            contentDescription = stringResource(R.string.settings_back),
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(28.dp),
        )
    }
    Text(
        text = stringResource(R.string.settings_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun <T> SettingGroup(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            OptionChip(
                label = label,
                selected = value == selected,
                onClick = { onSelect(value) },
            )
        }
    }
}

@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container =
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val content =
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = FemtoDimens.MinTouchTarget),
        shape = RoundedCornerShape(14.dp),
        color = container,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = content,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SystemGroup(
    onOpenNotificationAccess: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    Text(
        text = stringResource(R.string.settings_group_system),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FilledTonalButton(
        onClick = onOpenNotificationAccess,
        modifier = Modifier.fillMaxWidth().heightIn(min = FemtoDimens.MinTouchTarget),
    ) {
        Text(text = stringResource(R.string.settings_open_notification_access))
    }
    FilledTonalButton(
        onClick = onOpenSystemSettings,
        modifier = Modifier.fillMaxWidth().heightIn(min = FemtoDimens.MinTouchTarget),
    ) {
        Text(text = stringResource(R.string.settings_open_system_settings))
    }
}

@Composable
private fun SliderSetting(
    title: String,
    valueLabel: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Slider(
        value = value.coerceIn(range.first, range.last).toFloat(),
        onValueChange = { onValueChange(it.roundToInt().coerceIn(range.first, range.last)) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
    )
}

// The display's maximum refresh rate (fps), the ceiling for the map frame-rate
// slider. Falls back to 60 when the modes cannot be read; clamped to a sane band.
@Composable
private fun rememberMaxDisplayFps(): Int {
    val context = LocalContext.current
    return remember(context) {
        val fromModes = context.display?.supportedModes?.maxOfOrNull { it.refreshRate }
        val refresh = fromModes ?: context.display?.refreshRate ?: DEFAULT_MAX_FPS
        refresh.roundToInt().coerceIn(MIN_MAP_FPS, MAX_MAP_FPS_CEILING)
    }
}

private const val MIN_MAP_FPS = 1
private const val MAX_MAP_FPS_CEILING = 120
private const val DEFAULT_MAX_FPS = 60f

// A human-readable label for a font theme (e.g. INTER -> "Inter").
private fun FontTheme.displayName(): String = name.lowercase().replaceFirstChar(Char::uppercaseChar)

// Small modifier helper: clip to a circle and make clickable, for the back box.
private fun Modifier.clipClickable(onClick: () -> Unit): Modifier =
    this
        .clip(RoundedCornerShape(percent = 50))
        .clickable(onClick = onClick)

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
        )
    }
}

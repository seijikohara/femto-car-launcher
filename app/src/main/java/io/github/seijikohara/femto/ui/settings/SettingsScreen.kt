package io.github.seijikohara.femto.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCcw
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.AccentColor
import io.github.seijikohara.femto.data.ClockSetting
import io.github.seijikohara.femto.data.FontSlot
import io.github.seijikohara.femto.data.FullscreenSetting
import io.github.seijikohara.femto.data.MapColorScheme
import io.github.seijikohara.femto.data.MapRenderMode
import io.github.seijikohara.femto.data.MapStyleSetting
import io.github.seijikohara.femto.data.SpeedUnitSetting
import io.github.seijikohara.femto.data.TemperatureUnitSetting
import io.github.seijikohara.femto.data.ThemeMode
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.accentSeedColor
import kotlin.math.roundToInt

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

        SettingsSection(title = stringResource(R.string.settings_section_display)) {
            ChoiceRow(
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
            AccentRow(
                selected = uiState.accentColor,
                onSelect = { onAction(SettingsAction.SetAccentColor(it)) },
            )
            FontRow(
                title = stringResource(R.string.settings_group_font_latin),
                family = uiState.latinFont,
                onClick = { onOpenFontPicker(FontSlot.LATIN) },
            )
            FontRow(
                title = stringResource(R.string.settings_group_font_cjk),
                family = uiState.cjkFont,
                onClick = { onOpenFontPicker(FontSlot.CJK) },
            )
            SwitchRow(
                title = stringResource(R.string.settings_group_fullscreen),
                checked = uiState.fullscreen == FullscreenSetting.ON,
                onCheckedChange = { onAction(SettingsAction.SetFullscreen(it.toFullscreen())) },
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_units)) {
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

        SettingsSection(title = stringResource(R.string.settings_section_map)) {
            ChoiceRow(
                title = stringResource(R.string.settings_group_map_rendering),
                options =
                    listOf(
                        MapRenderMode.LIVE to stringResource(R.string.settings_map_mode_live),
                        MapRenderMode.SNAPSHOT to stringResource(R.string.settings_map_mode_snapshot),
                    ),
                selected = uiState.mapRenderMode,
                onSelect = { onAction(SettingsAction.SetMapRenderMode(it)) },
            )
            ChoiceRow(
                title = stringResource(R.string.settings_group_map_style),
                options =
                    listOf(
                        MapStyleSetting.AUTO to stringResource(R.string.settings_option_auto),
                        MapStyleSetting.LIGHT to stringResource(R.string.settings_theme_light),
                        MapStyleSetting.DARK to stringResource(R.string.settings_theme_dark),
                    ),
                selected = uiState.mapStyle,
                onSelect = { onAction(SettingsAction.SetMapStyle(it)) },
            )
            // Independent colour schemes for the light and dark contexts. ACCENT is
            // the adaptive accent-tinted default; the rest are fixed OpenFreeMap styles.
            ChoiceRow(
                title = stringResource(R.string.settings_group_map_scheme_light),
                options =
                    listOf(
                        MapColorScheme.ACCENT to stringResource(R.string.settings_map_scheme_accent),
                        MapColorScheme.POSITRON to stringResource(R.string.settings_map_scheme_positron),
                        MapColorScheme.BRIGHT to stringResource(R.string.settings_map_scheme_bright),
                        MapColorScheme.LIBERTY to stringResource(R.string.settings_map_scheme_liberty),
                    ),
                selected = uiState.mapSchemeLight,
                onSelect = { onAction(SettingsAction.SetMapSchemeLight(it)) },
            )
            ChoiceRow(
                title = stringResource(R.string.settings_group_map_scheme_dark),
                options =
                    listOf(
                        MapColorScheme.ACCENT to stringResource(R.string.settings_map_scheme_accent),
                        MapColorScheme.DARK_MATTER to stringResource(R.string.settings_map_scheme_dark_matter),
                        MapColorScheme.DARK to stringResource(R.string.settings_map_scheme_dark),
                        MapColorScheme.FIORD to stringResource(R.string.settings_map_scheme_fiord),
                    ),
                selected = uiState.mapSchemeDark,
                onSelect = { onAction(SettingsAction.SetMapSchemeDark(it)) },
            )
            SliderRow(
                title = stringResource(R.string.settings_group_map_tilt),
                valueLabel = stringResource(R.string.settings_map_tilt_value, uiState.mapTiltDeg),
                value = uiState.mapTiltDeg,
                range = MIN_MAP_TILT..MAX_MAP_TILT,
                onValueChange = { onAction(SettingsAction.SetMapTilt(it)) },
            )
            SliderRow(
                title = stringResource(R.string.settings_group_map_zoom),
                valueLabel = stringResource(R.string.settings_map_zoom_value, uiState.mapZoom),
                value = uiState.mapZoom,
                range = MIN_MAP_ZOOM..MAX_MAP_ZOOM,
                onValueChange = { onAction(SettingsAction.SetMapZoom(it)) },
            )
            // Marker vertical position applies to both backends (0 = map centre,
            // 100 = just above the speed overlay), so it sits outside the
            // mode-specific block below.
            SliderRow(
                title = stringResource(R.string.settings_group_map_marker_pos),
                valueLabel = stringResource(R.string.settings_map_marker_pos_value, uiState.mapMarkerPos),
                value = uiState.mapMarkerPos,
                range = MIN_MAP_MARKER_POS..MAX_MAP_MARKER_POS,
                onValueChange = { onAction(SettingsAction.SetMapMarkerPos(it)) },
            )
            // Mode-specific rows: the live (WebGL) backends expose 3D buildings /
            // terrain; the snapshot backend exposes the bitmap sharpness. Showing
            // only the rows that affect the chosen backend keeps the panel honest
            // (e.g. sharpness does nothing on the live map).
            if (uiState.mapRenderMode == MapRenderMode.SNAPSHOT) {
                SliderRow(
                    title = stringResource(R.string.settings_group_map_quality),
                    valueLabel = stringResource(R.string.settings_map_quality_value, uiState.mapRenderPercent),
                    value = uiState.mapRenderPercent,
                    range = MIN_MAP_QUALITY..MAX_MAP_QUALITY,
                    onValueChange = { onAction(SettingsAction.SetMapRenderPercent(it)) },
                    description = stringResource(R.string.settings_map_quality_desc),
                )
            } else {
                SwitchRow(
                    title = stringResource(R.string.settings_group_map_3d),
                    checked = uiState.map3dBuildings,
                    onCheckedChange = { onAction(SettingsAction.SetMap3dBuildings(it)) },
                )
                SwitchRow(
                    title = stringResource(R.string.settings_group_map_terrain),
                    checked = uiState.mapTerrain,
                    onCheckedChange = { onAction(SettingsAction.SetMapTerrain(it)) },
                    summary = stringResource(R.string.settings_map_terrain_desc),
                )
            }
        }

        SettingsSection(title = stringResource(R.string.settings_section_panels)) {
            SwitchRow(
                title = stringResource(R.string.settings_group_panel_calendar),
                checked = uiState.showCalendar,
                onCheckedChange = { onAction(SettingsAction.SetShowCalendar(it)) },
            )
            SwitchRow(
                title = stringResource(R.string.settings_group_panel_weather),
                checked = uiState.showWeather,
                onCheckedChange = { onAction(SettingsAction.SetShowWeather(it)) },
            )
            SwitchRow(
                title = stringResource(R.string.settings_group_panel_music),
                checked = uiState.showMusic,
                onCheckedChange = { onAction(SettingsAction.SetShowMusic(it)) },
            )
        }

        SettingsSection(title = stringResource(R.string.settings_group_system)) {
            ActionRow(
                title = stringResource(R.string.settings_open_notification_access),
                onClick = onOpenNotificationAccess,
            )
            ActionRow(
                title = stringResource(R.string.settings_open_system_settings),
                onClick = onOpenSystemSettings,
            )
            ResetRow(onConfirm = { onAction(SettingsAction.ResetToDefaults) })
        }
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

// A category: a small colored header above a flat (0 dp) rounded card holding the
// section's rows, echoing the Android Settings app's grouped layout.
@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp),
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(content = content)
    }
}

// The accent picker: a title over a horizontally-scrolling row of color swatches.
// Selecting a swatch applies its seed immediately, so the picker (and the whole
// app) recolors live. DYNAMIC keeps Material You wallpaper color.
@Composable
private fun AccentRow(
    selected: AccentColor,
    onSelect: (AccentColor) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
) {
    Text(
        text = stringResource(R.string.settings_group_accent),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AccentColor.entries.forEach { accent ->
            AccentSwatch(
                accent = accent,
                selected = accent == selected,
                onClick = { onSelect(accent) },
            )
        }
    }
}

// One accent swatch: a >= MinTouchTarget tap target around a circular color chip.
// A preset shows its seed; DYNAMIC shows a sweep gradient to read as "automatic".
// The selected chip grows and gains an onSurface ring (color-agnostic, unlike a
// tinted check that could vanish on a light seed).
@Composable
private fun AccentSwatch(
    accent: AccentColor,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(accent.labelRes())
    val seed = accent.accentSeedColor()
    val ringColor =
        if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier =
            modifier
                .size(FemtoDimens.MinTouchTarget)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val fill =
            if (seed != null) {
                Modifier.background(seed)
            } else {
                Modifier.background(Brush.sweepGradient(DynamicAccentSweep))
            }
        Box(
            modifier =
                Modifier
                    .size(if (selected) 44.dp else 38.dp)
                    .clip(CircleShape)
                    .then(fill)
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = ringColor,
                        shape = CircleShape,
                    ),
        )
    }
}

// A single-choice row: shows the current value as its summary and opens a radio
// dialog on tap (the Android ListPreference pattern).
@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    SettingRow(
        title = title,
        modifier = modifier.clickable { dialogOpen = true },
        summary = options.firstOrNull { it.first == selected }?.second,
    ) {
        TrailingIcon(Lucide.ChevronRight)
    }
    if (dialogOpen) {
        ChoiceDialog(
            title = title,
            options = options,
            selected = selected,
            onSelect = {
                onSelect(it)
                dialogOpen = false
            },
            onDismiss = { dialogOpen = false },
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = title) },
    text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            options.forEach { (value, label) ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = FemtoDimens.MinTouchTarget)
                            .selectable(
                                selected = value == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(value) },
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(selected = value == selected, onClick = null)
                    Text(text = label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    },
    // Tapping a radio option commits and dismisses (select-on-tap), so there is
    // no confirm action — only Cancel.
    confirmButton = {},
    dismissButton = {
        TextButton(onClick = onDismiss) {
            Text(text = stringResource(R.string.settings_cancel))
        }
    },
)

// A boolean row: the whole row is the toggle (role = Switch), so the inline
// Switch is presentation-only (onCheckedChange = null) and never double-fires. An
// optional [summary] explains what the toggle does (e.g. attribution / cost notes).
@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) = SettingRow(
    title = title,
    modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    summary = summary,
) {
    Switch(checked = checked, onCheckedChange = null)
}

// A numeric row: an inline slider under the title / current-value line, with an
// optional [description] caption beneath that explains what the value trades off.
@Composable
private fun SliderRow(
    title: String,
    valueLabel: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) = Column(
    modifier =
        modifier
            .fillMaxWidth()
            .heightIn(min = FemtoDimens.MinTouchTarget)
            .padding(horizontal = 20.dp, vertical = 10.dp),
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
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (description != null) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Slider(
        value = value.coerceIn(range.first, range.last).toFloat(),
        onValueChange = { onValueChange(it.roundToInt().coerceIn(range.first, range.last)) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
    )
}

// A navigation row: links out to a system screen, marked with an external glyph.
@Composable
private fun ActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = SettingRow(
    title = title,
    modifier = modifier.clickable(onClick = onClick),
) {
    TrailingIcon(Lucide.ExternalLink)
}

// A destructive row: resetting every setting to its default. Tapping opens a
// confirm dialog — the only destructive action in Settings — so a stray tap on
// the head unit never wipes the user's configuration.
@Composable
private fun ResetRow(
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    SettingRow(
        title = stringResource(R.string.settings_reset_to_defaults),
        modifier = modifier.clickable { dialogOpen = true },
    ) {
        TrailingIcon(Lucide.RotateCcw)
    }
    if (dialogOpen) {
        ResetConfirmDialog(
            onConfirm = {
                onConfirm()
                dialogOpen = false
            },
            onDismiss = { dialogOpen = false },
        )
    }
}

@Composable
private fun ResetConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(R.string.settings_reset_confirm_title)) },
    text = { Text(text = stringResource(R.string.settings_reset_confirm_message)) },
    confirmButton = {
        TextButton(onClick = onConfirm) {
            Text(text = stringResource(R.string.settings_reset_confirm))
        }
    },
    dismissButton = {
        TextButton(onClick = onDismiss) {
            Text(text = stringResource(R.string.settings_cancel))
        }
    },
)

// Shared row scaffold: a tap target ≥ MinTouchTarget with a title, optional
// summary, and a trailing slot. The caller supplies the interaction (clickable /
// toggleable) through [modifier] so each row keeps the right accessibility role.
@Composable
private fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    trailing: @Composable () -> Unit = {},
) = Row(
    modifier =
        modifier
            .fillMaxWidth()
            .heightIn(min = FemtoDimens.MinTouchTarget)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    trailing()
}

@Composable
private fun TrailingIcon(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
) = Icon(
    imageVector = imageVector,
    contentDescription = null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.size(FemtoDimens.InlineIconSize),
)

private const val MIN_MAP_TILT = 0
private const val MAX_MAP_TILT = 60
private const val MIN_MAP_ZOOM = 12
private const val MAX_MAP_ZOOM = 19

// Marker vertical-position band (percent): 0 centres the marker; 100 drops it to
// just above the speed panel.
private const val MIN_MAP_MARKER_POS = 0
private const val MAX_MAP_MARKER_POS = 100

// Snapshot render resolution band (percent). The floor stays well above zero so
// the upscaled map keeps roads legible; 100 is full panel resolution.
private const val MIN_MAP_QUALITY = 30
private const val MAX_MAP_QUALITY = 100

private fun Boolean.toFullscreen(): FullscreenSetting = if (this) FullscreenSetting.ON else FullscreenSetting.OFF

// The human-readable label for an accent, used as each swatch's content
// description (the swatches are color-only, so they need an accessible name).
private fun AccentColor.labelRes(): Int =
    when (this) {
        AccentColor.DYNAMIC -> R.string.settings_accent_dynamic
        AccentColor.BLUE -> R.string.settings_accent_blue
        AccentColor.TEAL -> R.string.settings_accent_teal
        AccentColor.GREEN -> R.string.settings_accent_green
        AccentColor.AMBER -> R.string.settings_accent_amber
        AccentColor.ORANGE -> R.string.settings_accent_orange
        AccentColor.RED -> R.string.settings_accent_red
        AccentColor.VIOLET -> R.string.settings_accent_violet
        AccentColor.PINK -> R.string.settings_accent_pink
    }

// Rainbow stops for the DYNAMIC swatch's sweep gradient; the first hue repeats at
// the end so the sweep closes seamlessly. Signals "automatic / wallpaper-derived".
private val DynamicAccentSweep =
    listOf(
        Color(0xFFEF5350),
        Color(0xFFFFCA28),
        Color(0xFF66BB6A),
        Color(0xFF26C6DA),
        Color(0xFF42A5F5),
        Color(0xFFAB47BC),
        Color(0xFFEF5350),
    )

// A font-slot row: the current family (or "System default") under the title,
// opening the full Google Fonts picker on tap.
@Composable
private fun FontRow(
    title: String,
    family: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = SettingRow(
    title = title,
    modifier = modifier.clickable(onClick = onClick),
    summary = family ?: stringResource(R.string.settings_font_system),
) {
    TrailingIcon(Lucide.ChevronRight)
}

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
            onOpenFontPicker = {},
        )
    }
}

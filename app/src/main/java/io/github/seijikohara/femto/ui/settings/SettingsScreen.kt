package io.github.seijikohara.femto.ui.settings

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCcw
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.AccentColor
import io.github.seijikohara.femto.data.display.AssistantLaunchSetting
import io.github.seijikohara.femto.data.display.ClockSetting
import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.display.FullscreenSetting
import io.github.seijikohara.femto.data.display.MAX_MAP_ZOOM
import io.github.seijikohara.femto.data.display.MIN_MAP_ZOOM
import io.github.seijikohara.femto.data.display.MapColorScheme
import io.github.seijikohara.femto.data.display.MapRenderMode
import io.github.seijikohara.femto.data.display.MapStyleSetting
import io.github.seijikohara.femto.data.display.OrientationSetting
import io.github.seijikohara.femto.data.display.SpeedUnitSetting
import io.github.seijikohara.femto.data.display.TemperatureUnitSetting
import io.github.seijikohara.femto.data.display.ThemeMode
import io.github.seijikohara.femto.data.display.ThemePreset
import io.github.seijikohara.femto.data.display.ThemePresets
import io.github.seijikohara.femto.data.fonts.FontSlot
import io.github.seijikohara.femto.data.location.LocationQualitySetting
import io.github.seijikohara.femto.ui.theme.DynamicAccentSweep
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
    onOpenDiagnostics: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
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
            ThemePresetRow(
                selectedPreset =
                    ThemePresets.matchingOrNull(
                        accentColor = uiState.accentColor,
                        mapSchemeLight = uiState.mapSchemeLight,
                        mapSchemeDark = uiState.mapSchemeDark,
                    ),
                onSelect = { onAction(SettingsAction.ApplyThemePreset(it)) },
            )
            SettingsSubheader(stringResource(R.string.settings_subheader_screen))
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
            SettingsSubheader(stringResource(R.string.settings_subheader_glass))
            SliderRow(
                title = stringResource(R.string.settings_group_glass_blur),
                valueLabel = stringResource(R.string.settings_glass_blur_value, uiState.glassBlurRadius),
                value = uiState.glassBlurRadius,
                range = MIN_GLASS_BLUR..MAX_GLASS_BLUR,
                onValueChange = { onAction(SettingsAction.SetGlassBlurRadius(it)) },
            )
            SliderRow(
                title = stringResource(R.string.settings_group_glass_opacity),
                valueLabel = stringResource(R.string.settings_glass_opacity_value, uiState.glassTintScale),
                value = uiState.glassTintScale,
                range = MIN_GLASS_OPACITY..MAX_GLASS_OPACITY,
                onValueChange = { onAction(SettingsAction.SetGlassTintScale(it)) },
            )
            SettingsSubheader(stringResource(R.string.settings_subheader_fonts))
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
            SettingsSubheader(stringResource(R.string.settings_subheader_map_rendering))
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
            // Snapshot exposes bitmap sharpness; the live (WebGL) backend exposes 3D
            // buildings + terrain. Each row sits directly under the rendering parent
            // and slides in/out, so the swap reads as a result of the mode change. A
            // hidden row keeps its stored value; only the layout changes.
            AnimatedVisibility(visible = uiState.mapRenderMode == MapRenderMode.SNAPSHOT) {
                SliderRow(
                    title = stringResource(R.string.settings_group_map_quality),
                    valueLabel = stringResource(R.string.settings_map_quality_value, uiState.mapRenderPercent),
                    value = uiState.mapRenderPercent,
                    range = MIN_MAP_QUALITY..MAX_MAP_QUALITY,
                    onValueChange = { onAction(SettingsAction.SetMapRenderPercent(it)) },
                    description = stringResource(R.string.settings_map_quality_desc),
                )
            }
            AnimatedVisibility(visible = uiState.mapRenderMode == MapRenderMode.LIVE) {
                Column {
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
            SettingsSubheader(stringResource(R.string.settings_subheader_map_appearance))
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
            // Light scheme applies for AUTO + LIGHT, Dark scheme for AUTO + DARK. AUTO
            // can use either (the system theme decides), so AUTO shows both; a fixed
            // LIGHT / DARK hides the scheme it never uses. Independent colour schemes:
            // ACCENT is the adaptive accent-tinted default, the rest fixed OpenFreeMap.
            AnimatedVisibility(visible = uiState.mapStyle != MapStyleSetting.DARK) {
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
            }
            AnimatedVisibility(visible = uiState.mapStyle != MapStyleSetting.LIGHT) {
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
            }
            SettingsSubheader(stringResource(R.string.settings_subheader_map_camera))
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
            SwitchRow(
                title = stringResource(R.string.settings_group_map_north_up),
                checked = uiState.mapNorthUp,
                onCheckedChange = { onAction(SettingsAction.SetMapNorthUp(it)) },
                summary = stringResource(R.string.settings_map_north_up_desc),
            )
            // Marker vertical position applies to both backends (0 = map centre,
            // 100 = just above the speed overlay).
            SliderRow(
                title = stringResource(R.string.settings_group_map_marker_pos),
                valueLabel = stringResource(R.string.settings_map_marker_pos_value, uiState.mapMarkerPos),
                value = uiState.mapMarkerPos,
                range = MIN_MAP_MARKER_POS..MAX_MAP_MARKER_POS,
                onValueChange = { onAction(SettingsAction.SetMapMarkerPos(it)) },
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_location)) {
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
            SwitchRow(
                title = stringResource(R.string.settings_group_panel_music_spectrum),
                checked = uiState.musicSpectrum,
                onCheckedChange = { onAction(SettingsAction.SetMusicSpectrum(it)) },
                summary = stringResource(R.string.settings_panel_music_spectrum_desc),
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

// A sub-group label inside a section card, separating related rows under a
// section heading. Same 18sp as the section heading but muted (onSurfaceVariant
// vs the section's primary) and indented to align with row content, so it reads
// as a child cluster rather than a new section.
@Composable
private fun SettingsSubheader(
    title: String,
    modifier: Modifier = Modifier,
) = Text(
    text = title,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.padding(start = 20.dp, top = 14.dp, bottom = 2.dp),
)

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

// The theme-preset picker: named bundles of accent + map colour schemes from the
// ThemePresets registry. Tapping one applies the whole look at once; the accent
// and map-scheme controls then reflect it. The data-driven "theme as data"
// surface — mass-producing a theme is a registry entry, not new UI here.
@Composable
private fun ThemePresetRow(
    selectedPreset: ThemePreset?,
    onSelect: (ThemePreset) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
) {
    Text(
        text = stringResource(R.string.settings_group_theme_preset),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemePresets.all.forEach { preset ->
            ThemePresetChip(
                preset = preset,
                selected = preset == selectedPreset,
                onClick = { onSelect(preset) },
            )
        }
    }
}

// One preset chip: the accent seed dot + the preset name, in a >= MinTouchTarget
// tap target. Selected swaps to the secondaryContainer fill + a primary border
// (the same color-agnostic emphasis the accent swatch uses).
@Composable
private fun ThemePresetChip(
    preset: ThemePreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val seed = preset.accentColor.accentSeedColor()
    val fill =
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val border =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val labelColor =
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier =
            modifier
                .heightIn(min = FemtoDimens.MinTouchTarget)
                .clip(MaterialTheme.shapes.large)
                .background(fill)
                .border(width = if (selected) 2.dp else 1.dp, color = border, shape = MaterialTheme.shapes.large)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val dot =
            if (seed !=
                null
            ) {
                Modifier.background(seed)
            } else {
                Modifier.background(Brush.sweepGradient(DynamicAccentSweep))
            }
        Box(modifier = Modifier.size(20.dp).clip(CircleShape).then(dot))
        Text(
            text = stringResource(preset.labelRes()),
            style = MaterialTheme.typography.titleMedium,
            color = labelColor,
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
            // A long localized title shares the row with the value via SpaceBetween;
            // weight(fill = false) keeps short titles in place but caps a long one so
            // it ellipsizes instead of wrapping and shoving the value off the row.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
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

// Marker vertical-position band (percent): 0 centres the marker; 100 drops it to
// just above the speed panel.
private const val MIN_MAP_MARKER_POS = 0
private const val MAX_MAP_MARKER_POS = 100

// Snapshot render resolution band (percent). The floor stays well above zero so
// the upscaled map keeps roads legible; 100 is full panel resolution.
private const val MIN_MAP_QUALITY = 30
private const val MAX_MAP_QUALITY = 100

// Glass-overlay blur radius (dp) and tint opacity (percent of the per-theme base
// alpha; 100 = the default look, 0 = no tint).
private const val MIN_GLASS_BLUR = 0
private const val MAX_GLASS_BLUR = 40
private const val MIN_GLASS_OPACITY = 0
private const val MAX_GLASS_OPACITY = 150

// Location-request interval slider, in 250 ms steps (250 ms – 2 s); the persisted
// value is milliseconds. Minimum-distance band in metres; 0 delivers every fix.
private const val LOCATION_INTERVAL_STEP_MS = 250L
private const val MIN_LOCATION_INTERVAL_STEPS = 1
private const val MAX_LOCATION_INTERVAL_STEPS = 8
private const val MIN_LOCATION_MIN_DISTANCE = 0
private const val MAX_LOCATION_MIN_DISTANCE = 25

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

// The display name for a theme preset (keyed by ThemePreset.key, so the registry
// stays free of any resource dependency).
private fun ThemePreset.labelRes(): Int =
    when (key) {
        "ocean" -> R.string.theme_preset_ocean
        "forest" -> R.string.theme_preset_forest
        "dusk" -> R.string.theme_preset_dusk
        else -> R.string.theme_preset_dynamic
    }

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
            onOpenDiagnostics = {},
            onOpenLicenses = {},
            onOpenPrivacyPolicy = {},
        )
    }
}

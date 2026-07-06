package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.AccentColor
import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.data.display.MapColorScheme
import io.github.seijikohara.femto.data.display.MapStyleSetting
import io.github.seijikohara.femto.data.display.ThemeMode
import io.github.seijikohara.femto.data.fonts.FontSlot
import io.github.seijikohara.femto.ui.settings.SettingsAction
import io.github.seijikohara.femto.ui.settings.SettingsUiState
import io.github.seijikohara.femto.ui.theme.DynamicAccentSweep
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.LocalFemtoDarkTheme
import io.github.seijikohara.femto.ui.theme.accentSeedColor

private const val MIN_GLASS_BLUR = 0
private const val MAX_GLASS_BLUR = 40
private const val MIN_GLASS_OPACITY = 0
private const val MAX_GLASS_OPACITY = 100

@Composable
internal fun AppearanceSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onOpenFontPicker: (FontSlot) -> Unit,
    modifier: Modifier = Modifier,
) = SettingsSection(title = stringResource(R.string.settings_section_appearance), modifier = modifier) {
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
    SettingsSubheader(stringResource(R.string.settings_subheader_map_color))
    MapThemeMatchRow(
        mapStyle = uiState.mapStyle,
        onSelect = { onAction(SettingsAction.SetMapStyle(it)) },
    )
    if (uiState.mapBackend == MapBackend.OSM) {
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
                        MapColorScheme.DARK_MATTER to
                            stringResource(R.string.settings_map_scheme_dark_matter),
                        MapColorScheme.DARK to stringResource(R.string.settings_map_scheme_dark),
                        MapColorScheme.FIORD to stringResource(R.string.settings_map_scheme_fiord),
                    ),
                selected = uiState.mapSchemeDark,
                onSelect = { onAction(SettingsAction.SetMapSchemeDark(it)) },
            )
        }
    }
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

// Theme stays the one primary light/dark control; this is the map's secondary,
// clearly-subordinate override. Checked (the default) means mapStyle == AUTO,
// which already follows the app's resolved theme (LocalFemtoDarkTheme). Turning
// it off freezes the map at whatever the app currently renders (no visual jump at
// the instant of toggling) and reveals a plain Light/Dark choice for further
// manual override.
@Composable
private fun MapThemeMatchRow(
    mapStyle: MapStyleSetting,
    onSelect: (MapStyleSetting) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appIsDark = LocalFemtoDarkTheme.current
    SwitchRow(
        title = stringResource(R.string.settings_map_match_theme),
        summary = stringResource(R.string.settings_map_match_theme_desc),
        checked = mapStyle == MapStyleSetting.AUTO,
        onCheckedChange = { matchTheme ->
            onSelect(
                when {
                    matchTheme -> MapStyleSetting.AUTO
                    appIsDark -> MapStyleSetting.DARK
                    else -> MapStyleSetting.LIGHT
                },
            )
        },
        modifier = modifier,
    )
    AnimatedVisibility(visible = mapStyle != MapStyleSetting.AUTO) {
        ChoiceRow(
            title = stringResource(R.string.settings_group_map_style),
            options =
                listOf(
                    MapStyleSetting.LIGHT to stringResource(R.string.settings_theme_light),
                    MapStyleSetting.DARK to stringResource(R.string.settings_theme_dark),
                ),
            selected = mapStyle,
            onSelect = onSelect,
        )
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

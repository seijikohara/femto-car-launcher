package io.github.seijikohara.femto.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.seijikohara.femto.data.display.ThemeMode
import io.github.seijikohara.femto.data.display.ThemePreset
import io.github.seijikohara.femto.data.display.ThemePresets
import io.github.seijikohara.femto.data.fonts.FontSlot
import io.github.seijikohara.femto.ui.settings.components.ChoiceRow
import io.github.seijikohara.femto.ui.settings.components.FontRow
import io.github.seijikohara.femto.ui.settings.components.Header
import io.github.seijikohara.femto.ui.settings.components.LocationSection
import io.github.seijikohara.femto.ui.settings.components.MapSection
import io.github.seijikohara.femto.ui.settings.components.PanelsSection
import io.github.seijikohara.femto.ui.settings.components.ScreenSection
import io.github.seijikohara.femto.ui.settings.components.SettingsSection
import io.github.seijikohara.femto.ui.settings.components.SettingsSubheader
import io.github.seijikohara.femto.ui.settings.components.SliderRow
import io.github.seijikohara.femto.ui.settings.components.SwitchRow
import io.github.seijikohara.femto.ui.settings.components.SystemSection
import io.github.seijikohara.femto.ui.settings.components.UnitsSection
import io.github.seijikohara.femto.ui.theme.DynamicAccentSweep
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.accentSeedColor

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
                accentColor = uiState.accentColor,
                selectedPreset =
                    ThemePresets.matchingOrNull(
                        accentColor = uiState.accentColor,
                        mapSchemeLight = uiState.mapSchemeLight,
                        mapSchemeDark = uiState.mapSchemeDark,
                    ),
                onSelect = { onAction(SettingsAction.ApplyThemePreset(it)) },
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

        ScreenSection(uiState = uiState, onAction = onAction)

        UnitsSection(uiState = uiState, onAction = onAction)

        MapSection(uiState = uiState, onAction = onAction)

        LocationSection(uiState = uiState, onAction = onAction)

        PanelsSection(uiState = uiState, onAction = onAction, onOpenSystemSettings = onOpenSystemSettings)

        SystemSection(
            onAction = onAction,
            onOpenNotificationAccess = onOpenNotificationAccess,
            onOpenSystemSettings = onOpenSystemSettings,
            onOpenDiagnostics = onOpenDiagnostics,
            onOpenLicenses = onOpenLicenses,
            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
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

// The theme-preset picker: named bundles of accent + map colour schemes from the
// ThemePresets registry. Tapping one applies the whole look at once; the accent
// and map-scheme controls then reflect it. The data-driven "theme as data"
// surface — mass-producing a theme is a registry entry, not new UI here. When the
// current accent/map-scheme combination matches no registered bundle, a
// non-interactive "Custom" chip renders instead of leaving every chip unselected,
// so a fine-tuned combination reads as a deliberate state rather than a stale one.
@Composable
private fun ThemePresetRow(
    accentColor: AccentColor,
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
        if (selectedPreset == null) {
            CustomPresetChip(accentColor = accentColor)
        }
    }
}

// The "you are here" readout when no preset bundle matches: same visual weight as
// a selected ThemePresetChip (secondaryContainer fill + primary border) but not
// clickable — Custom is a status, not an action to invoke. The dot shows the
// CURRENT accent live, since there is no single preset color to show.
@Composable
private fun CustomPresetChip(
    accentColor: AccentColor,
    modifier: Modifier = Modifier,
) {
    val seed = accentColor.accentSeedColor()
    Row(
        modifier =
            modifier
                .heightIn(min = FemtoDimens.MinTouchTarget)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .border(width = 2.dp, color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.large)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val dot = if (seed !=
            null
        ) {
            Modifier.background(seed)
        } else {
            Modifier.background(Brush.sweepGradient(DynamicAccentSweep))
        }
        Box(modifier = Modifier.size(20.dp).clip(CircleShape).then(dot))
        Text(
            text = stringResource(R.string.settings_theme_preset_custom),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
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

// Glass-overlay blur radius (dp) and tint opacity (percent of the per-theme base
// alpha; 100 = the default look, 0 = no tint).
private const val MIN_GLASS_BLUR = 0
private const val MAX_GLASS_BLUR = 40
private const val MIN_GLASS_OPACITY = 0
private const val MAX_GLASS_OPACITY = 100

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

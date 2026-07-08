package io.github.seijikohara.femto.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.fonts.FontSlot
import io.github.seijikohara.femto.ui.settings.components.AppearanceSection
import io.github.seijikohara.femto.ui.settings.components.Header
import io.github.seijikohara.femto.ui.settings.components.LocationSection
import io.github.seijikohara.femto.ui.settings.components.MapSection
import io.github.seijikohara.femto.ui.settings.components.PanelsSection
import io.github.seijikohara.femto.ui.settings.components.ScreenSection
import io.github.seijikohara.femto.ui.settings.components.SettingsCategoryDetail
import io.github.seijikohara.femto.ui.settings.components.SettingsCategoryList
import io.github.seijikohara.femto.ui.settings.components.SystemSection
import io.github.seijikohara.femto.ui.settings.components.UnitsSection
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

/**
 * In-app settings, laid out as a master-detail pair: a category rail / list
 * (see [SettingsCategoryList]) and a detail pane for the selected category's
 * rows (see [SettingsCategoryDetail]). [BoxWithConstraints] reads the
 * available width to pick the shape — never a specific device: **wide** shows
 * the rail and the detail pane side by side; **narrow** shows the category
 * list until one is tapped, then replaces it with the detail view (a back
 * arrow returns to the list) — the two-pane-to-list-detail fallback Android's
 * own Settings app uses between a tablet and a phone.
 *
 * A row carries a title plus the current value as a summary; single-choice
 * rows open a radio dialog, boolean rows toggle an inline switch, numeric rows
 * host an inline slider, and the System rows link out.
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
                .padding(FemtoDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Header(onBack = onBack)

        val entries =
            settingsCategoryEntries(
                uiState = uiState,
                onAction = onAction,
                onOpenFontPicker = onOpenFontPicker,
                onOpenSystemSettings = onOpenSystemSettings,
                onOpenNotificationAccess = onOpenNotificationAccess,
                onOpenDiagnostics = onOpenDiagnostics,
                onOpenLicenses = onOpenLicenses,
                onOpenPrivacyPolicy = onOpenPrivacyPolicy,
            )
        // Hoisted here (not the ViewModel): which category is showing is pure
        // navigation state, not a persisted setting. rememberSaveable keeps it
        // across rotation the same way the old per-section expand flags did.
        var selectedId by rememberSaveable { mutableStateOf(SettingsCategoryId.entries.first()) }
        // Narrow list-detail only — whether the detail view covers the list.
        // Meaningless in the wide layout, where the rail and detail pane are
        // always both visible side by side.
        var showDetail by rememberSaveable { mutableStateOf(false) }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (maxWidth >= SettingsWidePaneBreakpoint) {
                SettingsWidePane(
                    entries = entries,
                    selectedId = selectedId,
                    onSelect = { selectedId = it },
                    onAction = onAction,
                )
            } else {
                SettingsNarrowPane(
                    entries = entries,
                    selectedId = selectedId,
                    showDetail = showDetail,
                    onSelect = {
                        selectedId = it
                        showDetail = true
                    },
                    onBack = { showDetail = false },
                    onAction = onAction,
                )
            }
        }
    }
}

// The wide shape: a fixed-width category rail beside a detail pane filling
// the rest of the row, both spanning the full available height so each
// scrolls independently of the other (and of the rail's own scroll, if the
// 7 categories ever outgrow it).
@Composable
private fun SettingsWidePane(
    entries: List<SettingsCategoryEntry>,
    selectedId: SettingsCategoryId,
    onSelect: (SettingsCategoryId) -> Unit,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxSize(),
    horizontalArrangement = Arrangement.spacedBy(FemtoDimens.ScreenPadding),
) {
    SettingsCategoryList(
        selectedId = selectedId,
        onSelect = onSelect,
        modifier = Modifier.width(SettingsRailWidth).fillMaxHeight(),
    )
    val selected = entries.first { it.id == selectedId }
    SettingsCategoryDetail(
        title = stringResource(selected.id.titleRes),
        onReset = selected.id.sectionId?.let { sectionId -> { onAction(SettingsAction.ResetSection(sectionId)) } },
        content = selected.content,
        modifier = Modifier.weight(1f).fillMaxHeight(),
    )
}

// The narrow list-detail shape: the category list fills the pane until a
// category is selected, then the detail view (with a back arrow to return)
// replaces it — only one of the two is ever composed at a time.
@Composable
private fun SettingsNarrowPane(
    entries: List<SettingsCategoryEntry>,
    selectedId: SettingsCategoryId,
    showDetail: Boolean,
    onSelect: (SettingsCategoryId) -> Unit,
    onBack: () -> Unit,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) = Box(modifier = modifier.fillMaxSize()) {
    if (showDetail) {
        val selected = entries.first { it.id == selectedId }
        SettingsCategoryDetail(
            title = stringResource(selected.id.titleRes),
            onBack = onBack,
            onReset = selected.id.sectionId?.let { sectionId -> { onAction(SettingsAction.ResetSection(sectionId)) } },
            content = selected.content,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        SettingsCategoryList(
            selectedId = selectedId,
            onSelect = onSelect,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// One entry per Settings category: pairs a [SettingsCategoryId] with the rows
// composable it drives. Built once per SettingsScreen composition so the wide
// and narrow shapes above look up the very same selected entry. The per-
// category *Section composables (AppearanceSection, ScreenSection, ...) own
// only their rows now — title and reset wiring live once here (via
// SettingsCategoryId.titleRes / sectionId), not duplicated per section as
// they were when each section wrapped itself in the old collapsible card.
private data class SettingsCategoryEntry(
    val id: SettingsCategoryId,
    val content: @Composable () -> Unit,
)

@Composable
private fun settingsCategoryEntries(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onOpenFontPicker: (FontSlot) -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
): List<SettingsCategoryEntry> =
    listOf(
        SettingsCategoryEntry(SettingsCategoryId.APPEARANCE) {
            AppearanceSection(uiState = uiState, onAction = onAction, onOpenFontPicker = onOpenFontPicker)
        },
        SettingsCategoryEntry(SettingsCategoryId.SCREEN) {
            ScreenSection(uiState = uiState, onAction = onAction)
        },
        SettingsCategoryEntry(SettingsCategoryId.UNITS) {
            UnitsSection(uiState = uiState, onAction = onAction)
        },
        SettingsCategoryEntry(SettingsCategoryId.MAP) {
            MapSection(uiState = uiState, onAction = onAction)
        },
        SettingsCategoryEntry(SettingsCategoryId.LOCATION) {
            LocationSection(uiState = uiState, onAction = onAction)
        },
        SettingsCategoryEntry(SettingsCategoryId.PANELS) {
            PanelsSection(uiState = uiState, onAction = onAction, onOpenSystemSettings = onOpenSystemSettings)
        },
        SettingsCategoryEntry(SettingsCategoryId.SYSTEM) {
            SystemSection(
                onAction = onAction,
                onOpenNotificationAccess = onOpenNotificationAccess,
                onOpenSystemSettings = onOpenSystemSettings,
                onOpenDiagnostics = onOpenDiagnostics,
                onOpenLicenses = onOpenLicenses,
                onOpenPrivacyPolicy = onOpenPrivacyPolicy,
            )
        },
    )

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

// The width above which the rail + detail pane both fit comfortably: a
// ~200 dp rail (SettingsRailWidth) plus a detail pane wide enough to host a
// slider row's title/value pair without crowding.
private val SettingsWidePaneBreakpoint: Dp = 640.dp

// The category rail's fixed width in the wide layout.
private val SettingsRailWidth: Dp = 200.dp

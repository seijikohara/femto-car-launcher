package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.calendar.CalendarInfo
import io.github.seijikohara.femto.ui.settings.SettingsAction
import io.github.seijikohara.femto.ui.settings.SettingsUiState
import io.github.seijikohara.femto.ui.theme.FemtoDimens

@Composable
internal fun PanelsSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) = SettingsSection(title = stringResource(R.string.settings_section_panels), modifier = modifier) {
    SwitchRow(
        title = stringResource(R.string.settings_group_panel_calendar),
        checked = uiState.showCalendar,
        onCheckedChange = { onAction(SettingsAction.SetShowCalendar(it)) },
    )
    AnimatedVisibility(visible = uiState.showCalendar) {
        MultiSelectRow(
            title = stringResource(R.string.settings_visible_calendars),
            summary = visibleCalendarsSummary(
                uiState.hasCalendarAccess,
                uiState.availableCalendars,
                uiState.hiddenCalendarIds,
            ),
            hasCalendarAccess = uiState.hasCalendarAccess,
            calendars = uiState.availableCalendars,
            hiddenIds = uiState.hiddenCalendarIds,
            onToggle = { id, hidden -> onAction(SettingsAction.SetCalendarHidden(id, hidden)) },
            onOpenAppSettings = onOpenSystemSettings,
        )
    }
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
    SwitchRow(
        title = stringResource(R.string.settings_group_panel_music_album),
        checked = uiState.musicShowAlbum,
        onCheckedChange = { onAction(SettingsAction.SetMusicShowAlbum(it)) },
        summary = stringResource(R.string.settings_panel_music_album_desc),
    )
    SwitchRow(
        title = stringResource(R.string.settings_group_panel_music_art),
        checked = uiState.musicShowArt,
        onCheckedChange = { onAction(SettingsAction.SetMusicShowArt(it)) },
        summary = stringResource(R.string.settings_panel_music_art_desc),
    )
}

// Summarises the current visible-calendar selection in one line for the row subtitle.
// Three distinct states: permission denied, granted but no calendars, or a count of
// visible vs total. An empty hidden-ids set means every calendar is shown.
@Composable
private fun visibleCalendarsSummary(
    hasCalendarAccess: Boolean,
    calendars: List<CalendarInfo>,
    hiddenIds: Set<Long>,
): String =
    when {
        !hasCalendarAccess -> {
            stringResource(R.string.settings_visible_calendars_none)
        }

        calendars.isEmpty() -> {
            stringResource(R.string.settings_visible_calendars_empty)
        }

        hiddenIds.isEmpty() -> {
            stringResource(R.string.settings_visible_calendars_all)
        }

        else -> {
            val shown = calendars.count { it.id !in hiddenIds }
            stringResource(R.string.settings_visible_calendars_count, shown, calendars.size)
        }
    }

// A choice row that opens a multi-select dialog — same open/dismiss pattern as
// ChoiceRow, but the dialog hosts checkboxes instead of radio buttons so multiple
// calendars can be independently toggled.
@Composable
private fun MultiSelectRow(
    title: String,
    summary: String,
    hasCalendarAccess: Boolean,
    calendars: List<CalendarInfo>,
    hiddenIds: Set<Long>,
    onToggle: (Long, Boolean) -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    SettingRow(
        modifier = modifier.clickable { open = true },
        title = title,
        summary = summary,
    )
    if (open) {
        MultiSelectDialog(
            title = title,
            hasCalendarAccess = hasCalendarAccess,
            calendars = calendars,
            hiddenIds = hiddenIds,
            onToggle = onToggle,
            onOpenAppSettings = onOpenAppSettings,
            onDismiss = { open = false },
        )
    }
}

// Multi-select dialog for the visible-calendars picker. Three states: permission
// denied (shows a grant button linking to system settings), granted but no calendars
// found, or the checkbox list. Each calendar row shows a colour dot (12 dp decorative
// marker from CalendarInfo.color), display name, and account name. The whole row is
// the toggle target (toggleable with Role.Checkbox); the Checkbox is visual-only
// (onCheckedChange = null) so a single tap never double-fires. Touch targets meet
// FemtoDimens.MinTouchTarget; text uses bodyLarge / bodyMedium.
@Composable
private fun MultiSelectDialog(
    title: String,
    hasCalendarAccess: Boolean,
    calendars: List<CalendarInfo>,
    hiddenIds: Set<Long>,
    onToggle: (Long, Boolean) -> Unit,
    onOpenAppSettings: () -> Unit,
    onDismiss: () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
        when {
            !hasCalendarAccess -> {
                Column {
                    Text(stringResource(R.string.settings_visible_calendars_none))
                    TextButton(
                        onClick = {
                            onOpenAppSettings()
                            onDismiss()
                        },
                        modifier = Modifier.heightIn(min = FemtoDimens.MinTouchTarget),
                    ) {
                        Text(stringResource(R.string.settings_open_system_settings))
                    }
                }
            }

            calendars.isEmpty() -> {
                Text(stringResource(R.string.settings_visible_calendars_empty))
            }

            else -> {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    calendars.forEach { calendar ->
                        val shown = calendar.id !in hiddenIds
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = FemtoDimens.MinTouchTarget)
                                    // Hidden is the inverse of the new shown state.
                                    .toggleable(
                                        value = shown,
                                        role = Role.Checkbox,
                                        onValueChange = { newShown -> onToggle(calendar.id, !newShown) },
                                    ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Checkbox(checked = shown, onCheckedChange = null)
                            Box(
                                modifier =
                                    Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(calendar.color)),
                            )
                            Column {
                                Text(calendar.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(calendar.accountName, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    },
    confirmButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_dialog_close)) }
    },
)

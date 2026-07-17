package io.github.seijikohara.femto.ui.settings.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.location.LocationQualitySetting
import io.github.seijikohara.femto.data.location.TrackRetentionSetting
import io.github.seijikohara.femto.ui.settings.SettingsAction
import io.github.seijikohara.femto.ui.settings.SettingsUiState
import io.github.seijikohara.femto.ui.settings.TrackExportState

private const val LOCATION_INTERVAL_STEP_MS = 250L
private const val MIN_LOCATION_INTERVAL_STEPS = 1
private const val MAX_LOCATION_INTERVAL_STEPS = 8
private const val MIN_LOCATION_MIN_DISTANCE = 0
private const val MAX_LOCATION_MIN_DISTANCE = 25

// The GPX community MIME type (unregistered with IANA, but what the GPX
// recorder genre shares files as); the suggested name carries the extension
// because Android's MimeTypeMap cannot derive one from this type.
private const val GPX_EXPORT_MIME_TYPE = "application/gpx+xml"
private const val GPX_EXPORT_SUGGESTED_NAME = "femto-track-log.gpx"

// The Location category's rows; see AppearanceSection's header comment on why
// there is no title / reset wiring here.
@Composable
internal fun LocationSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier) {
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
    SwitchRow(
        title = stringResource(R.string.settings_group_track_recording),
        checked = uiState.trackRecordingEnabled,
        onCheckedChange = { onAction(SettingsAction.SetTrackRecording(it)) },
        summary = stringResource(R.string.settings_track_recording_desc),
    )
    ChoiceRow(
        title = stringResource(R.string.settings_group_track_retention),
        options =
            listOf(
                TrackRetentionSetting.DAYS_30 to stringResource(R.string.settings_track_retention_30),
                TrackRetentionSetting.DAYS_90 to stringResource(R.string.settings_track_retention_90),
                TrackRetentionSetting.DAYS_365 to stringResource(R.string.settings_track_retention_365),
                TrackRetentionSetting.UNLIMITED to stringResource(R.string.settings_track_retention_unlimited),
            ),
        selected = uiState.trackRetention,
        onSelect = { onAction(SettingsAction.SetTrackRetention(it)) },
    )
    // SAF document picker: the user chooses where the GPX lands, so no storage
    // permission enters the manifest; a null uri means they backed out.
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(GPX_EXPORT_MIME_TYPE)) { uri ->
            uri?.let { onAction(SettingsAction.ExportTrackLog(it)) }
        }
    ActionRow(
        title = stringResource(R.string.settings_track_export),
        summary = trackExportSummary(uiState.trackExport),
        onClick = { exportLauncher.launch(GPX_EXPORT_SUGGESTED_NAME) },
    )
    ResetRow(
        onConfirm = { onAction(SettingsAction.ClearTrackHistory) },
        title = stringResource(R.string.settings_track_delete),
        confirmTitle = stringResource(R.string.settings_track_delete_confirm_title),
        confirmMessage = stringResource(R.string.settings_track_delete_confirm_message),
        icon = Lucide.Trash2,
    )
}

// The export row's summary doubles as its status line: the resting description
// until an export runs, then live progress / outcome.
@Composable
private fun trackExportSummary(state: TrackExportState): String =
    when (state) {
        TrackExportState.Idle -> {
            stringResource(R.string.settings_track_export_desc)
        }

        TrackExportState.Running -> {
            stringResource(R.string.settings_track_export_running)
        }

        is TrackExportState.Done -> {
            pluralStringResource(
                R.plurals.settings_track_export_done,
                // Quantity selection saturates at Int.MAX_VALUE; the format
                // argument keeps the exact long count.
                state.points.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                state.points,
            )
        }

        TrackExportState.Failed -> {
            stringResource(R.string.settings_track_export_failed)
        }
    }

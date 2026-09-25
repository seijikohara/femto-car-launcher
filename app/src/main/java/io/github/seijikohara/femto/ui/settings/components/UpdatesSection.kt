package io.github.seijikohara.femto.ui.settings.components

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PackageCheck
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.RotateCw
import io.github.seijikohara.femto.BuildConfig
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.update.UpdateChannel
import io.github.seijikohara.femto.data.update.UpdateFailure
import io.github.seijikohara.femto.ui.settings.SettingsAction
import io.github.seijikohara.femto.ui.settings.SettingsDocument
import io.github.seijikohara.femto.ui.settings.SettingsUiState
import io.github.seijikohara.femto.ui.settings.UpdateStatus
import io.github.seijikohara.femto.ui.settings.UpdateStep
import io.github.seijikohara.femto.ui.settings.UpdatesUiState
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.PreviewTextStress
import java.time.Instant
import kotlin.math.roundToInt

// The Updates category's rows: the running build, the updater's status with
// the one step it offers next, the daily check, and the release page as the
// manual path. See AppearanceSection's header comment on why there is no
// title / reset wiring here. Opening the section starts nothing by itself:
// every step waits for a tap.
@Composable
internal fun UpdatesSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onOpenDocument: (SettingsDocument) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier) {
    val updates = uiState.updates
    updates.updatedTo?.let { version ->
        UpdatedNotice(version = version)
        // Acknowledged when the section closes: the notice stays for as long as
        // it is on screen, and does not come back afterwards. Read through
        // rememberUpdatedState: keying the effect on the callback would restart
        // it, and so acknowledge, whenever the host passes a new lambda.
        val currentOnAction by rememberUpdatedState(onAction)
        DisposableEffect(version) { onDispose { currentOnAction(SettingsAction.AcknowledgeUpdatedTo) } }
    }
    SettingRow(
        title = stringResource(R.string.settings_updates_version),
        summary = stringResource(R.string.settings_updates_version_summary, BuildConfig.VERSION_NAME, channelLabel()),
    )
    UpdateStatusRow(
        status = updates.status,
        canCheck = updates.canCheck,
        onCheck = { onAction(SettingsAction.CheckForUpdates) },
    )
    updates.step?.let { step -> UpdateStepRow(step = step, onAction = onAction) }
    // A build that never checks has no daily check to switch.
    if (updates.status != UpdateStatus.Disabled) {
        SwitchRow(
            title = stringResource(R.string.settings_updates_auto_check),
            checked = updates.autoCheck,
            onCheckedChange = { onAction(SettingsAction.SetUpdateAutoCheck(it)) },
            summary = stringResource(R.string.settings_updates_auto_check_desc),
        )
    }
    ActionRow(
        title = stringResource(R.string.settings_updates_release_page),
        onClick = { onOpenDocument(SettingsDocument.RELEASE_PAGE) },
        summary = stringResource(R.string.settings_updates_release_page_desc),
    )
}

// The confirmation that the update this process succeeded is in place.
@Composable
private fun UpdatedNotice(
    version: String,
    modifier: Modifier = Modifier,
) = SettingRow(
    title = stringResource(R.string.settings_updates_updated_to, version),
    modifier = modifier,
    summary = stringResource(R.string.settings_updates_updated_to_desc),
) {
    FemtoIcon(
        imageVector = Lucide.CircleCheck,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(FemtoDimens.InlineIconSize),
    )
}

// The check row doubles as the updater's status line (the track-export row's
// pattern): a tap checks, and the summary says where the updater stands. While
// a check cannot start (one is running, the offer is being acted on, or the
// build never checks) the row only reports, rather than take a tap that does
// nothing.
@Composable
private fun UpdateStatusRow(
    status: UpdateStatus,
    canCheck: Boolean,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier) {
    val title = stringResource(R.string.settings_updates_check)
    val summary = updateStatusSummary(status)
    // Each outcome is announced, but not each percent of a download: the bar
    // below reports the progress to accessibility services itself.
    val announce = status !is UpdateStatus.Downloading
    if (canCheck) {
        ActionRow(
            title = title,
            onClick = onCheck,
            summary = summary,
            summaryLiveRegion = announce,
            icon = Lucide.RefreshCw,
        )
    } else {
        SettingRow(title = title, summary = summary, summaryLiveRegion = announce)
    }
    if (status is UpdateStatus.Downloading) {
        LinearProgressIndicator(
            progress = { status.fraction },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingRowHorizontalInset)
                    .padding(bottom = SettingRowVerticalInset),
        )
    }
}

// The one step the updater offers next (see UpdateStep).
@Composable
private fun UpdateStepRow(
    step: UpdateStep,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) = when (step) {
    is UpdateStep.Download -> {
        ActionRow(
            title = stringResource(R.string.settings_updates_download),
            onClick = { onAction(SettingsAction.DownloadUpdate) },
            modifier = modifier,
            summary = stringResource(R.string.settings_updates_download_size, fileSize(step.sizeBytes)),
            icon = Lucide.Download,
        )
    }

    is UpdateStep.Install -> {
        InstallStepRow(
            title = stringResource(R.string.settings_updates_install),
            summary = stringResource(R.string.settings_updates_install_desc),
            blockedWhileMoving = step.blockedWhileMoving,
            grantDeclined = step.grantDeclined,
            onClick = { onAction(SettingsAction.InstallUpdate) },
            modifier = modifier,
        )
    }

    is UpdateStep.ShowInstallDialog -> {
        InstallStepRow(
            title = stringResource(R.string.settings_updates_show_install_dialog),
            summary = stringResource(R.string.settings_updates_show_install_dialog_desc),
            blockedWhileMoving = step.blockedWhileMoving,
            grantDeclined = step.grantDeclined,
            onClick = { onAction(SettingsAction.InstallUpdate) },
            modifier = modifier,
        )
    }

    is UpdateStep.Retry -> {
        ActionRow(
            title = stringResource(R.string.settings_updates_retry),
            onClick = { onAction(SettingsAction.DownloadUpdate) },
            modifier = modifier,
            summary = stringResource(R.string.settings_updates_retry_desc, step.versionName, fileSize(step.sizeBytes)),
            icon = Lucide.RotateCw,
        )
    }
}

// A step that puts the system's install confirmation on screen. While a fix
// shows the vehicle moving it stays in place, untappable, and says when it
// becomes available, rather than vanishing and leaving the ready update
// unexplained. After a trip to the "Install unknown apps" access that came
// back without it, the row says so, and a tap sends the user there again.
@Composable
private fun InstallStepRow(
    title: String,
    summary: String,
    blockedWhileMoving: Boolean,
    grantDeclined: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = if (blockedWhileMoving) {
    SettingRow(title = title, modifier = modifier, summary = stringResource(R.string.settings_updates_parked_only))
} else {
    ActionRow(
        title = title,
        onClick = onClick,
        modifier = modifier,
        summary = if (grantDeclined) stringResource(R.string.settings_updates_install_grant_declined) else summary,
        // The decline is an outcome, so it is announced like the check row's.
        summaryLiveRegion = grantDeclined,
        icon = Lucide.PackageCheck,
    )
}

// A download size in the device's locale, e.g. "45 MB".
@Composable
private fun fileSize(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

@Composable
private fun updateStatusSummary(status: UpdateStatus): String =
    when (status) {
        UpdateStatus.Disabled -> {
            stringResource(R.string.settings_updates_status_disabled)
        }

        is UpdateStatus.Idle -> {
            status.lastAttemptAt
                ?.let { stringResource(R.string.settings_updates_status_last_checked, attemptTime(it)) }
                ?: stringResource(R.string.settings_updates_status_never_checked)
        }

        UpdateStatus.Checking -> {
            stringResource(R.string.settings_updates_status_checking)
        }

        is UpdateStatus.UpToDate -> {
            status.lastAttemptAt
                ?.let { stringResource(R.string.settings_updates_status_up_to_date_at, attemptTime(it)) }
                ?: stringResource(R.string.settings_updates_status_up_to_date)
        }

        is UpdateStatus.Available -> {
            stringResource(R.string.settings_updates_status_available, status.versionName)
        }

        is UpdateStatus.Downloading -> {
            stringResource(
                R.string.settings_updates_status_downloading,
                status.versionName,
                (status.fraction * 100).roundToInt(),
            )
        }

        is UpdateStatus.Ready -> {
            stringResource(R.string.settings_updates_status_ready, status.versionName)
        }

        is UpdateStatus.Installing -> {
            stringResource(R.string.settings_updates_status_installing, status.versionName)
        }

        is UpdateStatus.Failed -> {
            stringResource(failureText(status.reason))
        }
    }

// Each failure phrased for the person who has to act on it.
@StringRes
private fun failureText(reason: UpdateFailure): Int =
    when (reason) {
        UpdateFailure.NETWORK -> R.string.settings_updates_failed_network
        UpdateFailure.RATE_LIMITED -> R.string.settings_updates_failed_rate_limited
        UpdateFailure.STORAGE -> R.string.settings_updates_failed_storage
        UpdateFailure.VERIFY -> R.string.settings_updates_failed_verify
        UpdateFailure.INSTALL_CONFLICT -> R.string.settings_updates_failed_install_conflict
        UpdateFailure.INSTALL_BLOCKED -> R.string.settings_updates_failed_install_blocked
        UpdateFailure.OTHER -> R.string.settings_updates_failed_other
    }

// Date and time in the device's locale and 12/24-hour choice, the way the trip
// panel dates its trips.
@Composable
private fun attemptTime(at: Instant): String =
    DateUtils.formatDateTime(
        LocalContext.current,
        at.toEpochMilli(),
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
    )

// The channel this build follows, named for a person; a flavor without a feed
// of its own shows its raw name.
@Composable
private fun channelLabel(): String =
    when (UpdateChannel.fromFlavorOrNull(BuildConfig.FLAVOR)) {
        UpdateChannel.STABLE -> stringResource(R.string.settings_updates_channel_stable)
        UpdateChannel.NIGHTLY -> stringResource(R.string.settings_updates_channel_nightly)
        null -> BuildConfig.FLAVOR
    }

// An update on offer: the download step shows its size before anything moves.
@PreviewLightDark
@PreviewTextStress
@Composable
private fun UpdatesSectionAvailablePreview() =
    UpdatesSectionPreviewHost(
        UpdatesUiState(
            status = UpdateStatus.Available(PREVIEW_VERSION),
            canCheck = true,
            step = UpdateStep.Download(sizeBytes = PREVIEW_APK_BYTES),
            autoCheck = true,
            updatedTo = null,
            updateOffered = true,
        ),
    )

// A download under way: the status line counts up and the bar tracks it.
@PreviewLightDark
@Composable
private fun UpdatesSectionDownloadingPreview() =
    UpdatesSectionPreviewHost(
        UpdatesUiState(
            status = UpdateStatus.Downloading(PREVIEW_VERSION, fraction = 0.42f),
            canCheck = false,
            step = null,
            autoCheck = true,
            updatedTo = null,
            updateOffered = true,
        ),
    )

// A verified update while a fix shows the vehicle moving: the install step
// waits, and says so. The notice from the previous update still shows.
@PreviewLightDark
@Composable
private fun UpdatesSectionReadyWhileMovingPreview() =
    UpdatesSectionPreviewHost(
        UpdatesUiState(
            status = UpdateStatus.Ready(PREVIEW_VERSION),
            canCheck = false,
            step = UpdateStep.Install(blockedWhileMoving = true, grantDeclined = false),
            autoCheck = true,
            updatedTo = "2026.09.24-1",
            updateOffered = true,
        ),
    )

// A verified update after the "Install unknown apps" access came back off:
// the install row says why nothing was installed.
@PreviewLightDark
@Composable
private fun UpdatesSectionGrantDeclinedPreview() =
    UpdatesSectionPreviewHost(
        UpdatesUiState(
            status = UpdateStatus.Ready(PREVIEW_VERSION),
            canCheck = false,
            step = UpdateStep.Install(blockedWhileMoving = false, grantDeclined = true),
            autoCheck = true,
            updatedTo = null,
            updateOffered = true,
        ),
    )

// A failed download whose offer is still known: phrased for a person, with a
// retry that shows what it may download.
@PreviewLightDark
@Composable
private fun UpdatesSectionFailedPreview() =
    UpdatesSectionPreviewHost(
        UpdatesUiState(
            status = UpdateStatus.Failed(UpdateFailure.NETWORK),
            canCheck = true,
            step = UpdateStep.Retry(PREVIEW_VERSION, PREVIEW_APK_BYTES),
            autoCheck = false,
            updatedTo = null,
            updateOffered = true,
        ),
    )

@Composable
private fun UpdatesSectionPreviewHost(
    updates: UpdatesUiState,
    modifier: Modifier = Modifier,
) = FemtoTheme {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainer) {
        UpdatesSection(
            uiState = SettingsUiState.Initial.copy(updates = updates),
            onAction = {},
            onOpenDocument = {},
        )
    }
}

private const val PREVIEW_VERSION = "2026.09.25-1"

// A release APK's size, the scale the download step's summary has to fit.
private const val PREVIEW_APK_BYTES = 45_310_215L

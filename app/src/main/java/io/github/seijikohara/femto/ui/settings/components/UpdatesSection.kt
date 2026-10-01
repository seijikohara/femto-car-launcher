package io.github.seijikohara.femto.ui.settings.components

import android.text.format.DateUtils
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
import androidx.lifecycle.compose.LifecycleStartEffect
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PackageCheck
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.Trash2
import io.github.seijikohara.femto.BuildConfig
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.update.UpdateChannel
import io.github.seijikohara.femto.data.update.UpdateFailure
import io.github.seijikohara.femto.ui.common.fileSize
import io.github.seijikohara.femto.ui.settings.AvailableVersion
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

// The Updates category's rows: the running build, the build on offer, the
// check, the one step the updater offers next, discarding a staged download
// and skipping the offered build, the daily check, and the release page as
// the manual path. See AppearanceSection's header comment on
// why there is no title / reset wiring here. Opening the section starts
// nothing by itself (the dashboard prompt's "Update" starts the one-tap update
// through SettingsRoute). Each step starts with a tap, except the one-tap
// update's install, which follows its verified download while the section
// stays on screen.
@Composable
internal fun UpdatesSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onOpenDocument: (SettingsDocument) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier) {
    val updates = uiState.updates
    // Read through rememberUpdatedState: keying the effects below on the
    // callback would restart them, and so report, whenever the host passes a
    // new lambda.
    val currentOnAction by rememberUpdatedState(onAction)
    // On screen while composed and started. Meanwhile every offer shown counts
    // as seen, so the dashboard's prompt never asks about it. Off screen once it
    // leaves the composition or the screen stops (behind another app): a
    // one-tap update under way then no longer installs by itself, later or
    // elsewhere.
    LifecycleStartEffect(Unit) {
        currentOnAction(SettingsAction.UpdatesShown)
        onStopOrDispose { currentOnAction(SettingsAction.UpdatesHidden) }
    }
    updates.updatedTo?.let { version ->
        UpdatedNotice(version = version)
        // Acknowledged when the section closes: the notice stays for as long as
        // it is on screen, and does not come back afterwards.
        DisposableEffect(version) { onDispose { currentOnAction(SettingsAction.AcknowledgeUpdatedTo) } }
    }
    SettingRow(
        title = stringResource(R.string.settings_updates_version),
        summary = stringResource(R.string.settings_updates_version_summary, BuildConfig.VERSION_NAME, channelLabel()),
    )
    updates.availableVersion?.let { available -> AvailableVersionRow(available = available) }
    UpdateStatusRow(
        status = updates.status,
        canCheck = updates.canCheck,
        onCheck = { onAction(SettingsAction.CheckForUpdates) },
    )
    updates.step?.let { step -> UpdateStepRow(step = step, onAction = onAction) }
    if (updates.canDiscard) {
        ActionRow(
            title = stringResource(R.string.settings_updates_discard),
            onClick = { onAction(SettingsAction.DiscardUpdate) },
            summary = stringResource(R.string.settings_updates_discard_desc),
            icon = Lucide.Trash2,
        )
    }
    // The offer always names its build, so the row reads it from there.
    (updates.availableVersion as? AvailableVersion.Offered)?.takeIf { updates.canSkip }?.let { offered ->
        ActionRow(
            title = stringResource(R.string.settings_updates_skip),
            onClick = { onAction(SettingsAction.SkipUpdate) },
            summary = stringResource(R.string.settings_updates_skip_desc, offered.versionName),
            icon = Lucide.BellOff,
        )
    }
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

// The build on offer, or a check's finding that there is none. What a check
// found is announced here, as the check row announces the check itself.
@Composable
private fun AvailableVersionRow(
    available: AvailableVersion,
    modifier: Modifier = Modifier,
) = SettingRow(
    title = stringResource(R.string.settings_updates_available),
    modifier = modifier,
    summary =
        when (available) {
            is AvailableVersion.Offered -> {
                stringResource(
                    if (available.skipped) {
                        R.string.settings_updates_available_summary_skipped
                    } else {
                        R.string.settings_updates_available_summary
                    },
                    available.versionName,
                    fileSize(available.sizeBytes),
                )
            }

            AvailableVersion.UpToDate -> {
                stringResource(R.string.settings_updates_up_to_date)
            }
        },
    summaryLiveRegion = true,
)

// The check row reports the check itself (the track-export row's pattern): a
// tap checks, and the summary says when the last check ran or why it failed.
// What it found is the "Available version" row's. While a check cannot start
// (one is running, the offer is being acted on, or the build never checks) the
// row only reports, rather than take a tap that does nothing.
@Composable
private fun UpdateStatusRow(
    status: UpdateStatus,
    canCheck: Boolean,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
) = if (canCheck) {
    ActionRow(
        title = stringResource(R.string.settings_updates_check),
        onClick = onCheck,
        modifier = modifier,
        summary = updateStatusSummary(status),
        summaryLiveRegion = true,
        icon = Lucide.RefreshCw,
    )
} else {
    SettingRow(
        title = stringResource(R.string.settings_updates_check),
        modifier = modifier,
        summary = updateStatusSummary(status),
        summaryLiveRegion = true,
    )
}

// The one step the updater offers next (see UpdateStep). The one-tap update is
// one "Update to …" row through its stages: offered, downloading, verified,
// and handed to the installer.
@Composable
private fun UpdateStepRow(
    step: UpdateStep,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) = when (step) {
    is UpdateStep.Download -> {
        ActionRow(
            title = updateTitle(step.versionName),
            onClick = {
                onAction(
                    if (step.downloadOnly) SettingsAction.DownloadUpdate else SettingsAction.StartUpdate,
                )
            },
            modifier = modifier,
            summary =
                if (step.grantDeclined) {
                    // Nothing has downloaded yet, so the copy says the update did not start.
                    stringResource(R.string.settings_updates_update_grant_declined)
                } else {
                    stringResource(R.string.settings_updates_update_download_desc, fileSize(step.sizeBytes))
                },
            // The decline is an outcome, so it is announced like the check row's.
            summaryLiveRegion = step.grantDeclined,
            icon = Lucide.Download,
        )
    }

    is UpdateStep.Downloading -> {
        DownloadingRow(title = updateTitle(step.versionName), fraction = step.fraction, modifier = modifier)
    }

    is UpdateStep.Install -> {
        InstallStepRow(
            title = updateTitle(step.versionName),
            summary = stringResource(R.string.settings_updates_update_ready_desc),
            blockedWhileMoving = step.blockedWhileMoving,
            grantDeclined = step.grantDeclined,
            onClick = { onAction(SettingsAction.InstallUpdate) },
            modifier = modifier,
            announceSummary = true,
        )
    }

    // The outcome of the install tap, announced like the download's.
    is UpdateStep.Installing -> {
        SettingRow(
            title = updateTitle(step.versionName),
            modifier = modifier,
            summary = stringResource(R.string.settings_updates_update_installing),
            summaryLiveRegion = true,
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

// The one-tap update's download under way: the summary counts up, and the bar
// under the row tracks it. The row takes no tap meanwhile.
@Composable
private fun DownloadingRow(
    title: String,
    fraction: Float,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier) {
    // Not announced percent by percent: the bar reports the progress to
    // accessibility services itself.
    SettingRow(
        title = title,
        summary = stringResource(R.string.settings_updates_update_downloading, (fraction * 100).roundToInt()),
    )
    LinearProgressIndicator(
        progress = { fraction },
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingRowHorizontalInset)
                .padding(bottom = SettingRowVerticalInset),
    )
}

// A step that puts the system's install confirmation on screen. While a fix
// shows the vehicle moving it stays in place, untappable, and says when it
// becomes available, rather than vanishing and leaving the ready update
// unexplained. After a trip to the "Install unknown apps" access that came
// back without it, the row says so, and a tap sends the user there again.
// [announceSummary] marks a summary that is itself an outcome (the verified
// download), announced as it appears.
@Composable
private fun InstallStepRow(
    title: String,
    summary: String,
    blockedWhileMoving: Boolean,
    grantDeclined: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    announceSummary: Boolean = false,
) = if (blockedWhileMoving) {
    SettingRow(title = title, modifier = modifier, summary = stringResource(R.string.settings_updates_parked_only))
} else {
    ActionRow(
        title = title,
        onClick = onClick,
        modifier = modifier,
        summary = if (grantDeclined) stringResource(R.string.settings_updates_install_grant_declined) else summary,
        // The decline is an outcome, so it is announced like the check row's.
        summaryLiveRegion = grantDeclined || announceSummary,
        icon = Lucide.PackageCheck,
    )
}

// The one-tap update row's title, through every stage.
@Composable
private fun updateTitle(versionName: String): String = stringResource(R.string.settings_updates_update, versionName)

// Null for a result whose attempt time the store lost: the row then shows its
// title alone rather than claim no check has run.
@Composable
private fun updateStatusSummary(status: UpdateStatus): String? =
    when (status) {
        UpdateStatus.Disabled -> {
            stringResource(R.string.settings_updates_status_disabled)
        }

        UpdateStatus.NeverChecked -> {
            stringResource(R.string.settings_updates_status_never_checked)
        }

        UpdateStatus.Checking -> {
            stringResource(R.string.settings_updates_status_checking)
        }

        is UpdateStatus.Checked -> {
            status.lastAttemptAt?.let { stringResource(R.string.settings_updates_status_last_checked, attemptTime(it)) }
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
        UpdateFailure.DEVELOPER_VERIFICATION_OFFLINE -> R.string.settings_updates_failed_developer_verification_offline
        UpdateFailure.DEVELOPER_VERIFICATION_BLOCKED -> R.string.settings_updates_failed_developer_verification_blocked
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

// An update on offer: the "Available version" row names it, and the one-tap
// update row shows its size before anything moves.
@PreviewLightDark
@PreviewTextStress
@Composable
private fun UpdatesSectionAvailablePreview() =
    UpdatesSectionPreviewHost(
        UpdatesUiState(
            status = UpdateStatus.Checked(PreviewAttempt),
            canCheck = true,
            availableVersion = PreviewOffer,
            step = UpdateStep.Download(PREVIEW_VERSION, PREVIEW_APK_BYTES, grantDeclined = false, downloadOnly = false),
            canDiscard = false,
            canSkip = true,
            autoCheck = true,
            updatedTo = null,
            updateOffered = true,
        ),
    )

// A skipped build: still named, and marked so; the update stays a deliberate
// choice, and the skip row is gone.
@PreviewLightDark
@Composable
private fun UpdatesSectionSkippedPreview() =
    UpdatesSectionPreviewHost(
        UpdatesUiState(
            status = UpdateStatus.Checked(PreviewAttempt),
            canCheck = true,
            availableVersion = PreviewOffer.copy(skipped = true),
            step = UpdateStep.Download(PREVIEW_VERSION, PREVIEW_APK_BYTES, grantDeclined = false, downloadOnly = false),
            canDiscard = false,
            canSkip = false,
            autoCheck = true,
            updatedTo = null,
            updateOffered = false,
        ),
    )

// Nothing newer: the "Available version" row says so, and no step follows.
@PreviewLightDark
@Composable
private fun UpdatesSectionUpToDatePreview() =
    UpdatesSectionPreviewHost(
        UpdatesUiState(
            status = UpdateStatus.Checked(PreviewAttempt),
            canCheck = true,
            availableVersion = AvailableVersion.UpToDate,
            step = null,
            canDiscard = false,
            canSkip = false,
            autoCheck = true,
            updatedTo = null,
            updateOffered = false,
        ),
    )

// A download under way: the update row counts up and the bar tracks it.
@PreviewLightDark
@Composable
private fun UpdatesSectionDownloadingPreview() =
    UpdatesSectionPreviewHost(
        UpdatesUiState(
            status = UpdateStatus.Checked(PreviewAttempt),
            canCheck = false,
            availableVersion = PreviewOffer,
            step = UpdateStep.Downloading(PREVIEW_VERSION, fraction = 0.42f),
            canDiscard = false,
            canSkip = false,
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
            status = UpdateStatus.Checked(PreviewAttempt),
            canCheck = false,
            availableVersion = PreviewOffer,
            step = UpdateStep.Install(PREVIEW_VERSION, blockedWhileMoving = true, grantDeclined = false),
            canDiscard = true,
            canSkip = true,
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
            status = UpdateStatus.Checked(PreviewAttempt),
            canCheck = false,
            availableVersion = PreviewOffer,
            step = UpdateStep.Install(PREVIEW_VERSION, blockedWhileMoving = false, grantDeclined = true),
            canDiscard = true,
            canSkip = true,
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
            availableVersion = PreviewOffer,
            step = UpdateStep.Retry(PREVIEW_VERSION, PREVIEW_APK_BYTES),
            canDiscard = false,
            canSkip = true,
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

private val PreviewAttempt = Instant.parse("2026-09-25T08:30:00Z")

private val PreviewOffer = AvailableVersion.Offered(PREVIEW_VERSION, PREVIEW_APK_BYTES)

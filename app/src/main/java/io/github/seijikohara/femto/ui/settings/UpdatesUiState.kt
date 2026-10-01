package io.github.seijikohara.femto.ui.settings

import io.github.seijikohara.femto.data.update.DEFAULT_AUTO_CHECK
import io.github.seijikohara.femto.data.update.UpdateFailure
import io.github.seijikohara.femto.data.update.UpdateSettings
import io.github.seijikohara.femto.data.update.UpdateState
import io.github.seijikohara.femto.data.update.discardableOrNull
import io.github.seijikohara.femto.data.update.isResting
import io.github.seijikohara.femto.data.update.offeredManifestOrNull
import io.github.seijikohara.femto.data.update.offersUpdate
import io.github.seijikohara.femto.data.update.skippableOrNull
import io.github.seijikohara.femto.data.update.skipped
import java.time.Instant

/**
 * What the Updates section shows: the check row ([status]), the build on offer
 * or a check's finding that there is none ([availableVersion]), and the one
 * step the updater offers next ([step]), plus the daily check and the
 * one-time notice. Each fact has one row: the check row reports the check, the
 * "Available version" row what it found, the step row what to do about it.
 * [SettingsViewModel] derives it with [updatesUiState]; nothing here is
 * persisted but [autoCheck].
 */
internal data class UpdatesUiState(
    val status: UpdateStatus,
    /** Whether a tap on the check row starts a check; while one cannot start, the row only reports. */
    val canCheck: Boolean,
    /** The "Available version" row; null hides it, until a check has found something to say. */
    val availableVersion: AvailableVersion?,
    val step: UpdateStep?,
    /** Whether the "Discard download" row shows: a verified download is staged and waits for no installer. */
    val canDiscard: Boolean,
    /**
     * Whether the "Skip this version" row shows: a build is on offer, waits
     * for no transfer or installer, and is not skipped.
     */
    val canSkip: Boolean,
    val autoCheck: Boolean,
    /** The version this process was updated to, until the section acknowledges it; null otherwise. */
    val updatedTo: String?,
    /**
     * Whether an update waits and is not skipped (UpdateState.offersUpdate,
     * the dock badge's rule); the category list's dot.
     */
    val updateOffered: Boolean,
) {
    companion object {
        val Initial =
            UpdatesUiState(
                status = UpdateStatus.NeverChecked,
                canCheck = false,
                availableVersion = null,
                step = null,
                canDiscard = false,
                canSkip = false,
                autoCheck = DEFAULT_AUTO_CHECK,
                updatedTo = null,
                updateOffered = false,
            )
    }
}

/** The check row's status line: the check itself, never what it found (see [AvailableVersion]). */
internal sealed interface UpdateStatus {
    /** This build never checks: a local build, or a flavor without a feed. */
    data object Disabled : UpdateStatus

    /** No check has run: no result, and no recorded attempt. */
    data object NeverChecked : UpdateStatus

    data object Checking : UpdateStatus

    /**
     * No check is running, and one has. [lastAttemptAt] is the last recorded
     * attempt, failed and uninformative ones included. It is null only when a
     * result stands but the store lost the attempt's time; the row then shows
     * no time rather than a false "not checked yet".
     */
    data class Checked(
        val lastAttemptAt: Instant?,
    ) : UpdateStatus

    /** The last check, download or install failed; one that still names its offer keeps it on offer. */
    data class Failed(
        val reason: UpdateFailure,
    ) : UpdateStatus
}

/** The "Available version" row: the newer build on offer, or a check's finding that there is none. */
internal sealed interface AvailableVersion {
    /**
     * [sizeBytes] is the download's size, shown before anything moves.
     * [skipped] marks the build the user skipped: still named here, honestly,
     * though it raises no dot and no prompt.
     */
    data class Offered(
        val versionName: String,
        val sizeBytes: Long,
        val skipped: Boolean = false,
    ) : AvailableVersion

    data object UpToDate : AvailableVersion
}

/**
 * The one step the updater offers next. [Download], [Downloading], [Install]
 * and [Installing] are the stages of one "Update to …" row, the one-tap
 * update. The two steps that put the system's install confirmation on screen
 * carry `blockedWhileMoving`: a local gate (AGENTS.md#driving-lockout) that
 * holds them while a fix shows the vehicle moving, so the dialog never pops up
 * over navigation. Checks and downloads are never gated; they put nothing on
 * screen. Under the same gate, [Download]'s tap only downloads
 * (`downloadOnly`). Every step a tap on which asks for the "Install unknown
 * apps" access carries `grantDeclined`: the last such tap sent the user to
 * that access, and they came back without turning it on.
 */
internal sealed interface UpdateStep {
    /**
     * Download the offered build, then install it; [sizeBytes] is shown before
     * the transfer starts. The tap asks for the "Install unknown apps" access
     * first when it is missing, hence `grantDeclined`. While a fix shows the
     * vehicle moving, [downloadOnly]: the tap starts a plain download and asks
     * for nothing, so Android's access screen never opens while driving. The
     * access and the install wait for the [Install] step's tap once parked, and
     * no decline describes a tap that asks for no access.
     */
    data class Download(
        val versionName: String,
        val sizeBytes: Long,
        val grantDeclined: Boolean,
        val downloadOnly: Boolean,
    ) : UpdateStep

    /** The download under way; [fraction] runs 0..1 over its size. The row takes no tap meanwhile. */
    data class Downloading(
        val versionName: String,
        val fraction: Float,
    ) : UpdateStep

    /** Install the downloaded and verified build. */
    data class Install(
        val versionName: String,
        val blockedWhileMoving: Boolean,
        val grantDeclined: Boolean,
    ) : UpdateStep

    /**
     * Handed to the system installer, whose confirmation has not arrived yet:
     * the row reports the hand-off and takes no tap.
     */
    data class Installing(
        val versionName: String,
    ) : UpdateStep

    /**
     * Show the platform's pending confirmation again: one dismissed with Home
     * sends no verdict. Named apart from [Install]'s "Update to …" because
     * Android 13 shows no progress once the user accepts, and an "Update"
     * label would invite a second acceptance.
     */
    data class ShowInstallDialog(
        val blockedWhileMoving: Boolean,
        val grantDeclined: Boolean,
    ) : UpdateStep

    /**
     * Try the offer that failed again; the updater skips the transfer when the
     * verified file is still staged. [sizeBytes] is shown first, since the
     * retry may download it all again.
     */
    data class Retry(
        val versionName: String,
        val sizeBytes: Long,
    ) : UpdateStep
}

/**
 * The Updates section's state for the updater's [state], its [settings],
 * the pending [updatedTo] notice, whether a fix shows the vehicle moving
 * ([installBlocked]), and whether the last install tap came back without the
 * "Install unknown apps" access ([installGrantDeclined]).
 *
 * The "last checked" time is the persisted attempt for every status: the
 * updater records it before each request leaves, failed and uninformative
 * attempts included, so it is the one source that also covers a check that
 * ended up to date.
 */
internal fun updatesUiState(
    state: UpdateState,
    settings: UpdateSettings,
    updatedTo: String?,
    installBlocked: Boolean,
    installGrantDeclined: Boolean,
): UpdatesUiState {
    val lastAttemptAt = settings.lastCheckAttemptAt?.let(Instant::ofEpochMilli)
    return UpdatesUiState(
        status =
            when (state) {
                UpdateState.Disabled -> {
                    UpdateStatus.Disabled
                }

                UpdateState.Checking -> {
                    UpdateStatus.Checking
                }

                is UpdateState.Failed -> {
                    UpdateStatus.Failed(state.reason)
                }

                // No result yet: only a recorded attempt says a check has run.
                is UpdateState.Idle -> {
                    lastAttemptAt?.let(UpdateStatus::Checked) ?: UpdateStatus.NeverChecked
                }

                // A result says a check has run, even when the store lost its time.
                UpdateState.UpToDate,
                is UpdateState.Available,
                is UpdateState.Downloading,
                is UpdateState.Ready,
                is UpdateState.Installing,
                -> {
                    UpdateStatus.Checked(lastAttemptAt)
                }
            },
        canCheck = state.isResting(),
        availableVersion =
            state.offeredManifestOrNull()?.let {
                AvailableVersion.Offered(it.versionName, it.apk.size, skipped = settings.skipped(it.versionCode))
            }
                ?: AvailableVersion.UpToDate.takeIf { state == UpdateState.UpToDate },
        step =
            when (state) {
                is UpdateState.Available -> {
                    UpdateStep.Download(
                        state.manifest.versionName,
                        state.manifest.apk.size,
                        grantDeclined = installGrantDeclined && !installBlocked,
                        downloadOnly = installBlocked,
                    )
                }

                is UpdateState.Downloading -> {
                    UpdateStep.Downloading(state.manifest.versionName, state.fraction)
                }

                is UpdateState.Ready -> {
                    UpdateStep.Install(
                        state.manifest.versionName,
                        blockedWhileMoving = installBlocked,
                        grantDeclined = installGrantDeclined,
                    )
                }

                // Only a kept confirmation can be shown again; before it arrives,
                // the hand-off is still under way, and the row says so.
                is UpdateState.Installing -> {
                    state.confirmation?.let {
                        UpdateStep.ShowInstallDialog(
                            blockedWhileMoving = installBlocked,
                            grantDeclined = installGrantDeclined,
                        )
                    } ?: UpdateStep.Installing(state.manifest.versionName)
                }

                // A failure that lost its offer leaves only a new check, which the
                // check row already is.
                is UpdateState.Failed -> {
                    state.manifest?.let { UpdateStep.Retry(it.versionName, it.apk.size) }
                }

                UpdateState.Disabled,
                is UpdateState.Idle,
                UpdateState.Checking,
                UpdateState.UpToDate,
                -> {
                    null
                }
            },
        canDiscard = state.discardableOrNull() != null,
        canSkip = state.skippableOrNull()?.let { !settings.skipped(it.versionCode) } == true,
        autoCheck = settings.autoCheck,
        updatedTo = updatedTo,
        updateOffered = state.offersUpdate(settings),
    )
}

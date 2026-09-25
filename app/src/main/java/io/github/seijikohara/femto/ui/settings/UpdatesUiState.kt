package io.github.seijikohara.femto.ui.settings

import io.github.seijikohara.femto.data.update.DEFAULT_AUTO_CHECK
import io.github.seijikohara.femto.data.update.UpdateFailure
import io.github.seijikohara.femto.data.update.UpdateSettings
import io.github.seijikohara.femto.data.update.UpdateState
import io.github.seijikohara.femto.data.update.isResting
import io.github.seijikohara.femto.data.update.offersUpdate
import java.time.Instant

/**
 * What the Updates section shows: the updater's state reduced to what a person
 * reads ([status]) and the one step it offers next ([step]), plus the daily
 * check and the one-time notice. [SettingsViewModel] derives it with
 * [updatesUiState]; nothing here is persisted but [autoCheck].
 */
internal data class UpdatesUiState(
    val status: UpdateStatus,
    /** Whether a tap on the check row starts a check; while one cannot start, the row only reports. */
    val canCheck: Boolean,
    val step: UpdateStep?,
    val autoCheck: Boolean,
    /** The version this process was updated to, until the section acknowledges it; null otherwise. */
    val updatedTo: String?,
    /** Whether an update waits (UpdateState.offersUpdate, the dock badge's rule); the category list's dot. */
    val updateOffered: Boolean,
) {
    companion object {
        val Initial =
            UpdatesUiState(
                status = UpdateStatus.Idle(lastAttemptAt = null),
                canCheck = false,
                step = null,
                autoCheck = DEFAULT_AUTO_CHECK,
                updatedTo = null,
                updateOffered = false,
            )
    }
}

/** The check row's status line, one per [UpdateState]. */
internal sealed interface UpdateStatus {
    /** This build never checks: a local build, or a flavor without a feed. */
    data object Disabled : UpdateStatus

    /** No result in this process yet; [lastAttemptAt] is the last recorded attempt, if any. */
    data class Idle(
        val lastAttemptAt: Instant?,
    ) : UpdateStatus

    data object Checking : UpdateStatus

    data class UpToDate(
        val lastAttemptAt: Instant?,
    ) : UpdateStatus

    data class Available(
        val versionName: String,
    ) : UpdateStatus

    /** [fraction] runs 0..1 over the download's size. */
    data class Downloading(
        val versionName: String,
        val fraction: Float,
    ) : UpdateStatus

    /** Downloaded and verified against its manifest. */
    data class Ready(
        val versionName: String,
    ) : UpdateStatus

    /** Handed to the system installer, which may be waiting for the user to confirm. */
    data class Installing(
        val versionName: String,
    ) : UpdateStatus

    data class Failed(
        val reason: UpdateFailure,
    ) : UpdateStatus
}

/**
 * The one step the updater offers next. The two that put the system's install
 * confirmation on screen carry `blockedWhileMoving`: a local gate
 * (AGENTS.md#driving-lockout) that holds them while a fix shows the vehicle
 * moving, so the dialog never pops up over navigation. Checks and downloads
 * are never gated; they put nothing on screen. The same two carry
 * `grantDeclined`: the last install tap sent the user to the "Install unknown
 * apps" access, and they came back without turning it on.
 */
internal sealed interface UpdateStep {
    /** Download the offered build; [sizeBytes] is shown before the transfer starts. */
    data class Download(
        val sizeBytes: Long,
    ) : UpdateStep

    data class Install(
        val blockedWhileMoving: Boolean,
        val grantDeclined: Boolean,
    ) : UpdateStep

    /**
     * Show the platform's pending confirmation again: one dismissed with Home
     * sends no verdict. Named apart from [Install] because Android 13 shows no
     * progress once the user accepts, and an "Install" label would invite a
     * second acceptance.
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
                UpdateState.Disabled -> UpdateStatus.Disabled
                is UpdateState.Idle -> UpdateStatus.Idle(lastAttemptAt)
                UpdateState.Checking -> UpdateStatus.Checking
                UpdateState.UpToDate -> UpdateStatus.UpToDate(lastAttemptAt)
                is UpdateState.Available -> UpdateStatus.Available(state.manifest.versionName)
                is UpdateState.Downloading -> UpdateStatus.Downloading(state.manifest.versionName, state.fraction)
                is UpdateState.Ready -> UpdateStatus.Ready(state.manifest.versionName)
                is UpdateState.Installing -> UpdateStatus.Installing(state.manifest.versionName)
                is UpdateState.Failed -> UpdateStatus.Failed(state.reason)
            },
        canCheck = state.isResting(),
        step =
            when (state) {
                is UpdateState.Available -> {
                    UpdateStep.Download(state.manifest.apk.size)
                }

                is UpdateState.Ready -> {
                    UpdateStep.Install(blockedWhileMoving = installBlocked, grantDeclined = installGrantDeclined)
                }

                // Only a kept confirmation can be shown again; before it arrives,
                // the hand-off is still under way.
                is UpdateState.Installing -> {
                    state.confirmation?.let {
                        UpdateStep.ShowInstallDialog(
                            blockedWhileMoving = installBlocked,
                            grantDeclined = installGrantDeclined,
                        )
                    }
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
                is UpdateState.Downloading,
                -> {
                    null
                }
            },
        autoCheck = settings.autoCheck,
        updatedTo = updatedTo,
        updateOffered = state.offersUpdate(),
    )
}

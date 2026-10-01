package io.github.seijikohara.femto.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.content.IntentCompat
import io.github.seijikohara.femto.data.common.finishAsync
import kotlinx.coroutines.Job

private const val TAG = "InstallStatusReceiver"

/**
 * The platform's verdicts on install sessions, as [InstallStatusReceiver]
 * reports them; [UpdateRepository] acts on them. Each returns the work the
 * verdict started (its records written, its confirmation shown), or null when
 * it started none, so the receiver can hold its broadcast until that work is
 * done.
 */
internal interface InstallVerdicts {
    fun onConfirmationRequested(
        sessionId: Int,
        confirmation: InstallConfirmation,
    ): Job?

    fun onInstallCancelled(sessionId: Int): Job?

    fun onInstallFailed(
        sessionId: Int,
        reason: UpdateFailure,
    ): Job?
}

/** What one [PackageInstaller.EXTRA_STATUS] means for the updater. */
internal sealed interface InstallOutcome {
    /** The platform waits for the user to confirm, and sends the intent that asks them. */
    data object NeedsConfirmation : InstallOutcome

    /** Installed. A self-update kills this process first, so the next start sees it, not this one. */
    data object Installed : InstallOutcome

    /** Actively aborted: the user declined, or the session was abandoned. */
    data object Declined : InstallOutcome

    /** The platform refused the install for [reason]. */
    data class Refused(
        val reason: UpdateFailure,
    ) : InstallOutcome
}

/**
 * The outcome of [status]. A conflict is an APK signed with another key; a
 * block is a device policy or a verifier; a storage failure is a full disk.
 * An abort is the user's decline, unless [developerVerificationFailed]: then
 * Android's developer verification blocked the install. Every other failure,
 * including a status a later platform adds, is [UpdateFailure.OTHER].
 */
internal fun installOutcomeOf(
    status: Int,
    developerVerificationFailed: Boolean = false,
): InstallOutcome =
    when (status) {
        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
            InstallOutcome.NeedsConfirmation
        }

        PackageInstaller.STATUS_SUCCESS -> {
            InstallOutcome.Installed
        }

        PackageInstaller.STATUS_FAILURE_ABORTED if developerVerificationFailed -> {
            InstallOutcome.Refused(UpdateFailure.DEVELOPER_VERIFICATION)
        }

        PackageInstaller.STATUS_FAILURE_ABORTED -> {
            InstallOutcome.Declined
        }

        PackageInstaller.STATUS_FAILURE_CONFLICT -> {
            InstallOutcome.Refused(UpdateFailure.INSTALL_CONFLICT)
        }

        PackageInstaller.STATUS_FAILURE_BLOCKED -> {
            InstallOutcome.Refused(UpdateFailure.INSTALL_BLOCKED)
        }

        PackageInstaller.STATUS_FAILURE_STORAGE -> {
            InstallOutcome.Refused(UpdateFailure.STORAGE)
        }

        else -> {
            InstallOutcome.Refused(UpdateFailure.OTHER)
        }
    }

/**
 * Receives the platform's status for every install session the updater
 * commits. Not exported: the platform's own broadcasts still reach it, and
 * [pendingIntent] addresses it explicitly.
 */
internal class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val app = context.applicationContext
        finishAsync(TAG) { deliverInstallStatus(app, intent, UpdateRepository.get(app)) }
    }

    companion object {
        /**
         * The status receiver session [sessionId] commits with. Mutable,
         * because the platform fills in the status extras, and commit() rejects
         * an immutable one from an app that targets API 35 or higher. Explicit,
         * because a mutable PendingIntent around an implicit Intent throws for
         * an app that targets API 34 or higher.
         */
        fun pendingIntent(
            context: Context,
            sessionId: Int,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallStatusReceiver::class.java),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}

/**
 * Report the status in [intent] to [verdicts], and return once the work the
 * verdict started is done: the receiver holds its broadcast until then.
 * Separate from the receiver, so tests can pass their own [verdicts] instead
 * of the app-wide repository.
 */
internal suspend fun deliverInstallStatus(
    context: Context,
    intent: Intent,
    verdicts: InstallVerdicts,
) {
    val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, PackageInstaller.SessionInfo.INVALID_ID)
    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
    // The platform's own detail (which certificate, which policy) goes to the
    // log only: the UI phrases the outcome.
    Log.i(TAG, "session $sessionId: status $status (${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)})")
    val work =
        when (val outcome = installOutcomeOf(status, intent.developerVerificationFailed())) {
            InstallOutcome.NeedsConfirmation -> requestConfirmation(context, intent, sessionId, verdicts)
            InstallOutcome.Installed -> null
            InstallOutcome.Declined -> verdicts.onInstallCancelled(sessionId)
            is InstallOutcome.Refused -> verdicts.onInstallFailed(sessionId, outcome.reason)
        }
    work?.join()
}

// From Android 16 QPR2 (API 36.1), an install that Android's developer
// verification blocks fails as STATUS_FAILURE_ABORTED, the user's decline,
// but with EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON, which a decline never
// carries (PackageInstaller reference). Earlier releases verify no developer,
// so the extra is not read there.
private fun Intent.developerVerificationFailed(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
        Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1 &&
        hasExtra(PackageInstaller.EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON)

// A request that carries no intent to show leaves nothing the user could
// confirm: the attempt fails, and a retry starts a new session.
// A `when`, not an elvis chain: a confirmation verdict that starts no work
// returns null, and must not fall through to a failure.
private fun requestConfirmation(
    context: Context,
    intent: Intent,
    sessionId: Int,
    verdicts: InstallVerdicts,
): Job? =
    when (val confirmation = confirmationOrNull(context, intent)) {
        null -> verdicts.onInstallFailed(sessionId, UpdateFailure.OTHER)
        else -> verdicts.onConfirmationRequested(sessionId, confirmation)
    }

// IntentCompat, not the platform's typed getParcelableExtra: on Android 13
// (this app's floor, and what the AI boxes run) the typed call can throw
// (b/232589966), and the untyped one is deprecated. Outside an activity, the
// platform starts an activity only into a new task. A start that throws (no
// package installer to confirm with, on a locked-down ROM) reports failure, so
// the attempt can end instead of waiting on a dialog that never appears.
private fun confirmationOrNull(
    context: Context,
    intent: Intent,
): InstallConfirmation? =
    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let { request ->
        InstallConfirmation {
            runCatching { context.startActivity(Intent(request).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { Log.w(TAG, "showing the install confirmation failed", it) }
                .isSuccess
        }
    }

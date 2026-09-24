package io.github.seijikohara.femto.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.core.content.IntentCompat

private const val TAG = "InstallStatusReceiver"

/** The platform's verdicts on install sessions, as [InstallStatusReceiver] reports them; [UpdateRepository] acts on them. */
internal interface InstallVerdicts {
    fun onConfirmationRequested(
        sessionId: Int,
        confirmation: InstallConfirmation,
    )

    fun onInstallCancelled(sessionId: Int)

    fun onInstallFailed(
        sessionId: Int,
        reason: UpdateFailure,
    )
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
 * Every other failure, including a status a later platform adds, is
 * [UpdateFailure.OTHER].
 */
internal fun installOutcomeOf(status: Int): InstallOutcome =
    when (status) {
        PackageInstaller.STATUS_PENDING_USER_ACTION -> InstallOutcome.NeedsConfirmation
        PackageInstaller.STATUS_SUCCESS -> InstallOutcome.Installed
        PackageInstaller.STATUS_FAILURE_ABORTED -> InstallOutcome.Declined
        PackageInstaller.STATUS_FAILURE_CONFLICT -> InstallOutcome.Refused(UpdateFailure.INSTALL_CONFLICT)
        PackageInstaller.STATUS_FAILURE_BLOCKED -> InstallOutcome.Refused(UpdateFailure.INSTALL_BLOCKED)
        PackageInstaller.STATUS_FAILURE_STORAGE -> InstallOutcome.Refused(UpdateFailure.STORAGE)
        else -> InstallOutcome.Refused(UpdateFailure.OTHER)
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
    ) = deliverInstallStatus(context.applicationContext, intent, UpdateRepository.get(context))

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
 * Report the status in [intent] to [verdicts]. Separate from the receiver, so
 * tests can pass their own [verdicts] instead of the app-wide repository.
 */
internal fun deliverInstallStatus(
    context: Context,
    intent: Intent,
    verdicts: InstallVerdicts,
) {
    val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, PackageInstaller.SessionInfo.INVALID_ID)
    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
    // The platform's own detail (which certificate, which policy) goes to the
    // log only: the UI phrases the outcome.
    Log.i(TAG, "session $sessionId: status $status (${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)})")
    when (val outcome = installOutcomeOf(status)) {
        InstallOutcome.NeedsConfirmation -> requestConfirmation(context, intent, sessionId, verdicts)
        InstallOutcome.Installed -> Unit
        InstallOutcome.Declined -> verdicts.onInstallCancelled(sessionId)
        is InstallOutcome.Refused -> verdicts.onInstallFailed(sessionId, outcome.reason)
    }
}

// A request that carries no intent to show leaves nothing the user could
// confirm: the attempt fails, and a retry starts a new session.
private fun requestConfirmation(
    context: Context,
    intent: Intent,
    sessionId: Int,
    verdicts: InstallVerdicts,
) = confirmationOrNull(context, intent)
    ?.let { verdicts.onConfirmationRequested(sessionId, it) }
    ?: verdicts.onInstallFailed(sessionId, UpdateFailure.OTHER)

// IntentCompat, not the platform's typed getParcelableExtra: on Android 13
// (this app's floor, and what the AI boxes run) the typed call can throw
// (b/232589966), and the untyped one is deprecated. Outside an activity, the
// platform starts an activity only into a new task.
private fun confirmationOrNull(
    context: Context,
    intent: Intent,
): InstallConfirmation? =
    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let { request ->
        InstallConfirmation {
            runCatching { context.startActivity(Intent(request).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { Log.w(TAG, "showing the install confirmation failed", it) }
        }
    }

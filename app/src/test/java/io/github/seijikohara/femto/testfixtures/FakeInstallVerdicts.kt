package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.update.InstallConfirmation
import io.github.seijikohara.femto.data.update.InstallVerdicts
import io.github.seijikohara.femto.data.update.UpdateFailure
import kotlinx.coroutines.Job

/** One verdict [FakeInstallVerdicts] received. */
internal sealed interface ReceivedVerdict {
    data class ConfirmationRequested(
        val sessionId: Int,
        val confirmation: InstallConfirmation,
    ) : ReceivedVerdict

    data class Cancelled(
        val sessionId: Int,
    ) : ReceivedVerdict

    data class Failed(
        val sessionId: Int,
        val reason: UpdateFailure,
    ) : ReceivedVerdict
}

/**
 * Records every verdict it receives, in order, and answers each with [work]:
 * none by default, or a job a test completes to model records still being
 * written.
 */
internal class FakeInstallVerdicts(
    private val work: Job? = null,
) : InstallVerdicts {
    val received = mutableListOf<ReceivedVerdict>()

    override fun onConfirmationRequested(
        sessionId: Int,
        confirmation: InstallConfirmation,
    ): Job? {
        received += ReceivedVerdict.ConfirmationRequested(sessionId, confirmation)
        return work
    }

    override fun onInstallCancelled(sessionId: Int): Job? {
        received += ReceivedVerdict.Cancelled(sessionId)
        return work
    }

    override fun onInstallFailed(
        sessionId: Int,
        reason: UpdateFailure,
    ): Job? {
        received += ReceivedVerdict.Failed(sessionId, reason)
        return work
    }
}

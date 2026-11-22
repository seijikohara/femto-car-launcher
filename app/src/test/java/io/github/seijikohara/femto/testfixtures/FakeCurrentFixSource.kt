package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.location.CurrentFix
import io.github.seijikohara.femto.data.location.CurrentFixSource
import kotlinx.coroutines.CompletableDeferred
import kotlin.coroutines.cancellation.CancellationException

/**
 * In-memory [CurrentFixSource]: every request answers [answer], null standing for a request that found no position in
 * time. A request made after [gateRequest] waits at the returned gate first, as one waits on the receiver.
 */
internal class FakeCurrentFixSource(
    var answer: CurrentFix? = null,
) : CurrentFixSource {
    /** How many requests were made. */
    var requests = 0
        private set

    /** How many requests their caller's cancel withdrew while they waited. */
    var withdrawn = 0
        private set

    private var gate: CompletableDeferred<Unit>? = null

    /** Hold the requests that follow until the returned gate completes. */
    fun gateRequest(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { gate = it }

    override suspend fun currentFixOrNull(): CurrentFix? {
        requests++
        try {
            gate?.await()
        } catch (cancelled: CancellationException) {
            withdrawn++
            throw cancelled
        }
        return answer
    }
}

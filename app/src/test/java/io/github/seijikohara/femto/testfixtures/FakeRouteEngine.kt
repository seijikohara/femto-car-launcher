package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.routing.EngineOutcome
import io.github.seijikohara.femto.data.routing.EngineRequest
import io.github.seijikohara.femto.data.routing.EngineSearch
import io.github.seijikohara.femto.data.routing.RouteEngine
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.CountDownLatch

/**
 * A [RouteEngine] for planner tests. Every search answers [outcome]; with [holdsUntilTerminated] set, a search blocks
 * its thread like BRouter's until [EngineSearch.terminate] is called, and then answers BRouter's message for a killed
 * search. [started] completes once a search runs, from the engine's thread.
 */
internal class FakeRouteEngine(
    var outcome: EngineOutcome = EngineOutcome.Error("no outcome set"),
    private val holdsUntilTerminated: Boolean = false,
) : RouteEngine {
    /** Every prepared request, in call order. */
    val requests = mutableListOf<EngineRequest>()

    /** The time limit of every search, in call order. */
    val timeLimits = mutableListOf<Long>()

    val started = CompletableDeferred<Unit>()

    private val termination = CountDownLatch(1)

    val terminated: Boolean get() = termination.count == 0L

    override fun prepare(request: EngineRequest): EngineSearch {
        requests += request
        return object : EngineSearch {
            override fun run(timeLimitMs: Long): EngineOutcome {
                timeLimits += timeLimitMs
                started.complete(Unit)
                if (holdsUntilTerminated) {
                    termination.await()
                    return EngineOutcome.Error("operation killed by thread-priority-watchdog after 0 seconds")
                }
                return outcome
            }

            override fun terminate() = termination.countDown()
        }
    }
}

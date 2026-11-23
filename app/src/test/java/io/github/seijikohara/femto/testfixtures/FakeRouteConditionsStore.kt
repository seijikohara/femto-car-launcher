package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.routing.RouteConditions
import io.github.seijikohara.femto.data.routing.RouteConditionsStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [RouteConditionsStore]: each update applies to the stored value in one step and shows in [conditions] at
 * once, as the stored file would. After [holdWrites], updates wait until the returned gate completes, as writes to a
 * slow disk do.
 */
internal class FakeRouteConditionsStore(
    initial: RouteConditions = RouteConditions(),
) : RouteConditionsStore {
    private val state = MutableStateFlow(initial)
    private var writeGate: CompletableDeferred<Unit>? = null

    override val conditions: StateFlow<RouteConditions> = state

    override suspend fun update(transform: (RouteConditions) -> RouteConditions) {
        writeGate?.await()
        state.update(transform)
    }

    /** Hold every update from now on until the returned gate completes. */
    fun holdWrites(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { writeGate = it }
}

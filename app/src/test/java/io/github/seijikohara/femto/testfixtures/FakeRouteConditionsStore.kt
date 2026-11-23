package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.routing.RouteConditions
import io.github.seijikohara.femto.data.routing.RouteConditionsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory [RouteConditionsStore]: each write shows in [conditions] at once, as the stored file would. */
internal class FakeRouteConditionsStore(
    initial: RouteConditions = RouteConditions(),
) : RouteConditionsStore {
    private val state = MutableStateFlow(initial)

    override val conditions: StateFlow<RouteConditions> = state

    override suspend fun setConditions(conditions: RouteConditions) {
        state.value = conditions
    }
}

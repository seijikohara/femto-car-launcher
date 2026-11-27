package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.routing.NavigationSettings
import io.github.seijikohara.femto.data.routing.NavigationSettingsStore
import io.github.seijikohara.femto.data.routing.RouteRowTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [NavigationSettingsStore]: every setter mutates a [MutableStateFlow] synchronously, so a test sees the
 * write with no DataStore IO. Like the real store, [resetToDefaults] restores every setting.
 */
internal class FakeNavigationSettingsStore(
    initial: NavigationSettings = NavigationSettings.Default,
) : NavigationSettingsStore {
    private val state = MutableStateFlow(initial)

    override val settings: StateFlow<NavigationSettings> = state

    /** The persisted values right now, for assertions. */
    val current: NavigationSettings get() = state.value

    override suspend fun setRerouteAutomatically(value: Boolean) =
        state.update { it.copy(rerouteAutomatically = value) }

    override suspend fun setSuggestOtherRoutes(value: Boolean) = state.update { it.copy(suggestOtherRoutes = value) }

    override suspend fun setRestoreRouteAfterRestart(value: Boolean) =
        state.update { it.copy(restoreRouteAfterRestart = value) }

    override suspend fun setRouteRowTime(value: RouteRowTime) = state.update { it.copy(routeRowTime = value) }

    override suspend fun setVehicleOnRouteLine(value: Boolean) = state.update { it.copy(vehicleOnRouteLine = value) }

    override suspend fun setTurnCard(value: Boolean) = state.update { it.copy(turnCard = value) }

    override suspend fun setTurnCardNextAfter(value: Boolean) = state.update { it.copy(turnCardNextAfter = value) }

    override suspend fun setTurnCardStraight(value: Boolean) = state.update { it.copy(turnCardStraight = value) }

    override suspend fun setSpokenPrompts(value: Boolean) = state.update { it.copy(spokenPrompts = value) }

    override suspend fun resetToDefaults() {
        state.value = NavigationSettings.Default
    }
}

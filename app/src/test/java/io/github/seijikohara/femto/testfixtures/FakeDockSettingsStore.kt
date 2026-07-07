package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.dock.DockNavId
import io.github.seijikohara.femto.data.dock.DockSettingsStore
import io.github.seijikohara.femto.data.dock.DockStatusId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [DockSettingsStore] for view-model tests: every setter mutates a
 * [MutableStateFlow] synchronously, so a test sees the write with no DataStore
 * IO and no cross-dispatcher timing. Defaults match the read-path fallback
 * (every id in its enum's declared order, nothing hidden).
 */
internal class FakeDockSettingsStore : DockSettingsStore {
    private val navOrderState = MutableStateFlow(DockNavId.entries.toList())
    private val navHiddenState = MutableStateFlow<Set<DockNavId>>(emptySet())
    private val statusOrderState = MutableStateFlow(DockStatusId.entries.toList())
    private val statusHiddenState = MutableStateFlow<Set<DockStatusId>>(emptySet())

    override val navOrder: Flow<List<DockNavId>> = navOrderState
    override val navHidden: Flow<Set<DockNavId>> = navHiddenState
    override val statusOrder: Flow<List<DockStatusId>> = statusOrderState
    override val statusHidden: Flow<Set<DockStatusId>> = statusHiddenState

    override suspend fun setNavOrder(value: List<DockNavId>) {
        navOrderState.value = value
    }

    override suspend fun toggleNavHidden(id: DockNavId) {
        navHiddenState.update { if (id in it) it - id else it + id }
    }

    override suspend fun setStatusOrder(value: List<DockStatusId>) {
        statusOrderState.value = value
    }

    override suspend fun toggleStatusHidden(id: DockStatusId) {
        statusHiddenState.update { if (id in it) it - id else it + id }
    }

    override suspend fun resetToDefaults() {
        navOrderState.value = DockNavId.entries.toList()
        navHiddenState.value = emptySet()
        statusOrderState.value = DockStatusId.entries.toList()
        statusHiddenState.value = emptySet()
    }
}

package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.apps.DrawerIconSize
import io.github.seijikohara.femto.data.apps.DrawerLayout
import io.github.seijikohara.femto.data.apps.DrawerSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [DrawerSettingsStore] for view-model tests: every setter mutates a
 * [MutableStateFlow] synchronously, so a test sees the write with no DataStore IO
 * and no cross-dispatcher timing.
 */
internal class FakeDrawerSettingsStore(
    initialLayout: DrawerLayout = DrawerLayout.GRID,
    initialIconSize: DrawerIconSize = DrawerIconSize.MEDIUM,
    initialPinned: List<String> = emptyList(),
) : DrawerSettingsStore {
    private val layoutState = MutableStateFlow(initialLayout)
    private val iconSizeState = MutableStateFlow(initialIconSize)
    private val pinnedState = MutableStateFlow(initialPinned)

    override val layout: Flow<DrawerLayout> = layoutState
    override val iconSize: Flow<DrawerIconSize> = iconSizeState
    override val pinned: Flow<List<String>> = pinnedState

    override suspend fun setLayout(value: DrawerLayout) = layoutState.update { value }

    override suspend fun setIconSize(value: DrawerIconSize) = iconSizeState.update { value }

    override suspend fun togglePinned(flattenedComponent: String) =
        pinnedState.update { current ->
            if (flattenedComponent in current) current - flattenedComponent else current + flattenedComponent
        }
}

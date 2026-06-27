package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.calendar.CalendarPreferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [CalendarPreferencesStore] for view-model tests: every setter mutates a
 * [MutableStateFlow] synchronously, so a test sees the write with no DataStore IO
 * and no cross-dispatcher timing.
 */
internal class FakeCalendarPreferencesStore(
    initialHidden: Set<Long> = emptySet(),
) : CalendarPreferencesStore {
    private val state = MutableStateFlow(initialHidden)
    override val hiddenCalendarIds: Flow<Set<Long>> = state

    override suspend fun setCalendarHidden(
        id: Long,
        hidden: Boolean,
    ) = state.update { if (hidden) it + id else it - id }
}

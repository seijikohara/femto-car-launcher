package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.FontSelection
import io.github.seijikohara.femto.data.FontSelectionStore
import io.github.seijikohara.femto.data.FontSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [FontSelectionStore] for repository tests. Backed by a
 * [MutableStateFlow], which — like DataStore — skips the re-emission when an
 * unchanged selection is written, so the repository's retry trigger for
 * re-chosen families is exercised faithfully.
 */
internal class FakeFontSelectionStore(
    initial: FontSelection = FontSelection.System,
) : FontSelectionStore {
    private val state = MutableStateFlow(initial)

    override val selection: Flow<FontSelection> = state

    override suspend fun setFamily(
        slot: FontSlot,
        family: String?,
    ) = state.update { it.with(slot, family) }

    override suspend fun resetToDefaults() {
        state.value = FontSelection.System
    }
}

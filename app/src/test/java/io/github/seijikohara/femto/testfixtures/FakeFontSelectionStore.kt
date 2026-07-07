package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.fonts.FontSelection
import io.github.seijikohara.femto.data.fonts.FontSelectionStore
import io.github.seijikohara.femto.data.fonts.FontSlot
import io.github.seijikohara.femto.data.fonts.FontSource
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

    override suspend fun setSource(
        slot: FontSlot,
        source: FontSource,
    ) = state.update { it.with(slot, source) }

    override suspend fun resetToDefaults() {
        state.value = FontSelection.System
    }
}

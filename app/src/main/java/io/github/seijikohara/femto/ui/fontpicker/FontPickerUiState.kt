package io.github.seijikohara.femto.ui.fontpicker

import io.github.seijikohara.femto.data.fonts.FontSlot
import io.github.seijikohara.femto.data.fonts.GoogleFontFamily

/** Catalog load state for the picker list. */
internal enum class PickerStatus {
    LOADING,
    READY,
    ERROR,
}

/**
 * State for the Google Fonts picker. [families] is already filtered to the
 * [slot] (CJK-capable for the fallback slot) and the current [query]; the
 * separate "system default" entry is rendered by the screen, not listed here.
 */
internal data class FontPickerUiState(
    val slot: FontSlot,
    val query: String = "",
    val selectedFamily: String? = null,
    val families: List<GoogleFontFamily> = emptyList(),
    val downloading: Set<String> = emptySet(),
    val downloadFailed: Set<String> = emptySet(),
    val status: PickerStatus = PickerStatus.LOADING,
)

/** User intents from the picker. */
internal sealed interface FontPickerAction {
    data class Search(
        val query: String,
    ) : FontPickerAction

    /** Choose a family for the slot; a null family restores the system font. */
    data class Choose(
        val family: String?,
    ) : FontPickerAction
}

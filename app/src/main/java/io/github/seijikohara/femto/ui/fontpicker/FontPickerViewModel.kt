package io.github.seijikohara.femto.ui.fontpicker

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.seijikohara.femto.data.common.WhileUiSubscribed
import io.github.seijikohara.femto.data.fonts.CatalogState
import io.github.seijikohara.femto.data.fonts.FontRepository
import io.github.seijikohara.femto.data.fonts.FontSlot
import io.github.seijikohara.femto.data.fonts.GoogleFontFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Binds the shared [FontRepository] to one [slot]'s picker: triggers the catalog
 * load, filters it to the slot and the live search query, and forwards the
 * choice back to the repository (which downloads, swaps the theme, and evicts
 * the old font). Re-choosing a failed family routes through the same path and
 * retries its download.
 */
internal class FontPickerViewModel(
    private val repository: FontRepository,
    private val slot: FontSlot,
) : ViewModel() {
    private val query = MutableStateFlow("")

    val uiState: StateFlow<FontPickerUiState> =
        combine(
            repository.catalog,
            repository.selection,
            repository.downloading,
            repository.downloadFailed,
            query,
        ) { catalog, selection, downloading, downloadFailed, currentQuery ->
            val (families, status) = filterCatalog(catalog, currentQuery)
            FontPickerUiState(
                slot = slot,
                query = currentQuery,
                selectedFamily = selection.familyFor(slot),
                families = families,
                downloading = downloading,
                downloadFailed = downloadFailed,
                status = status,
            )
        }.stateIn(viewModelScope, WhileUiSubscribed, FontPickerUiState(slot))

    init {
        repository.ensureCatalog()
    }

    fun onAction(action: FontPickerAction) {
        when (action) {
            is FontPickerAction.Search -> query.value = action.query
            is FontPickerAction.Choose -> repository.choose(slot, action.family)
        }
    }

    private fun filterCatalog(
        catalog: CatalogState,
        currentQuery: String,
    ): Pair<List<GoogleFontFamily>, PickerStatus> =
        when (catalog) {
            CatalogState.Idle, CatalogState.Loading -> {
                emptyList<GoogleFontFamily>() to PickerStatus.LOADING
            }

            CatalogState.Error -> {
                emptyList<GoogleFontFamily>() to PickerStatus.ERROR
            }

            is CatalogState.Loaded -> {
                val trimmed = currentQuery.trim()
                val families =
                    catalog.families.filter { family ->
                        family.fits(slot) && (trimmed.isEmpty() || family.family.contains(trimmed, ignoreCase = true))
                    }
                families to PickerStatus.READY
            }
        }
}

internal class FontPickerViewModelFactory(
    private val application: Application,
    private val slot: FontSlot,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        @Suppress("UNCHECKED_CAST")
        return FontPickerViewModel(FontRepository.get(application), slot) as T
    }
}

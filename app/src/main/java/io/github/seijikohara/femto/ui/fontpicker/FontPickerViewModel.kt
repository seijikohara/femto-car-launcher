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
import io.github.seijikohara.femto.data.fonts.SystemFontFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Binds the shared [FontRepository] to one [slot]'s picker: triggers the Google
 * Fonts catalog load, filters both it and the repository's installed-font
 * catalog to the slot and the live search query, and forwards the choice back
 * to the repository (which downloads / resolves from disk, swaps the theme,
 * and evicts the old Google Fonts cache entry). Re-choosing a failed Google
 * family routes through the same path and retries its download.
 */
internal class FontPickerViewModel(
    private val repository: FontRepository,
    private val slot: FontSlot,
) : ViewModel() {
    private val query = MutableStateFlow("")

    val uiState: StateFlow<FontPickerUiState> =
        combine(
            combine(repository.catalog, repository.systemFonts, ::CatalogSnapshot),
            repository.selection,
            repository.downloading,
            repository.downloadFailed,
            query,
        ) { snapshot, selection, downloading, downloadFailed, currentQuery ->
            val (families, status) = filterCatalog(snapshot.catalog, currentQuery)
            FontPickerUiState(
                slot = slot,
                query = currentQuery,
                selectedSource = selection.sourceFor(slot),
                families = families,
                systemFonts = filterSystemFonts(snapshot.systemFonts, currentQuery),
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
            is FontPickerAction.Choose -> repository.choose(slot, action.source)
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

    private fun filterSystemFonts(
        all: List<SystemFontFamily>,
        currentQuery: String,
    ): List<SystemFontFamily> {
        val trimmed = currentQuery.trim()
        return all.filter { family ->
            family.fits(slot) && (trimmed.isEmpty() || family.familyName.contains(trimmed, ignoreCase = true))
        }
    }

    // Bundles the two independently-loaded catalogs so a 6th flow does not
    // outgrow kotlinx.coroutines' typed combine() overloads (which top out at
    // five); the outer combine's lambda destructures this by component.
    private data class CatalogSnapshot(
        val catalog: CatalogState,
        val systemFonts: List<SystemFontFamily>,
    )
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

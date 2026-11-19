package io.github.seijikohara.femto.ui.offlinemap

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.seijikohara.femto.data.common.WhileUiSubscribed
import io.github.seijikohara.femto.data.display.DisplayPreferences
import io.github.seijikohara.femto.data.display.DisplaySettingsStore
import io.github.seijikohara.femto.data.location.LocationGraph
import io.github.seijikohara.femto.data.map.offline.CatalogRegion
import io.github.seijikohara.femto.data.map.offline.OfflineRegionRepository
import io.github.seijikohara.femto.data.map.offline.OfflineRegionsState
import io.github.seijikohara.femto.data.map.offline.OfflineStorageLimit
import io.github.seijikohara.femto.data.map.offline.RegionCatalog
import io.github.seijikohara.femto.data.map.offline.RegionCatalogResult
import io.github.seijikohara.femto.data.map.offline.RegionOffer
import io.github.seijikohara.femto.data.map.offline.RegionOffer.Unavailable.Reason
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

// The dashboard keeps the location flow hot, so a known fix arrives at once; without one the picker opens unsuggested.
private val PositionTimeout = 2.seconds

/**
 * Narrow port for the saved-regions repository the sheet drives. The factory binds it to
 * [OfflineRegionRepository]; tests substitute an in-memory fake. [start], [resume] and [cancel] return before the
 * state changes: the sheet follows [state], never a value read right after one of them.
 */
internal interface OfflineRegionsPort {
    val state: StateFlow<OfflineRegionsState>

    suspend fun load()

    suspend fun catalog(
        longitude: Double?,
        latitude: Double?,
    ): RegionCatalogResult

    suspend fun offer(region: CatalogRegion): RegionOffer

    fun start(offer: RegionOffer.Ready)

    fun resume()

    fun cancel()

    suspend fun delete(regionId: String)
}

/**
 * Binds the saved-regions repository to the sheet. The repository holds the saved regions and the one download, so
 * a reopened sheet shows a download that kept running; the picker path, the pending offer and the open dialog are
 * this ViewModel's own and reset when the sheet closes. Nothing is fetched until a tap asks for it: opening the
 * picker fetches the region list, and picking a region probes its file.
 */
internal class OfflineRegionsViewModel(
    private val port: OfflineRegionsPort,
    private val displayPreferences: DisplaySettingsStore,
    /** The latest known position, longitude first; null without one. */
    private val position: suspend () -> Pair<Double, Double>?,
) : ViewModel() {
    private val navigation = MutableStateFlow(Navigation())

    private var catalogJob: Job? = null
    private var offerJob: Job? = null

    val uiState: StateFlow<OfflineRegionsUiState> =
        combine(
            port.state,
            displayPreferences.settings.map { it.offlineStorageLimit }.distinctUntilChanged(),
            navigation,
        ) { regions, limit, nav ->
            OfflineRegionsUiState(
                loaded = regions.loaded,
                saved = regions.saved,
                download = regions.download,
                usedBytes = regions.usedBytes,
                freeBytes = regions.freeBytes,
                limit = limit,
                picker = nav.picker?.uiState(savedIds = regions.saved.mapTo(HashSet()) { it.id }),
                dialog = nav.dialog,
            )
        }.stateIn(viewModelScope, WhileUiSubscribed, OfflineRegionsUiState())

    init {
        viewModelScope.launch { port.load() }
    }

    fun onAction(action: OfflineRegionsAction) {
        when (action) {
            OfflineRegionsAction.OpenStorageLimit -> {
                showDialog(OfflineRegionsDialog.StorageLimit)
            }

            is OfflineRegionsAction.SetStorageLimit -> {
                setStorageLimit(action.limit)
            }

            OfflineRegionsAction.AddRegion -> {
                openPicker()
            }

            OfflineRegionsAction.RetryCatalog -> {
                retryCatalog()
            }

            is OfflineRegionsAction.OpenRegion -> {
                openRegion(action.regionId)
            }

            is OfflineRegionsAction.PickRegion -> {
                pickRegion(action.regionId)
            }

            OfflineRegionsAction.Back -> {
                back()
            }

            OfflineRegionsAction.ConfirmSave -> {
                confirmSave()
            }

            OfflineRegionsAction.ChangeLimit -> {
                closePicker()
                showDialog(OfflineRegionsDialog.StorageLimit)
            }

            is OfflineRegionsAction.DeleteRegion -> {
                askDelete(action.regionId)
            }

            OfflineRegionsAction.ConfirmDelete -> {
                confirmDelete()
            }

            OfflineRegionsAction.CancelDownload -> {
                askCancel()
            }

            OfflineRegionsAction.ConfirmCancel -> {
                port.cancel()
                closeDialog()
            }

            OfflineRegionsAction.ResumeDownload -> {
                port.resume()
            }

            OfflineRegionsAction.DismissDownload -> {
                port.cancel()
            }

            OfflineRegionsAction.CloseDialog -> {
                closeDialog()
            }

            OfflineRegionsAction.Closed -> {
                closePicker()
                navigation.value = Navigation()
            }
        }
    }

    private fun setStorageLimit(limit: OfflineStorageLimit) {
        viewModelScope.launch { displayPreferences.setOfflineStorageLimit(limit) }
        closeDialog()
    }

    private fun openPicker() {
        navigation.update { it.copy(picker = Picker()) }
        loadCatalog()
    }

    private fun retryCatalog() {
        if (navigation.value.picker?.catalog == RegionCatalogResult.Unavailable) {
            navigation.update { it.copy(picker = Picker()) }
            loadCatalog()
        }
    }

    private fun loadCatalog() {
        catalogJob?.cancel()
        catalogJob =
            viewModelScope.launch {
                val here = position()
                val result = port.catalog(longitude = here?.first, latitude = here?.second)
                navigation.update { nav -> nav.copy(picker = nav.picker?.copy(catalog = result)) }
            }
    }

    private fun openRegion(regionId: String) =
        navigation.update { nav ->
            val picker = nav.picker
            // A saved region's file already covers its sub-regions; opening them would offer bytes saved twice.
            val opens =
                picker?.pendingId == null &&
                    !isSaved(regionId) &&
                    picker?.catalogOrNull()?.hasOfferedChildren(regionId) == true
            if (picker != null && opens) nav.copy(picker = picker.copy(path = picker.path + regionId)) else nav
        }

    private fun pickRegion(regionId: String) {
        val region =
            navigation.value.picker
                ?.takeIf { it.pendingId == null }
                ?.catalogOrNull()
                ?.regionOrNull(regionId)
                ?.takeIf { it.approxBytes != null && !isSaved(regionId) }
                ?: return
        navigation.update { nav -> nav.copy(picker = nav.picker?.copy(pendingId = regionId)) }
        offerJob =
            viewModelScope.launch {
                val offer = port.offer(region)
                navigation.update { nav ->
                    nav.copy(
                        picker = nav.picker?.copy(pendingId = null),
                        dialog = offer.dialogOrNull(),
                        ready = offer as? RegionOffer.Ready,
                    )
                }
            }
    }

    // The pending offer belongs to the level on screen, so leaving that level drops it.
    private fun back() {
        offerJob?.cancel()
        if (navigation.value.picker
                ?.path
                .isNullOrEmpty()
        ) {
            closePicker()
        } else {
            navigation.update { nav ->
                nav.copy(picker = nav.picker?.let { it.copy(path = it.path.dropLast(1), pendingId = null) })
            }
        }
    }

    private fun confirmSave() {
        navigation.value.ready?.let(port::start)
        closePicker()
        closeDialog()
    }

    private fun askDelete(regionId: String) {
        port.state.value.saved
            .firstOrNull { it.id == regionId }
            ?.let { showDialog(OfflineRegionsDialog.ConfirmDelete(it)) }
    }

    private fun confirmDelete() {
        (navigation.value.dialog as? OfflineRegionsDialog.ConfirmDelete)?.let { dialog ->
            viewModelScope.launch { port.delete(dialog.region.id) }
        }
        closeDialog()
    }

    private fun askCancel() {
        port.state.value.download
            ?.let { showDialog(OfflineRegionsDialog.ConfirmCancel(it.name)) }
    }

    private fun isSaved(regionId: String): Boolean =
        port.state.value.saved
            .any { it.id == regionId }

    private fun closePicker() {
        catalogJob?.cancel()
        offerJob?.cancel()
        navigation.update { it.copy(picker = null) }
    }

    private fun showDialog(dialog: OfflineRegionsDialog) = navigation.update { it.copy(dialog = dialog) }

    private fun closeDialog() = navigation.update { it.copy(dialog = null, ready = null) }

    private data class Navigation(
        val picker: Picker? = null,
        val dialog: OfflineRegionsDialog? = null,
        // The offer the save confirmation shows: start takes it whole, its catalogue region and probed file included.
        val ready: RegionOffer.Ready? = null,
    )

    private data class Picker(
        /** Null while the region list loads. */
        val catalog: RegionCatalogResult? = null,
        /** The opened regions, from the root down. */
        val path: List<String> = emptyList(),
        val pendingId: String? = null,
    ) {
        fun catalogOrNull(): RegionCatalog? = (catalog as? RegionCatalogResult.Loaded)?.catalog

        fun uiState(savedIds: Set<String>): RegionPickerUiState =
            when (catalog) {
                null -> RegionPickerUiState.Loading
                RegionCatalogResult.Unavailable -> RegionPickerUiState.Unavailable
                is RegionCatalogResult.Loaded -> catalog.level(path.lastOrNull(), savedIds, pendingId)
            }
    }
}

private fun RegionCatalogResult.Loaded.level(
    parentId: String?,
    savedIds: Set<String>,
    pendingId: String?,
): RegionPickerUiState.Level {
    val parent = parentId?.let(catalog::regionOrNull)
    // Deeper levels are already narrowed by hand, so the suggestion shows at the root only.
    val suggestion = suggestedId?.takeIf { parentId == null }?.let(catalog::regionOrNull)
    val whole = parent?.takeIf { it.approxBytes != null }
    return RegionPickerUiState.Level(
        parentName = parent?.name,
        suggestion = suggestion?.let { catalog.row(it, savedIds) },
        rows =
            listOfNotNull(whole?.let { catalog.row(it, savedIds, whole = true) }) +
                catalog.childrenOf(parentId).map { catalog.row(it, savedIds) },
        pendingId = pendingId,
    )
}

private fun RegionCatalog.row(
    region: CatalogRegion,
    savedIds: Set<String>,
    whole: Boolean = false,
): RegionRow =
    RegionRow(
        id = region.id,
        name = region.name,
        approxBytes = region.approxBytes,
        hasChildren = !whole && hasOfferedChildren(region.id),
        saved = region.id in savedIds,
        whole = whole,
    )

// A busy repository or a region saved since cannot be reached from the sheet, which disables Add region during a
// download and marks saved rows; should one come back anyway, the offer closes without a word.
private fun RegionOffer.dialogOrNull(): OfflineRegionsDialog? =
    when (this) {
        is RegionOffer.Ready -> {
            OfflineRegionsDialog.ConfirmSave(region.name, file.sizeBytes, usedAfterBytes, limitBytes, freeBytes)
        }

        is RegionOffer.OverLimit -> {
            OfflineRegionsDialog.OverLimit(region.name, sizeBytes, overByBytes, limitBytes)
        }

        is RegionOffer.NoSpace -> {
            OfflineRegionsDialog.NoSpace(region.name, sizeBytes, freeBytes)
        }

        is RegionOffer.Unavailable -> {
            OfflineRegionsDialog.OfferUnavailable(region.name, reason).takeUnless {
                reason == Reason.BUSY || reason == Reason.ALREADY_SAVED
            }
        }
    }

internal class OfflineRegionsViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    // The repository is created here, when the sheet first opens, never on the launch path.
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        @Suppress("UNCHECKED_CAST")
        return OfflineRegionsViewModel(
            port = OfflineRegionRepository.get(application).asPort(),
            displayPreferences = DisplayPreferences(application),
            position = { latestPositionOrNull() },
        ) as T
    }

    // The latest fix the shared pipeline holds; the picker never asks for the location permission.
    private suspend fun latestPositionOrNull(): Pair<Double, Double>? =
        withTimeoutOrNull(PositionTimeout) { LocationGraph.get(application).locationFlow().first() }
            ?.let { it.longitude to it.latitude }

    private fun OfflineRegionRepository.asPort(): OfflineRegionsPort {
        val repository = this
        return object : OfflineRegionsPort {
            override val state: StateFlow<OfflineRegionsState> = repository.state

            override suspend fun load() = repository.load()

            override suspend fun catalog(
                longitude: Double?,
                latitude: Double?,
            ): RegionCatalogResult = repository.catalog(longitude, latitude)

            override suspend fun offer(region: CatalogRegion): RegionOffer = repository.offer(region)

            override fun start(offer: RegionOffer.Ready) = repository.start(offer)

            override fun resume() = repository.resume()

            override fun cancel() = repository.cancel()

            override suspend fun delete(regionId: String) = repository.delete(regionId)
        }
    }
}

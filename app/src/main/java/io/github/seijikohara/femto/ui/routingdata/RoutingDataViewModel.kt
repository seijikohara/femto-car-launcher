package io.github.seijikohara.femto.ui.routingdata

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.seijikohara.femto.data.common.WhileUiSubscribed
import io.github.seijikohara.femto.data.routing.InstalledRoutingTile
import io.github.seijikohara.femto.data.routing.RoutingTileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Narrow port for the routing tile store the sheet drives. The factory binds it to [RoutingTileStore]; tests
 * substitute an in-memory fake. [tiles] reads the store again after every change, a delete included.
 */
internal interface RoutingDataPort {
    val tiles: Flow<List<InstalledRoutingTile>>

    suspend fun delete(name: String)

    suspend fun deleteAll()
}

/**
 * Binds the routing tile store to the Routing data sheet: the tiles on the device, and their deletes, each after a
 * confirmation. A delete waits in the store for a download of the same tile to end; the list follows the store, so
 * the tile goes from the list once its files are gone. Nothing here downloads.
 */
internal class RoutingDataViewModel(
    private val port: RoutingDataPort,
) : ViewModel() {
    private val dialog = MutableStateFlow<RoutingDataDialog?>(null)

    // Null until the store has read its directory.
    private val tiles: Flow<List<InstalledRoutingTile>?> = port.tiles

    val uiState: StateFlow<RoutingDataUiState> =
        combine(
            tiles.onStart { emit(null) },
            dialog,
        ) { tiles, open ->
            RoutingDataUiState(loaded = tiles != null, tiles = tiles.orEmpty(), dialog = open)
        }.stateIn(viewModelScope, WhileUiSubscribed, RoutingDataUiState())

    fun onAction(action: RoutingDataAction) {
        when (action) {
            is RoutingDataAction.DeleteTile -> {
                uiState.value.tiles
                    .firstOrNull { it.name == action.name }
                    ?.let { dialog.value = RoutingDataDialog.ConfirmDelete(it) }
            }

            RoutingDataAction.ConfirmDelete -> {
                (dialog.value as? RoutingDataDialog.ConfirmDelete)?.let { confirm ->
                    viewModelScope.launch { port.delete(confirm.tile.name) }
                }
                dialog.value = null
            }

            RoutingDataAction.DeleteAll -> {
                uiState.value
                    .takeIf { it.tiles.isNotEmpty() }
                    ?.let { dialog.value = RoutingDataDialog.ConfirmDeleteAll(it.totalBytes) }
            }

            RoutingDataAction.ConfirmDeleteAll -> {
                if (dialog.value is RoutingDataDialog.ConfirmDeleteAll) viewModelScope.launch { port.deleteAll() }
                dialog.value = null
            }

            RoutingDataAction.CloseDialog, RoutingDataAction.Closed -> {
                dialog.value = null
            }
        }
    }
}

internal class RoutingDataViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        val store = RoutingTileStore.get(application)

        @Suppress("UNCHECKED_CAST")
        return RoutingDataViewModel(
            port =
                object : RoutingDataPort {
                    override val tiles: Flow<List<InstalledRoutingTile>> = store.installed

                    override suspend fun delete(name: String) = store.delete(name)

                    override suspend fun deleteAll() = store.deleteAll()
                },
        ) as T
    }
}

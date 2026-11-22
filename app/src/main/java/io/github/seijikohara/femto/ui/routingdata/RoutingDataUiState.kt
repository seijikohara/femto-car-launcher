package io.github.seijikohara.femto.ui.routingdata

import io.github.seijikohara.femto.data.routing.InstalledRoutingTile

/**
 * State for the Routing data sheet: the installed routing tiles, by name, and the confirmation on screen. [loaded]
 * turns true once the store has read its directory, so an empty list never flashes before the tiles.
 */
internal data class RoutingDataUiState(
    val loaded: Boolean = false,
    val tiles: List<InstalledRoutingTile> = emptyList(),
    val dialog: RoutingDataDialog? = null,
) {
    val totalBytes: Long get() = tiles.sumOf { it.sizeBytes }
}

/** The confirmation before a delete; every delete asks first. */
internal sealed interface RoutingDataDialog {
    data class ConfirmDelete(
        val tile: InstalledRoutingTile,
    ) : RoutingDataDialog

    data class ConfirmDeleteAll(
        val totalBytes: Long,
    ) : RoutingDataDialog
}

/** User intents from the sheet. */
internal sealed interface RoutingDataAction {
    data class DeleteTile(
        val name: String,
    ) : RoutingDataAction

    data object ConfirmDelete : RoutingDataAction

    data object DeleteAll : RoutingDataAction

    data object ConfirmDeleteAll : RoutingDataAction

    data object CloseDialog : RoutingDataAction

    /** The sheet left the screen; a confirmation left open ends with it. */
    data object Closed : RoutingDataAction
}

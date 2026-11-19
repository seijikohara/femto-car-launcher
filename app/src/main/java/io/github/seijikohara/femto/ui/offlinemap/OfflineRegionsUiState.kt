package io.github.seijikohara.femto.ui.offlinemap

import io.github.seijikohara.femto.data.map.offline.OfflineStorageLimit
import io.github.seijikohara.femto.data.map.offline.RegionDownload
import io.github.seijikohara.femto.data.map.offline.RegionOffer
import io.github.seijikohara.femto.data.map.offline.SavedRegion

/**
 * State for the Saved map regions sheet: the repository's saved regions, download and storage figures, the storage
 * limit, and the sheet's own navigation. The list shows while [picker] is null.
 */
internal data class OfflineRegionsUiState(
    val loaded: Boolean = false,
    /** By name, case-insensitive. */
    val saved: List<SavedRegion> = emptyList(),
    val download: RegionDownload? = null,
    val usedBytes: Long = 0,
    val freeBytes: Long = 0,
    val limit: OfflineStorageLimit = OfflineStorageLimit.NONE,
    val picker: RegionPickerUiState? = null,
    val dialog: OfflineRegionsDialog? = null,
)

internal sealed interface RegionPickerUiState {
    data object Loading : RegionPickerUiState

    data object Unavailable : RegionPickerUiState

    data class Level(
        /** The region whose sub-regions show; null at the root. */
        val parentName: String?,
        /** The region for the current location, shown above the root's rows only. */
        val suggestion: RegionRow?,
        /** The whole parent first when it has a file, then the sub-regions by name. */
        val rows: List<RegionRow>,
        /** The region whose offer is being judged; no row takes a tap meanwhile. */
        val pendingId: String?,
    ) : RegionPickerUiState
}

internal data class RegionRow(
    val id: String,
    val name: String,
    /** Null when the region has no file of its own. */
    val approxBytes: Long?,
    /** Whether a tap opens the region's sub-regions rather than asking for its offer; a saved row opens nothing. */
    val hasChildren: Boolean,
    val saved: Boolean,
    /** The `All of <name>` row that picks the opened region whole. */
    val whole: Boolean = false,
)

internal sealed interface OfflineRegionsDialog {
    data class ConfirmSave(
        val name: String,
        val sizeBytes: Long,
        val usedAfterBytes: Long,
        val limitBytes: Long?,
        val freeBytes: Long,
    ) : OfflineRegionsDialog

    data class OverLimit(
        val name: String,
        val sizeBytes: Long,
        val overByBytes: Long,
        val limitBytes: Long,
    ) : OfflineRegionsDialog

    data class NoSpace(
        val name: String,
        val sizeBytes: Long,
        val freeBytes: Long,
    ) : OfflineRegionsDialog

    /** Only the reasons the sheet can reach: network, gone, or no size. */
    data class OfferUnavailable(
        val name: String,
        val reason: RegionOffer.Unavailable.Reason,
    ) : OfflineRegionsDialog

    data class ConfirmDelete(
        val region: SavedRegion,
    ) : OfflineRegionsDialog

    data class ConfirmCancel(
        val name: String,
    ) : OfflineRegionsDialog

    data object StorageLimit : OfflineRegionsDialog
}

/** User intents from the sheet. Taps that would fetch something are the only ones that do. */
internal sealed interface OfflineRegionsAction {
    data object OpenStorageLimit : OfflineRegionsAction

    data class SetStorageLimit(
        val limit: OfflineStorageLimit,
    ) : OfflineRegionsAction

    /** Open the picker, which fetches the region list. */
    data object AddRegion : OfflineRegionsAction

    data object RetryCatalog : OfflineRegionsAction

    data class OpenRegion(
        val regionId: String,
    ) : OfflineRegionsAction

    /** Ask what saving the region would take, which probes its file. */
    data class PickRegion(
        val regionId: String,
    ) : OfflineRegionsAction

    /** Up one picker level, or from the root back to the list. */
    data object Back : OfflineRegionsAction

    data object ConfirmSave : OfflineRegionsAction

    /** From the over-limit dialog: back to the list with the limit choice open. */
    data object ChangeLimit : OfflineRegionsAction

    data class DeleteRegion(
        val regionId: String,
    ) : OfflineRegionsAction

    data object ConfirmDelete : OfflineRegionsAction

    data object CancelDownload : OfflineRegionsAction

    data object ConfirmCancel : OfflineRegionsAction

    data object ResumeDownload : OfflineRegionsAction

    /** Clear a failed download, which has nothing left on disk to discard. */
    data object DismissDownload : OfflineRegionsAction

    data object CloseDialog : OfflineRegionsAction

    /** The sheet left the screen; a reopened sheet starts on the list. */
    data object Closed : OfflineRegionsAction
}

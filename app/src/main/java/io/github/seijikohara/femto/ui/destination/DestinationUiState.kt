package io.github.seijikohara.femto.ui.destination

import io.github.seijikohara.femto.data.location.VehicleMotion
import io.github.seijikohara.femto.data.places.PlaceTarget
import io.github.seijikohara.femto.data.places.SavedPlace
import io.github.seijikohara.femto.data.voice.VoiceState

/**
 * State for the destination panel. [motion] is the shared vehicle-motion
 * verdict (`LocationGraph.vehicleMotion()`); [typingAllowed] derives from it
 * the gate on every edit that involves typing — the text field, saving and
 * deleting places — while voice and a tap on a saved place work in every
 * motion state (issue #389, Safety).
 */
internal data class DestinationUiState(
    val query: String,
    val voice: VoiceState,
    val places: List<SavedPlace>,
    val motion: VehicleMotion,
) {
    /**
     * True unless the verdict is [VehicleMotion.MOVING]. The gate fails open:
     * [VehicleMotion.UNKNOWN] (no fix yet, a cached or network-only fix, no
     * location permission) allows typing. A phone without a fix or the
     * permission reads UNKNOWN for good, and voice plus saved places alone
     * would leave it no way to enter a new destination. MOVING still catches
     * the first live fix's own speed, which `TripState.stationary` alone
     * misses because that fix only anchors the trip math.
     */
    val typingAllowed: Boolean get() = motion != VehicleMotion.MOVING

    companion object {
        // UNKNOWN until the verdict arrives, so the gate fails open in the
        // same direction as typingAllowed describes.
        val Initial: DestinationUiState =
            DestinationUiState(
                query = "",
                voice = VoiceState.Idle,
                places = emptyList(),
                motion = VehicleMotion.UNKNOWN,
            )
    }
}

/** Intents the destination panel reports up to its ViewModel. */
internal sealed interface DestinationAction {
    /** The text field changed; ignored while moving. */
    data class QueryChanged(
        val text: String,
    ) : DestinationAction

    data object StartListening : DestinationAction

    data object StopListening : DestinationAction

    /** Keep the current query as a saved place labelled with it; not while moving. */
    data object SaveQuery : DestinationAction

    /**
     * Keep [point] (the current fix) as a saved place; not while moving. The
     * label is [address] (the address the dashboard shows), else the
     * coordinates, never the typed query: a query left over from a search
     * would name the spot after somewhere else.
     */
    data class SaveCurrentLocation(
        val point: PlaceTarget.Point,
        val address: String,
    ) : DestinationAction

    /** Remove a saved place; not while moving. */
    data class DeletePlace(
        val id: Long,
    ) : DestinationAction

    /**
     * Empty the query after a hand-off, so the next open starts fresh. Allowed
     * in every motion state: it types nothing.
     */
    data object ClearQuery : DestinationAction
}

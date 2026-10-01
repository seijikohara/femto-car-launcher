package io.github.seijikohara.femto.ui.destination

import io.github.seijikohara.femto.data.places.PlaceTarget
import io.github.seijikohara.femto.data.places.SavedPlace
import io.github.seijikohara.femto.data.voice.VoiceState

/**
 * State for the destination panel. [stationary] mirrors
 * `TripState.stationary`: it gates every edit that involves typing — the text
 * field, saving and deleting places — while voice and a tap on a saved place
 * work in every motion state (issue #389, Safety).
 */
internal data class DestinationUiState(
    val query: String,
    val voice: VoiceState,
    val places: List<SavedPlace>,
    val stationary: Boolean,
) {
    companion object {
        // Fail-closed until the trip state speaks: typing stays off rather than
        // briefly on for a vehicle that turns out to be moving.
        val Initial: DestinationUiState =
            DestinationUiState(
                query = "",
                voice = VoiceState.Idle,
                places = emptyList(),
                stationary = false,
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

    /** Keep the current query as a saved place labelled with it; stationary only. */
    data object SaveQuery : DestinationAction

    /**
     * Keep [point] (the current fix) as a saved place; stationary only. The
     * label is the typed query when there is one, else [address] (the address
     * the dashboard shows), else the coordinates.
     */
    data class SaveCurrentLocation(
        val point: PlaceTarget.Point,
        val address: String,
    ) : DestinationAction

    /** Remove a saved place; stationary only. */
    data class DeletePlace(
        val id: Long,
    ) : DestinationAction

    /**
     * Empty the query after a hand-off, so the next open starts fresh. Allowed
     * in every motion state: it types nothing.
     */
    data object ClearQuery : DestinationAction
}

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
 *
 * [handOffAvailable] is false when no installed app takes a destination; the
 * panel then hands off nothing and says why. It reads true until checked, so a
 * hand-off that works is never blocked by a check that has not run.
 *
 * [routeAvailable] says whether this build and map backend offer a route on
 * the launcher's own map (`routingAvailableFlow`); off, the panel shows
 * nothing about routes. [geocoderAvailable] says whether text can become a
 * position on this device at all; without a geocoder only a saved point can be
 * routed to. [route] is where a route the panel asked for stands.
 */
internal data class DestinationUiState(
    val query: String,
    val voice: VoiceState,
    val places: List<SavedPlace>,
    val motion: VehicleMotion,
    val handOffAvailable: Boolean = true,
    val routeAvailable: Boolean = false,
    val geocoderAvailable: Boolean = false,
    val route: RouteStep = RouteStep.Idle,
) {
    /**
     * Whether [target] can be routed to: a point always, text only through a
     * geocoder. Like a tap on a saved place, a route needs no typing, so it
     * works in every motion state.
     */
    fun canRoute(target: PlaceTarget): Boolean = routeAvailable && (target is PlaceTarget.Point || geocoderAvailable)

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
                handOffAvailable = true,
            )
    }
}

/**
 * Where a route the panel asked for stands. Every step but [Idle] takes the
 * panel's body over, so the driver sees one thing at a time.
 */
internal sealed interface RouteStep {
    data object Idle : RouteStep

    /** The geocoder is turning the destination's text into a position. */
    data object Locating : RouteStep

    /** The route needs routing data the device lacks: [bytes] to download, on the driver's word. */
    data class ConfirmDownload(
        val bytes: Long,
    ) : RouteStep

    data class Downloading(
        val receivedBytes: Long,
        val totalBytes: Long,
    ) : RouteStep

    /** BRouter is searching; a long route can take up to its time limit. */
    data object Computing : RouteStep

    /** The route is the active route now; the panel closes. */
    data object Done : RouteStep

    data class Failed(
        val reason: RouteFailure,
    ) : RouteStep
}

/**
 * The last position fix the dashboard holds: where it was ([point]) and when, on
 * the elapsed-realtime clock ([elapsedRealtimeNanos], as the fix's
 * `Location.getElapsedRealtimeNanos`). The location flow keeps replaying its
 * last fix after the receiver goes quiet, so the fix may be old; a route judges
 * its age (`isFresh`) as it starts.
 */
internal data class LastFix(
    val point: PlaceTarget.Point,
    val elapsedRealtimeNanos: Long,
)

/** Why a route did not come about; each has its own sentence in the panel. */
internal enum class RouteFailure {
    /** No current fix (none yet, or only an old one): a route starts from the current position. */
    NO_FIX,

    /** No location permission, so no fix will come: a route starts from the current position. */
    NO_LOCATION_PERMISSION,

    /** The geocoder answered and knows no such place. */
    PLACE_NOT_FOUND,

    /** The geocoder could not answer, as without a network. */
    LOOKUP_FAILED,

    /** The start or the destination lies where the routing data has no roads, such as open sea. */
    NO_ROAD_DATA,

    /** The routing data would not leave the free space the system keeps. */
    NO_SPACE,

    /** The search ran out of time; a closer destination may work. */
    TIMED_OUT,

    /** The installed routing data was built for another version of the app's lookup table. */
    DATA_VERSION_MISMATCH,

    /** The server could not say what to download, or a download broke off. */
    DOWNLOAD_FAILED,

    /** Anything else the engine reports, such as no road connecting the two ends. */
    NO_ROUTE,
}

/** Intents the destination panel reports up to its ViewModel. */
internal sealed interface DestinationAction {
    /** The text field changed; ignored while moving. */
    data class QueryChanged(
        val text: String,
    ) : DestinationAction

    data object StartListening : DestinationAction

    data object StopListening : DestinationAction

    /**
     * Cancel recognition and discard any pending result: a hand-off and a
     * closing panel use it, so a late transcript can neither refill the query
     * nor leave the microphone open behind another app.
     */
    data object CancelListening : DestinationAction

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

    /**
     * Look again for an app that takes the hand-off. The panel's host sends it
     * on opening and on every return to the launcher, since a maps app may have
     * been installed or removed in between.
     */
    data object CheckHandOff : DestinationAction

    /**
     * Compute a route from [origin] (the last fix, null without one) to
     * [target] on the launcher's own map. A fix that is no longer fresh counts
     * as none. Ignored unless [DestinationUiState.canRoute] holds; allowed in
     * every motion state.
     */
    data class RouteTo(
        val target: PlaceTarget,
        val origin: LastFix?,
    ) : DestinationAction

    /** The driver agreed to the download [RouteStep.ConfirmDownload] asked about. */
    data object ConfirmDownload : DestinationAction

    /** After [RouteFailure.DATA_VERSION_MISMATCH]: delete the route's routing data and fetch it again. */
    data object DownloadAgain : DestinationAction

    /**
     * Stop the route under way, or put its message away, and give the panel
     * its entry back: Cancel, OK, a closing panel, and the host once a route
     * is [RouteStep.Done].
     */
    data object DismissRoute : DestinationAction
}

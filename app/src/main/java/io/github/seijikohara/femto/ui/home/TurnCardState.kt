package io.github.seijikohara.femto.ui.home

import io.github.seijikohara.femto.data.routing.RerouteStatus
import io.github.seijikohara.femto.data.routing.RouteTurn
import io.github.seijikohara.femto.data.routing.TurnKind
import io.github.seijikohara.femto.data.routing.UpcomingTurn

/**
 * The turn right after the announced one shows beside it when it lies within this many metres of it: two turns this
 * close leave no time to read the card again between them. A start, to tune on the road.
 */
internal const val THEN_WITHIN_METERS = 150.0

/** What to do at the next point the card announces. */
internal sealed interface TurnManeuver {
    /** A turn of [kind]; [exitNumber] is the roundabout's exit (0 elsewhere); [leftward] tells a plain U-turn's side. */
    data class Turn(
        val kind: TurnKind,
        val exitNumber: Int = 0,
        val leftward: Boolean = true,
    ) : TurnManeuver

    /** No turn is left: the destination. */
    data object Arrive : TurnManeuver
}

/** What the turn card shows (issue #389 phase 4). */
internal data class TurnCardState(
    val maneuver: TurnManeuver,
    /** Along the route, from the vehicle. */
    val distanceMeters: Double,
    /** The turn right after [maneuver], when it follows within [THEN_WITHIN_METERS]. */
    val then: TurnManeuver.Turn? = null,
    /** The announced turn's [RouteTurn.pointIndex], which tells one turn from the next; null for the destination. */
    val pointIndex: Int? = null,
    /**
     * Whether the spoken prompts are on, which a tap on the card switches; null where the card offers no control for
     * them, because the device cannot speak.
     */
    val spoken: Boolean? = null,
)

/** The three Settings → Navigation switches the turn card follows, and what its spoken-prompts control shows. */
internal data class TurnCardSettings(
    val show: Boolean = true,
    val showNextAfter: Boolean = true,
    val showStraight: Boolean = false,
    /** Whether the spoken prompts are on; null where the card offers no control for them. */
    val spoken: Boolean? = null,
)

/**
 * Return what the turn card shows, or null for no card: with the card [TurnCardSettings.show] off, for a route without
 * turn data ([upcomingTurns] null), until a fix has been placed on the line ([fixPlaced]; before it the turns count
 * from the start of the line, which a restored route can lie far behind), while the vehicle is [offRoute], while a
 * reroute's status shows ([reroute] not [RerouteStatus.NONE]) and while the route choice card is open ([choosing]).
 *
 * The first turn ahead that the card announces is the manoeuvre; "continue straight" counts only with
 * [TurnCardSettings.showStraight], and the engine's off-route and beeline hints never do, as no driver acts on them.
 * With no turn left the card shows the destination at [remainingMeters].
 */
internal fun turnCardStateOrNull(
    upcomingTurns: List<UpcomingTurn>?,
    remainingMeters: Double,
    fixPlaced: Boolean,
    offRoute: Boolean,
    reroute: RerouteStatus,
    choosing: Boolean,
    settings: TurnCardSettings,
): TurnCardState? =
    upcomingTurns
        ?.takeIf { settings.show && fixPlaced && !offRoute && reroute == RerouteStatus.NONE && !choosing }
        ?.filter { it.turn.kind.announced(settings.showStraight) }
        ?.let { turns ->
            turns.firstOrNull()?.let { first ->
                TurnCardState(
                    maneuver = first.turn.maneuver(),
                    distanceMeters = first.distanceMeters,
                    then =
                        turns
                            .getOrNull(1)
                            ?.takeIf {
                                settings.showNextAfter &&
                                    it.distanceMeters - first.distanceMeters <= THEN_WITHIN_METERS
                            }?.turn
                            ?.maneuver(),
                    pointIndex = first.turn.pointIndex,
                    spoken = settings.spoken,
                )
            }
                ?: TurnCardState(
                    maneuver = TurnManeuver.Arrive,
                    distanceMeters = remainingMeters,
                    spoken = settings.spoken,
                )
        }

private fun TurnKind.announced(showStraight: Boolean): Boolean =
    when (this) {
        TurnKind.OFF_ROUTE, TurnKind.BEELINE -> false
        TurnKind.STRAIGHT -> showStraight
        else -> true
    }

// The engine's angle is negative to the left; a U-turn reported straight back (0) counts as one to the left.
private fun RouteTurn.maneuver(): TurnManeuver.Turn =
    TurnManeuver.Turn(kind = kind, exitNumber = exitNumber, leftward = kind != TurnKind.U_TURN || angleDegrees <= 0)

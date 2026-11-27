package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.routing.TurnKind
import io.github.seijikohara.femto.ui.home.TurnCardState
import io.github.seijikohara.femto.ui.home.TurnManeuver

/** The turn card's three shapes: a turn, a turn with the turn after next, and the destination. */
internal object TurnCardFixtures {
    /** A left turn 300 m ahead: "300 m", "Turn left". */
    val turn = TurnCardState(maneuver = TurnManeuver.Turn(TurnKind.LEFT), distanceMeters = 300.0)

    /** [turn] with a right turn following closely: the small arrow under "Then". */
    val turnThen = turn.copy(then = TurnManeuver.Turn(TurnKind.RIGHT))

    /** [state] with the control for the spoken prompts, which are [on] or off. */
    fun withVoice(
        state: TurnCardState,
        on: Boolean,
    ) = state.copy(spoken = on)

    /** No turn left: the flag, "1.2 km", "Destination". */
    val arrive = TurnCardState(maneuver = TurnManeuver.Arrive, distanceMeters = 1_240.0)
}

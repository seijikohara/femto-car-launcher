package io.github.seijikohara.femto.ui.home

// The advance prompt comes about ADVANCE_LEAD_SECONDS before the turn at the current speed, never nearer than the
// minimum nor farther than the maximum: long enough to change lane, short enough to be remembered. A start, to tune
// on the road.
private const val ADVANCE_LEAD_SECONDS = 20.0
private const val ADVANCE_LEAD_MIN_METERS = 200.0
private const val ADVANCE_LEAD_MAX_METERS = 800.0

// The final prompt comes about FINAL_LEAD_SECONDS before the turn, between the minimum and the maximum: the reminder
// that the turn is here. A start, to tune on the road.
private const val FINAL_LEAD_SECONDS = 6.0
private const val FINAL_LEAD_MIN_METERS = 30.0
private const val FINAL_LEAD_MAX_METERS = 150.0

// The memory before any turn has been seen; no follow of the dashboard carries this number.
private const val NO_FOLLOW = -1

/** Which of a turn's two prompts is due. */
internal enum class TurnPromptStage { ADVANCE, FINAL }

/** One prompt that is due: the card's content at that moment. */
internal data class TurnPrompt(
    val stage: TurnPromptStage,
    val maneuver: TurnManeuver,
    val distanceMeters: Double,
    val then: TurnManeuver.Turn?,
)

/** What the planner remembers between fixes: the turn it last saw and which of its prompts are behind it. */
internal data class TurnPromptMemory(
    val followId: Int = NO_FOLLOW,
    val pointIndex: Int? = null,
    val arriving: Boolean = false,
    val advanceBehind: Boolean = false,
    val finalBehind: Boolean = false,
)

/** What [step] decided: the memory to carry on with and the prompt that is due, if any. */
internal data class TurnPromptStep(
    val memory: TurnPromptMemory,
    val prompt: TurnPrompt?,
)

/**
 * Return what to say for [card] now, if anything, and what to remember. A null [card] says nothing and forgets
 * nothing, so the same turn showing again after a hide is not announced a second time. A turn is the follow it belongs
 * to ([followId]) and its point on the route; the destination is one more turn, with no point.
 *
 * The final prompt is due within its lead of the turn, the advance prompt within its own. The advance prompt is
 * spoken only while the turn lies farther than twice the final lead: nearer, the two would run into each other, and
 * only the final one is said. An unknown or negative speed counts as standing still, so the minimum leads apply.
 */
internal fun TurnPromptMemory.step(
    followId: Int,
    card: TurnCardState?,
    speedMetersPerSecond: Double?,
): TurnPromptStep =
    card?.let { shown ->
        val arriving = shown.maneuver is TurnManeuver.Arrive
        val known =
            takeIf { it.followId == followId && it.pointIndex == shown.pointIndex && it.arriving == arriving }
                ?: TurnPromptMemory(followId = followId, pointIndex = shown.pointIndex, arriving = arriving)
        val speed = (speedMetersPerSecond ?: 0.0).coerceAtLeast(0.0)
        val finalLead = (speed * FINAL_LEAD_SECONDS).coerceIn(FINAL_LEAD_MIN_METERS, FINAL_LEAD_MAX_METERS)
        val advanceLead = (speed * ADVANCE_LEAD_SECONDS).coerceIn(ADVANCE_LEAD_MIN_METERS, ADVANCE_LEAD_MAX_METERS)

        fun promptOf(stage: TurnPromptStage) =
            TurnPrompt(
                stage = stage,
                maneuver = shown.maneuver,
                distanceMeters = shown.distanceMeters,
                then = shown.then,
            )
        when {
            shown.distanceMeters <= finalLead && !known.finalBehind -> {
                TurnPromptStep(known.copy(advanceBehind = true, finalBehind = true), promptOf(TurnPromptStage.FINAL))
            }

            shown.distanceMeters <= advanceLead && !known.advanceBehind -> {
                TurnPromptStep(
                    memory = known.copy(advanceBehind = true),
                    prompt = promptOf(TurnPromptStage.ADVANCE).takeIf { shown.distanceMeters > 2 * finalLead },
                )
            }

            else -> {
                TurnPromptStep(known, prompt = null)
            }
        }
    } ?: TurnPromptStep(this, prompt = null)

package io.github.seijikohara.femto.ui.home

import android.content.res.Resources
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.home.components.spokenTurnDistance
import io.github.seijikohara.femto.ui.home.components.turnText
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import kotlin.math.roundToInt

// The distance in a diagnostics label rounds to this many metres: a label says how far out the prompt came, and a
// finer figure would only echo the fix's jitter.
private const val LABEL_STEP_METERS = 10

/**
 * Return the sentence to speak for this prompt, with the distance in [speedUnit]'s family. The sentence reads the
 * app's string resources, which are English only today and so is the voice. When they are translated, this sentence
 * must keep reading the English ones, or the voice's language must follow the translation. The turn's text starts in lower case inside a sentence ("In 300 metres, turn left.") and keeps
 * its capital where it opens one ("Turn left.").
 */
internal fun TurnPrompt.text(
    resources: Resources,
    speedUnit: SpeedUnit,
): String =
    when (stage) {
        TurnPromptStage.ADVANCE -> advanceText(resources, speedUnit)
        TurnPromptStage.FINAL -> finalText(resources)
    }

private fun TurnPrompt.advanceText(
    resources: Resources,
    speedUnit: SpeedUnit,
): String {
    val (value, unit) = spokenTurnDistance(distanceMeters, speedUnit, resources)
    return when {
        maneuver is TurnManeuver.Arrive -> {
            resources.getString(R.string.voice_arrive_advance, value, unit)
        }

        then != null -> {
            resources.getString(
                R.string.voice_turn_advance_then,
                value,
                unit,
                midSentence(maneuver, resources),
                midSentence(then, resources),
            )
        }

        else -> {
            resources.getString(R.string.voice_turn_advance, value, unit, midSentence(maneuver, resources))
        }
    }
}

private fun TurnPrompt.finalText(resources: Resources): String =
    when {
        maneuver is TurnManeuver.Arrive -> {
            resources.getString(R.string.voice_arrive_final)
        }

        then != null -> {
            resources.getString(
                R.string.voice_turn_final_then,
                turnText(maneuver, resources),
                midSentence(then, resources),
            )
        }

        else -> {
            resources.getString(R.string.voice_turn_final, turnText(maneuver, resources))
        }
    }

// Only the first letter changes, so "Make a U-turn" keeps its capital U.
private fun midSentence(
    maneuver: TurnManeuver,
    resources: Resources,
): String = turnText(maneuver, resources).replaceFirstChar { it.lowercase() }

/**
 * Return the prompt as the diagnostics report shows it: the stage and how far out it came, in whole metres rounded to
 * 10, such as "advance at 280 m". Never the manoeuvre, which is where the vehicle is going.
 */
internal fun TurnPrompt.label(): String =
    "${stage.name.lowercase()} at ${(distanceMeters / LABEL_STEP_METERS).roundToInt() * LABEL_STEP_METERS} m"

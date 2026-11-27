package io.github.seijikohara.femto.ui.home

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.data.routing.TurnKind
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** The sentence each prompt speaks, in English whatever the device's language. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TurnPromptTextTest {
    private val resources: Resources = ApplicationProvider.getApplicationContext<Context>().resources

    private val left = TurnManeuver.Turn(TurnKind.LEFT)
    private val right = TurnManeuver.Turn(TurnKind.RIGHT)

    @Test
    fun `the advance prompt names the distance and the turn`() {
        assertEquals("In 300 metres, turn left.", textOf(prompt(TurnPromptStage.ADVANCE, left, 300.0)))
    }

    @Test
    fun `the advance prompt with a turn after next adds it`() {
        assertEquals(
            "In 300 metres, turn left, then turn right.",
            textOf(prompt(TurnPromptStage.ADVANCE, left, 300.0, then = right)),
        )
    }

    @Test
    fun `the final prompt is the turn with its capital`() {
        assertEquals("Turn left.", textOf(prompt(TurnPromptStage.FINAL, left, 80.0)))
    }

    @Test
    fun `the final prompt with a turn after next adds it in lower case`() {
        assertEquals(
            "Turn left, then turn right.",
            textOf(prompt(TurnPromptStage.FINAL, left, 80.0, then = right)),
        )
    }

    @Test
    fun `the advance prompt for the destination`() {
        assertEquals(
            "In 300 metres, you will reach your destination.",
            textOf(prompt(TurnPromptStage.ADVANCE, TurnManeuver.Arrive, 300.0)),
        )
    }

    @Test
    fun `the final prompt for the destination`() {
        assertEquals("You have arrived.", textOf(prompt(TurnPromptStage.FINAL, TurnManeuver.Arrive, 40.0)))
    }

    @Test
    fun `a distance in feet is spoken in feet`() {
        assertEquals(
            "In 650 feet, turn left.",
            textOf(prompt(TurnPromptStage.ADVANCE, left, 198.12), SpeedUnit.MILES_PER_HOUR),
        )
    }

    @Test
    fun `a long distance in miles is spoken in miles`() {
        assertEquals(
            "In 0.3 miles, turn left.",
            textOf(prompt(TurnPromptStage.ADVANCE, left, 482.8), SpeedUnit.MILES_PER_HOUR),
        )
    }

    @Test
    fun `a long distance in kilometres is spoken in kilometres`() {
        assertEquals(
            "In 1.2 kilometres, turn left.",
            textOf(prompt(TurnPromptStage.ADVANCE, left, 1_240.0)),
        )
    }

    @Test
    fun `a roundabout exit is spoken with its number`() {
        assertEquals(
            "In 300 metres, take exit 2.",
            textOf(
                prompt(
                    TurnPromptStage.ADVANCE,
                    TurnManeuver.Turn(TurnKind.ROUNDABOUT_CLOCKWISE, exitNumber = 2),
                    300.0,
                ),
            ),
        )
    }

    @Test
    fun `a U-turn keeps its capital U`() {
        assertEquals(
            "In 300 metres, make a U-turn.",
            textOf(prompt(TurnPromptStage.ADVANCE, TurnManeuver.Turn(TurnKind.U_TURN_LEFT), 300.0)),
        )
    }

    @Test
    @Config(qualifiers = "ar-rEG")
    fun `under a native-digit locale the sentence keeps ASCII digits`() {
        assertEquals("In 300 metres, take exit 2.", textOf(exitPrompt()))
    }

    @Test
    fun `the label gives the stage and the distance rounded to 10 m`() {
        assertEquals(
            listOf("advance at 280 m", "final at 80 m"),
            listOf(
                prompt(TurnPromptStage.ADVANCE, left, 278.0).label(),
                prompt(TurnPromptStage.FINAL, right, 83.4).label(),
            ),
        )
    }

    private fun exitPrompt() =
        prompt(TurnPromptStage.ADVANCE, TurnManeuver.Turn(TurnKind.ROUNDABOUT_CLOCKWISE, exitNumber = 2), 300.0)

    private fun textOf(
        prompt: TurnPrompt,
        speedUnit: SpeedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
    ) = prompt.text(resources, speedUnit)

    private fun prompt(
        stage: TurnPromptStage,
        maneuver: TurnManeuver,
        meters: Double,
        then: TurnManeuver.Turn? = null,
    ) = TurnPrompt(stage = stage, maneuver = maneuver, distanceMeters = meters, then = then)
}

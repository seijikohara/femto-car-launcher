package io.github.seijikohara.femto.ui.home.components

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.data.routing.TurnKind
import io.github.seijikohara.femto.ui.home.TurnManeuver
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** The turn card's line of text for every manoeuvre, in English whatever the device's language. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TurnTextTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `every manoeuvre has its text`() {
        assertEquals(EXPECTED.values.toList(), texts())
    }

    // The app ships English strings only: under a device language that writes native digits, the sentence stays
    // English and the exit number in it stays in ASCII digits, as every number on the card does.
    @Test
    @Config(qualifiers = "ar-rEG")
    fun `under a native-digit locale the texts are the same English sentences with ASCII digits`() {
        assertEquals(EXPECTED.values.toList(), texts())
    }

    // The spoken sentences read the lines through the resources, with no composition to read them in.
    @Test
    fun `the lines read from the resources are the card's own`() {
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        assertEquals(EXPECTED.values.toList(), EXPECTED.keys.map { turnText(it, resources) })
    }

    @Test
    fun `every turn kind is covered`() {
        assertEquals(
            TurnKind.entries.toSet(),
            EXPECTED.keys
                .filterIsInstance<TurnManeuver.Turn>()
                .map { it.kind }
                .toSet(),
        )
    }

    private fun texts(): List<String> {
        var texts = emptyList<String>()
        rule.setContent { texts = EXPECTED.keys.map { turnText(it) } }
        rule.waitForIdle()
        return texts
    }

    private companion object {
        val EXPECTED: Map<TurnManeuver, String> =
            linkedMapOf(
                TurnManeuver.Turn(TurnKind.STRAIGHT) to "Continue straight",
                TurnManeuver.Turn(TurnKind.LEFT) to "Turn left",
                TurnManeuver.Turn(TurnKind.RIGHT) to "Turn right",
                TurnManeuver.Turn(TurnKind.SLIGHT_LEFT) to "Turn slightly left",
                TurnManeuver.Turn(TurnKind.SLIGHT_RIGHT) to "Turn slightly right",
                TurnManeuver.Turn(TurnKind.SHARP_LEFT) to "Turn sharply left",
                TurnManeuver.Turn(TurnKind.SHARP_RIGHT) to "Turn sharply right",
                TurnManeuver.Turn(TurnKind.KEEP_LEFT) to "Keep left",
                TurnManeuver.Turn(TurnKind.KEEP_RIGHT) to "Keep right",
                TurnManeuver.Turn(TurnKind.U_TURN_LEFT) to "Make a U-turn",
                TurnManeuver.Turn(TurnKind.U_TURN_RIGHT) to "Make a U-turn",
                TurnManeuver.Turn(TurnKind.U_TURN, leftward = false) to "Make a U-turn",
                TurnManeuver.Turn(TurnKind.ROUNDABOUT_COUNTERCLOCKWISE, exitNumber = 2) to "Take exit 2",
                TurnManeuver.Turn(TurnKind.ROUNDABOUT_CLOCKWISE, exitNumber = 12) to "Take exit 12",
                TurnManeuver.Turn(TurnKind.ROUNDABOUT_CLOCKWISE) to "Enter the roundabout",
                // The arrow shows the side.
                TurnManeuver.Turn(TurnKind.EXIT_LEFT) to "Take the exit",
                TurnManeuver.Turn(TurnKind.EXIT_RIGHT) to "Take the exit",
                // The card never announces these two (turnCardStateOrNull drops them); they read as going on.
                TurnManeuver.Turn(TurnKind.OFF_ROUTE) to "Continue straight",
                TurnManeuver.Turn(TurnKind.BEELINE) to "Continue straight",
                TurnManeuver.Arrive to "Destination",
            )
    }
}

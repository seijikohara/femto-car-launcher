package io.github.seijikohara.femto.ui.home

import io.github.seijikohara.femto.data.routing.TurnKind
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** When the turn card's content is spoken: two prompts per turn, led by the speed, each at most once. */
class TurnPromptsTest {
    // 50 km/h: the advance lead is 278 m, the final lead 83 m.
    @Test
    fun `at 50 km per hour the advance prompt is due inside 278 m and not outside it`() {
        assertEquals(
            listOf(null, TurnPromptStage.ADVANCE),
            listOf(290.0, 270.0).map { meters -> stageAt(meters, speed = 13.9) },
        )
    }

    @Test
    fun `at 50 km per hour the final prompt is due inside 83 m and not outside it`() {
        assertEquals(
            listOf(null, TurnPromptStage.FINAL),
            listOf(90.0, 80.0).map { meters -> stageAt(meters, speed = 13.9, seen = pastTheAdvancePrompt) },
        )
    }

    // 100 km/h: 556 m and 150 m, the final one at its cap.
    @Test
    fun `at 100 km per hour the advance prompt is due inside 556 m`() {
        assertEquals(
            listOf(null, TurnPromptStage.ADVANCE),
            listOf(570.0, 540.0).map { meters -> stageAt(meters, speed = 27.8) },
        )
    }

    @Test
    fun `at 100 km per hour the final lead is capped at 150 m`() {
        assertEquals(
            listOf(null, TurnPromptStage.FINAL),
            listOf(160.0, 140.0).map { meters -> stageAt(meters, speed = 27.8, seen = pastTheAdvancePrompt) },
        )
    }

    @Test
    fun `the advance lead is capped at 800 m`() {
        assertEquals(
            listOf(null, TurnPromptStage.ADVANCE),
            listOf(850.0, 780.0).map { meters -> stageAt(meters, speed = 60.0) },
        )
    }

    @Test
    fun `at a standstill the minimum leads apply`() {
        assertEquals(
            listOf(null, TurnPromptStage.ADVANCE, TurnPromptStage.FINAL),
            listOf(210.0, 190.0, 25.0).map { meters -> stageAt(meters, speed = 0.0) },
        )
    }

    @Test
    fun `an unknown speed counts as standing still`() {
        assertEquals(
            stageAt(190.0, speed = 0.0),
            stageAt(190.0, speed = null),
        )
    }

    @Test
    fun `a negative speed counts as standing still`() {
        assertEquals(
            stageAt(190.0, speed = 0.0),
            stageAt(190.0, speed = -3.0),
        )
    }

    @Test
    fun `each prompt is spoken once while the distance keeps shrinking`() {
        assertEquals(
            listOf(null, TurnPromptStage.ADVANCE, null, null, TurnPromptStage.FINAL, null),
            stagesOf(300.0, 270.0, 200.0, 100.0, 80.0, 40.0, speed = 13.9),
        )
    }

    @Test
    fun `a distance that jitters upward after a prompt does not repeat it`() {
        assertEquals(
            listOf(TurnPromptStage.ADVANCE, null, null, TurnPromptStage.FINAL, null, null),
            stagesOf(270.0, 280.0, 260.0, 80.0, 90.0, 70.0, speed = 13.9),
        )
    }

    @Test
    fun `a turn first seen inside twice the final lead gets no advance prompt and still its final one`() {
        assertEquals(
            listOf(null, TurnPromptStage.FINAL),
            stagesOf(150.0, 80.0, speed = 13.9),
        )
    }

    @Test
    fun `a turn first seen inside the final lead gets the final prompt at once`() {
        assertEquals(
            listOf(TurnPromptStage.FINAL, null),
            stagesOf(60.0, 50.0, speed = 13.9),
        )
    }

    @Test
    fun `a new turn resets the memory`() {
        assertEquals(
            listOf(TurnPromptStage.FINAL, null, TurnPromptStage.FINAL),
            listOf(
                turn(pointIndex = 5, meters = 60.0),
                turn(pointIndex = 5, meters = 50.0),
                turn(pointIndex = 9, meters = 60.0),
            ).runningPrompts(followId = 1, speed = 13.9),
        )
    }

    @Test
    fun `the same point index under another follow resets the memory`() {
        val first = TurnPromptMemory().at(turn(pointIndex = 5, meters = 60.0))
        val sameFollow = first.memory.at(turn(pointIndex = 5, meters = 50.0))
        val newFollow = first.memory.at(turn(pointIndex = 5, meters = 50.0), followId = 2)
        assertEquals(
            listOf(null, TurnPromptStage.FINAL),
            listOf(sameFollow.prompt?.stage, newFollow.prompt?.stage),
        )
    }

    @Test
    fun `a null card says nothing and keeps the memory`() {
        val spoken = TurnPromptMemory().at(turn(meters = 60.0))
        val hidden = spoken.memory.at(card = null)
        val shownAgain = hidden.memory.at(turn(meters = 50.0))
        assertEquals(
            Triple(null, spoken.memory, null),
            Triple(hidden.prompt, hidden.memory, shownAgain.prompt),
        )
    }

    @Test
    fun `the destination gets both prompts`() {
        assertEquals(
            listOf(TurnPromptStage.ADVANCE, TurnPromptStage.FINAL),
            listOf(arrive(meters = 270.0), arrive(meters = 40.0)).runningPrompts(followId = 1, speed = 13.9),
        )
    }

    @Test
    fun `the destination is a turn of its own`() {
        val atTurn = TurnPromptMemory().at(turn(pointIndex = 5, meters = 60.0))
        assertEquals(
            TurnPromptStage.FINAL,
            atTurn.memory
                .at(arrive(meters = 40.0))
                .prompt
                ?.stage,
        )
    }

    @Test
    fun `the prompt carries the card's manoeuvre, distance and turn after next`() {
        val card = turn(meters = 60.0).copy(then = TurnManeuver.Turn(TurnKind.RIGHT))
        assertEquals(
            TurnPrompt(
                stage = TurnPromptStage.FINAL,
                maneuver = TurnManeuver.Turn(TurnKind.LEFT),
                distanceMeters = 60.0,
                then = TurnManeuver.Turn(TurnKind.RIGHT),
            ),
            TurnPromptMemory().at(card).prompt,
        )
    }

    @Test
    fun `no prompt is due while the turn is out of range`() {
        assertNull(TurnPromptMemory().at(turn(meters = 5_000.0)).prompt)
    }

    // A turn whose advance prompt is behind it, so that only the final lead is under test.
    private val pastTheAdvancePrompt = TurnPromptMemory(followId = 1, pointIndex = 0, advanceBehind = true)

    private fun stageAt(
        meters: Double,
        speed: Double?,
        seen: TurnPromptMemory = TurnPromptMemory(),
    ): TurnPromptStage? =
        seen.step(followId = 1, card = turn(meters = meters), speedMetersPerSecond = speed).prompt?.stage

    // One fix at 50 km/h.
    private fun TurnPromptMemory.at(
        card: TurnCardState?,
        followId: Int = 1,
    ) = step(followId, card, speedMetersPerSecond = 13.9)

    private fun stagesOf(
        vararg meters: Double,
        speed: Double,
    ): List<TurnPromptStage?> = meters.map { turn(meters = it) }.runningPrompts(followId = 1, speed = speed)

    private fun List<TurnCardState>.runningPrompts(
        followId: Int,
        speed: Double,
    ): List<TurnPromptStage?> =
        runningFold(TurnPromptStep(TurnPromptMemory(), prompt = null)) { step, card ->
            step.memory.step(followId, card, speed)
        }.drop(1).map { it.prompt?.stage }

    private fun turn(
        meters: Double,
        pointIndex: Int = 0,
    ) = TurnCardState(maneuver = TurnManeuver.Turn(TurnKind.LEFT), distanceMeters = meters, pointIndex = pointIndex)

    private fun arrive(meters: Double) = TurnCardState(maneuver = TurnManeuver.Arrive, distanceMeters = meters)
}

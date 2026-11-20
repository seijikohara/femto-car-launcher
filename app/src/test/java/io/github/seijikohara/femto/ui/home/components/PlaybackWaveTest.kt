package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.data.display.MotionTier
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

/**
 * Decision table for [playbackWave]: the wave rises only while a track plays
 * with the vehicle stationary, and travels only at the standard motion tier.
 * Every other cell holds still, which is also what lets an idle-waiting
 * screenshot capture settle.
 */
@RunWith(Parameterized::class)
internal class PlaybackWaveTest(
    private val isPlaying: Boolean,
    private val stationary: Boolean,
    private val motionTier: MotionTier,
    private val expected: PlaybackWave,
) {
    @Test
    fun `decides whether the progress wave rises and travels`() {
        assertEquals(expected, playbackWave(isPlaying = isPlaying, stationary = stationary, motionTier = motionTier))
    }

    companion object {
        private val Flat = PlaybackWave(raised = false, travelling = false)
        private val Still = PlaybackWave(raised = true, travelling = false)
        private val Travelling = PlaybackWave(raised = true, travelling = true)

        @JvmStatic
        @Parameterized.Parameters(name = "playing={0}, stationary={1}, tier={2} -> {3}")
        fun cases(): List<Array<Any>> =
            listOf(
                arrayOf(true, true, MotionTier.STANDARD, Travelling),
                arrayOf(true, true, MotionTier.REDUCED, Still),
                arrayOf(true, true, MotionTier.OFF, Still),
                arrayOf(true, false, MotionTier.STANDARD, Flat),
                arrayOf(true, false, MotionTier.REDUCED, Flat),
                arrayOf(true, false, MotionTier.OFF, Flat),
                arrayOf(false, true, MotionTier.STANDARD, Flat),
                arrayOf(false, true, MotionTier.REDUCED, Flat),
                arrayOf(false, true, MotionTier.OFF, Flat),
                arrayOf(false, false, MotionTier.STANDARD, Flat),
                arrayOf(false, false, MotionTier.REDUCED, Flat),
                arrayOf(false, false, MotionTier.OFF, Flat),
            )
    }
}

package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.data.display.MotionTier

/**
 * How the music card's wavy progress indicator draws: [raised] gives the
 * progress line its wave, [travelling] moves the wave along the line.
 */
internal data class PlaybackWave(
    val raised: Boolean,
    val travelling: Boolean,
)

/**
 * Return how the playback progress wave draws.
 *
 * The wave rises only while a track plays with the vehicle stationary, the
 * same gate as the title marquee: a live wave on the always-visible card is a
 * distraction profile of its own while driving (AGENTS.md#driving-lockout),
 * and a paused track has nothing to show. It travels only at
 * [MotionTier.STANDARD]: the travel is an animation that never ends, so the
 * reduced and off tiers hold the raised wave still. A screenshot capture that
 * waits for an idle frame relies on the same rule to settle.
 */
internal fun playbackWave(
    isPlaying: Boolean,
    stationary: Boolean,
    motionTier: MotionTier,
): PlaybackWave =
    (isPlaying && stationary).let { raised ->
        PlaybackWave(raised = raised, travelling = raised && motionTier == MotionTier.STANDARD)
    }

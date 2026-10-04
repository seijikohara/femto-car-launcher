package io.github.seijikohara.femto.data.video

import io.github.seijikohara.femto.data.location.VehicleMotion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class VideoPictureGateTest {
    private val motion = MutableStateFlow(VehicleMotion.UNKNOWN)
    private val hideWhileDriving = MutableStateFlow(true)

    // The latest value the gate emitted, collected in the background.
    private fun TestScope.collectGate(): () -> Boolean? {
        var latest: Boolean? = null
        backgroundScope.launch { videoPictureVisibleFlow(motion, hideWhileDriving).collect { latest = it } }
        runCurrent()
        return { latest }
    }

    @Test
    fun `an unknown verdict keeps the picture hidden`() =
        runTest {
            val visible = collectGate()

            advanceTimeBy(VIDEO_PICTURE_STOP_DWELL_MS * 10)
            runCurrent()

            assertEquals(false, visible())
        }

    @Test
    fun `a moving verdict keeps the picture hidden`() =
        runTest {
            motion.value = VehicleMotion.MOVING
            val visible = collectGate()

            advanceTimeBy(VIDEO_PICTURE_STOP_DWELL_MS * 10)
            runCurrent()

            assertEquals(false, visible())
        }

    @Test
    fun `a parked verdict shows the picture only after the stop dwell`() =
        runTest {
            val visible = collectGate()
            motion.value = VehicleMotion.PARKED
            runCurrent()

            advanceTimeBy(VIDEO_PICTURE_STOP_DWELL_MS - 1)
            runCurrent()
            assertEquals(false, visible(), "the picture came back before the dwell")

            advanceTimeBy(1)
            runCurrent()
            assertEquals(true, visible())
        }

    @Test
    fun `a move hides the picture at once`() =
        runTest {
            val visible = collectGate()
            motion.value = VehicleMotion.PARKED
            advanceTimeBy(VIDEO_PICTURE_STOP_DWELL_MS)
            runCurrent()
            assertEquals(true, visible())

            motion.value = VehicleMotion.MOVING
            runCurrent()

            assertEquals(false, visible())
        }

    @Test
    fun `a break in the stop starts the dwell over`() =
        runTest {
            val visible = collectGate()
            motion.value = VehicleMotion.PARKED
            advanceTimeBy(VIDEO_PICTURE_STOP_DWELL_MS - 1)
            runCurrent()

            motion.value = VehicleMotion.UNKNOWN
            runCurrent()
            motion.value = VehicleMotion.PARKED
            advanceTimeBy(VIDEO_PICTURE_STOP_DWELL_MS - 1)
            runCurrent()
            assertEquals(false, visible())

            advanceTimeBy(1)
            runCurrent()
            assertEquals(true, visible())
        }

    @Test
    fun `with the gate turned off the picture shows while moving`() =
        runTest {
            motion.value = VehicleMotion.MOVING
            hideWhileDriving.value = false

            val visible = collectGate()

            assertEquals(true, visible())
        }

    @Test
    fun `turning the gate back on while moving hides the picture at once`() =
        runTest {
            motion.value = VehicleMotion.MOVING
            hideWhileDriving.value = false
            val visible = collectGate()

            hideWhileDriving.value = true
            runCurrent()

            assertEquals(false, visible())
        }

    @Test
    fun `a failing motion source hides the picture`() =
        runTest {
            var latest: Boolean? = null
            backgroundScope.launch {
                videoPictureVisibleFlow(flow { error("location stack failed") }, hideWhileDriving)
                    .collect { latest = it }
            }
            runCurrent()

            assertEquals(false, latest)
        }
}

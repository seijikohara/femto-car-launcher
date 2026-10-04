package io.github.seijikohara.femto.data.common

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The receivers' work under goAsync(): finish() lands exactly once, after the
 * work and never before it, whether the work succeeds, fails or outlasts the
 * broadcast's time budget. A receiver that never finishes holds its broadcast
 * until the system calls the app unresponsive.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReceiverWorkTest {
    private var finishes = 0

    @Test
    fun `finish waits for the work, then lands once`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            backgroundScope.launchFinishing(TAG, finish = { finishes++ }) { gate.await() }
            runCurrent()
            assertEquals(0, finishes)

            gate.complete(Unit)
            runCurrent()

            assertEquals(1, finishes)
        }

    @Test
    fun `finish lands once when the work fails`() =
        runTest {
            backgroundScope.launchFinishing(TAG, finish = { finishes++ }) { throw IllegalStateException("disk broke") }
            runCurrent()

            assertEquals(1, finishes)
        }

    @Test
    fun `finish lands once the work outlasts the budget`() =
        runTest {
            backgroundScope.launchFinishing(TAG, finish = { finishes++ }) { awaitCancellation() }
            advanceTimeBy(RECEIVER_WORK_BUDGET_MS - 1)
            runCurrent()
            assertEquals(0, finishes)

            advanceTimeBy(1)
            runCurrent()

            assertEquals(1, finishes)
        }

    private companion object {
        const val TAG = "ReceiverWorkTest"
    }
}

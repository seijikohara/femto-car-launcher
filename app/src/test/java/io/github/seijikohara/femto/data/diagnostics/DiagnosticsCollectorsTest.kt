package io.github.seijikohara.femto.data.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DiagnosticsCollectorsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `registry covers every section exactly once in SectionId order`() {
        assertEquals(SectionId.entries.toList(), diagnosticsCollectors(context).map { it.id })
    }

    @Test
    fun `logs collection completes only after the music collector releases the spectrum gate`() =
        runTest {
            val collectors = diagnosticsCollectors(context).associateBy { it.id }
            // A real dispatcher keeps the LOGS gate timeout on the wall clock:
            // under the auto-advancing virtual clock it would fire instantly
            // while MUSIC still runs, faking a release that never happened.
            withContext(Dispatchers.Default) {
                val logs = async { collectors.getValue(SectionId.LOGS).collect() }
                delay(100)
                assertFalse(logs.isCompleted) // parked on the spectrum gate
                collectors.getValue(SectionId.MUSIC).collect() // releases it
                // Bound well under production's 5s SPECTRUM_AWAIT_TIMEOUT_MS: once the
                // gate is released, LOGS completes near-instantly, so 1s is ample. If
                // the release above ever broke, only that 5s fallback would complete
                // LOGS — this ceiling turns that silent pass into a loud timeout.
                withTimeout(1_000) { logs.await() }
            }
        }

    @Test
    fun `a freshly built registry re-arms the spectrum gate after a prior registry's gate completed`() =
        runTest {
            // Run a first registry's MUSIC+LOGS pair to completion, exactly like
            // a first Refresh would: its spectrum gate ends up completed.
            val first = diagnosticsCollectors(context).associateBy { it.id }
            withContext(Dispatchers.Default) {
                first.getValue(SectionId.MUSIC).collect()
                first.getValue(SectionId.LOGS).collect()
            }

            // A second, independently built registry — what `collectorsProvider()`
            // returns on the next Refresh — must own its own unstarted gate,
            // not inherit the first registry's already-completed one.
            val second = diagnosticsCollectors(context).associateBy { it.id }
            withContext(Dispatchers.Default) {
                val logs = async { second.getValue(SectionId.LOGS).collect() }
                delay(100)
                assertFalse(logs.isCompleted) // parked on the SECOND registry's own gate
                second.getValue(SectionId.MUSIC).collect() // releases it
                // Bounded under the 5s gate-timeout fallback, as in the first test: a
                // failed re-arm would complete LOGS only via that fallback, which this
                // 1s ceiling surfaces as a failure instead of a slow pass.
                withTimeout(1_000) { logs.await() }
            }
        }
}

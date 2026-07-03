package io.github.seijikohara.femto.data.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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
                logs.await()
            }
        }
}

@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.seijikohara.femto.data.calendar

import android.Manifest
import android.app.Application
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.data.system.SystemPermissionSignals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CalendarChangeTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `registers the calendar observer after a read-calendar grant refresh`() =
        runTest {
            // READ_CALENDAR withheld at first collection: registration is skipped so
            // the home screen does not crash on cold start. A grant landing while the
            // launcher stays foreground nudges refreshes; the flow must then re-attempt
            // the ContentObserver registration rather than staying dead until a restart.
            val collectJob =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    calendarChangeFlow(application).collect { }
                }
            advanceUntilIdle()

            assertEquals(0, registeredObserverCount())

            shadowOf(application).grantPermissions(Manifest.permission.READ_CALENDAR)
            SystemPermissionSignals.refreshes.emit(Unit)
            advanceUntilIdle()

            assertTrue(registeredObserverCount() >= 1)

            collectJob.cancel()
        }

    private fun registeredObserverCount(): Int =
        shadowOf(application.contentResolver)
            .getContentObservers(CalendarContract.Events.CONTENT_URI)
            .size
}

package io.github.seijikohara.femto.data

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CalendarRepositoryTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `emits a clock-only snapshot when calendar permission is denied`() =
        runTest {
            // READ_CALENDAR is intentionally not granted: the events list must
            // be empty while the clock-driven day strip still renders fully.
            val repository =
                CalendarRepository(
                    application,
                    clockFlow = flowOf(ClockTick(LocalTime.NOON, LocalDate.of(2026, 6, 3))),
                )

            val snapshot = repository.snapshotFlow().first()

            assertNotNull(snapshot)
            assertTrue(snapshot.events.isEmpty())
            assertEquals(6, snapshot.dayStrip.size)
        }

    @Test
    fun `keeps emitting when the calendar provider throws`() =
        runTest {
            // Grant the permission so the repository attempts the provider
            // query, then make the registered provider throw to simulate a
            // mid-stream revoke / OEM provider fault. The snapshot must still
            // resolve with an empty events list rather than tearing down the
            // dashboard StateFlow.
            shadowOf(application).grantPermissions(Manifest.permission.READ_CALENDAR)
            Robolectric
                .buildContentProvider(ThrowingCalendarProvider::class.java)
                .create(CalendarContract.AUTHORITY)

            val repository =
                CalendarRepository(
                    application,
                    clockFlow = flowOf(ClockTick(LocalTime.NOON, LocalDate.of(2026, 6, 3))),
                )

            val snapshot = repository.snapshotFlow().first()

            assertNotNull(snapshot)
            assertTrue(snapshot.events.isEmpty())
            assertEquals(6, snapshot.dayStrip.size)
        }

    /**
     * Stand-in calendar provider whose [query] throws [SecurityException], the
     * fault the repository's `runCatching` guards must absorb.
     */
    class ThrowingCalendarProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor = throw SecurityException("calendar provider unavailable")

        override fun getType(uri: Uri): String? = null

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri? = null

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }
}

package io.github.seijikohara.femto.data

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import android.text.format.DateFormat
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
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
            // The snapshot must mark access denied in-band so the card can
            // render the denial message rather than a hollow strip.
            assertFalse(snapshot.hasCalendarAccess)
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

    @Test
    fun `places an all-day event on the correct local day in a negative-offset zone`() =
        runTest {
            // An all-day Instances row stores BEGIN as UTC midnight of the event
            // day. Read with the system zone, an event at 2099-06-15T00:00Z
            // lands a day early (2099-06-14) west of UTC. Reading all-day rows
            // in UTC keeps the dot on the real local day.
            shadowOf(application).grantPermissions(Manifest.permission.READ_CALENDAR)
            Robolectric
                .buildContentProvider(AllDayCalendarProvider::class.java)
                .create(CalendarContract.AUTHORITY)

            val newYork = ZoneId.of("America/New_York")
            val repository =
                CalendarRepository(
                    application,
                    clockFlow = flowOf(ClockTick(LocalTime.NOON, AllDayCalendarProvider.TODAY)),
                    zone = newYork,
                )

            val snapshot = repository.snapshotFlow().first()
            assertNotNull(snapshot)
            // The permission is granted here, so the snapshot must report
            // access true (the mirror of the denied-path assertion above).
            assertTrue(snapshot.hasCalendarAccess)

            // The event day is one day after today, so a correct read dots the
            // second strip cell and leaves today (cell 0) undotted; a system-zone
            // read would shift the dot to today.
            val eventDay = AllDayCalendarProvider.EVENT_DATE
            val dottedDays = snapshot.dayStrip.filter { it.hasEvent }.map { it.date }
            assertEquals(listOf(eventDay), dottedDays)
            assertTrue(snapshot.dayStrip.first { it.date == eventDay }.hasEvent)
            assertTrue(
                snapshot.dayStrip
                    .first { it.date == AllDayCalendarProvider.TODAY }
                    .hasEvent
                    .not(),
            )

            // The all-day event carries no clock time.
            assertEquals(1, snapshot.events.size)
            assertNull(snapshot.events.single().time)
        }

    @Test
    fun `month label follows the locale field order`() =
        runTest {
            // The label is produced from getBestDateTimePattern(locale, "yMMMM").
            // en places the month first; ja / ko place the year first. Asserting
            // against the same pattern-derived expectation (rather than a
            // hardcoded English string) proves the formatter is locale-aware
            // without baking a brittle literal per locale.
            val today = LocalDate.of(2026, 3, 30)
            listOf(Locale.ENGLISH, Locale.JAPANESE, Locale.KOREAN).forEach { locale ->
                val repository =
                    CalendarRepository(
                        application,
                        clockFlow = flowOf(ClockTick(LocalTime.NOON, today)),
                        locale = locale,
                    )

                val snapshot = repository.snapshotFlow().first()
                assertNotNull(snapshot)

                val expected =
                    today.format(
                        DateTimeFormatter.ofPattern(
                            DateFormat.getBestDateTimePattern(locale, "yMMMM"),
                            locale,
                        ),
                    )
                assertEquals(expected, snapshot.monthLabel)
            }

            // The English and Japanese labels must differ: ja leads with the
            // year, en leads with the month name. This guards against a
            // regression to the old hand-joined "Month Year" ordering.
            val enLabel =
                CalendarRepository(
                    application,
                    clockFlow = flowOf(ClockTick(LocalTime.NOON, today)),
                    locale = Locale.ENGLISH,
                ).snapshotFlow().first()!!.monthLabel
            val jaLabel =
                CalendarRepository(
                    application,
                    clockFlow = flowOf(ClockTick(LocalTime.NOON, today)),
                    locale = Locale.JAPANESE,
                ).snapshotFlow().first()!!.monthLabel
            assertTrue(enLabel != jaLabel)
        }

    /**
     * Stand-in calendar provider returning a single all-day Instances row whose
     * BEGIN is UTC midnight of [EVENT_DATE]. The cursor mirrors the requested
     * projection so both the events query and the dot-dates query read the same
     * row regardless of column order.
     */
    class AllDayCalendarProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            val columns: Array<out String> =
                projection ?: arrayOf(CalendarContract.Instances.BEGIN)
            val cursor = MatrixCursor(columns)
            cursor.addRow(
                columns.map { column ->
                    when (column) {
                        CalendarContract.Instances.BEGIN -> EVENT_BEGIN_MS
                        CalendarContract.Instances.ALL_DAY -> 1
                        CalendarContract.Instances.TITLE -> "Holiday"
                        else -> null
                    }
                },
            )
            return cursor
        }

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

        companion object {
            // Far-future so the event stays ahead of the real `Instant.now()`
            // floor the events query applies, keeping the test stable over time.
            val EVENT_DATE: LocalDate = LocalDate.of(2099, 6, 15)
            val TODAY: LocalDate = EVENT_DATE.minusDays(1)
            val EVENT_BEGIN_MS: Long =
                EVENT_DATE.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
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

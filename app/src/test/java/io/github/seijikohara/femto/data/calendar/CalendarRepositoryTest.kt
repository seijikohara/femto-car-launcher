package io.github.seijikohara.femto.data.calendar

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.data.clock.ClockTick
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
                    hiddenCalendarIds = flowOf(emptySet()),
                )

            val snapshot = repository.snapshotFlow().first()

            assertNotNull(snapshot)
            // The snapshot must mark access denied in-band so the card can
            // render the denial message rather than a hollow strip.
            assertFalse(snapshot.hasCalendarAccess)
            assertTrue(snapshot.days.all { it.events.isEmpty() })
            assertEquals(30, snapshot.days.size)
        }

    @Test
    fun `keeps emitting when the calendar provider throws`() =
        runTest {
            // Grant the permission so the repository attempts the provider
            // query, then make the registered provider throw to simulate a
            // mid-stream revoke. The snapshot must still resolve with an empty
            // events list rather than tearing down the dashboard StateFlow —
            // and a revoke is the documented permission-degradation path, so
            // it must NOT be flagged as a provider fault.
            shadowOf(application).grantPermissions(Manifest.permission.READ_CALENDAR)
            ThrowingCalendarProvider.failure = SecurityException("calendar permission revoked")
            Robolectric
                .buildContentProvider(ThrowingCalendarProvider::class.java)
                .create(CalendarContract.AUTHORITY)

            val repository =
                CalendarRepository(
                    application,
                    clockFlow = flowOf(ClockTick(LocalTime.NOON, LocalDate.of(2026, 6, 3))),
                    hiddenCalendarIds = flowOf(emptySet()),
                )

            val snapshot = repository.snapshotFlow().first()

            assertNotNull(snapshot)
            assertFalse(snapshot.queryFailed)
            assertTrue(snapshot.days.all { it.events.isEmpty() })
            assertEquals(30, snapshot.days.size)
        }

    @Test
    fun `flags queryFailed when the provider throws an unexpected exception`() =
        runTest {
            // An OEM provider fault (anything other than the SecurityException
            // revoke) must surface in-band: the empty events list is a read
            // failure, not "granted but nothing scheduled", so the snapshot
            // carries queryFailed = true for the card to render the failure hint.
            shadowOf(application).grantPermissions(Manifest.permission.READ_CALENDAR)
            ThrowingCalendarProvider.failure = SQLiteException("disk I/O error")
            Robolectric
                .buildContentProvider(ThrowingCalendarProvider::class.java)
                .create(CalendarContract.AUTHORITY)

            val repository =
                CalendarRepository(
                    application,
                    clockFlow = flowOf(ClockTick(LocalTime.NOON, LocalDate.of(2026, 6, 3))),
                    hiddenCalendarIds = flowOf(emptySet()),
                )

            val snapshot = repository.snapshotFlow().first()

            assertNotNull(snapshot)
            assertTrue(snapshot.queryFailed)
            // Access itself is still granted: queryFailed is the orthogonal
            // provider-fault axis, not a second denial flag.
            assertTrue(snapshot.hasCalendarAccess)
            assertTrue(snapshot.days.all { it.events.isEmpty() })
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
                    hiddenCalendarIds = flowOf(emptySet()),
                    zoneProvider = { newYork },
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
            val dottedDays = snapshot.days.filter { it.hasEvent }.map { it.date }
            assertEquals(listOf(eventDay), dottedDays)
            assertTrue(snapshot.days.first { it.date == eventDay }.hasEvent)
            assertTrue(
                snapshot.days
                    .first { it.date == AllDayCalendarProvider.TODAY }
                    .hasEvent
                    .not(),
            )

            // The all-day event lands on its own day's cell and carries no clock
            // time.
            val eventDayEvents = snapshot.days.first { it.date == eventDay }.events
            assertEquals(1, eventDayEvents.size)
            assertNull(eventDayEvents.single().time)
        }

    @Test
    fun `groups every event onto its own day cell in time order`() =
        runTest {
            // Four events on today plus one two days out. Each lands on its day's
            // cell; today keeps all four in time order (no per-day cap); the day
            // between carries nothing.
            shadowOf(application).grantPermissions(Manifest.permission.READ_CALENDAR)
            Robolectric
                .buildContentProvider(MultiDayCalendarProvider::class.java)
                .create(CalendarContract.AUTHORITY)

            val repository =
                CalendarRepository(
                    application,
                    clockFlow = flowOf(ClockTick(LocalTime.NOON, MultiDayCalendarProvider.TODAY)),
                    hiddenCalendarIds = flowOf(emptySet()),
                    zoneProvider = { ZoneOffset.UTC },
                )

            val snapshot = repository.snapshotFlow().first()
            assertNotNull(snapshot)

            val today = snapshot.days.first { it.date == MultiDayCalendarProvider.TODAY }
            assertEquals(listOf("A", "B", "C", "D"), today.events.map { it.title })

            val later = snapshot.days.first { it.date == MultiDayCalendarProvider.TODAY.plusDays(2) }
            assertEquals(listOf("E"), later.events.map { it.title })

            assertTrue(
                snapshot.days
                    .first { it.date == MultiDayCalendarProvider.TODAY.plusDays(1) }
                    .events
                    .isEmpty(),
            )
        }

    @Test
    fun `maps event end time and location`() =
        runTest {
            shadowOf(application).grantPermissions(Manifest.permission.READ_CALENDAR)
            Robolectric
                .buildContentProvider(MultiDayCalendarProvider::class.java)
                .create(CalendarContract.AUTHORITY)

            val repository =
                CalendarRepository(
                    application,
                    clockFlow = flowOf(ClockTick(LocalTime.NOON, MultiDayCalendarProvider.TODAY)),
                    hiddenCalendarIds = flowOf(emptySet()),
                    zoneProvider = { ZoneOffset.UTC },
                )

            val today = repository
                .snapshotFlow()
                .first()!!
                .days
                .first { it.date == MultiDayCalendarProvider.TODAY }
            val first = today.events.first { it.title == "A" }

            // "A" starts 09:00 UTC; the provider ends it one hour later and puts it in Room 4.
            assertEquals(LocalTime.of(9, 0), first.time)
            assertEquals(LocalTime.of(10, 0), first.endTime)
            assertEquals("Room 4", first.location)

            // "B" has a blank ("") location in the provider → mapped to null;
            // still carries an end time.
            val second = today.events.first { it.title == "B" }
            assertEquals(LocalTime.of(11, 0), second.endTime)
            assertNull(second.location)
        }

    @Test
    fun `scans the events window once for ticks sharing the same date`() =
        runTest {
            shadowOf(application).grantPermissions(Manifest.permission.READ_CALENDAR)
            CountingCalendarProvider.queryCount = 0
            Robolectric
                .buildContentProvider(CountingCalendarProvider::class.java)
                .create(CalendarContract.AUTHORITY)

            val date = LocalDate.of(2026, 6, 3)
            val repository =
                CalendarRepository(
                    application,
                    // Three ticks a minute apart on the same calendar date — the
                    // production cadence between midnights.
                    clockFlow =
                        flowOf(
                            ClockTick(LocalTime.of(12, 0), date),
                            ClockTick(LocalTime.of(12, 1), date),
                            ClockTick(LocalTime.of(12, 2), date),
                        ),
                    hiddenCalendarIds = flowOf(emptySet()),
                )

            val snapshot = repository.snapshotFlow().first()

            assertNotNull(snapshot)
            // Same-date ticks collapse to one rebuild key, so the 30-day
            // Instances window is scanned once — not once per minute.
            assertEquals(1, CountingCalendarProvider.queryCount)
        }

    /**
     * Calendar IDs hidden in the preferences must not appear in the agenda.
     *
     * The provider fake captures the selection string and also applies the
     * NOT IN filter so the end-to-end title assertion is meaningful.
     */
    @Test
    fun `hides events from hidden calendars`() =
        runTest {
            shadowOf(application).grantPermissions(Manifest.permission.READ_CALENDAR)
            HiddenCalendarProvider.lastSelection = null
            Robolectric
                .buildContentProvider(HiddenCalendarProvider::class.java)
                .create(CalendarContract.AUTHORITY)

            val repo =
                CalendarRepository(
                    application,
                    clockFlow = flowOf(ClockTick(LocalTime.NOON, HiddenCalendarProvider.TODAY)),
                    hiddenCalendarIds = flowOf(setOf(2L)),
                    zoneProvider = { ZoneOffset.UTC },
                )
            val snapshot = repo.snapshotFlow().first()

            assertNotNull(snapshot)
            // The repository must build a NOT IN clause so the resolver can filter.
            val sel = HiddenCalendarProvider.lastSelection
            assertNotNull(sel)
            assertTrue(sel.contains("NOT IN (2)"), "expected NOT IN clause but got: $sel")
            // The provider honours the NOT IN clause; only the Work event (cal 1) survives.
            val titles = snapshot.days.flatMap { it.events }.map { it.title }
            assertEquals(listOf("Work"), titles)
        }

    @Test
    fun `keeps the color-bar gate off when one calendar carries two event colors`() =
        runTest {
            shadowOf(application).grantPermissions(Manifest.permission.READ_CALENDAR)
            GateCalendarProvider.rows =
                listOf(
                    GateCalendarProvider.Row(calId = 1L, color = 0xFF0000),
                    GateCalendarProvider.Row(calId = 1L, color = 0x00FF00),
                )
            Robolectric
                .buildContentProvider(GateCalendarProvider::class.java)
                .create(CalendarContract.AUTHORITY)

            val snapshot =
                CalendarRepository(
                    application,
                    clockFlow = flowOf(ClockTick(LocalTime.NOON, GateCalendarProvider.TODAY)),
                    hiddenCalendarIds = flowOf(emptySet()),
                    zoneProvider = { ZoneOffset.UTC },
                ).snapshotFlow().first()

            assertNotNull(snapshot)
            // Two display colors from ONE calendar must not sprout indicators:
            // the gate counts calendars, not colors.
            assertFalse(snapshot.multipleCalendarsVisible)
        }

    @Test
    fun `raises the color-bar gate when the window spans two calendars`() =
        runTest {
            shadowOf(application).grantPermissions(Manifest.permission.READ_CALENDAR)
            GateCalendarProvider.rows =
                listOf(
                    GateCalendarProvider.Row(calId = 1L, color = 0xFF0000),
                    GateCalendarProvider.Row(calId = 2L, color = 0xFF0000),
                )
            Robolectric
                .buildContentProvider(GateCalendarProvider::class.java)
                .create(CalendarContract.AUTHORITY)

            val snapshot =
                CalendarRepository(
                    application,
                    clockFlow = flowOf(ClockTick(LocalTime.NOON, GateCalendarProvider.TODAY)),
                    hiddenCalendarIds = flowOf(emptySet()),
                    zoneProvider = { ZoneOffset.UTC },
                ).snapshotFlow().first()

            assertNotNull(snapshot)
            // Both rows share one display color: only the distinct CALENDAR_IDs
            // may raise the gate.
            assertTrue(snapshot.multipleCalendarsVisible)
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
                        hiddenCalendarIds = flowOf(emptySet()),
                        localeProvider = { locale },
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
                    hiddenCalendarIds = flowOf(emptySet()),
                    localeProvider = { Locale.ENGLISH },
                ).snapshotFlow().first()!!.monthLabel
            val jaLabel =
                CalendarRepository(
                    application,
                    clockFlow = flowOf(ClockTick(LocalTime.NOON, today)),
                    hiddenCalendarIds = flowOf(emptySet()),
                    localeProvider = { Locale.JAPANESE },
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
     * Stand-in calendar provider returning four timed rows on [TODAY] and one on
     * `TODAY + 2`, all titled, in `BEGIN ASC` order. Exercises per-day grouping and
     * time-ordering (all four of today's rows are kept — there is no per-day cap).
     */
    class MultiDayCalendarProvider : ContentProvider() {
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
            ROWS.forEach { (beginMs, title) ->
                cursor.addRow(
                    columns.map { column ->
                        when (column) {
                            CalendarContract.Instances.BEGIN -> {
                                beginMs
                            }

                            CalendarContract.Instances.ALL_DAY -> {
                                0
                            }

                            CalendarContract.Instances.TITLE -> {
                                title
                            }

                            CalendarContract.Instances.END -> {
                                beginMs + 60 * 60 * 1000L
                            }

                            CalendarContract.Instances.EVENT_LOCATION -> {
                                when (title) {
                                    "A" -> "Room 4"
                                    "B" -> ""
                                    else -> null
                                }
                            }

                            else -> {
                                null
                            }
                        }
                    },
                )
            }
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
            val TODAY: LocalDate = LocalDate.of(2099, 7, 1)

            private fun at(
                date: LocalDate,
                hour: Int,
            ): Long =
                date
                    .atTime(hour, 0)
                    .atZone(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()

            val ROWS: List<Pair<Long, String>> =
                listOf(
                    at(TODAY, 9) to "A",
                    at(TODAY, 10) to "B",
                    at(TODAY, 11) to "C",
                    at(TODAY, 12) to "D",
                    at(TODAY.plusDays(2), 14) to "E",
                )
        }
    }

    /**
     * Stand-in calendar provider that counts [query] invocations and returns an
     * empty cursor. [queryCount] is reset explicitly by each test because
     * Robolectric can share the companion across test methods.
     */
    class CountingCalendarProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            queryCount += 1
            return MatrixCursor(projection ?: arrayOf(CalendarContract.Instances.BEGIN))
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
            var queryCount: Int = 0
        }
    }

    /**
     * Stand-in calendar provider with two rows — calendar 1 ("Work") and calendar 2
     * ("Private"). It captures the [selection] argument passed by the repository and
     * also applies the NOT IN filter so the end-to-end title assertion is meaningful.
     * [lastSelection] is reset by the test before registering the provider because
     * Robolectric can share the companion across test methods.
     */
    class HiddenCalendarProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            lastSelection = selection
            val excludedIds = parseNotInIds(selection)
            val columns: Array<out String> =
                projection ?: arrayOf(CalendarContract.Instances.BEGIN)
            val cursor = MatrixCursor(columns)
            ROWS
                .filter { row -> row.calId !in excludedIds }
                .forEach { row ->
                    cursor.addRow(
                        columns.map { column ->
                            when (column) {
                                CalendarContract.Instances.BEGIN -> row.beginMs
                                CalendarContract.Instances.ALL_DAY -> 0
                                CalendarContract.Instances.TITLE -> row.title
                                else -> null
                            }
                        },
                    )
                }
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

        data class Row(
            val calId: Long,
            val title: String,
            val beginMs: Long,
        )

        companion object {
            var lastSelection: String? = null
            val TODAY: LocalDate = LocalDate.of(2099, 8, 1)

            private fun at(hour: Int): Long =
                TODAY
                    .atTime(hour, 0)
                    .atZone(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()

            val ROWS: List<Row> =
                listOf(
                    Row(1L, "Work", at(9)),
                    Row(2L, "Private", at(10)),
                )

            // Extracts the IDs from a "NOT IN (1,2,3)" clause in the selection string.
            // IDs are Longs without spaces, matching the joinToString(",") format the
            // repository produces.
            fun parseNotInIds(selection: String?): Set<Long> =
                Regex("""NOT IN \(([0-9,]+)\)""")
                    .find(selection.orEmpty())
                    ?.groupValues
                    ?.get(1)
                    ?.split(",")
                    ?.mapNotNull { it.toLongOrNull() }
                    ?.toSet()
                    .orEmpty()
        }
    }

    /**
     * Stand-in calendar provider for the multipleCalendarsVisible gate: serves
     * whatever [rows] the test stages, answering the DISPLAY_COLOR and
     * CALENDAR_ID columns the gate reads. [rows] is set by each test before
     * registering the provider because Robolectric can share the companion
     * across test methods.
     */
    class GateCalendarProvider : ContentProvider() {
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
            rows.forEachIndexed { index, row ->
                cursor.addRow(
                    columns.map { column ->
                        when (column) {
                            CalendarContract.Instances.BEGIN -> at(9 + index)
                            CalendarContract.Instances.ALL_DAY -> 0
                            CalendarContract.Instances.TITLE -> "Event $index"
                            CalendarContract.Instances.DISPLAY_COLOR -> row.color
                            CalendarContract.Instances.CALENDAR_ID -> row.calId
                            else -> null
                        }
                    },
                )
            }
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

        data class Row(
            val calId: Long,
            val color: Int,
        )

        companion object {
            var rows: List<Row> = emptyList()
            val TODAY: LocalDate = LocalDate.of(2099, 9, 1)

            private fun at(hour: Int): Long =
                TODAY
                    .atTime(hour, 0)
                    .atZone(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
        }
    }

    /**
     * Stand-in calendar provider whose [query] throws [failure], the fault the
     * repository's `runCatching` guards must absorb. [failure] is set explicitly
     * by each test (rather than relying on a default) because Robolectric can
     * share the companion across test methods.
     */
    class ThrowingCalendarProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor = throw failure

        companion object {
            // SecurityException exercises the permission-degradation path;
            // any other exception exercises the queryFailed provider-fault path.
            var failure: RuntimeException = SecurityException("calendar provider unavailable")
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
    }
}

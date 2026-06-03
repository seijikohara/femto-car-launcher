@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package io.github.seijikohara.femto.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Calendar surface for the dashboard.
 *
 * Combines two upstream signals — the wall clock (so today / weekday /
 * month follow real time without an extra timer) and the events provider
 * (so the strip dots and event list refresh when the user edits a calendar
 * event). The events query window is `today + 5 days` to match the 6-cell
 * strip on the calendar card.
 *
 * When `READ_CALENDAR` is denied the snapshot still emits from the clock
 * alone, but with `hasCalendarAccess = false` so the card renders the denial
 * message rather than a hollow strip. The calling UI treats a null snapshot
 * as "loading"; a non-null snapshot with `hasCalendarAccess = true` and an
 * empty events list means "granted but nothing scheduled".
 */
internal class CalendarRepository(
    private val context: Context,
    private val clockFlow: Flow<ClockTick>,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault(),
) {
    fun snapshotFlow(): Flow<CalendarSnapshot?> =
        combine(
            clockFlow,
            calendarChangeFlow().onStart { emit(Unit) },
        ) { tick, _ ->
            buildSnapshot(tick.date)
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

    private fun buildSnapshot(today: LocalDate): CalendarSnapshot {
        val strip = (0 until DAY_STRIP_LENGTH).map { offset ->
            val date = today.plusDays(offset.toLong())
            DayCell(
                date = date,
                weekdayLetter = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                hasEvent = false,
            )
        }
        val granted = hasPermission()
        val events = if (granted) readEvents(today) else emptyList()
        // readEventDates runs its own full-window query (earlier-today events
        // dot days the future-only readEvents window misses), so it must run
        // whenever the permission is granted — not only when readEvents found
        // future events.
        val datesWithEvents = if (granted) readEventDates(today) else emptySet()
        val stripWithDots = strip.map { it.copy(hasEvent = it.date in datesWithEvents) }
        return CalendarSnapshot(
            today = today,
            weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
            monthLabel = monthLabelOf(today),
            dayStrip = stripWithDots,
            events = events.take(EVENT_LIST_LIMIT),
            hasCalendarAccess = granted,
        )
    }

    /**
     * Format the "month year" head label using the locale's preferred field
     * order. `getBestDateTimePattern` resolves the skeleton "yMMMM" to e.g.
     * "MMMM y" for en (March 2026) but a year-first pattern for ja / ko
     * (2026年3月). A hand-joined "Month Year" string would force English
     * ordering on every locale.
     */
    private fun monthLabelOf(today: LocalDate): String =
        today.format(
            DateTimeFormatter.ofPattern(
                DateFormat.getBestDateTimePattern(locale, "yMMMM"),
                locale,
            ),
        )

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Map each event to its start date so the day-strip can render a dot.
     * Kept separate from [readEvents] so the events list itself only carries
     * the time-of-day for display.
     */
    @SuppressLint("MissingPermission") // Caller checks READ_CALENDAR before subscribing.
    private fun readEventDates(today: LocalDate): Set<LocalDate> {
        val begin = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today
            .plusDays(DAY_STRIP_LENGTH.toLong())
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI
            .buildUpon()
            .let { ContentUris.appendId(it, begin) }
            .let { ContentUris.appendId(it, end) }
            .build()
        // A mid-stream permission revoke (SecurityException) or an OEM provider
        // fault (SQLiteException) must not tear down the dashboard StateFlow.
        return runCatching {
            val dates = mutableSetOf<LocalDate>()
            context.contentResolver
                .query(
                    uri,
                    arrayOf(
                        CalendarContract.Instances.BEGIN,
                        CalendarContract.Instances.ALL_DAY,
                    ),
                    null,
                    null,
                    "${CalendarContract.Instances.BEGIN} ASC",
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val startMs = cursor.getLong(0)
                        val allDay = cursor.getInt(1) != 0
                        dates += localDateOf(startMs, allDay)
                    }
                }
            dates.toSet()
        }.getOrDefault(emptySet())
    }

    @SuppressLint("MissingPermission") // Caller checks READ_CALENDAR before subscribing.
    private fun readEvents(today: LocalDate): List<EventItem> {
        val begin = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today
            .plusDays(DAY_STRIP_LENGTH.toLong())
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI
            .buildUpon()
            .let { ContentUris.appendId(it, begin) }
            .let { ContentUris.appendId(it, end) }
            .build()
        val now = Instant
            .now()
            .toEpochMilli()
        // A mid-stream permission revoke (SecurityException) or an OEM provider
        // fault (SQLiteException) must not tear down the dashboard StateFlow.
        return runCatching {
            val items = mutableListOf<EventItem>()
            context.contentResolver
                .query(
                    uri,
                    arrayOf(
                        CalendarContract.Instances.BEGIN,
                        CalendarContract.Instances.TITLE,
                        CalendarContract.Instances.ALL_DAY,
                    ),
                    "${CalendarContract.Instances.BEGIN} >= ?",
                    arrayOf(now.toString()),
                    "${CalendarContract.Instances.BEGIN} ASC",
                )?.use { cursor ->
                    while (cursor.moveToNext() && items.size < EVENT_LIST_LIMIT) {
                        val startMs = cursor.getLong(0)
                        val title = cursor.getString(1) ?: continue
                        val allDay = cursor.getInt(2) != 0
                        items += EventItem(
                            // All-day rows carry no clock time; surface null so
                            // the card renders an "all day" label instead of a
                            // spurious 00:00 derived from the system zone.
                            time = if (allDay) null else LocalTime.ofInstant(Instant.ofEpochMilli(startMs), zone),
                            title = title,
                        )
                    }
                }
            items.toList()
        }.getOrDefault(emptyList())
    }

    /**
     * Resolve an Instances.BEGIN epoch-millis to the local calendar day.
     *
     * All-day instances store BEGIN as UTC midnight of the event day, so they
     * must be read with [ZoneOffset.UTC]; applying the system zone west of UTC
     * shifts the day one earlier. Timed instances are absolute instants and
     * resolve in the system [zone].
     */
    private fun localDateOf(
        startMs: Long,
        allDay: Boolean,
    ): LocalDate =
        Instant
            .ofEpochMilli(startMs)
            .atZone(if (allDay) ZoneOffset.UTC else zone)
            .toLocalDate()

    /**
     * Re-emit whenever the calendar provider notifies a change. Debounced
     * because edit / delete operations on a single event can fire several
     * notifications in quick succession.
     */
    private fun calendarChangeFlow(): Flow<Unit> =
        callbackFlow {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    trySend(Unit)
                }
            }
            context.contentResolver.registerContentObserver(
                CalendarContract.Events.CONTENT_URI,
                // notifyForDescendants =
                true,
                observer,
            )
            awaitClose { context.contentResolver.unregisterContentObserver(observer) }
        }.debounce(CHANGE_DEBOUNCE_MS)

    private companion object {
        const val DAY_STRIP_LENGTH = 6
        const val EVENT_LIST_LIMIT = 2
        const val CHANGE_DEBOUNCE_MS = 500L
    }
}

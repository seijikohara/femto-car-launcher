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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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
 * When `READ_CALENDAR` is denied the events list is empty; the day strip
 * and head still render from the clock alone. The calling UI treats a
 * null snapshot as "loading"; an empty events list with non-null snapshot
 * means "granted but nothing scheduled".
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
        val events = if (hasPermission()) readEvents(today) else emptyList()
        val datesWithEvents = readEventDates(today, events)
        val stripWithDots = strip.map { it.copy(hasEvent = it.date in datesWithEvents) }
        return CalendarSnapshot(
            today = today,
            weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
            monthLabel = "${today.month.getDisplayName(TextStyle.FULL, locale)} ${today.year}",
            dayStrip = stripWithDots,
            events = events.take(EVENT_LIST_LIMIT),
        )
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Map each event to its start date so the day-strip can render a dot.
     * Kept separate from [readEvents] so the events list itself only carries
     * the time-of-day for display.
     */
    @SuppressLint("MissingPermission") // Caller checks READ_CALENDAR before subscribing.
    private fun readEventDates(today: LocalDate, events: List<EventItem>): Set<LocalDate> {
        if (events.isEmpty()) return emptySet()
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
        val dates = mutableSetOf<LocalDate>()
        context.contentResolver
            .query(
                uri,
                arrayOf(CalendarContract.Instances.BEGIN),
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val startMs = cursor.getLong(0)
                    dates += java.time.Instant
                        .ofEpochMilli(startMs)
                        .atZone(zone)
                        .toLocalDate()
                }
            }
        return dates
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
        val now = java.time.Instant
            .now()
            .toEpochMilli()
        val items = mutableListOf<EventItem>()
        context.contentResolver
            .query(
                uri,
                arrayOf(
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.TITLE,
                ),
                "${CalendarContract.Instances.BEGIN} >= ?",
                arrayOf(now.toString()),
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext() && items.size < EVENT_LIST_LIMIT) {
                    val startMs = cursor.getLong(0)
                    val title = cursor.getString(1) ?: continue
                    items += EventItem(
                        time = LocalTime.ofInstant(java.time.Instant.ofEpochMilli(startMs), zone),
                        title = title,
                    )
                }
            }
        return items
    }

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

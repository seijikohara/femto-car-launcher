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
import android.util.Log
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

private const val TAG = "CalendarRepository"

/**
 * Calendar surface for the dashboard.
 *
 * Combines two upstream signals — the wall clock (so today / weekday /
 * month follow real time without an extra timer) and the events provider
 * (so the per-day events refresh when the user edits a calendar event). The
 * events query window is `today + WINDOW_DAYS` so the card can render a vertical
 * scrollable list of the coming days; each day's full event list is attached to
 * its day cell (days with no events are present too, with an empty list).
 *
 * When `READ_CALENDAR` is denied the snapshot still emits from the clock
 * alone, but with `hasCalendarAccess = false` so the card renders the denial
 * message rather than a hollow strip. The calling UI treats a null snapshot
 * as "loading"; a non-null snapshot with `hasCalendarAccess = true` and an
 * empty events list means "granted but nothing scheduled". An unexpected
 * provider fault (anything other than the mid-stream `SecurityException`
 * revoke) sets `queryFailed = true` so an empty list never fakes that
 * "nothing scheduled" contract.
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
        val granted = hasPermission()
        // null marks a provider fault (see readWindow); the days still build
        // from the clock alone so the strip never disappears.
        val eventsByDay = if (granted) readWindow(today) else emptyMap()
        val days = (0 until WINDOW_DAYS).map { offset ->
            val date = today.plusDays(offset.toLong())
            DayCell(
                date = date,
                weekdayLetter = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                events = eventsByDay?.get(date).orEmpty(),
            )
        }
        return CalendarSnapshot(
            today = today,
            weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
            monthLabel = monthLabelOf(today),
            days = days,
            hasCalendarAccess = granted,
            queryFailed = eventsByDay == null,
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
     * Build the `Instances` content URI for the `today .. today + WINDOW_DAYS`
     * window. `Instances` requires the begin / end millis embedded as path ids
     * rather than passed as a selection.
     */
    private fun windowUri(today: LocalDate) =
        CalendarContract.Instances.CONTENT_URI
            .buildUpon()
            .let {
                ContentUris.appendId(it, today.atStartOfDay(zone).toInstant().toEpochMilli())
            }.let {
                ContentUris.appendId(
                    it,
                    today
                        .plusDays(WINDOW_DAYS.toLong())
                        .atStartOfDay(zone)
                        .toInstant()
                        .toEpochMilli(),
                )
            }.build()

    /**
     * Scan the window once and group every event by its local day. The card lists
     * each day's full set of events (no per-day cap — the card scrolls), so the
     * whole day is held with no `BEGIN >= now` future-only filter. Rows arrive
     * `BEGIN ASC`, so each day's list stays time-ordered.
     *
     * A mid-stream permission revoke (SecurityException) or an OEM provider
     * fault (SQLiteException) must not tear down the dashboard StateFlow, so the
     * whole scan is guarded. The two faults degrade differently: a revoke is the
     * documented permission path and yields an empty map, while any other fault
     * returns null so [buildSnapshot] can flag `queryFailed` instead of faking
     * "granted but nothing scheduled".
     */
    @SuppressLint("MissingPermission") // Caller checks READ_CALENDAR before subscribing.
    private fun readWindow(today: LocalDate): Map<LocalDate, List<EventItem>>? =
        runCatching {
            val byDay = linkedMapOf<LocalDate, MutableList<EventItem>>()
            context.contentResolver
                .query(
                    windowUri(today),
                    arrayOf(
                        CalendarContract.Instances.BEGIN,
                        CalendarContract.Instances.TITLE,
                        CalendarContract.Instances.ALL_DAY,
                    ),
                    null,
                    null,
                    "${CalendarContract.Instances.BEGIN} ASC",
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val startMs = cursor.getLong(0)
                        // Skip null-title rows entirely: a row that cannot be shown
                        // must not contribute to its day's list either.
                        val title = cursor.getString(1) ?: continue
                        val allDay = cursor.getInt(2) != 0
                        byDay.getOrPut(localDateOf(startMs, allDay)) { mutableListOf() } +=
                            EventItem(
                                // All-day rows carry no clock time; surface null
                                // so the card renders an "all day" label instead
                                // of a spurious 00:00 derived from the system zone.
                                time = if (allDay) null else LocalTime.ofInstant(Instant.ofEpochMilli(startMs), zone),
                                title = title,
                            )
                    }
                }
            byDay.mapValues { (_, events) -> events.toList() }
        }.onFailure {
            when (it) {
                // Mid-stream revoke is the expected degradation path (see KDoc);
                // anything else is a real provider fault and must be visible.
                is SecurityException -> Log.d(TAG, "calendar window query denied", it)

                else -> Log.e(TAG, "calendar window query failed", it)
            }
        }.getOrElse { if (it is SecurityException) emptyMap() else null }

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
     *
     * Registering an observer on the calendar provider requires `READ_CALENDAR`;
     * without it `registerContentObserver` throws `SecurityException`. On a
     * launcher that would crash the home screen on every cold start until the
     * user grants the calendar, so a denied (or racing-revoked) grant skips
     * registration rather than throwing. The card already renders the denial
     * fallback from the clock alone (see [snapshotFlow]), and a grant that
     * arrives later is picked up on the next clock tick, which re-runs
     * [buildSnapshot] and its permission check. This mirrors [readWindow], whose
     * `runCatching` already guards the query side against the same fault.
     */
    private fun calendarChangeFlow(): Flow<Unit> =
        callbackFlow {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    trySend(Unit)
                }
            }
            val registered =
                hasPermission() &&
                    runCatching {
                        context.contentResolver.registerContentObserver(
                            CalendarContract.Events.CONTENT_URI,
                            // notifyForDescendants =
                            true,
                            observer,
                        )
                    }.isSuccess
            awaitClose {
                if (registered) context.contentResolver.unregisterContentObserver(observer)
            }
        }.debounce(CHANGE_DEBOUNCE_MS)

    private companion object {
        // Vertical day-list horizon (today .. today + WINDOW_DAYS).
        const val WINDOW_DAYS = 30
        const val CHANGE_DEBOUNCE_MS = 500L
    }
}

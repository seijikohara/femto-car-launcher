package io.github.seijikohara.femto.data.calendar

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.seijikohara.femto.data.clock.ClockTick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
    private val hiddenCalendarIds: Flow<Set<Long>>,
    // Read per rebuild rather than captured at construction: the repository
    // outlives timezone and locale changes (a phone mounted as car nav crosses
    // borders), and captured values would pin the agenda to the old zone /
    // labels until the process dies.
    private val zoneProvider: () -> ZoneId = ZoneId::systemDefault,
    private val localeProvider: () -> Locale = Locale::getDefault,
) {
    fun snapshotFlow(): Flow<CalendarSnapshot?> =
        combine(
            // The snapshot depends on the calendar date, the permission state,
            // and the zone — not on the tick's minute. Keying the clock side on
            // that triple drops the old once-a-minute full 30-day Instances
            // scan (recurrence expansion included) while keeping the ≤1-minute
            // pickup of a runtime grant or a timezone change that the per-tick
            // rebuild provided.
            clockFlow
                .map { Triple(it.date, hasPermission(), zoneProvider()) }
                .distinctUntilChanged(),
            calendarChangeFlow(context).onStart { emit(Unit) },
            hiddenCalendarIds,
        ) { (date, granted, zone), _, hidden ->
            // The build consumes the key's own values (not fresh provider
            // reads) so the snapshot always matches the key that produced it.
            buildSnapshot(date, granted, zone, hidden)
        }.distinctUntilChanged().flowOn(Dispatchers.IO)

    private fun buildSnapshot(
        today: LocalDate,
        granted: Boolean,
        zone: ZoneId,
        hidden: Set<Long>,
    ): CalendarSnapshot {
        val locale = localeProvider()
        // null marks a provider fault (see readWindow); the days still build
        // from the clock alone so the strip never disappears.
        val eventsByDay = if (granted) readWindow(today, zone, hidden) else emptyMap()
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
            monthLabel = monthLabelOf(today, locale),
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
    private fun monthLabelOf(
        today: LocalDate,
        locale: Locale,
    ): String =
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
    private fun windowUri(
        today: LocalDate,
        zone: ZoneId,
    ) = CalendarContract.Instances.CONTENT_URI
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
    private fun readWindow(
        today: LocalDate,
        zone: ZoneId,
        hidden: Set<Long>,
    ): Map<LocalDate, List<EventItem>>? =
        runCatching {
            val byDay = linkedMapOf<LocalDate, MutableList<EventItem>>()
            // Respect the user's per-calendar visibility: events from a calendar
            // hidden in the calendar app must not surface on the dashboard.
            // Instances joins Calendars, so VISIBLE filters here. Additionally
            // exclude any calendar the user has hidden via dashboard preferences.
            // IDs are Longs (no injection risk); inline them as a NOT IN list.
            val selection =
                buildString {
                    append("${CalendarContract.Calendars.VISIBLE} = 1")
                    if (hidden.isNotEmpty()) {
                        append(" AND ${CalendarContract.Instances.CALENDAR_ID} NOT IN (")
                        append(hidden.joinToString(","))
                        append(")")
                    }
                }
            context.contentResolver
                .query(
                    windowUri(today, zone),
                    arrayOf(
                        CalendarContract.Instances.BEGIN,
                        CalendarContract.Instances.TITLE,
                        CalendarContract.Instances.ALL_DAY,
                    ),
                    selection,
                    null,
                    "${CalendarContract.Instances.BEGIN} ASC",
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val startMs = cursor.getLong(0)
                        // Skip null-title rows entirely: a row that cannot be shown
                        // must not contribute to its day's list either.
                        val title = cursor.getString(1) ?: continue
                        val allDay = cursor.getInt(2) != 0
                        byDay.getOrPut(localDateOf(startMs, allDay, zone)) { mutableListOf() } +=
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
        zone: ZoneId,
    ): LocalDate =
        Instant
            .ofEpochMilli(startMs)
            .atZone(if (allDay) ZoneOffset.UTC else zone)
            .toLocalDate()

    private companion object {
        // Vertical day-list horizon (today .. today + WINDOW_DAYS).
        const val WINDOW_DAYS = 30
    }
}

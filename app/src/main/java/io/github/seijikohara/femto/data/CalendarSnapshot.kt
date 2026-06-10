package io.github.seijikohara.femto.data

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.LocalTime

/**
 * Calendar surface for the dashboard.
 *
 * A hero "today" anchor plus a forward-rolling vertical list of [days] (each with
 * that day's full event list, days with no events included), which the card renders
 * as a scrollable column starting at [today].
 *
 * `null` denotes "not loaded yet" only. Permission denial is carried
 * in-band by [hasCalendarAccess]: a non-null snapshot with
 * [hasCalendarAccess] == false tells the card to render the denial message
 * instead of an empty list plus a misleading "no upcoming events".
 * [queryFailed] carries the third state — access granted but the provider
 * query faulted — so an OEM provider error is never presented as a free week.
 */
@Immutable
data class CalendarSnapshot(
    val today: LocalDate,
    val weekday: String,
    val monthLabel: String,
    val days: List<DayCell>,
    // false means READ_CALENDAR is denied, so the list carries no real data
    // and the card shows the denial message instead.
    val hasCalendarAccess: Boolean,
    // true means the provider query failed unexpectedly (e.g. SQLiteException),
    // so the empty list is a fault, not "nothing scheduled"; the card shows the
    // failure message instead of a hollow agenda.
    val queryFailed: Boolean = false,
)

@Immutable
data class DayCell(
    val date: LocalDate,
    val weekdayLetter: String,
    // The day's events, ordered by start time (the full set — the card scrolls).
    val events: List<EventItem>,
) {
    val hasEvent: Boolean get() = events.isNotEmpty()
}

@Immutable
data class EventItem(
    // null marks an all-day event: it has no clock time, so the card renders
    // an "all day" label instead of a formatted time.
    val time: LocalTime?,
    val title: String,
)

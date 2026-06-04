package io.github.seijikohara.femto.data

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.LocalTime

/**
 * Calendar surface for the dashboard.
 *
 * The shape mirrors the visual hierarchy of the calendar card: a hero
 * "today" anchor on the left, a 6-day strip rolling forward, and the events
 * of whichever day the user has selected. The strip is always six entries
 * starting at [today] so the card can iterate without size guards, and each
 * cell carries its own day's events so selecting a cell needs no second
 * query.
 *
 * `null` denotes "not loaded yet" only. Permission denial is carried
 * in-band by [hasCalendarAccess]: a non-null snapshot with
 * [hasCalendarAccess] == false tells the card to render the denial message
 * instead of an empty strip plus a misleading "no upcoming events".
 */
@Immutable
data class CalendarSnapshot(
    val today: LocalDate,
    val weekday: String,
    val monthLabel: String,
    val dayStrip: List<DayCell>,
    // false means READ_CALENDAR is denied, so the strip carries no real data
    // and the card shows the denial message instead.
    val hasCalendarAccess: Boolean,
)

@Immutable
data class DayCell(
    val date: LocalDate,
    val weekdayLetter: String,
    // The day's events, ordered by start time and capped per day upstream so a
    // busy day cannot grow the card without bound.
    val events: List<EventItem>,
) {
    // Derive the strip dot from the listed events so "has an event" and the
    // shown events can never disagree.
    val hasEvent: Boolean get() = events.isNotEmpty()
}

@Immutable
data class EventItem(
    // null marks an all-day event: it has no clock time, so the card renders
    // an "all day" label instead of a formatted time.
    val time: LocalTime?,
    val title: String,
)

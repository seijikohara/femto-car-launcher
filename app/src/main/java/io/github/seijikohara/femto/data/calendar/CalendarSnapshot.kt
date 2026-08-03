package io.github.seijikohara.femto.data.calendar

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
internal data class CalendarSnapshot(
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
    // true when the window's events span more than one distinct calendar, so a
    // per-event color bar adds information (with zero or one calendar the bar
    // is redundant — even when a single calendar's events carry per-event color
    // overrides). The card and panel gate the bar on this. Set by the
    // repository; the denied and query-failed paths carry no events, so it
    // stays false.
    val multipleCalendarsVisible: Boolean = false,
) {
    // Days worth showing on the compact dashboard card: those with events,
    // plus today even when free. The card is short on room, so it skips every
    // other free day rather than clip before reaching a real entry. The
    // full-screen maximize panel covers the same [days] window — it must never
    // show less than the card it expanded from — but has room to keep the free
    // days between events for rhythm, so it walks [days] itself (see
    // CalendarPanel.kt) rather than reusing this narrower filter.
    val visibleDays: List<DayCell> get() = days.filter { it.hasEvent || it.date == today }

    // Days the full-screen maximize panel renders: everything up to its last day
    // with an event, but never fewer than [PANEL_MIN_LOOKAHEAD_DAYS].
    //
    // Two rules meet here. Reaching the last event is what makes the panel a
    // superset of the card — a shorter panel-only window used to hide events the
    // card was already showing. The floor is what keeps it a *look-ahead*:
    // trimming alone would collapse a quiet month to a single row, less than the
    // panel has ever shown. Between them only the trailing free days go; the free
    // days between events are the rhythm the panel spends its room on.
    //
    // Sitting beside [visibleDays] on purpose: the two are one fact with two
    // answers, and the invariant that binds them — the panel never shows less
    // than the card it expanded from — is only obvious when both rules are read
    // together.
    val agendaDays: List<DayCell>
        get() = days.take(maxOf(days.indexOfLast { it.hasEvent } + 1, PANEL_MIN_LOOKAHEAD_DAYS))

    internal companion object {
        // Two weeks of agenda even when nothing is scheduled — the window the
        // panel carried before it grew to follow the card's full horizon.
        const val PANEL_MIN_LOOKAHEAD_DAYS = 14
    }
}

@Immutable
internal data class DayCell(
    val date: LocalDate,
    val weekdayLetter: String,
    // The day's events, ordered by start time (the full set — the card scrolls).
    val events: List<EventItem>,
) {
    val hasEvent: Boolean get() = events.isNotEmpty()
}

@Immutable
internal data class EventItem(
    // null marks an all-day event: it has no clock time, so the card renders
    // an "all day" label instead of a formatted time.
    val time: LocalTime?,
    val title: String,
    // Event end (clock time); null for all-day or open-ended events. Shown only
    // in the maximized calendar panel — the compact card shows the start alone.
    val endTime: LocalTime? = null,
    // Event location; null/blank when the calendar carries none. Panel-only.
    val location: String? = null,
    // The event's effective display color: CalendarContract Instances.DISPLAY_COLOR
    // (the per-event color if set, else the calendar color), forced opaque by the
    // repository and held as an ARGB int. Paints the multi-calendar color bar; the
    // 0 default is a placeholder for non-provider constructions (previews, tests),
    // which never show the bar (multipleCalendarsVisible stays false without it).
    val color: Int = 0,
)

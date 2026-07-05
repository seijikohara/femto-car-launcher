package io.github.seijikohara.femto.data.calendar

import io.github.seijikohara.femto.data.clock.ClockTick
import io.github.seijikohara.femto.data.display.BriefingScope
import java.time.LocalDate
import java.time.LocalTime

/**
 * An [event] paired with the [date] of the [DayCell] it came from, so a
 * caller can label non-today events with their day (e.g. "Tomorrow", a
 * weekday name) instead of a bare, ambiguous time.
 */
internal data class UpcomingEvent(
    val event: EventItem,
    val date: LocalDate,
)

/**
 * The next event on or after [now] within [scope]'s horizon, for the
 * driving-face one-line briefing. Unlike the cockpit agenda (which lists a
 * whole day, past events included), a single "next" glance must not surface
 * an already-started event, so today's timed events are filtered by start
 * time. An all-day event (`time == null`) on today counts as upcoming; every
 * later day's events (within the horizon) are unconditionally future.
 * [scope] bounds how far ahead the search looks — a far-off event must never
 * surface while driving. Null-safe on the whole snapshot; null when access is
 * denied or the query failed. Pure — unit-testable without Compose.
 */
internal fun CalendarSnapshot?.nextUpcomingEventOrNull(
    now: ClockTick,
    scope: BriefingScope,
): UpcomingEvent? {
    val lastDay =
        when (scope) {
            BriefingScope.TODAY -> now.date
            BriefingScope.THROUGH_TOMORROW -> now.date.plusDays(1)
            BriefingScope.UPCOMING -> LocalDate.MAX
        }
    return this
        ?.takeIf { it.hasCalendarAccess && !it.queryFailed }
        ?.days
        ?.asSequence()
        ?.filter { it.date in now.date..lastDay }
        ?.flatMap { day ->
            day.events
                .asSequence()
                .filter { day.date > now.date || it.isUpcomingAt(now.time) }
                .map { UpcomingEvent(it, day.date) }
        }?.firstOrNull()
}

private fun EventItem.isUpcomingAt(now: LocalTime): Boolean = time == null || time >= now

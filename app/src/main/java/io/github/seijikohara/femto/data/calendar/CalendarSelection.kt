package io.github.seijikohara.femto.data.calendar

import io.github.seijikohara.femto.data.clock.ClockTick
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
 * TODAY's next-up event for the driving-face event block: it never looks past
 * today (a bare glance while driving must not surface tomorrow's event as if
 * it were happening now), and it prefers a timed event over an all-day one
 * even when the all-day entry sorts earlier in [DayCell.events]. Selection is:
 * the nearest upcoming timed event today (start >= [now]'s time); else today's
 * all-day event, if any, as a fallback; else null. Null-safe on the whole
 * snapshot; null when access is denied or the query failed. Pure —
 * unit-testable without Compose.
 */
internal fun CalendarSnapshot?.todayEventOrNull(now: ClockTick): UpcomingEvent? =
    this
        ?.takeIf { it.hasCalendarAccess && !it.queryFailed }
        ?.days
        ?.firstOrNull { it.date == now.date }
        ?.events
        ?.let { events ->
            events
                .asSequence()
                .filter { it.time != null && it.time >= now.time }
                .minByOrNull { requireNotNull(it.time) }
                ?: events.firstOrNull { it.time == null }
        }?.let { UpcomingEvent(it, now.date) }

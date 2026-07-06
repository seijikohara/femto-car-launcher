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
 * The next event on or after [now] within [scope]'s horizon. Unlike the
 * cockpit agenda (which lists a whole day, past events included), a single
 * "next" glance must not surface an already-started event, so today's timed
 * events are filtered by start time. An all-day event (`time == null`) on
 * today counts as upcoming; every later day's events (within the horizon)
 * are unconditionally future. [scope] bounds how far ahead the search looks.
 * Null-safe on the whole snapshot; null when access is denied or the query
 * failed. Pure — unit-testable without Compose.
 *
 * NOTE: the driving-face event block now calls [todayEventOrNull] instead
 * (a strictly-today selection with no [BriefingScope] horizon), so this
 * function currently has no production caller — [BriefingScope] is vestigial
 * for driving until its Settings wiring is reconsidered. Kept (and still
 * tested) as the scope-aware primitive in case a future surface needs it.
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

/**
 * TODAY's next-up event for the driving-face event block — deliberately
 * narrower than [nextUpcomingEventOrNull]: it never looks past today (a bare
 * glance while driving must not surface tomorrow's event as if it were
 * happening now, so [BriefingScope] does not apply here), and it prefers a
 * timed event over an all-day one even when the all-day entry sorts earlier
 * in [DayCell.events]. Selection is: the nearest upcoming timed event today
 * (start >= [now]'s time); else today's all-day event, if any, as a fallback;
 * else null. Null-safe on the whole snapshot; null when access is denied or
 * the query failed. Pure — unit-testable without Compose.
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

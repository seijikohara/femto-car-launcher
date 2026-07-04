package io.github.seijikohara.femto.data.calendar

import io.github.seijikohara.femto.data.clock.ClockTick
import java.time.LocalTime

/**
 * The next event on or after [now], for the driving-face one-line briefing.
 * Unlike the cockpit agenda (which lists a whole day, past events included), a
 * single "next" glance must not surface an already-started event, so today's
 * timed events are filtered by start time. An all-day event (`time == null`) on
 * today counts as upcoming; every later day's events are unconditionally future.
 * Null-safe on the whole snapshot; null when access is denied or the query
 * failed. Pure — unit-testable without Compose.
 */
internal fun CalendarSnapshot?.nextUpcomingEventOrNull(now: ClockTick): EventItem? =
    this
        ?.takeIf { it.hasCalendarAccess && !it.queryFailed }
        ?.days
        ?.asSequence()
        ?.filter { it.date >= now.date }
        ?.flatMap { day ->
            day.events.asSequence().filter { day.date > now.date || it.isUpcomingAt(now.time) }
        }?.firstOrNull()

private fun EventItem.isUpcomingAt(now: LocalTime): Boolean = time == null || time >= now

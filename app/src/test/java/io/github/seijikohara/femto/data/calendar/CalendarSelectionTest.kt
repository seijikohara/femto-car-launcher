package io.github.seijikohara.femto.data.calendar

import io.github.seijikohara.femto.data.clock.ClockTick
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalendarSelectionTest {
    private val today = LocalDate.of(2026, 5, 1)
    private val now = ClockTick(time = LocalTime.of(12, 0), date = today)

    private fun snapshot(vararg days: DayCell) =
        CalendarSnapshot(today, "Friday", "MAY 2026", days.toList(), hasCalendarAccess = true)

    private fun day(
        date: LocalDate,
        vararg events: EventItem,
    ) = DayCell(date, "F", events.toList())

    @Test fun `todayEventOrNull returns the nearest upcoming timed event today`() {
        val past = EventItem(LocalTime.of(9, 0), "Standup")
        val next = EventItem(LocalTime.of(14, 0), "Pickup")
        val later = EventItem(LocalTime.of(16, 0), "Later")
        assertEquals(
            next,
            snapshot(day(today, past, later, next)).todayEventOrNull(now)?.event,
        )
    }

    @Test fun `todayEventOrNull treats an event starting exactly now as upcoming`() {
        val nowEvent = EventItem(LocalTime.of(12, 0), "Meeting")
        assertEquals(nowEvent, snapshot(day(today, nowEvent)).todayEventOrNull(now)?.event)
    }

    @Test fun `todayEventOrNull falls back to today's all-day event when no timed event remains`() {
        val allDay = EventItem(null, "Holiday")
        val past = EventItem(LocalTime.of(9, 0), "Past")
        assertEquals(allDay, snapshot(day(today, past, allDay)).todayEventOrNull(now)?.event)
    }

    @Test fun `todayEventOrNull prefers a later timed event over an earlier-sorted all-day event`() {
        val allDay = EventItem(null, "Holiday")
        val next = EventItem(LocalTime.of(14, 0), "Pickup")
        // The all-day entry sorts first in the day's event list, but a real
        // upcoming time still wins — this selection never treats "sorts
        // earlier" as "wins".
        assertEquals(next, snapshot(day(today, allDay, next)).todayEventOrNull(now)?.event)
    }

    @Test fun `todayEventOrNull returns null when today has only past events and no all-day fallback`() {
        val past = EventItem(LocalTime.of(9, 0), "Past")
        assertNull(snapshot(day(today, past)).todayEventOrNull(now))
    }

    @Test fun `todayEventOrNull never looks past today`() {
        val tomorrow = today.plusDays(1)
        val tomorrowEvent = EventItem(LocalTime.of(8, 0), "Tomorrow only")
        assertNull(snapshot(day(tomorrow, tomorrowEvent)).todayEventOrNull(now))
    }

    @Test fun `todayEventOrNull returns null when access denied or query failed or null`() {
        assertNull((null as CalendarSnapshot?).todayEventOrNull(now))
        assertNull(
            CalendarSnapshot(
                today,
                "F",
                "M",
                listOf(day(today, EventItem(LocalTime.of(14, 0), "x"))),
                hasCalendarAccess = false,
            ).todayEventOrNull(now),
        )
        assertNull(
            CalendarSnapshot(
                today,
                "F",
                "M",
                listOf(day(today, EventItem(LocalTime.of(14, 0), "x"))),
                hasCalendarAccess = true,
                queryFailed = true,
            ).todayEventOrNull(now),
        )
    }

    @Test fun `todayEventOrNull's returned date is always today`() {
        val allDay = EventItem(null, "Holiday")
        assertEquals(today, snapshot(day(today, allDay)).todayEventOrNull(now)?.date)
    }
}

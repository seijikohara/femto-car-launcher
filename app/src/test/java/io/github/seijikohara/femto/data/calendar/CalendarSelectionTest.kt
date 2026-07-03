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

    @Test fun `skips today events that already started`() {
        val past = EventItem(LocalTime.of(9, 0), "Standup")
        val next = EventItem(LocalTime.of(14, 0), "Pickup")
        assertEquals(next, snapshot(day(today, past, next)).nextUpcomingEventOrNull(now))
    }

    @Test fun `keeps an all-day event today`() {
        val allDay = EventItem(null, "Holiday")
        assertEquals(
            allDay,
            snapshot(day(today, EventItem(LocalTime.of(9, 0), "Past"), allDay)).nextUpcomingEventOrNull(now),
        )
    }

    @Test fun `an event starting exactly now is upcoming`() {
        val nowEvent = EventItem(LocalTime.of(12, 0), "Meeting")
        assertEquals(nowEvent, snapshot(day(today, nowEvent)).nextUpcomingEventOrNull(now))
    }

    @Test fun `falls through to a future day when today has only past events`() {
        val tomorrow = today.plusDays(1)
        val futureEarly = EventItem(LocalTime.of(8, 0), "Early tomorrow")
        val snap = snapshot(day(today, EventItem(LocalTime.of(9, 0), "Past")), day(tomorrow, futureEarly))
        assertEquals(futureEarly, snap.nextUpcomingEventOrNull(now))
    }

    @Test fun `returns null when access denied or query failed or null`() {
        assertNull((null as CalendarSnapshot?).nextUpcomingEventOrNull(now))
        assertNull(
            CalendarSnapshot(
                today,
                "F",
                "M",
                listOf(day(today, EventItem(LocalTime.of(14, 0), "x"))),
                hasCalendarAccess = false,
            ).nextUpcomingEventOrNull(now),
        )
        assertNull(
            CalendarSnapshot(
                today,
                "F",
                "M",
                listOf(day(today, EventItem(LocalTime.of(14, 0), "x"))),
                hasCalendarAccess = true,
                queryFailed = true,
            ).nextUpcomingEventOrNull(now),
        )
    }
}

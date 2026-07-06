package io.github.seijikohara.femto.data.calendar

import io.github.seijikohara.femto.data.clock.ClockTick
import io.github.seijikohara.femto.data.display.BriefingScope
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
        assertEquals(
            next,
            snapshot(day(today, past, next)).nextUpcomingEventOrNull(now, BriefingScope.UPCOMING)?.event,
        )
    }

    @Test fun `keeps an all-day event today`() {
        val allDay = EventItem(null, "Holiday")
        assertEquals(
            allDay,
            snapshot(day(today, EventItem(LocalTime.of(9, 0), "Past"), allDay))
                .nextUpcomingEventOrNull(now, BriefingScope.UPCOMING)
                ?.event,
        )
    }

    @Test fun `an event starting exactly now is upcoming`() {
        val nowEvent = EventItem(LocalTime.of(12, 0), "Meeting")
        assertEquals(
            nowEvent,
            snapshot(day(today, nowEvent)).nextUpcomingEventOrNull(now, BriefingScope.UPCOMING)?.event,
        )
    }

    @Test fun `falls through to a future day when today has only past events`() {
        val tomorrow = today.plusDays(1)
        val futureEarly = EventItem(LocalTime.of(8, 0), "Early tomorrow")
        val snap = snapshot(day(today, EventItem(LocalTime.of(9, 0), "Past")), day(tomorrow, futureEarly))
        assertEquals(futureEarly, snap.nextUpcomingEventOrNull(now, BriefingScope.UPCOMING)?.event)
    }

    @Test fun `returns null when access denied or query failed or null`() {
        assertNull((null as CalendarSnapshot?).nextUpcomingEventOrNull(now, BriefingScope.UPCOMING))
        assertNull(
            CalendarSnapshot(
                today,
                "F",
                "M",
                listOf(day(today, EventItem(LocalTime.of(14, 0), "x"))),
                hasCalendarAccess = false,
            ).nextUpcomingEventOrNull(now, BriefingScope.UPCOMING),
        )
        assertNull(
            CalendarSnapshot(
                today,
                "F",
                "M",
                listOf(day(today, EventItem(LocalTime.of(14, 0), "x"))),
                hasCalendarAccess = true,
                queryFailed = true,
            ).nextUpcomingEventOrNull(now, BriefingScope.UPCOMING),
        )
    }

    @Test fun `TODAY scope excludes a tomorrow-only event`() {
        val tomorrow = today.plusDays(1)
        val snap = snapshot(day(tomorrow, EventItem(LocalTime.of(8, 0), "Tomorrow only")))
        assertNull(snap.nextUpcomingEventOrNull(now, BriefingScope.TODAY))
    }

    @Test fun `TODAY scope includes today's upcoming event`() {
        val next = EventItem(LocalTime.of(14, 0), "Pickup")
        val snap = snapshot(day(today, next))
        assertEquals(next, snap.nextUpcomingEventOrNull(now, BriefingScope.TODAY)?.event)
    }

    @Test fun `THROUGH_TOMORROW scope includes today and tomorrow but not the day after`() {
        val tomorrow = today.plusDays(1)
        val dayAfter = today.plusDays(2)
        val tomorrowEvent = EventItem(LocalTime.of(8, 0), "Tomorrow")
        val snap = snapshot(day(tomorrow, tomorrowEvent), day(dayAfter, EventItem(LocalTime.of(9, 0), "Later")))
        assertEquals(tomorrowEvent, snap.nextUpcomingEventOrNull(now, BriefingScope.THROUGH_TOMORROW)?.event)

        val dayAfterOnly = snapshot(day(dayAfter, EventItem(LocalTime.of(9, 0), "Later")))
        assertNull(dayAfterOnly.nextUpcomingEventOrNull(now, BriefingScope.THROUGH_TOMORROW))
    }

    @Test fun `UPCOMING scope includes a far-off event`() {
        val farOff = today.plusMonths(2)
        val farEvent = EventItem(LocalTime.of(9, 0), "Far off")
        val snap = snapshot(day(farOff, farEvent))
        assertEquals(farEvent, snap.nextUpcomingEventOrNull(now, BriefingScope.UPCOMING)?.event)
    }

    @Test fun `returned date is the parent day cell's date`() {
        val tomorrow = today.plusDays(1)
        val tomorrowEvent = EventItem(LocalTime.of(8, 0), "Tomorrow")
        val snap = snapshot(day(tomorrow, tomorrowEvent))
        assertEquals(tomorrow, snap.nextUpcomingEventOrNull(now, BriefingScope.UPCOMING)?.date)
    }

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
        // upcoming time still wins — unlike nextUpcomingEventOrNull's
        // first-match order, this selection never treats "sorts earlier" as
        // "wins".
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

package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.CalendarSnapshot
import io.github.seijikohara.femto.data.DayCell
import io.github.seijikohara.femto.data.EventItem
import java.time.LocalDate
import java.time.LocalTime

internal fun fakeCalendarSnapshot(
    today: LocalDate = LocalDate.of(2026, 5, 1),
    weekday: String = "Friday",
    monthLabel: String = "May 2026",
    dayStrip: List<DayCell> =
        listOf(
            DayCell(LocalDate.of(2026, 5, 1), "Fri", true),
            DayCell(LocalDate.of(2026, 5, 2), "Sat", false),
            DayCell(LocalDate.of(2026, 5, 3), "Sun", true),
            DayCell(LocalDate.of(2026, 5, 4), "Mon", false),
            DayCell(LocalDate.of(2026, 5, 5), "Tue", false),
            DayCell(LocalDate.of(2026, 5, 6), "Wed", true),
        ),
    events: List<EventItem> =
        listOf(
            EventItem(LocalTime.of(10, 30), "Team standup"),
            EventItem(LocalTime.of(14, 0), "Pick up kids"),
        ),
    hasCalendarAccess: Boolean = true,
): CalendarSnapshot =
    CalendarSnapshot(
        today = today,
        weekday = weekday,
        monthLabel = monthLabel,
        dayStrip = dayStrip,
        events = events,
        hasCalendarAccess = hasCalendarAccess,
    )

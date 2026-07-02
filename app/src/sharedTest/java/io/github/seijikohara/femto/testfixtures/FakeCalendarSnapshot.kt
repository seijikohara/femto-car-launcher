package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.calendar.CalendarSnapshot
import io.github.seijikohara.femto.data.calendar.DayCell
import io.github.seijikohara.femto.data.calendar.EventItem
import java.time.LocalDate
import java.time.LocalTime

internal fun fakeCalendarSnapshot(
    today: LocalDate = LocalDate.of(2026, 5, 1),
    weekday: String = "Friday",
    monthLabel: String = "May 2026",
    days: List<DayCell> =
        listOf(
            DayCell(
                LocalDate.of(2026, 5, 1),
                "Fri",
                listOf(
                    EventItem(LocalTime.of(10, 30), "Team standup", endTime = LocalTime.of(11, 0), location = "Room 4"),
                    EventItem(LocalTime.of(14, 0), "Pick up kids", endTime = LocalTime.of(14, 30)),
                ),
            ),
            DayCell(LocalDate.of(2026, 5, 2), "Sat", emptyList()),
            DayCell(LocalDate.of(2026, 5, 3), "Sun", listOf(EventItem(LocalTime.of(9, 0), "Brunch"))),
            DayCell(LocalDate.of(2026, 5, 4), "Mon", emptyList()),
            DayCell(LocalDate.of(2026, 5, 5), "Tue", emptyList()),
            DayCell(LocalDate.of(2026, 5, 6), "Wed", listOf(EventItem(null, "Holiday"))),
        ),
    hasCalendarAccess: Boolean = true,
    queryFailed: Boolean = false,
): CalendarSnapshot =
    CalendarSnapshot(
        today = today,
        weekday = weekday,
        monthLabel = monthLabel,
        days = days,
        hasCalendarAccess = hasCalendarAccess,
        queryFailed = queryFailed,
    )

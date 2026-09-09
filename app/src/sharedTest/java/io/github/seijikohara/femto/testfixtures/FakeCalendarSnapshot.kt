package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.calendar.CalendarSnapshot
import io.github.seijikohara.femto.data.calendar.DayCell
import io.github.seijikohara.femto.data.calendar.EventItem
import java.time.LocalDate
import java.time.LocalTime

// The three calendars the fixture's window spans — a work, a family and a
// holidays calendar — in the opaque display colours the provider would hand
// the repository (EventItem.color). Three, so the captures show the colour bar
// telling calendars apart, not one colour repeated.
private const val WORK_CALENDAR_COLOR = 0xFF3B82F6.toInt()
private const val FAMILY_CALENDAR_COLOR = 0xFF22C55E.toInt()
private const val HOLIDAYS_CALENDAR_COLOR = 0xFFF59E0B.toInt()

internal fun fakeCalendarSnapshot(
    today: LocalDate = LocalDate.of(2026, 5, 1),
    days: List<DayCell> =
        listOf(
            DayCell(
                LocalDate.of(2026, 5, 1),
                "Fri",
                listOf(
                    EventItem(
                        LocalTime.of(10, 30),
                        "Team standup",
                        endTime = LocalTime.of(11, 0),
                        location = "Room 4",
                        color = WORK_CALENDAR_COLOR,
                    ),
                    EventItem(
                        LocalTime.of(14, 0),
                        "Pick up kids",
                        endTime = LocalTime.of(14, 30),
                        color = FAMILY_CALENDAR_COLOR,
                    ),
                ),
            ),
            DayCell(LocalDate.of(2026, 5, 2), "Sat", emptyList()),
            DayCell(
                LocalDate.of(2026, 5, 3),
                "Sun",
                listOf(EventItem(LocalTime.of(9, 0), "Brunch", color = FAMILY_CALENDAR_COLOR)),
            ),
            DayCell(LocalDate.of(2026, 5, 4), "Mon", emptyList()),
            DayCell(LocalDate.of(2026, 5, 5), "Tue", emptyList()),
            DayCell(
                LocalDate.of(2026, 5, 6),
                "Wed",
                listOf(EventItem(null, "Holiday", color = HOLIDAYS_CALENDAR_COLOR)),
            ),
        ),
    hasCalendarAccess: Boolean = true,
    queryFailed: Boolean = false,
    // Gates the per-calendar colour bar. True by default, with the events above
    // coloured per calendar exactly as the provider does, so the card's and the
    // panel's bars have a golden; false renders the single-calendar form.
    multipleCalendarsVisible: Boolean = true,
): CalendarSnapshot =
    CalendarSnapshot(
        today = today,
        days = days,
        hasCalendarAccess = hasCalendarAccess,
        queryFailed = queryFailed,
        multipleCalendarsVisible = multipleCalendarsVisible,
    )

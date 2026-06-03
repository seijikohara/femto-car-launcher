package io.github.seijikohara.femto.data

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.LocalTime

/**
 * Calendar surface for the dashboard.
 *
 * The shape mirrors the visual hierarchy of the calendar card: a hero
 * "today" anchor on the left, a 6-day strip rolling forward, and a list of
 * the next few events. The strip is always six entries starting at [today]
 * so the card can iterate without size guards.
 *
 * `null` is used at the [HomeUiState] level to denote "not loaded yet or
 * permission denied"; the card renders an empty state in that case.
 */
@Immutable
data class CalendarSnapshot(
    val today: LocalDate,
    val weekday: String,
    val monthLabel: String,
    val dayStrip: List<DayCell>,
    val events: List<EventItem>,
)

@Immutable
data class DayCell(
    val date: LocalDate,
    val weekdayLetter: String,
    val hasEvent: Boolean,
)

@Immutable
data class EventItem(
    // null marks an all-day event: it has no clock time, so the card renders
    // an "all day" label instead of a formatted time.
    val time: LocalTime?,
    val title: String,
)

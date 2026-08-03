package io.github.seijikohara.femto.data.calendar

import io.github.seijikohara.femto.data.calendar.CalendarRepository.Companion.WINDOW_DAYS
import io.github.seijikohara.femto.data.calendar.CalendarSnapshot.Companion.PANEL_MIN_LOOKAHEAD_DAYS
import io.github.seijikohara.femto.testfixtures.fakeCalendarSnapshot
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two dashboard surfaces derive their day lists from one snapshot:
 * the compact card takes [CalendarSnapshot.visibleDays], the maximize panel
 * takes [CalendarSnapshot.agendaDays]. They are allowed to differ in *rhythm*
 * (the panel keeps the free days between events; the card skips them) but the
 * panel must never carry fewer events than the card it expanded from.
 *
 * That invariant was broken: the panel used its own 14-day look-ahead while the
 * card walked the snapshot's full 30, so maximizing a card made every event past
 * the second week disappear.
 */
class CalendarSurfaceDaysTest {
    private fun day(
        offset: Long,
        vararg titles: String,
    ) = DayCell(
        date = TODAY.plusDays(offset),
        weekdayLetter = "D",
        events = titles.map { EventItem(LocalTime.NOON, it) },
    )

    private fun snapshotOf(days: List<DayCell>) = fakeCalendarSnapshot(today = TODAY, days = days)

    private fun offsetsOf(days: List<DayCell>) = days.map { ChronoUnit.DAYS.between(TODAY, it.date) }

    @Test
    fun `the panel carries every event the card shows`() {
        // An event on the window's last day is the case the old 14-day panel cap
        // dropped.
        val last = WINDOW.last
        val snapshot = snapshotOf(WINDOW.map { offset -> if (offset == last) day(offset, "Far") else day(offset) })

        val cardEvents = snapshot.visibleDays.flatMap { it.events }
        val panelEvents = snapshot.agendaDays.flatMap { it.events }

        assertEquals(listOf("Far"), cardEvents.map { it.title })
        assertTrue(
            panelEvents.containsAll(cardEvents),
            "the panel dropped events the card shows: ${cardEvents - panelEvents.toSet()}",
        )
    }

    @Test
    fun `the panel keeps the free days between events`() {
        // The rhythm the panel spends its extra room on — the card skips these.
        val snapshot = snapshotOf(listOf(day(0, "Now"), day(1), day(2), day(3, "Later")))

        assertEquals(listOf(0L, 1L, 2L, 3L), offsetsOf(snapshot.agendaDays))
        assertEquals(listOf(0L, 3L), offsetsOf(snapshot.visibleDays))
    }

    @Test
    fun `the panel drops the free days trailing the last event`() {
        // A quiet tail is nothing but blank "No events" rows to scroll past, so
        // the agenda stops at the last event — once past the look-ahead floor.
        val lastEvent = PANEL_MIN_LOOKAHEAD_DAYS + 4L
        val snapshot = snapshotOf(WINDOW.map { if (it == lastEvent) day(it, "Only") else day(it) })

        assertEquals(lastEvent, offsetsOf(snapshot.agendaDays).last())
    }

    @Test
    fun `the panel still looks ahead when nothing is scheduled`() {
        // Trimming to the last event must not collapse the panel to a single row:
        // a quiet month would then show less agenda than it ever has.
        val snapshot = snapshotOf(WINDOW.map { offset -> day(offset) })

        assertEquals(PANEL_MIN_LOOKAHEAD_DAYS, snapshot.agendaDays.size)
        assertEquals(listOf(TODAY), snapshot.visibleDays.map { it.date })
    }

    @Test
    fun `the panel never splits a lone day into two columns`() {
        // The landscape spread sends the last day to the right column, so a
        // one-day agenda would render with a blank left half. The floor is what
        // keeps that unreachable — assert it rather than trust it.
        assertTrue(PANEL_MIN_LOOKAHEAD_DAYS >= 2)
        assertTrue(snapshotOf(WINDOW.map { offset -> day(offset) }).agendaDays.size >= 2)
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 5, 1)

        // The repository's rendered window; the fixture mirrors it so the
        // card-vs-panel comparison runs over a production-shaped snapshot.
        val WINDOW = 0L until WINDOW_DAYS.toLong()
    }
}
